#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 37 — architecture_artefact_front_matter. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 37 — architecture_artefact_front_matter (enforcer E55, ADR-0068)
#
# Every L0/L1/L2 architecture artefact MUST declare a level: + view:
# front-matter (YAML at top of file for .md; top-level key for .yaml).
# Targets: ARCHITECTURE.md, agent-*/ARCHITECTURE.md, docs/L2/**/*.md (excluding
# README.md while empty), docs/adr/*.yaml.
# ---------------------------------------------------------------------------
_r37_fail=0
_valid_levels='^(L0|L1|L2)$'
_valid_views='^(logical|development|process|physical|scenarios)$'

_check_front_matter_md() {
  local _f="$1"
  local _level _view
  _level="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^level:[[:space:]]/{sub(/^level:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  _view="$(awk 'BEGIN{in_fm=0; n=0} /^---[[:space:]]*$/{n++; if(n==1){in_fm=1; next} if(n==2){exit}} in_fm && /^view:[[:space:]]/{sub(/^view:[[:space:]]*/,""); sub(/[[:space:]]*$/,""); print; exit}' "$_f" 2>/dev/null)"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'level:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing 'view:' YAML front-matter (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

_check_front_matter_yaml() {
  local _f="$1"
  local _level _view
  _level="$(grep -E '^level:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^level:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  _view="$(grep -E '^view:[[:space:]]' "$_f" 2>/dev/null | head -1 | sed -E 's/^view:[[:space:]]*([A-Za-z0-9_]+).*/\1/')"
  if [[ -z "$_level" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'level:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_level" =~ $_valid_levels ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f level: '$_level' is not one of L0|L1|L2"; _r37_fail=1
  fi
  if [[ -z "$_view" ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f missing top-level 'view:' (CLAUDE.md Rule 33 / ADR-0068)"; _r37_fail=1; return
  fi
  if [[ ! "$_view" =~ $_valid_views ]]; then
    fail_rule "architecture_artefact_front_matter" "$_f view: '$_view' is not one of logical|development|process|physical|scenarios"; _r37_fail=1
  fi
}

[[ -f ARCHITECTURE.md ]] && _check_front_matter_md ARCHITECTURE.md
while IFS= read -r _f37; do
  [[ -z "$_f37" ]] && continue
  _check_front_matter_md "$_f37"
done < <(find . -maxdepth 2 -type f -name 'ARCHITECTURE.md' ! -path './ARCHITECTURE.md' 2>/dev/null | sort || true)
while IFS= read -r _f37; do
  [[ -z "$_f37" ]] && continue
  _check_front_matter_md "$_f37"
done < <(find docs/L2 -type f -name '*.md' 2>/dev/null | sort || true)
while IFS= read -r _f37; do
  [[ -z "$_f37" ]] && continue
  _check_front_matter_yaml "$_f37"
done < <(find docs/adr -maxdepth 1 -type f -name '*.yaml' 2>/dev/null | sort || true)
if [[ $_r37_fail -eq 0 ]]; then pass_rule "architecture_artefact_front_matter"; fi

# ---------------------------------------------------------------------------
