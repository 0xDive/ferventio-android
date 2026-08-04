#!/usr/bin/env python3
"""Fail-closed repository secret checks without printing secret values."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

MAX_FILE_BYTES = 5 * 1024 * 1024

EXCLUDED_DIR_NAMES = {
    ".git",
    ".gradle",
    ".gradle-dist",
    ".idea",
    ".kotlin",
    ".security-tools",
    ".signing",
    ".secrets",
    ".vscode",
    "build",
    "security-reports",
}

EXCLUDED_RELATIVE_PATHS = {
    "local.properties",
}

ALLOWED_SENSITIVE_FILENAMES = {
    ".env.example",
    ".env.sample",
    ".env.template",
    "gradle.properties.example",
}

SENSITIVE_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".key"}
SENSITIVE_EXACT_NAMES = {"google-services.json", "id_rsa", "id_ed25519"}

CONTENT_RULES: tuple[tuple[str, re.Pattern[str]], ...] = (
    (
        "private-key",
        re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----"),
    ),
    (
        "google-api-key",
        re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b"),
    ),
    (
        "github-token",
        re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{40,})\b"),
    ),
    (
        "aws-access-key",
        re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b"),
    ),
    (
        "slack-token",
        re.compile(r"\bxox[baprs]-[0-9A-Za-z-]{20,}\b"),
    ),
    (
        "stripe-secret-key",
        re.compile(r"\bsk_(?:live|test)_[0-9A-Za-z]{20,}\b"),
    ),
)

GENERIC_ASSIGNMENT = re.compile(
    r"(?im)^[ \t]*(?:export[ \t]+)?"
    r"(?P<name>[A-Z0-9_.-]*(?:PASSWORD|PASSWD|TOKEN|SECRET|PRIVATE_KEY|CLIENT_SECRET|API_KEY)[A-Z0-9_.-]*)"
    r"[ \t]*[:=][ \t]*[\"']?(?P<value>[^\s#\"']{16,})"
)

PLACEHOLDER_MARKERS = (
    "example",
    "placeholder",
    "replace-with",
    "changeme",
    "change-me",
    "redacted",
    "unconfigured",
    "not-a-real",
    "dummy",
    "sample",
    "fake",
    "test-only",
    "${",
    "{{",
    "<",
    "...",
)


@dataclass(frozen=True, order=True)
class Finding:
    path: str
    line: int
    rule: str


def run_git(root: Path, args: list[str]) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        ["git", "-C", str(root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )


def is_git_worktree(root: Path) -> bool:
    result = run_git(root, ["rev-parse", "--is-inside-work-tree"])
    return result.returncode == 0 and result.stdout.strip() == b"true"


def git_candidate_files(root: Path) -> list[Path]:
    result = run_git(root, ["ls-files", "-z", "--cached", "--others", "--exclude-standard"])
    if result.returncode != 0:
        raise RuntimeError("git ls-files failed")
    paths: list[Path] = []
    for raw in result.stdout.split(b"\0"):
        if not raw:
            continue
        relative = Path(os.fsdecode(raw))
        candidate = root / relative
        if candidate.is_file() and not candidate.is_symlink():
            paths.append(candidate)
    return sorted(paths)


def fallback_candidate_files(root: Path) -> list[Path]:
    paths: list[Path] = []
    for candidate in root.rglob("*"):
        if not candidate.is_file() or candidate.is_symlink():
            continue
        relative = candidate.relative_to(root)
        relative_posix = relative.as_posix()
        if relative_posix in EXCLUDED_RELATIVE_PATHS:
            continue
        if any(part in EXCLUDED_DIR_NAMES for part in relative.parts):
            continue
        paths.append(candidate)
    return sorted(paths)


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def is_placeholder(value: str) -> bool:
    normalized = value.strip().lower()
    if not normalized:
        return True
    return any(marker in normalized for marker in PLACEHOLDER_MARKERS)


def sensitive_path_rule(relative: Path) -> str | None:
    name = relative.name
    lower_name = name.lower()
    if lower_name in ALLOWED_SENSITIVE_FILENAMES:
        return None
    if lower_name == ".env" or (lower_name.startswith(".env.") and lower_name not in ALLOWED_SENSITIVE_FILENAMES):
        return "sensitive-filename"
    if lower_name in SENSITIVE_EXACT_NAMES:
        return "sensitive-filename"
    if lower_name.startswith("firebase-service-account") and lower_name.endswith(".json"):
        return "sensitive-filename"
    if Path(lower_name).suffix in SENSITIVE_SUFFIXES:
        return "sensitive-filename"
    if ".secrets" in relative.parts:
        return "sensitive-directory"
    return None


def scan_text(relative: Path, text: str) -> list[Finding]:
    findings: list[Finding] = []
    for rule, pattern in CONTENT_RULES:
        for match in pattern.finditer(text):
            findings.append(Finding(relative.as_posix(), line_number(text, match.start()), rule))

    compact = re.sub(r"\s+", "", text)
    service_account_marker = '"type":"service_' + 'account"'
    private_key_marker = '"private_' + 'key"'
    if service_account_marker in compact and private_key_marker in compact:
        offset = text.find('"type"')
        findings.append(Finding(relative.as_posix(), line_number(text, max(offset, 0)), "google-service-account"))

    # Generic assignment checks intentionally skip source/test fixtures. Provider-specific
    # signatures above are still checked in every text file.
    config_suffixes = {".env", ".properties", ".toml", ".yaml", ".yml", ".json", ".xml"}
    if relative.suffix.lower() in config_suffixes or relative.name.lower().startswith(".env"):
        for match in GENERIC_ASSIGNMENT.finditer(text):
            value = match.group("value")
            if not is_placeholder(value):
                findings.append(
                    Finding(relative.as_posix(), line_number(text, match.start()), "credential-assignment")
                )
    return findings


def scan(root: Path) -> tuple[list[Finding], int, bool]:
    git_mode = is_git_worktree(root)
    candidates = git_candidate_files(root) if git_mode else fallback_candidate_files(root)
    findings: list[Finding] = []
    scanned = 0

    for candidate in candidates:
        relative = candidate.relative_to(root)
        path_rule = sensitive_path_rule(relative)
        if path_rule is not None:
            findings.append(Finding(relative.as_posix(), 1, path_rule))

        try:
            size = candidate.stat().st_size
        except OSError:
            continue
        if size > MAX_FILE_BYTES:
            continue
        try:
            raw = candidate.read_bytes()
        except OSError:
            continue
        if b"\0" in raw:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        scanned += 1
        findings.extend(scan_text(relative, text))

    return sorted(set(findings)), scanned, git_mode


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    root = args.root.resolve()
    if not root.is_dir():
        print(f"Repository root does not exist: {root}", file=sys.stderr)
        return 2

    try:
        findings, scanned, git_mode = scan(root)
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 2

    if findings:
        print("Repository secret check failed. Values are intentionally not printed.", file=sys.stderr)
        for finding in findings:
            print(f"  {finding.path}:{finding.line}: {finding.rule}", file=sys.stderr)
        return 1

    mode = "git tracked/unignored" if git_mode else "filesystem fallback"
    print(f"Repository secret check passed: {scanned} UTF-8 files scanned ({mode}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
