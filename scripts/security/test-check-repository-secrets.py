#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check-repository-secrets.py")


class RepositorySecretScannerTest(unittest.TestCase):
    def create_repo(self) -> Path:
        root = Path(tempfile.mkdtemp(prefix="ferventio-secret-scan-"))
        subprocess.run(["git", "init", "-q", str(root)], check=True)
        subprocess.run(["git", "-C", str(root), "config", "user.email", "test@example.invalid"], check=True)
        subprocess.run(["git", "-C", str(root), "config", "user.name", "Ferventio Test"], check=True)
        return root

    def run_scan(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["python3", str(SCRIPT), "--root", str(root)],
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def test_placeholders_and_ignored_local_env_pass(self) -> None:
        root = self.create_repo()
        (root / ".gitignore").write_text(".env\n", encoding="utf-8")
        (root / ".env.example").write_text("ADMIN_TOKEN=replace-with-a-long-random-token\n", encoding="utf-8")
        (root / ".env").write_text("ADMIN_TOKEN=actual-local-value-that-is-ignored\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(root), "add", ".gitignore", ".env.example"], check=True)
        result = self.run_scan(root)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_tracked_env_is_rejected_without_printing_value(self) -> None:
        root = self.create_repo()
        secret = "actual-secret-value-1234567890"
        (root / ".env").write_text(f"ADMIN_TOKEN={secret}\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(root), "add", "-f", ".env"], check=True)
        result = self.run_scan(root)
        self.assertEqual(1, result.returncode)
        self.assertIn("sensitive-filename", result.stderr)
        self.assertNotIn(secret, result.stderr)

    def test_private_key_and_provider_token_are_rejected(self) -> None:
        root = self.create_repo()
        token = "gh" + "p_" + "abcdefghijklmnopqrstuvwxyz123456"
        (root / "config.txt").write_text(
            "-----BEGIN " + "PRIVATE KEY-----\n" + token + "\n-----END " + "PRIVATE KEY-----\n",
            encoding="utf-8",
        )
        subprocess.run(["git", "-C", str(root), "add", "config.txt"], check=True)
        result = self.run_scan(root)
        self.assertEqual(1, result.returncode)
        self.assertIn("private-key", result.stderr)
        self.assertIn("github-token", result.stderr)
        self.assertNotIn(token, result.stderr)


if __name__ == "__main__":
    unittest.main()
