"""Distillation pipeline: teacher -> LoRA student -> quantize -> export per platform -> eval -> sign -> publish.

Each stage is a pure function from a typed input artifact to an output artifact (content-addressed
on S3), so stages are cacheable, resumable and individually testable. Orchestrated by Argo Workflows
(see infra/), and runnable locally with --dry-run to print the plan.

    python pipelines/distill/pipeline.py --config pipelines/distill/config.yaml --dry-run
"""
from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass, field


@dataclass(frozen=True)
class Stage:
    name: str
    inputs: list[str]
    outputs: list[str]
    runs_on: str
    notes: str


@dataclass
class Plan:
    model_id: str = "pulse-intent"
    student: str = "google/gemma-3-1b-it"
    stages: list[Stage] = field(default_factory=list)


def build_plan() -> Plan:
    return Plan(stages=[
        Stage("build-dataset", ["s3://pulse-data/frames/accepted/*", "packages/ui-dsl/examples/*.json"],
              ["dataset.jsonl"], "cpu",
              "Teacher (large server LLM) labels redacted, consented utterances with IntentFrames; golden "
              "examples are pinned; synthetic paraphrases per locale (en, kn, hi, ta) widen coverage."),
        Stage("lora-finetune", ["dataset.jsonl"], ["adapter/"], "gpu:a10g",
              "PEFT LoRA r=16 on the student; output is JSON-only frames (same format as LlmPrompt)."),
        Stage("merge-and-quantize", ["adapter/"], ["student-int4/"], "gpu:a10g",
              "Merge adapter, then INT4 (per-channel, group 128) weight-only quantization."),
        Stage("export-mediapipe", ["student-int4/"], ["model.task"], "cpu",
              "ai-edge-torch -> TFLite -> MediaPipe .task bundle for Android (GPU delegate)."),
        Stage("export-gguf", ["student-int4/"], ["model.Q4_K_M.gguf"], "cpu",
              "llama.cpp convert + quantize for the iOS fallback rung and desktop dev."),
        Stage("export-coreml", ["student-int4/"], ["model.mlpackage"], "macos",
              "coremltools palettized INT4 (optional; Apple Foundation Models is the primary iOS rung)."),
        Stage("eval", ["model.task", "model.Q4_K_M.gguf", "packages/ui-dsl/examples"], ["eval.json"], "device-farm",
              "pulse_ai.eval golden accuracy gates plus on-device p50/p95 latency, RAM peak and thermal soak on "
              "the perf floor (Snapdragon 7-series, 6 GB)."),
        Stage("bsdiff-deltas", ["model.task", "previous/model.task"], ["model.task.delta"], "cpu",
              "Delta from the previous stable version (typically 5-15% of full size)."),
        Stage("sign-and-publish", ["eval.json", "model.task", "model.task.delta"], ["manifest.json"], "kms",
              "Ed25519 signature over ModelManifest.canonicalPayload(); POST to model-registry-service "
              "at rolloutPercent=1 (RolloutPolicy then ramps)."),
    ])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--config")
    args = ap.parse_args()
    plan = build_plan()
    if args.dry_run:
        print(json.dumps(asdict(plan), indent=2))
        return
    raise SystemExit("Execution runs on Argo (infra/argo). Use --dry-run locally.")


if __name__ == "__main__":
    main()
