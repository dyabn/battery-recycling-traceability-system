#!/usr/bin/env python3
"""Check initial traceability scaffolding without requiring later-stage artifacts."""

from pathlib import Path
import re
import sys


REQUIRED_PATHS = [
    ".codex/skills/business-system-development/SKILL.md",
    ".codex/skills/business-system-development/references/lifecycle.md",
    ".codex/skills/business-system-development/references/stage-gates.md",
    ".codex/skills/business-system-development/references/deliverables.md",
    ".codex/skills/business-system-development/references/traceability-rules.md",
    ".codex/skills/business-system-development/references/battery-project-profile.md",
    "contracts/traceability-index.md",
    "contracts/change-log.md",
    "contracts/open-questions.md",
    "project-state.yaml",
]

REQUIRED_ID_PREFIXES = [
    "MAT",
    "BIZ",
    "ROLE",
    "OBJ",
    "BR",
    "EX",
    "SLC",
    "UR",
    "FR",
    "NFR",
    "UC",
    "AC",
    "TC",
    "UI",
    "API",
    "DB",
    "CODE",
    "CHG",
    "Q",
]


def find_project_root() -> Path:
    here = Path(__file__).resolve()
    for parent in [here, *here.parents]:
        if (parent / "project-state.yaml").exists():
            return parent
    return here.parents[4]


def main() -> int:
    root = find_project_root()
    errors = []

    for relative in REQUIRED_PATHS:
        if not (root / relative).exists():
            errors.append(f"missing required traceability file: {relative}")

    rules_file = root / ".codex/skills/business-system-development/references/traceability-rules.md"
    if rules_file.exists():
        rules_text = rules_file.read_text(encoding="utf-8")
        for prefix in REQUIRED_ID_PREFIXES:
            if not re.search(rf"`{prefix}-xxx`", rules_text):
                errors.append(f"missing ID rule for {prefix}-xxx")

    index_file = root / "contracts/traceability-index.md"
    if index_file.exists():
        index_text = index_file.read_text(encoding="utf-8")
        if "当前尚未产生已确认需求" not in index_text:
            errors.append("traceability index should state that no confirmed requirements exist yet")

    if errors:
        print("Traceability validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Traceability validation passed.")
    print(f"Checked project: {root}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
