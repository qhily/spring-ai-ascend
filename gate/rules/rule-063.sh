#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 63 — release_note_retracted_tag_qualified. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 63 — release_note_retracted_tag_qualified (v2.0.0-rc2 / second-pass review F-γ structural prevention)
#
# Every tag listed in docs/governance/retracted-tags.txt MUST, wherever it is
# mentioned in an active release note under docs/logs/releases/*.md, appear either
#   (a) on the same line as "(retracted)" (case-insensitive), OR
#   (b) under a markdown heading (line starting with '#') containing
#       "Historical" or "Superseded" (case-insensitive).
# Drift would let a retracted tag be re-cited as a recommendation in a fresh
# release-note section, recreating the F-γ stale-evidence defect that the
# second-pass review's P1-2 finding flagged.
# ---------------------------------------------------------------------------
_r63_fail=0
_r63_list="docs/governance/retracted-tags.txt"
if [[ ! -f "$_r63_list" ]]; then
  fail_rule "release_note_retracted_tag_qualified" "$_r63_list missing -- v2.0.0-rc2 second-pass review F-γ prevention expects this list"
  _r63_fail=1
else
  # Extract just the tag column (first pipe field, comments skipped).
  _r63_tags=$(awk -F'|' '
    /^[[:space:]]*#/ { next }
    NF >= 1 && length($1) > 0 {
      t=$1
      sub(/^[[:space:]]+/, "", t)
      sub(/[[:space:]]+$/, "", t)
      if (t != "") print t
    }
  ' "$_r63_list")
  if [[ -z "$_r63_tags" ]]; then
    pass_rule "release_note_retracted_tag_qualified"
  else
    shopt -s nullglob
    for _r63_doc in docs/logs/releases/*.md; do
      [[ -f "$_r63_doc" ]] || continue
      while IFS= read -r _r63_tag; do
        [[ -z "$_r63_tag" ]] && continue
        # Find every line number that mentions this tag in this doc.
        _r63_lines=$(grep -nF "$_r63_tag" "$_r63_doc" | cut -d: -f1 || true)
        [[ -z "$_r63_lines" ]] && continue
        while IFS= read -r _r63_ln; do
          [[ -z "$_r63_ln" ]] && continue
          # Check (a): same line has "(retracted)" (case-insensitive).
          _r63_lineval=$(sed -n "${_r63_ln}p" "$_r63_doc")
          if echo "$_r63_lineval" | grep -qiE '\(retracted\)|retracted\b'; then
            continue
          fi
          # Check (b): scan upward for the nearest markdown heading (line starting with '#'),
          # check whether it contains "Historical" or "Superseded".
          _r63_qualified=0
          _r63_scan=$_r63_ln
          while [[ $_r63_scan -gt 0 ]]; do
            _r63_above=$(sed -n "${_r63_scan}p" "$_r63_doc")
            if echo "$_r63_above" | grep -qE '^#'; then
              if echo "$_r63_above" | grep -qiE 'historical|superseded'; then
                _r63_qualified=1
              fi
              break
            fi
            _r63_scan=$((_r63_scan - 1))
          done
          if [[ $_r63_qualified -eq 0 ]]; then
            fail_rule "release_note_retracted_tag_qualified" "$_r63_doc:$_r63_ln mentions retracted tag '$_r63_tag' without '(retracted)' qualifier on the line OR a 'Historical'/'Superseded' heading above"
            _r63_fail=1
          fi
        done <<< "$_r63_lines"
      done <<< "$_r63_tags"
    done
    shopt -u nullglob
  fi
fi
if [[ $_r63_fail -eq 0 ]]; then pass_rule "release_note_retracted_tag_qualified"; fi

# ===========================================================================
# Cross-corpus consistency audit prevention rules (2026-05-17)
# Authority: docs/logs/reviews/2026-05-17-cross-corpus-consistency-audit-response.en.md
# Closes structural design flaws G1, G2, G3 surfaced by the audit:
#   G1 — module count was hardcoded in 4 places
#   G2 — no metadata-vs-pom dependency cross-check
#   G3 — no SPI-package exhaustiveness cross-check
# Rules 64-66 with enforcer rows E94-E96 and 6 self-tests (2 per rule).
# ===========================================================================

# ---------------------------------------------------------------------------
