#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 16 — http_contract_w1_tenant_and_cancel_consistency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 16 — http_contract_w1_tenant_and_cancel_consistency
# ADR-0040: (a) no "replace.*X-Tenant-Id" in active docs; (b) http-api-contracts.md
# must not reference CREATED as initial status; (c) openapi-v1.yaml must not
# mention DELETE /v1/runs/{runId} as the cancel mechanism.
# ---------------------------------------------------------------------------
_r16_fail=0
# 16a: no forward-looking "will replace X-Tenant-Id" claim in active normative docs
# Exclude docs/adr/: ADRs may legitimately document rejected options and past wrong text.
while IFS= read -r _mdf16; do
  [[ -z "$_mdf16" ]] && continue
  if grep -qE 'TenantContextFilter[[:space:]]+(switches[[:space:]]+to|replaces?([[:space:]]+with)?[[:space:]]+JWT|moves[[:space:]]+to)[[:space:]]+JWT|will[[:space:]]+replace.*X-Tenant-Id|replace[[:space:]]+header-based.*with[[:space:]]+JWT|W1[[:space:]]+replaces.*X-Tenant-Id' "$_mdf16" 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "$_mdf16 contains a replacement-implying claim about X-Tenant-Id or TenantContextFilter. Per ADR-0040 W1 adds JWT cross-check; X-Tenant-Id is NOT replaced. Forbidden phrasings: 'switches to JWT', 'replaces with JWT', 'moves to JWT', 'will replace X-Tenant-Id'."
    _r16_fail=1
    break
  fi
done < <(find . -name '*.md' \
  ! -path './docs/archive/*' \
  ! -path './docs/logs/reviews/*' \
  ! -path './docs/adr/*' \
  ! -path './third_party/*' \
  ! -path './target/*' \
  ! -path './.git/*' \
  -type f 2>/dev/null | sort || true)
# 16b: http-api-contracts.md must not say CREATED as initial status
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/http-api-contracts.md' ]]; then
  if grep -qE 'starts in CREATED|CREATED stage|status.*CREATED' 'docs/contracts/http-api-contracts.md' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/http-api-contracts.md references CREATED as initial run status. Per ADR-0040 initial status is PENDING."
    _r16_fail=1
  fi
fi
# 16c: openapi-v1.yaml must not mention DELETE /v1/runs/{runId} as cancel
if [[ $_r16_fail -eq 0 ]] && [[ -f 'docs/contracts/openapi-v1.yaml' ]]; then
  if grep -qE 'DELETE[[:space:]]*/v1/runs/\{runId\}|DELETE.*runId.*cancel' 'docs/contracts/openapi-v1.yaml' 2>/dev/null; then
    fail_rule "http_contract_w1_tenant_and_cancel_consistency" "docs/contracts/openapi-v1.yaml references DELETE /v1/runs/{runId} as cancel. Per ADR-0040 cancel is POST /v1/runs/{id}/cancel."
    _r16_fail=1
  fi
fi
if [[ $_r16_fail -eq 0 ]]; then pass_rule "http_contract_w1_tenant_and_cancel_consistency"; fi

# ---------------------------------------------------------------------------
