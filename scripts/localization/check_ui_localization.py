#!/usr/bin/env python3
"""Verify generated localization artifacts and common Compose localization rules."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

from generate_ui_catalog import collect, load_translations

ROOT = Path(__file__).resolve().parents[2]
GENERATED = (
    ROOT / "app/src/main/res/values/ui_strings_generated.xml",
    ROOT / "app/src/main/res/values-ru/ui_strings_generated.xml",
    ROOT / "app/src/main/kotlin/io/ferventio/app/ui/app/UiStringCatalog.generated.kt",
)


def main() -> int:
    before = {path: path.read_bytes() if path.exists() else None for path in GENERATED}
    subprocess.run(
        [sys.executable, str(ROOT / "scripts/localization/generate_ui_catalog.py")],
        cwd=ROOT,
        check=True,
    )
    stale = [path for path in GENERATED if before[path] != path.read_bytes()]

    default_root = ET.parse(GENERATED[0]).getroot()
    russian_root = ET.parse(GENERATED[1]).getroot()
    default_names = {node.attrib["name"] for node in default_root if node.tag == "string"}
    russian_names = {node.attrib["name"] for node in russian_root if node.tag == "string"}
    if default_names != russian_names:
        print("Generated default and Russian catalogs contain different keys.", file=sys.stderr)
        return 1

    source_values = collect()
    translations = load_translations()
    missing_translations = [value for value in source_values if value not in translations]
    obsolete_translations = [value for value in translations if value not in set(source_values)]

    raw_text_calls: list[str] = []
    raw_accessibility_strings: list[str] = []
    raw_snackbar_strings: list[str] = []
    ui_root = ROOT / "app/src/main/kotlin/io/ferventio/app/ui"
    for path in ui_root.rglob("*.kt"):
        if path.name in {"LocalizedResources.kt", "LocalizedText.kt"}:
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if re.search(r"(?<![A-Za-z0-9_])Text\(", line):
                raw_text_calls.append(f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}")
            if re.search(r"contentDescription\s*=\s*\"[^\"]*[А-Яа-яЁё]", line):
                raw_accessibility_strings.append(
                    f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}"
                )
            if re.search(r"showSnackbar\s*\(\s*\"[^\"]*[А-Яа-яЁё]", line):
                raw_snackbar_strings.append(
                    f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}"
                )

    if stale:
        print("Generated localization files were stale:", file=sys.stderr)
        for path in stale:
            print(f"  {path.relative_to(ROOT)}", file=sys.stderr)
    if missing_translations:
        print("Missing explicit English translations:", file=sys.stderr)
        for value in missing_translations:
            print(f"  {value!r}", file=sys.stderr)
    if obsolete_translations:
        print("Remove obsolete English translations:", file=sys.stderr)
        for value in obsolete_translations:
            print(f"  {value!r}", file=sys.stderr)
    if raw_text_calls:
        print("Use LocalizedText for Compose interface text:", file=sys.stderr)
        print("\n".join(f"  {item}" for item in raw_text_calls), file=sys.stderr)
    if raw_accessibility_strings:
        print("Use localizedString for accessibility descriptions:", file=sys.stderr)
        print("\n".join(f"  {item}" for item in raw_accessibility_strings), file=sys.stderr)
    if raw_snackbar_strings:
        print("Resolve Snackbar text before entering callbacks:", file=sys.stderr)
        print("\n".join(f"  {item}" for item in raw_snackbar_strings), file=sys.stderr)
    return 1 if (
        stale
        or missing_translations
        or obsolete_translations
        or raw_text_calls
        or raw_accessibility_strings
        or raw_snackbar_strings
    ) else 0


if __name__ == "__main__":
    raise SystemExit(main())
