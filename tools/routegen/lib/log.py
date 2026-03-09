"""Simple pipeline logger with stage markers and JSONL support."""

from __future__ import annotations

import json
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Optional


class PipelineLogger:
    def __init__(self, work_dir: Path, centre_slug: str, reset: bool = True) -> None:
        self.work_dir = work_dir
        self.centre_slug = centre_slug
        self.log_path = work_dir / "pipeline.log"
        if reset:
            self._reset_logs()

    def _reset_logs(self) -> None:
        self.work_dir.mkdir(parents=True, exist_ok=True)
        if self.log_path.exists():
            self.log_path.unlink()

    def _stamp(self) -> str:
        return datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S")

    def _write(self, level: str, message: str) -> None:
        line = f"[{self._stamp()}] [{level}] {message}"
        print(line)
        with self.log_path.open("a", encoding="utf-8") as handle:
            handle.write(line + os.linesep)

    def stage(self, name: str) -> None:
        self._write("STAGE", f"== {name} ==")

    def info(self, message: str) -> None:
        self._write("INFO", message)

    def warn(self, message: str) -> None:
        self._write("WARN", message)

    def error(self, message: str) -> None:
        self._write("ERROR", message)

    def write_json(self, filename: str, payload: Any) -> Path:
        path = self.work_dir / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, ensure_ascii=True)
        return path

    def write_jsonl(self, filename: str, payload: Any) -> Path:
        path = self.work_dir / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(payload, ensure_ascii=True) + "\n")
        return path

    def set_context(self, key: str, value: Any) -> None:
        self.write_json("context.json", {key: value})


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path
