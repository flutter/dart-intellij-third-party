#!/usr/bin/env python3
"""Validate a generated report bundle's schema and artifact hashes."""

from __future__ import annotations

import sys
from pathlib import Path

from vm_service_audit import AuditError, validate_bundle


def main(arguments: list[str]) -> int:
    if len(arguments) != 1:
        print("Usage: validate_report.py <report-bundle>", file=sys.stderr)
        return 64
    try:
        skill_root = Path(__file__).resolve().parents[1]
        current = Path.cwd().resolve()
        repo_root = next(
            (
                candidate
                for candidate in (current, *current.parents)
                if (candidate / ".git").exists()
            ),
            None,
        )
        validate_bundle(
            Path(arguments[0]).resolve(),
            repo_root=repo_root,
            skill_root=skill_root,
        )
    except (AuditError, OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    print("report bundle is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
