#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 25 — peripheral_wave_qualifier. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 25 — peripheral_wave_qualifier
# ADR-0045: SPI Javadoc must not use "Primary sidecar impl:" or "Primary impl:"
# without a wave qualifier (W0-W4) in context. Active markdown docs must not use
# "Sidecar adapter —" without a wave qualifier or ADR reference. Closes PERIPHERAL-DRIFT.
# ---------------------------------------------------------------------------
_r25_fail=0
# 25a: SPI Java source in agent-runtime
while IFS= read -r _sf25; do
  [[ -z "$_sf25" ]] && continue
  if grep -q 'Primary sidecar impl:\|Primary impl:' "$_sf25" 2>/dev/null; then
    # For each matching line, check surrounding context for wave qualifier
    while IFS= read -r _hit25; do
      _ln25=$(printf '%s\n' "$_hit25" | grep -oE ':[0-9]+:' | tr -d ':' | head -1)
      _ctx25=$(sed -n "$((${_ln25:-0} > 2 ? ${_ln25} - 2 : 1)),$((${_ln25:-0} + 3))p" "$_sf25" 2>/dev/null | tr '\n' ' ')
      if ! printf '%s\n' "$_ctx25" | grep -qE '\bW[0-4]\b'; then
        fail_rule "peripheral_wave_qualifier" "$_sf25:$_ln25 contains 'Primary.*impl:' without wave qualifier (W0-W4) in context. Per ADR-0045 Gate Rule 25 future-wave impl claims must carry wave qualifiers."
        _r25_fail=1
      fi
    done < <(grep -nF 'Primary sidecar impl:' "$_sf25" 2>/dev/null; grep -nF 'Primary impl:' "$_sf25" 2>/dev/null)
  fi
done < <(find agent-service/src/main/java -name '*.java' ! -path './target/*' 2>/dev/null || true)
# 25b: active markdown docs
while IFS= read -r _af25; do
  [[ -z "$_af25" ]] && continue
  while IFS= read -r _mhit25; do
    _ln25m=$(printf '%s\n' "$_mhit25" | cut -d: -f1)
    _content25m=$(printf '%s\n' "$_mhit25" | cut -d: -f2-)
    if ! printf '%s\n' "$_content25m" | grep -qE '\bW[0-4]\b' && ! printf '%s\n' "$_content25m" | grep -q 'ADR-'; then
      fail_rule "peripheral_wave_qualifier" "$_af25:$_ln25m contains 'Sidecar adapter —' without wave qualifier or ADR reference. Per ADR-0045 Gate Rule 25."
      _r25_fail=1
    fi
  done < <(grep -nF 'Sidecar adapter —' "$_af25" 2>/dev/null || true)
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' ! -path './docs/delivery/*' \
  ! -path './docs/v6-rationale/*' ! -path './docs/plans/*' \
  ! -path './third_party/*' ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
if [[ $_r25_fail -eq 0 ]]; then pass_rule "peripheral_wave_qualifier"; fi

# ---------------------------------------------------------------------------
