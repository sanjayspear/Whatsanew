"""Cohort-level re-ranker. Mirrors the on-device LocalRanker formula with server-side affinities.

The device ranks with *personal* affinity; the server can only see DP-noised *cohort* affinity.
Phase 4 replaces this with a learned ranker (LightGBM/two-tower) trained on accepted batches;
the HTTP contract stays the same.
"""
from __future__ import annotations

from .schemas import Candidate, Scored


def similarity(signal_category: str, candidate: str) -> float:
    if candidate == signal_category or candidate.startswith(signal_category + ".") or signal_category.startswith(candidate + "."):
        return 1.0
    if signal_category.split(".")[0] == candidate.split(".")[0]:
        return 0.4
    return 0.0


def score(c: Candidate, radius_m: int, cohort_affinity: dict[str, float]) -> float:
    affinity = max((v * similarity(k, c.category) for k, v in cohort_affinity.items()), default=0.0)
    proximity = 1.0 - min(1.0, c.distanceMeters / max(radius_m, 1))
    quality = min(1.0, max(0.0, ((c.rating or 3.5) - 3.0) / 2.0))
    return 0.40 * min(affinity, 1.0) + 0.35 * proximity + 0.25 * quality


def rerank(candidates: list[Candidate], radius_m: int, cohort_affinity: dict[str, float]) -> list[Scored]:
    scored = [Scored(listingId=c.listingId, score=round(score(c, radius_m, cohort_affinity), 6)) for c in candidates]
    return sorted(scored, key=lambda s: s.score, reverse=True)
