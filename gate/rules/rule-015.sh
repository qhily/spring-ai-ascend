#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 15 — no_active_refs_deleted_wave_plan_paths. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 15 — no_active_refs_deleted_wave_plan_paths
# ADR-0041: active .md files (outside archive/reviews/third_party/target/.git)
# must not reference docs/plans/engineering-plan-W0-W4.md or
# docs/plans/roadmap-W0-W4.md. Both plans were archived per ADR-0037.
# ---------------------------------------------------------------------------
_r15_fail=0
_deleted_plan_refs=('docs/plans/engineering-plan-W0-W4.md' 'docs/plans/roadmap-W0-W4.md')
while IFS= read -r _mdf15; do
  [[ -z "$_mdf15" ]] && continue
  for _ref15 in "${_deleted_plan_refs[@]}"; do
    if grep -qF "$_ref15" "$_mdf15" 2>/dev/null; then
      fail_rule "no_active_refs_deleted_wave_plan_paths" "$_mdf15 references deleted plan path '$_ref15'. Per ADR-0041 Gate Rule 15 active docs must not reference archived plan paths."
      _r15_fail=1
      break 2
    fi
  done
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './docs/delivery/*' \
  ! -path './docs/v6-rationale/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
if [[ $_r15_fail -eq 0 ]]; then pass_rule "no_active_refs_deleted_wave_plan_paths"; fi

# ---------------------------------------------------------------------------
