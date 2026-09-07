#!/usr/bin/env python3
"""Enforce the resource-first localization contract.

LegacyUiStringCatalog is frozen compatibility glue for pre-resource UI literals.
New UI copy must use descriptive platform/shared string resources instead of extending
that catalog or reintroducing generated localization artifacts.
"""
from __future__ import annotations

import hashlib
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
LEGACY_CATALOG = ROOT / "app/src/main/kotlin/io/ferventio/app/ui/app/LegacyUiStringCatalog.kt"
LEGACY_CATALOG_SHA256 = "0872b7a81862f838bead4518d894ae03658fad504d68dee3ac2fedb4f56d553c"

FORBIDDEN_PATHS = (
    ROOT / "app/src/main/res/values/ui_strings_generated.xml",
    ROOT / "app/src/main/res/values-ru/ui_strings_generated.xml",
    ROOT / "app/src/main/kotlin/io/ferventio/app/ui/app/UiStringCatalog.generated.kt",
    ROOT / "scripts/localization/generate_ui_catalog.py",
    ROOT / "scripts/localization/build_en_translations.py",
    ROOT / "config/localization/ui_strings_en.json",
)
SOURCE_ROOTS = (
    ROOT / "app/src/main/kotlin",
    ROOT / "core/domain/src/main/kotlin",
    ROOT / "core/database/src/main/kotlin",
)
ANDROID_DEFAULT_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
ANDROID_RUSSIAN_STRINGS = ROOT / "app/src/main/res/values-ru/strings.xml"
SHARED_DEFAULT_STRINGS = ROOT / "shared/src/commonMain/composeResources/values/strings.xml"
SHARED_RUSSIAN_STRINGS = ROOT / "shared/src/commonMain/composeResources/values-ru/strings.xml"


def resource_names(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    return {
        node.attrib["name"]
        for node in root
        if node.tag in {"string", "plurals", "string-array"} and "name" in node.attrib
    }


def check_locale_pair(
    errors: list[str],
    label: str,
    default_path: Path,
    russian_path: Path,
    *,
    required: bool,
) -> None:
    if not default_path.exists() or not russian_path.exists():
        if required or default_path.exists() or russian_path.exists():
            missing = [
                path.relative_to(ROOT)
                for path in (default_path, russian_path)
                if not path.exists()
            ]
            errors.append(
                f"{label} localization is missing resource files:\n  "
                + "\n  ".join(map(str, missing))
            )
        return

    default = resource_names(default_path)
    russian = resource_names(russian_path)
    missing_ru = sorted(default - russian)
    missing_default = sorted(russian - default)
    if missing_ru:
        errors.append(
            f"{label} Russian strings are missing resource keys:\n  "
            + "\n  ".join(missing_ru)
        )
    if missing_default:
        errors.append(
            f"{label} default strings are missing resource keys:\n  "
            + "\n  ".join(missing_default)
        )


def main() -> int:
    errors: list[str] = []

    present = [path for path in FORBIDDEN_PATHS if path.exists()]
    if present:
        errors.append(
            "Generated localization artifacts must not exist:\n"
            + "\n".join(f"  {path.relative_to(ROOT)}" for path in present)
        )

    if not LEGACY_CATALOG.exists():
        errors.append("LegacyUiStringCatalog.kt is missing")
    else:
        actual_hash = hashlib.sha256(LEGACY_CATALOG.read_bytes()).hexdigest()
        if actual_hash != LEGACY_CATALOG_SHA256:
            errors.append(
                "LegacyUiStringCatalog.kt is frozen compatibility code; "
                "migrate copy to descriptive R.string resources instead of extending it"
            )

    check_locale_pair(
        errors,
        "Android",
        ANDROID_DEFAULT_STRINGS,
        ANDROID_RUSSIAN_STRINGS,
        required=True,
    )
    check_locale_pair(
        errors,
        "Shared Compose",
        SHARED_DEFAULT_STRINGS,
        SHARED_RUSSIAN_STRINGS,
        required=False,
    )

    direct_legacy_refs: list[str] = []
    for source_root in SOURCE_ROOTS:
        for path in source_root.rglob("*.kt"):
            if path == LEGACY_CATALOG or "/test/" in path.as_posix() or "/androidTest/" in path.as_posix():
                continue
            for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if "R.string.ui_" in line:
                    direct_legacy_refs.append(
                        f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}"
                    )
    if direct_legacy_refs:
        errors.append(
            "Do not reference legacy ui_* resources directly; add a descriptive R.string resource instead:\n  "
            + "\n  ".join(direct_legacy_refs)
        )

    forbidden_tokens = (
        "generate_ui_catalog.py",
        "build_en_translations.py",
        "ui_strings_en.json",
        "ui_strings_generated.xml",
        "UiStringCatalog.generated.kt",
    )
    stale_refs: list[str] = []
    for base in (ROOT / ".github", ROOT / "scripts", ROOT / "config"):
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path == Path(__file__):
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue
            for token in forbidden_tokens:
                if token in text:
                    stale_refs.append(f"{path.relative_to(ROOT)} references {token}")
    if stale_refs:
        errors.append(
            "Remove references to the deleted localization pipeline:\n  "
            + "\n  ".join(stale_refs)
        )

    if errors:
        print("\n\n".join(errors), file=sys.stderr)
        return 1

    print("resource-first localization contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
