"""Eval harness over the shared golden set (packages/ui-dsl/examples).

Every distilled model must pass this before model-registry will promote it (RolloutPolicy.canPromote).
Run: python -m pulse_ai.eval --examples ../packages/ui-dsl/examples
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path

from .frames import FrameModel, KeywordBaseline
from .schemas import IntentFrame


@dataclass
class Case:
    name: str
    utterance: str
    vision_tags: list[str]
    expected: IntentFrame


@dataclass
class Report:
    model: str
    total: int
    task_acc: float
    category_acc: float
    anchor_acc: float
    failures: list[str]

    @property
    def passed(self) -> bool:
        return self.task_acc >= 0.95 and self.category_acc >= 0.9 and self.anchor_acc >= 0.9


def load_cases(examples_dir: Path) -> list[Case]:
    cases = []
    for p in sorted(examples_dir.glob("*.json")):
        raw = json.loads(p.read_text())
        cases.append(Case(p.stem, raw["utterance"], raw.get("visionTags", []), IntentFrame.model_validate(raw["frame"])))
    return cases


def _anchor(f: IntentFrame) -> tuple | None:
    if not f.query or not f.query.near:
        return None
    a = f.query.near.anchor
    return (a.type, (a.name or "").lower())


def evaluate(model: FrameModel, cases: list[Case]) -> Report:
    task = cat = anc = 0
    failures = []
    for c in cases:
        got = model.frame_for(c.utterance, "en-IN", c.vision_tags)
        ok_task = got.task == c.expected.task
        exp_cats = sorted(c.expected.query.categories) if c.expected.query else []
        got_cats = sorted(got.query.categories) if got.query else []
        ok_cat = exp_cats == got_cats
        ok_anchor = _anchor(got) == _anchor(c.expected)
        task += ok_task
        cat += ok_cat
        anc += ok_anchor
        if not (ok_task and ok_cat and ok_anchor):
            failures.append(f"{c.name}: task={got.task} cats={got_cats} anchor={_anchor(got)}")
    n = max(len(cases), 1)
    return Report(model.name, len(cases), task / n, cat / n, anc / n, failures)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--examples", type=Path, default=Path(__file__).resolve().parents[2] / "packages/ui-dsl/examples")
    args = ap.parse_args()
    r = evaluate(KeywordBaseline(), load_cases(args.examples))
    print(json.dumps({**r.__dict__, "passed": r.passed}, indent=2))


if __name__ == "__main__":
    main()
