#!/usr/bin/env python3
"""Create a deterministic VM Service driver synchronization report."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from vm_service_audit import AuditError, build_report, load_baseline, load_source


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        description=(
            "Compare the plugin VM Service Java drivers with a Dart SDK service.md and "
            "write a deterministic Markdown/JSON updater handoff bundle. Protocol drift "
            "exits successfully; configuration and generation failures do not."
        )
    )
    result.add_argument(
        "--repo-root", help="Plugin repository root (default: discover from cwd)"
    )
    result.add_argument(
        "--sdk", help="Dart SDK Git checkout (default: sibling ../sdk)"
    )
    result.add_argument(
        "--target-ref",
        default="HEAD",
        help="Immutable SDK target resolved from this Git ref",
    )
    result.add_argument(
        "--service-md",
        help="Explicit target service.md; use --sdk as its history source",
    )
    result.add_argument(
        "--baseline-service-md",
        help="Explicit pinned baseline service.md for shallow/offline use",
    )
    result.add_argument(
        "--github",
        action="store_true",
        help="Resolve target main and history from official GitHub",
    )
    result.add_argument(
        "--latest-known-sha",
        help="Full live remote main SHA obtained during agent preflight",
    )
    result.add_argument(
        "--allow-stale-sdk",
        action="store_true",
        help=(
            "Audit a target known to be behind remote main after the human declines "
            "an update"
        ),
    )
    result.add_argument(
        "--allow-network",
        action="store_true",
        help=(
            "Allow GitHub retrieval and online fallback for pinned Dart dependencies"
        ),
    )
    result.add_argument(
        "--dart",
        default="dart",
        help="Dart executable used by the pinned generator",
    )
    result.add_argument(
        "--output",
        help=(
            "Report root (default: "
            "third_party/build/reports/vm-service-drivers)"
        ),
    )
    return result


def discover_repo_root(argument: str | None) -> Path:
    if argument:
        root = Path(argument).expanduser().resolve()
    else:
        current = Path.cwd().resolve()
        root = next(
            (
                candidate
                for candidate in (current, *current.parents)
                if (candidate / ".git").exists()
            ),
            current,
        )
    if not (root / "third_party").is_dir():
        raise AuditError(f"Not a dart-intellij plugin repository root: {root}")
    return root


def main(arguments: list[str] | None = None) -> int:
    args = parser().parse_args(arguments)
    try:
        repo_root = discover_repo_root(args.repo_root)
        skill_root = Path(__file__).resolve().parents[1]
        baseline = load_baseline(skill_root)
        source = load_source(args, repo_root, baseline)
        destination, report = build_report(
            args, repo_root, skill_root, source, baseline
        )
    except (AuditError, OSError, ValueError, KeyError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    try:
        display = destination.relative_to(repo_root)
    except ValueError:
        display = destination
    delta = report["drift"]["baseline_to_target"]
    print(f"report: {display}")
    print(
        "versions: "
        f"plugin={report['plugin']['protocol_version']} "
        f"sdk={report['source']['target']['protocol_version']}"
    )
    print(
        "generated drift: "
        f"added={len(delta['added'])} "
        f"changed={len(delta['changed'])} "
        f"removed={len(delta['removed'])}"
    )
    print(f"readiness: {report['readiness']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
