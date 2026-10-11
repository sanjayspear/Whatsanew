"""Server-side frame producers behind one protocol, so eval and serving use identical code paths."""
from __future__ import annotations

import json
import re
from typing import Protocol

from .schemas import IntentFrame


class FrameModel(Protocol):
    name: str

    def frame_for(self, utterance: str, language_tag: str, vision_tags: list[str]) -> IntentFrame: ...


def parse_first_json_object(raw: str) -> IntentFrame | None:
    """Same extraction contract as the Kotlin LlmPrompt.parse: the first balanced object, or nothing."""
    start = raw.find("{")
    if start < 0:
        return None
    depth, in_str, esc = 0, False, False
    for i in range(start, len(raw)):
        ch = raw[i]
        if esc:
            esc = False
        elif ch == "\\" and in_str:
            esc = True
        elif ch == '"':
            in_str = not in_str
        elif not in_str and ch == "{":
            depth += 1
        elif not in_str and ch == "}":
            depth -= 1
            if depth == 0:
                try:
                    return IntentFrame.model_validate(json.loads(raw[start : i + 1]))
                except (ValueError, TypeError):
                    return None
    return None


class KeywordBaseline:
    """Tiny deterministic baseline. It is the floor every distilled model must beat in eval."""

    name = "keyword-baseline"
    _CATS = {
        "cafe": "food.cafe", "coffee": "food.cafe", "theatre": "arts.theatre", "drama": "arts.theatre",
        "dosa": "food.restaurant.south_indian", "yoga": "fitness.yoga", "comedy": "comedy.standup",
    }

    def frame_for(self, utterance: str, language_tag: str, vision_tags: list[str]) -> IntentFrame:
        text = " ".join([utterance, *vision_tags]).lower()
        if "theme" in text:
            return IntentFrame(task="CUSTOMIZE_THEME", theme={}, confidence=0.5)
        if re.search(r"\b(move|put)\b.*\bmap\b", text):
            return IntentFrame(task="CUSTOMIZE_LAYOUT", layout=[{"type": "move", "role": "map", "position": 0}], confidence=0.5)
        cats = sorted({v for k, v in self._CATS.items() if re.search(rf"\b{k}s?\b", text)})
        if not cats:
            return IntentFrame(task="UNKNOWN", confidence=0.2)
        return IntentFrame(task="DISCOVER", query={"categories": cats}, confidence=0.5)
