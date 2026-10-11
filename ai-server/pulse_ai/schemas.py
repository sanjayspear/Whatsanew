"""Wire schemas mirroring packages/ui-dsl (Kotlin is the source of truth; golden JSON keeps them in sync)."""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

Task = Literal["DISCOVER", "REFINE", "CUSTOMIZE_THEME", "CUSTOMIZE_LAYOUT", "ACT", "UNKNOWN"]


class Anchor(BaseModel):
    type: Literal["here", "place", "point"]
    name: str | None = None
    lat: float | None = None
    lng: float | None = None


class GeoFilter(BaseModel):
    anchor: Anchor
    radiusMeters: int = 5000


class TimeWindow(BaseModel):
    preset: str | None = None


class ListingQuery(BaseModel):
    categories: list[str] = Field(default_factory=list)
    text: str | None = None
    near: GeoFilter | None = None
    time: TimeWindow | None = None
    freshness: str | None = None
    priceLevelMax: int | None = None
    openNow: bool | None = None


class IntentFrame(BaseModel):
    task: Task
    query: ListingQuery | None = None
    theme: dict | None = None
    layout: list[dict] = Field(default_factory=list)
    confidence: float = 1.0
    source: str = "SERVER"


class FrameRequest(BaseModel):
    utterance: str = Field(max_length=400)
    languageTag: str = "en-IN"
    visionTags: list[str] = Field(default_factory=list)


class Candidate(BaseModel):
    listingId: str
    category: str
    distanceMeters: float
    rating: float | None = None


class RerankRequest(BaseModel):
    cohort: str
    radiusMeters: int
    candidates: list[Candidate] = Field(max_length=200)


class Scored(BaseModel):
    listingId: str
    score: float


class RerankResponse(BaseModel):
    modelVersion: str
    ranked: list[Scored]
