#!/usr/bin/env python3
"""Fail when multiplatform common sources depend on platform-specific APIs or Android app layers."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

IMPORT_RE = re.compile(r"^import\s+([^\s]+)", re.MULTILINE)

FORBIDDEN_PREFIXES = (
    "android.",
    "java.",
    "javax.",
    "kotlin.jvm.",
    "androidx.activity.",
    "androidx.core.",
    "androidx.fragment.",
    "androidx.lifecycle.",
    "io.ferventio.app.application.",
    "io.ferventio.app.data.",
    "io.ferventio.app.network.",
    "io.ferventio.app.push.",
)

COMMON_SOURCE_ROOTS = (
    "shared/src/commonMain/kotlin",
    "core/domain/src/main/kotlin",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    root = args.root.resolve()
    errors: list[str] = []

    for relative_root in COMMON_SOURCE_ROOTS:
        common_root = root / relative_root
        if not common_root.is_dir():
            errors.append(f"{relative_root} is missing")
            continue
        for path in sorted(common_root.rglob("*.kt")):
            text = path.read_text(encoding="utf-8")
            for imported in IMPORT_RE.findall(text):
                if imported.startswith(FORBIDDEN_PREFIXES):
                    relative = path.relative_to(root).as_posix()
                    errors.append(f"{relative} imports platform/app-layer type {imported}")

    if errors:
        print("KMP common source boundary validation failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("KMP common source boundaries OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
