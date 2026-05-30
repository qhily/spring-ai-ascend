#!/usr/bin/env python3
"""Gate check: every ADR number is claimed by exactly one raw ADR source file.

Authority: ADR-0160 (ADR Governance Model — one remediation-ledger entry per raw
ADR, one normalized view per accepted ADR, both keyed by the ADR number) read
through the raw ADR sources it governs. Consumed by Rule G-33 (gate Rule 150,
enforcer E200), authored BLOCKING from the start.

An ADR number is the single authoritative IDENTIFIER for one architecture
decision. The downstream ID-keyed projections collapse a duplicate silently:
``AdrFactExtractor`` and ``AdrGraphFragmentEmitter`` both key ADRs by their
number, and ``AdrGraphFragmentEmitter`` builds a ``TreeMap<String,...>`` keyed by
id, so N files that share one number collapse to ONE map entry (last-writer-wins
by file sort order) — dropping the other N-1 ADRs from
``architecture/generated/adr-graph.dsl``, from
``architecture/facts/generated/adrs.json``, and from the workspace closure, and
leaving a reviewer unable to tell which colliding decision "ADR-NNNN" names. This
check turns that silent data loss into a gate-able defect.

The raw ADR sources are the NUMBERED decision files (basename begins with the
4-digit ADR number); they carry their number in two shapes, both scanned:

  * ``docs/adr/NNNN-*.yaml``            the YAML actives; number is the ``id:`` field
  * ``docs/adr/NNNN-*.md`` / ``docs/adr/locked/NNNN-*.md``
                                        the legacy active + locked Markdown ADRs;
                                        number is the leading first-heading ADR
                                        number (``# NNNN. ...`` or
                                        ``# ADR-NNNN -- ...``)

The un-numbered index / catalog companions under ``docs/adr/`` (``README.md``,
``INDEX.md``, ``ADR-CLASSIFICATION.md``, ``review-index.md``) carry no decision
identity and are deliberately out of scope.

The authority direction is one-way and the check asserts none of its own — it
reads which numbers the raw sources declare (the identity authority) and which
the fact layer enumerated (``architecture/facts/generated/adrs.json``, the apex
factual cross-check, ADR-0154 / Rule G-15), and it NEVER outranks a generated
fact (cascade: generated facts > DSL > Card/prose):

    ADR-0160 -> docs/adr/** -> architecture/facts/generated/adrs.json -> Rule 150 / E200

Findings (file-oriented; every colliding path is named):

  DUPLICATE-ID    two or more raw ADR files declare the same number; OR a number
                  the fact layer resolves to a different raw path than the file
                  that declared it (the same collision surfaced from the fact side)
  UNPARSEABLE-ID  a scanned ADR file carries no extractable number (a malformed
                  ``id:``, or a Markdown ADR with no leading ADR-number heading) —
                  a file whose identity cannot be read cannot be proven unique

Non-vacuity guard. The check FAILS CLOSED (exit 2) when its glob matches ZERO ADR
source files while ``docs/adr/`` exists — a path/format drift that silently empties
the scan set is never a pass. The check is materially vacuous (clean in every
mode) only when ``docs/adr/`` itself is absent (greenfield — the corpus has no
ADR to key).

Modes (``--mode``):

  advisory   Evaluate, print findings to stderr, and ALWAYS exit 0.
  blocking   Exit 1 on any finding — the default and only enforced posture. Unlike
             the lane-purity / readiness / reading-path ratchets, an ADR-number
             collision is never a tolerable interim state, so there is no advisory
             soak: the check lands blocking, with no grandfather list and no
             changed-files scoping (the identifier space is global; a collision is
             a collision regardless of which file a PR touched).

Usage:
    python3 gate/lib/check_adr_id_uniqueness.py --mode blocking
    python3 gate/lib/check_adr_id_uniqueness.py --mode advisory
    python3 gate/lib/check_adr_id_uniqueness.py --mode blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no findings in blocking mode; or
        docs/adr/ does not exist (greenfield)
    1 — one or more findings in blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, --repo not a directory, the
        non-vacuity guard tripped, or the apex adrs.json vanished/unreadable)
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical surface locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
ADR_DIR_REL = "docs/adr"
ADR_FACTS_REL = "architecture/facts/generated/adrs.json"

VALID_MODES = ("advisory", "blocking")

# The number in a YAML active is the `id:` scalar; tolerate optional quotes and an
# ADR- prefix (`id: ADR-0160`, `id: "ADR-0160"`, `id: 0160`).
_YAML_ID_RE = re.compile(r"""^\s*id\s*:\s*["']?(?:ADR-)?(\d{1,4})\b""", re.MULTILINE)
# The number in a Markdown ADR is the leading first-heading ADR number; the first
# `# ...` heading must carry it (`# 0160. ...` or `# ADR-0160 -- ...`).
_MD_HEADING_RE = re.compile(r"""^\#\s+(?:ADR-)?(\d{1,4})\b""")


# ===========================================================================
# Repo + path helpers.
# ===========================================================================
def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


class ConfigError(Exception):
    """A guard tripped or a required authority is unreadable (exit 2)."""


# ===========================================================================
# Findings.
# ===========================================================================
@dataclass(frozen=True)
class Finding:
    code: str       # DUPLICATE-ID / UNPARSEABLE-ID
    subject: str    # the ADR number (DUPLICATE-ID) or the file path (UNPARSEABLE-ID)
    detail: str

    def line(self) -> str:
        return f"adr-id-uniqueness [{self.code}] {self.subject}: {self.detail}"


# ===========================================================================
# Raw-source number extraction.
# ===========================================================================
def _canonical_number(raw: str) -> str:
    """Zero-pad a parsed ADR number to the canonical 4-digit ADR-NNNN form."""
    return f"ADR-{int(raw):04d}"


def extract_yaml_number(text: str) -> str | None:
    """The ADR number declared by a YAML active's `id:` field, or None."""
    m = _YAML_ID_RE.search(text)
    return _canonical_number(m.group(1)) if m else None


def extract_md_number(text: str) -> str | None:
    """The ADR number from a Markdown ADR's leading first `# ` heading, or None.

    Only the FIRST level-1 heading is authoritative; a later `# NNNN.` line in the
    body does not name the file's identity."""
    for line in text.splitlines():
        if line.startswith("# "):
            m = _MD_HEADING_RE.match(line)
            return _canonical_number(m.group(1)) if m else None
    return None


# A companion cue: prose that declares the file is the readable mirror OF a
# structured `.yaml` authority, not an independent decision claiming the number.
_COMPANION_CUE_RE = re.compile(
    r"(?i)\b(?:engineering-prose\s+companion|prose\s+companion|companion\s+to)\b"
    r"|the\s+yaml\s+is\s+the\s+structured\s+authority"
)


def is_prose_companion(stem: str, text: str) -> bool:
    """True when a Markdown ADR is a PROSE COMPANION to its sibling `.yaml`.

    The ADR-0155 pattern: a `docs/adr/NNNN-slug.md` that is the readable reasoning
    trail FOR the decision its sibling `docs/adr/NNNN-slug.yaml` owns. Such a file
    is NOT an independent identity claim on the ADR number — the `.yaml` is the
    structured authority, the `.md` only re-states it in prose (exactly the
    delegation-pointer shape Rule G-27 / E195 already honours for layer prose).

    The guard is deliberately narrow: it fires ONLY when BOTH hold —
      (1) the body links to a same-stem sibling `.yaml` (`](...NNNN-slug.yaml)` or
          a bare ``NNNN-slug.yaml`` reference), AND
      (2) the body carries an explicit companion cue.
    A Markdown ADR with no `.yaml` sibling, or one that competes for the number
    without delegating to a sibling, is NOT a companion and still claims identity.
    """
    sibling_yaml = f"{stem}.yaml"
    if sibling_yaml not in text:
        return False
    return bool(_COMPANION_CUE_RE.search(text))


def scan_raw_sources(root: Path) -> tuple[dict[str, list[str]], list[str], list[str]]:
    """Glob the raw ADR sources and group declared numbers -> [file rel-paths].

    Returns (number_to_files, unparseable_rel_paths, companion_rel_paths). Files
    whose number cannot be extracted are collected separately (a file whose identity
    cannot be read cannot be proven unique); Markdown prose companions that delegate
    to a sibling `.yaml` are recorded separately and excluded from identity
    competition.

    Raises ConfigError (exit 2) on the non-vacuity guard: the glob matched zero ADR
    source files while docs/adr/ exists."""
    # An ADR SOURCE is a numbered decision file: its basename begins with the
    # 4-digit ADR number (NNNN-slug.{yaml,md}). The un-numbered index / catalog
    # companions under docs/adr/ (README.md, INDEX.md, ADR-CLASSIFICATION.md,
    # review-index.md) are NOT ADR sources -- they carry no decision identity and
    # are deliberately out of scope (matching them would be a false UNPARSEABLE-ID).
    adr_dir = root / ADR_DIR_REL
    yaml_files = sorted(adr_dir.glob("[0-9][0-9][0-9][0-9]-*.yaml"))
    md_files = (
        sorted(adr_dir.glob("[0-9][0-9][0-9][0-9]-*.md"))
        + sorted((adr_dir / "locked").glob("[0-9][0-9][0-9][0-9]-*.md"))
    )
    scanned = yaml_files + md_files

    if not scanned:
        # docs/adr/ exists (caller checked) but the glob is empty -> a renamed dir
        # or a changed extension silently emptied the scan set. Never a pass.
        raise ConfigError(
            f"non-vacuity guard: {ADR_DIR_REL} exists but no ADR source files "
            f"(*.yaml / *.md / locked/*.md) matched -- a format/path drift emptied "
            f"the scan set"
        )

    number_to_files: dict[str, list[str]] = defaultdict(list)
    unparseable: list[str] = []
    companions: list[str] = []
    for path in scanned:
        rel = path.relative_to(root).as_posix()
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError as exc:
            raise ConfigError(f"cannot read raw ADR source {rel}: {exc}") from exc
        if path.suffix == ".yaml":
            number = extract_yaml_number(text)
        else:
            # A Markdown prose companion that delegates to its sibling `.yaml` is
            # not an independent identity claim — skip it (the `.yaml` owns the
            # number). The guard is narrow: it fires only on the explicit
            # companion-delegation shape (Rule G-27 / E195 delegation-pointer
            # analogue), never on a Markdown ADR that competes for a number.
            if is_prose_companion(path.stem, text):
                companions.append(rel)
                continue
            number = extract_md_number(text)
        if number is None:
            unparseable.append(rel)
        else:
            number_to_files[number].append(rel)
    return number_to_files, unparseable, companions


# ===========================================================================
# Fact-side cross-check.
# ===========================================================================
def load_fact_paths(root: Path) -> dict[str, str]:
    """Map each ADR number the fact layer enumerates -> its recorded source path.

    The fact layer (architecture/facts/generated/adrs.json) enumerates the YAML
    ADRs; it is the apex factual cross-check. A vanished/unreadable apex fact is a
    config error (exit 2) — a missing apex authority is never a pass. When a row
    carries no source-path field the cross-check for that number is simply skipped
    (the raw sources remain the identity authority)."""
    facts_path = root / ADR_FACTS_REL
    try:
        raw = facts_path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ConfigError(
            f"apex fact {ADR_FACTS_REL} is missing/unreadable: {exc} -- "
            f"a vanished generated fact is never a pass (Rule G-15 / ADR-0154)"
        ) from exc
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise ConfigError(f"cannot parse apex fact {ADR_FACTS_REL}: {exc}") from exc

    rows: list[dict] = []
    if isinstance(data, list):
        rows = [r for r in data if isinstance(r, dict)]
    elif isinstance(data, dict):
        for key in ("adrs", "items", "facts"):
            val = data.get(key)
            if isinstance(val, list):
                rows = [r for r in val if isinstance(r, dict)]
                break

    number_to_path: dict[str, str] = {}
    for row in rows:
        rid = row.get("id") or row.get("adr") or row.get("adrId")
        if not isinstance(rid, str):
            continue
        m = re.search(r"(\d{1,4})", rid)
        if not m:
            continue
        number = _canonical_number(m.group(1))
        for field in ("source_path", "sourcePath", "path", "file", "source"):
            src = row.get(field)
            if isinstance(src, str) and src:
                number_to_path[number] = src.replace("\\", "/")
                break
    return number_to_path


# ===========================================================================
# Orchestration.
# ===========================================================================
def evaluate(root: Path) -> tuple[list[Finding], dict[str, int]]:
    """Scan the raw sources + cross-check the apex fact. Raises ConfigError
    (exit 2) on the non-vacuity guard or a vanished apex fact.

    Returns (findings, stats) where stats summarises the scan set."""
    number_to_files, unparseable, companions = scan_raw_sources(root)
    findings: list[Finding] = []

    # DUPLICATE-ID across raw sources.
    for number in sorted(number_to_files):
        files = number_to_files[number]
        if len(files) > 1:
            findings.append(
                Finding("DUPLICATE-ID", number,
                        f"claimed by {len(files)} raw ADR files: {', '.join(sorted(files))}")
            )

    # UNPARSEABLE-ID.
    for rel in sorted(unparseable):
        findings.append(
            Finding("UNPARSEABLE-ID", rel,
                    "no extractable ADR number (malformed id: / no leading "
                    "ADR-number heading)")
        )

    # Fact-side cross-check: a number whose adrs.json source path disagrees with
    # the single raw file that declared it is the same collision from the apex side.
    fact_paths = load_fact_paths(root)
    for number, files in number_to_files.items():
        if len(files) != 1:
            continue  # multi-file collisions already reported above
        fact_src = fact_paths.get(number)
        if fact_src and fact_src not in files:
            findings.append(
                Finding("DUPLICATE-ID", number,
                        f"apex fact {ADR_FACTS_REL} resolves it to '{fact_src}' but the "
                        f"raw source that declared it is '{files[0]}' (cross-file collision)")
            )

    stats = {
        "numbers": len(number_to_files),
        "files": sum(len(v) for v in number_to_files.values()),
        "unparseable": len(unparseable),
        "companions": len(companions),
    }
    return findings, stats


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument("--mode", default="blocking", choices=VALID_MODES)
    ap.add_argument("--repo", default=None,
                    help="repository root (default: two levels above this script)")
    args = ap.parse_args(argv)

    root = Path(args.repo).resolve() if args.repo else repo_root()
    if not root.is_dir():
        print(f"adr-id-uniqueness: config error: --repo {root} is not a directory",
              file=sys.stderr)
        return 2

    # Greenfield: no docs/adr/ at all -> vacuously clean (no ADR to key).
    if not (root / ADR_DIR_REL).is_dir():
        print(f"adr-id-uniqueness [{args.mode}]: {ADR_DIR_REL}/ not present "
              f"(greenfield) -- 0 finding(s)", file=sys.stderr)
        return 0

    try:
        findings, stats = evaluate(root)
    except ConfigError as exc:
        print(f"adr-id-uniqueness: config error: {exc}", file=sys.stderr)
        return 2

    blocking = (args.mode == "blocking") and bool(findings)
    for f in findings:
        marker = "BLOCKING" if blocking else "advisory"
        print(f"{f.line()}  [{marker}]", file=sys.stderr)

    print(
        f"adr-id-uniqueness [{args.mode}]: {len(findings)} finding(s) over "
        f"{stats['numbers']} ADR number(s) / {stats['files']} source file(s)"
        + (f" ({stats['companions']} prose companion(s) excluded)"
           if stats["companions"] else ""),
        file=sys.stderr,
    )
    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())
