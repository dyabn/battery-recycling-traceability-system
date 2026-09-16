#!/usr/bin/env python3
"""Validate the minimal lifecycle state file for this course project."""

from pathlib import Path
import re
import sys


REQUIRED_TOP_LEVEL = [
    "project",
    "skill",
    "current_stage",
    "baseline",
    "confirmations",
    "materials",
    "deliverables",
    "open_questions",
    "next_action",
]

REQUIRED_GATES = ["C1", "C2", "C3", "C4"]


def find_project_root() -> Path:
    here = Path(__file__).resolve()
    for parent in [here, *here.parents]:
        if (parent / "project-state.yaml").exists():
            return parent
    return here.parents[4]


def main() -> int:
    root = find_project_root()
    state_file = root / "project-state.yaml"
    errors = []

    if not state_file.exists():
        print(f"ERROR: missing {state_file}")
        return 1

    text = state_file.read_text(encoding="utf-8")

    for key in REQUIRED_TOP_LEVEL:
        if not re.search(rf"(?m)^{re.escape(key)}\s*:", text):
            errors.append(f"missing top-level key: {key}")

    for gate in REQUIRED_GATES:
        if not re.search(rf"(?m)^\s+{gate}\s*:", text):
            errors.append(f"missing confirmation gate: {gate}")

    if "business-system-development" not in text:
        errors.append("skill name is not recorded in project-state.yaml")

    if "confirmed: false" not in text and "confirmed: true" not in text:
        errors.append("confirmation status must use confirmed: true/false")

    if errors:
        print("Project state validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Project state validation passed.")
    print(f"Checked: {state_file}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
