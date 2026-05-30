#!/usr/bin/env python3
"""Standalone unit harness for gate/lib/check_adr_id_uniqueness.py (Rule G-33 / E200).

Runs the ADR-ID-uniqueness checker against synthetic ADR corpora staged in a
temp directory and asserts the verdict for each scenario. Mirrors the standalone
harness pattern used by the sibling map/readiness checks; the gate-script self-
tests in gate/test_architecture_sync_gate.sh lock the three landing fixtures
(greenfield / clean / one duplicate negative) required by Rule 89 / E122 (c).

Run:  python3 gate/test_adr_id_uniqueness.py
Exit: 0 when every case passes; 1 on the first failure.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import check_adr_id_uniqueness as chk  # noqa: E402

_passed = 0
_failed = 0


def _check(name: str, cond: bool, detail: str = "") -> None:
    global _passed, _failed
    if cond:
        _passed += 1
        print(f"ok   - {name}")
    else:
        _failed += 1
        print(f"FAIL - {name}: {detail}", file=sys.stderr)


def _stage(root: Path, *, apex: bool = True) -> None:
    (root / "docs/adr/locked").mkdir(parents=True, exist_ok=True)
    if apex:
        facts = root / "architecture/facts/generated"
        facts.mkdir(parents=True, exist_ok=True)
        (facts / "adrs.json").write_text('{"facts": []}\n', encoding="utf-8")


def _run(root: Path, mode: str = "blocking") -> int:
    return chk.main(["--repo", str(root), "--mode", mode])


def test_greenfield_pos() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)  # no docs/adr/ at all
        _check("greenfield_no_docs_adr_is_clean", _run(root, "blocking") == 0,
               "greenfield (no docs/adr/) must exit 0 in every mode")


def test_clean_pos() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0200-aaa.yaml").write_text("id: ADR-0200\ntitle: A\n", encoding="utf-8")
        (root / "docs/adr/0201-real.md").write_text("# 0201. Real MD ADR\n\nbody\n", encoding="utf-8")
        (root / "docs/adr/locked/0001-locked.md").write_text(
            "# ADR-0001 — locked decision\n\nbody\n", encoding="utf-8")
        _check("clean_distinct_numbers_pass_blocking", _run(root, "blocking") == 0,
               "a corpus of distinct ADR numbers must pass blocking with 0 findings")


def test_duplicate_yaml_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0200-aaa.yaml").write_text("id: ADR-0200\n", encoding="utf-8")
        (root / "docs/adr/0200-bbb.yaml").write_text("id: ADR-0200\n", encoding="utf-8")
        _check("two_yaml_same_id_fails_blocking", _run(root, "blocking") == 1,
               "two YAML ADRs with the same id: must fail blocking (DUPLICATE-ID)")
        _check("two_yaml_same_id_advisory_exits_0", _run(root, "advisory") == 0,
               "advisory mode must report but never block")


def test_duplicate_cross_format_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0202-x.yaml").write_text("id: ADR-0202\n", encoding="utf-8")
        (root / "docs/adr/locked/0202-y.md").write_text("# ADR-0202 — collide\n", encoding="utf-8")
        _check("yaml_and_locked_md_same_number_fails", _run(root, "blocking") == 1,
               "a .yaml + locked .md claiming the same number must fail (cross-format DUPLICATE-ID)")


def test_unparseable_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0203-ok.yaml").write_text("id: ADR-0203\n", encoding="utf-8")
        # numbered .md with no leading ADR-number heading
        (root / "docs/adr/0204-broken.md").write_text("no heading\njust text\n", encoding="utf-8")
        _check("md_with_no_adr_heading_fails", _run(root, "blocking") == 1,
               "a numbered .md with no leading ADR-number heading must fail (UNPARSEABLE-ID)")


def test_prose_companion_pos() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0205-design.yaml").write_text("id: ADR-0205\ntitle: D\n", encoding="utf-8")
        # The ADR-0155 shape: a prose companion delegating to its sibling .yaml.
        (root / "docs/adr/0205-design.md").write_text(
            "---\nadr_id: ADR-0205\n---\n\n# ADR-0205 — design\n\n"
            "This is the engineering-prose companion to "
            "[`0205-design.yaml`](./0205-design.yaml). "
            "The yaml is the structured authority; this file gives the reasoning trail.\n",
            encoding="utf-8")
        _check("prose_companion_excluded_passes_blocking", _run(root, "blocking") == 0,
               "a Markdown prose companion delegating to its sibling .yaml is not a "
               "duplicate identity claim and must pass blocking")


def test_competing_md_not_companion_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        (root / "docs/adr/0206-design.yaml").write_text("id: ADR-0206\n", encoding="utf-8")
        # A .md claiming the same number WITHOUT the companion-delegation shape
        # (no sibling-yaml link / no companion cue) still competes for identity.
        (root / "docs/adr/0206-design.md").write_text(
            "# ADR-0206 — an independent claim\n\nbody with no delegation\n", encoding="utf-8")
        _check("competing_md_without_cue_fails", _run(root, "blocking") == 1,
               "a .md claiming a number without the companion-delegation shape must fail (DUPLICATE-ID)")


def test_non_vacuity_guard_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root)
        # docs/adr/ exists but only an un-numbered index file -> empty scan set.
        (root / "docs/adr/README.md").write_text("# index\n", encoding="utf-8")
        _check("empty_scan_set_fails_closed", _run(root, "blocking") == 2,
               "an empty scan set while docs/adr/ exists must fail closed (exit 2)")


def test_vanished_apex_neg() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, apex=False)  # no adrs.json
        (root / "docs/adr/0207-ok.yaml").write_text("id: ADR-0207\n", encoding="utf-8")
        _check("vanished_apex_adrs_json_fails_closed", _run(root, "blocking") == 2,
               "a vanished apex adrs.json while an ADR exists must fail closed (exit 2)")


def main() -> int:
    for fn in (
        test_greenfield_pos,
        test_clean_pos,
        test_duplicate_yaml_neg,
        test_duplicate_cross_format_neg,
        test_unparseable_neg,
        test_prose_companion_pos,
        test_competing_md_not_companion_neg,
        test_non_vacuity_guard_neg,
        test_vanished_apex_neg,
    ):
        fn()
    print(f"\n{_passed} passed, {_failed} failed")
    return 1 if _failed else 0


if __name__ == "__main__":
    sys.exit(main())
