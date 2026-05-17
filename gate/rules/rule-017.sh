#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 17 — contract_catalog_spi_table_matches_source. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 17 — contract_catalog_spi_table_matches_source
# ADR-0041: contract-catalog.md must list the 7 known active SPI interfaces.
# OssApiProbe must NOT appear before the **Probes sub-table heading.
# ---------------------------------------------------------------------------
_r17_fail=0
_catalog17='docs/contracts/contract-catalog.md'
_known_spis=('RunRepository' 'Checkpointer' 'GraphMemoryRepository' 'ResilienceContract' 'Orchestrator' 'GraphExecutor' 'AgentLoopExecutor')
if [[ -f "$_catalog17" ]]; then
  for _spi in "${_known_spis[@]}"; do
    if ! grep -qF "$_spi" "$_catalog17" 2>/dev/null; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 does not list SPI '$_spi'. Per ADR-0041 Gate Rule 17 all 7 active SPI interfaces must appear."
      _r17_fail=1
    fi
  done
  if [[ $_r17_fail -eq 0 ]]; then
    _past_probes=0
    while IFS= read -r _ln17; do
      if echo "$_ln17" | grep -qE '\*\*Probes|^#+[[:space:]]+Probes'; then _past_probes=1; fi
      if [[ $_past_probes -eq 0 ]] && echo "$_ln17" | grep -qF 'OssApiProbe'; then
        fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 contains OssApiProbe before the Probes sub-table. OssApiProbe is a probe, not an SPI. Per ADR-0041 Gate Rule 17."
        _r17_fail=1
        break
      fi
    done < "$_catalog17"
  fi
  # ADR-0044 extension: RunContext row in data-carriers sub-table must contain 'interface'
  if [[ $_r17_fail -eq 0 ]]; then
    _in_data_carriers=0
    _run_ctx_has_interface=0
    _run_ctx_found=0
    while IFS= read -r _ln17x; do
      if echo "$_ln17x" | grep -qE '\*\*Data carriers'; then _in_data_carriers=1; fi
      if [[ $_in_data_carriers -eq 1 ]] && echo "$_ln17x" | grep -qF 'RunContext'; then
        _run_ctx_found=1
        if echo "$_ln17x" | grep -qF 'interface'; then _run_ctx_has_interface=1; fi
        break
      fi
    done < "$_catalog17"
    if [[ $_run_ctx_found -eq 1 && $_run_ctx_has_interface -eq 0 ]]; then
      fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 RunContext row in data-carriers sub-table does not contain 'interface'. Per ADR-0044 Gate Rule 17 extension RunContext must be classified as interface."
      _r17_fail=1
    fi
  fi
else
  fail_rule "contract_catalog_spi_table_matches_source" "$_catalog17 not found."
  _r17_fail=1
fi
if [[ $_r17_fail -eq 0 ]]; then pass_rule "contract_catalog_spi_table_matches_source"; fi

# ---------------------------------------------------------------------------
