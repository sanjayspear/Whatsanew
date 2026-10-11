"""FastAPI entry point. Called by Spring services (intent-service, personalization-service) over
the mesh, never by devices directly. Production serving runs the distilled model on Triton;
this process hosts routing, validation and the cohort store."""
from __future__ import annotations

from fastapi import FastAPI

from .frames import KeywordBaseline
from .rerank import rerank
from .schemas import FrameRequest, IntentFrame, RerankRequest, RerankResponse

app = FastAPI(title="pulse-ai", version="0.1.0")
_model = KeywordBaseline()  # swapped for the Triton-backed client in P4
_cohorts: dict[str, dict[str, float]] = {"default": {"arts": 0.3, "food": 0.3}}
MODEL_VERSION = "baseline-0"


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok", "model": _model.name}


@app.post("/v1/frame", response_model=IntentFrame)
def frame(req: FrameRequest) -> IntentFrame:
    return _model.frame_for(req.utterance, req.languageTag, req.visionTags)


@app.post("/v1/rerank", response_model=RerankResponse)
def rerank_endpoint(req: RerankRequest) -> RerankResponse:
    affinity = _cohorts.get(req.cohort, _cohorts["default"])
    return RerankResponse(modelVersion=MODEL_VERSION, ranked=rerank(req.candidates, req.radiusMeters, affinity))
