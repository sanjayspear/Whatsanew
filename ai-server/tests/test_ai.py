from pathlib import Path

from fastapi.testclient import TestClient

from pulse_ai.app import app
from pulse_ai.eval import evaluate, load_cases
from pulse_ai.frames import KeywordBaseline, parse_first_json_object
from pulse_ai.rerank import rerank, similarity
from pulse_ai.schemas import Candidate

EXAMPLES = Path(__file__).resolve().parents[2] / "packages/ui-dsl/examples"


def test_golden_examples_load_with_python_schema():
    cases = load_cases(EXAMPLES)
    assert len(cases) == 5
    assert cases[0].expected.query.near.anchor.name == "Koramangala"


def test_baseline_is_measured_and_fails_the_promotion_gate():
    report = evaluate(KeywordBaseline(), load_cases(EXAMPLES))
    assert report.total == 5
    assert 0 < report.task_acc <= 1
    assert not report.passed, "the keyword baseline must not be promotable; it exists as a floor"


def test_similarity_matches_kotlin_ranker():
    assert similarity("arts.theatre", "arts.theatre.festival") == 1.0
    assert similarity("arts.theatre", "arts.cinema") == 0.4
    assert similarity("arts.theatre", "food.cafe") == 0.0


def test_rerank_prefers_cohort_affinity_then_proximity():
    ranked = rerank(
        [Candidate(listingId="cafe", category="food.cafe", distanceMeters=200, rating=4.5),
         Candidate(listingId="play", category="arts.theatre", distanceMeters=900, rating=4.5)],
        radius_m=3000, cohort_affinity={"arts": 1.0},
    )
    assert ranked[0].listingId == "play"


def test_json_extraction_contract():
    assert parse_first_json_object('noise {"task":"DISCOVER","query":{"text":"a } b"}} tail').query.text == "a } b"
    assert parse_first_json_object('{"task": ') is None


def test_http_endpoints():
    client = TestClient(app)
    assert client.get("/healthz").json()["status"] == "ok"
    r = client.post("/v1/frame", json={"utterance": "any theatre this weekend"})
    assert r.json()["task"] == "DISCOVER"
    r = client.post("/v1/rerank", json={"cohort": "default", "radiusMeters": 3000, "candidates": [
        {"listingId": "a", "category": "food.cafe", "distanceMeters": 100}]})
    assert r.json()["ranked"][0]["listingId"] == "a"
