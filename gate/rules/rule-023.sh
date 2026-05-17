#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 23 — active_doc_internal_links_resolve. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 23 — active_doc_internal_links_resolve
# ADR-0043: markdown links ](relative-path) in active normative docs must
# resolve to files that exist on disk. Excludes http://, https://, anchors.
# ---------------------------------------------------------------------------
_r23_fail=0
while IFS= read -r _af23; do
  [[ -z "$_af23" ]] && continue
  _dir23="$(dirname "$_af23")"
  while IFS= read -r _link23; do
    [[ -z "$_link23" ]] && continue
    # Strip anchor fragment
    _path23="${_link23%%#*}"
    [[ -z "$_path23" ]] && continue
    # Skip external and anchor-only links
    case "$_link23" in http://*|https://*|mailto:*|'#'*) continue ;; esac
    _resolved23="$(cd "$_dir23" 2>/dev/null && realpath -m "$_path23" 2>/dev/null || echo '')"
    if [[ -n "$_resolved23" && ! -e "$_resolved23" ]]; then
      fail_rule "active_doc_internal_links_resolve" "$_af23 has broken link to '$_link23' (resolved: '$_resolved23'). Per ADR-0043 Gate Rule 23 all internal links in active docs must resolve."
      _r23_fail=1
    fi
  done < <(grep -oE '\]\([^)]+\)' "$_af23" 2>/dev/null | sed 's/^](//;s/)$//' || true)
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' ! -path './docs/reviews/*' \
  ! -path './docs/adr/*' ! -path './docs/delivery/*' \
  ! -path './docs/v6-rationale/*' ! -path './docs/plans/*' \
  ! -path './third_party/*' ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
if [[ $_r23_fail -eq 0 ]]; then pass_rule "active_doc_internal_links_resolve"; fi

# ---------------------------------------------------------------------------
