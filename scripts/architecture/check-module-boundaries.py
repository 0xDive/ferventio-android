#!/usr/bin/env python3
"""Fail when source dependencies violate the documented Android module boundaries."""
from __future__ import annotations

import argparse
import re
import sys
import tomllib
from pathlib import Path

IMPORT_RE = re.compile(r"^import\s+([^\s]+)", re.MULTILINE)
TOOLCHAIN_17_PATTERNS = (
    "JavaLanguageVersion.of(17)",
    "jvmToolchain(17)",
    "languageVersion.set(JavaLanguageVersion.of(17))",
)
MAX_UI_FILE_LINES = 1800


def kotlin_files(root: Path) -> list[Path]:
    return sorted(root.rglob("*.kt")) if root.exists() else []


def imports(path: Path) -> set[str]:
    return set(IMPORT_RE.findall(path.read_text(encoding="utf-8")))


def rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    root = args.root.resolve()
    errors: list[str] = []

    if (root / "buildSrc").exists():
        errors.append("buildSrc/ is forbidden; repository plugins belong in isolated build-logic/")

    build_logic_source = root / "build-logic/src/main/kotlin/io/ferventio/build"
    required_build_logic_sources = (
        "FerventioVerificationPlugin.kt",
        "ExportOsvDependencyInventoryTask.kt",
        "VerifyFerventioServerCertificatePinsTask.kt",
        "VerifyPlayCrashReportingConfigurationTask.kt",
        "VerifyPrivacyPolicyConfigurationTask.kt",
        "VerifyRuntimeClasspathTask.kt",
    )
    for filename in required_build_logic_sources:
        if not (build_logic_source / filename).is_file():
            errors.append(
                f"build-logic is missing io.ferventio.build implementation source {filename}"
            )

    domain_root = root / "core/domain/src"
    for path in kotlin_files(domain_root):
        for name in imports(path):
            if name.startswith("android."):
                errors.append(f"{rel(path, root)} imports Android framework type {name}")
            if name.startswith("androidx.") and not name.startswith("androidx.compose.runtime."):
                errors.append(f"{rel(path, root)} imports non-runtime AndroidX type {name}")
            is_test_fixture = "/src/test/" in path.as_posix() and name.startswith("io.ferventio.app.testing.")
            if (
                name.startswith("io.ferventio.app.")
                and not name.startswith("io.ferventio.app.domain.")
                and not is_test_fixture
            ):
                errors.append(f"{rel(path, root)} imports application package {name}")

    database_root = root / "core/database/src"
    forbidden_database_prefixes = (
        "io.ferventio.app.application.",
        "io.ferventio.app.network.",
        "io.ferventio.app.push.",
        "io.ferventio.app.twitch.",
        "io.ferventio.app.ui.",
    )
    for path in kotlin_files(database_root):
        for name in imports(path):
            if name.startswith(forbidden_database_prefixes):
                errors.append(f"{rel(path, root)} imports upper-layer package {name}")

    database_build_script = root / "core/database/build.gradle.kts"
    database_build_text = database_build_script.read_text(encoding="utf-8")
    database_uses_serialization = any(
        any(name.startswith("kotlinx.serialization.") for name in imports(path))
        for path in kotlin_files(database_root)
    )
    if database_uses_serialization and "libs.kotlinx.serialization.json" not in database_build_text:
        errors.append(
            "core:database imports kotlinx.serialization but does not declare "
            "implementation(libs.kotlinx.serialization.json)"
        )

    for path in sorted((root / "app/src/main/kotlin/io/ferventio/app/ui").rglob("*.kt")):
        line_count = len(path.read_text(encoding="utf-8").splitlines())
        if line_count > MAX_UI_FILE_LINES:
            errors.append(
                f"{rel(path, root)} has {line_count} lines; split UI files above {MAX_UI_FILE_LINES} lines"
            )

        non_blank_lines = [
            line.strip()
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]
        if non_blank_lines and non_blank_lines[-1].startswith(("@Composable", "@OptIn")):
            errors.append(
                f"{rel(path, root)} ends with a dangling annotation; move it with the declaration "
                "when splitting Kotlin source files"
            )

    gradle_files = [
        *root.rglob("*.gradle.kts"),
        *root.rglob("*.gradle"),
    ]
    for path in sorted(set(gradle_files)):
        text = path.read_text(encoding="utf-8")
        for pattern in TOOLCHAIN_17_PATTERNS:
            if pattern in text:
                errors.append(
                    f"{rel(path, root)} requests a Java 17 compiler toolchain via {pattern!r}; "
                    "Gradle must run on Temurin 25 while emitting Java 17 bytecode"
                )

    # Use explicit checks because both daemon criteria live in the same file.
    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8")
    for module in (":app", ":benchmark", ":core:domain", ":core:database"):
        if module not in settings:
            errors.append(f"settings.gradle.kts does not include {module}")
    criteria = (root / "gradle/gradle-daemon-jvm.properties").read_text(encoding="utf-8")
    for line in ("toolchainVersion=25", "toolchainVendor=ADOPTIUM"):
        if line not in criteria:
            errors.append(f"gradle/gradle-daemon-jvm.properties is missing {line}")

    # The Gradle distributions endpoint does not publish a wrapper JAR for every
    # historical release. Bootstrap the verified JAR from Gradle's official
    # source repository instead, while keeping the published SHA-256 check.
    wrapper_scripts = (root / "gradlew", root / "gradlew.bat")
    broken_wrapper_url = "services.gradle.org/distributions/gradle-9.3.1-wrapper.jar"
    expected_wrapper_url = (
        "raw.githubusercontent.com/gradle/gradle/v9.3.1/"
        "gradle/wrapper/gradle-wrapper.jar"
    )
    expected_wrapper_sha256 = (
        "b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13"
    )
    for wrapper_script in wrapper_scripts:
        wrapper_text = wrapper_script.read_text(encoding="utf-8")
        if broken_wrapper_url in wrapper_text:
            errors.append(
                f"{rel(wrapper_script, root)} uses the nonexistent Gradle 9.3.1 wrapper JAR URL"
            )
        if expected_wrapper_url not in wrapper_text:
            errors.append(
                f"{rel(wrapper_script, root)} does not use Gradle's official v9.3.1 wrapper JAR source"
            )
        if expected_wrapper_sha256 not in wrapper_text:
            errors.append(
                f"{rel(wrapper_script, root)} is missing the published Gradle 9.3.1 wrapper JAR checksum"
            )

    catalog_path = root / "gradle/libs.versions.toml"
    with catalog_path.open("rb") as catalog_file:
        catalog = tomllib.load(catalog_file)
    versions = catalog.get("versions", {})
    libraries = catalog.get("libraries", {})
    agp_version = str(versions.get("agp", ""))
    baseline_version = str(versions.get("baselineprofile", ""))
    benchmark_version = str(versions.get("benchmark", ""))
    uiautomator_version = str(versions.get("uiautomator", ""))
    if agp_version.startswith("9.") and not baseline_version.startswith("1.5."):
        errors.append(
            "AGP 9 requires the Baseline Profile 1.5 plugin line; "
            f"found AGP {agp_version} with Baseline Profile {baseline_version}"
        )
    if benchmark_version != baseline_version:
        errors.append(
            "Baseline Profile plugin and Macrobenchmark versions must be aligned; "
            f"found {baseline_version} and {benchmark_version}"
        )
    if baseline_version.startswith("1.5.") and uiautomator_version != "2.4.0":
        errors.append(
            "Benchmark 1.5 must use the UiAutomator 2.4 toolchain; "
            f"found {uiautomator_version}"
        )

    # Compose Material icons are a legacy, separately versioned artifact. The
    # release line stopped at 1.7.8 and must not inherit the current Compose
    # runtime version (for example 1.11.4), because that coordinate does not
    # exist in Google Maven.
    material_icons_version = str(versions.get("material-icons", ""))
    material_icons_library = libraries.get("androidx-compose-material-icons-extended", {})
    material_icons_ref = str(material_icons_library.get("version", {}).get("ref", ""))
    if material_icons_version != "1.7.8":
        errors.append(
            "androidx.compose.material:material-icons-extended must be pinned to 1.7.8; "
            f"found {material_icons_version or 'no dedicated version'}"
        )
    if material_icons_ref != "material-icons":
        errors.append(
            "material-icons-extended must use the dedicated material-icons version key; "
            f"found version.ref={material_icons_ref or '<missing>'}"
        )

    # The warning controls are registered as a nested Gradle extension at runtime,
    # but Baseline Profile 1.5 does not expose a Kotlin DSL type-safe accessor for
    # this project combination. Keeping `warnings {}` in either module makes script
    # compilation fail before Android configuration starts.
    for build_script in (root / "app/build.gradle.kts", root / "benchmark/build.gradle.kts"):
        build_text = build_script.read_text(encoding="utf-8")
        if re.search(r"baselineProfile\s*\{[\s\S]*?warnings\s*\{", build_text):
            errors.append(
                f"{rel(build_script, root)} uses the unavailable Baseline Profile "
                "warnings Kotlin DSL accessor; leave warnings at plugin defaults"
            )

    if errors:
        print("Android architecture validation failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Android module boundaries OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
