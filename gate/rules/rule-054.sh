#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 54 — skill_capacity_runtime_resolver_present. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 54 — skill_capacity_runtime_resolver_present (enforcer E73, Rule 41.b / P-K, ADR-0070)
#
# A production ResilienceContract implementation MUST exist under
# agent-service/src/main that (a) implements the two-arg resolve signature
# returning SkillResolution and (b) consults a SkillCapacityRegistry's
# tryAcquire(...) method. The gate greps for the canonical class shape so a
# regression that silently admits every caller (returning admit() unconditionally)
# fails. The matching integration test (E73) verifies behaviour separately.
# ---------------------------------------------------------------------------
_r54_fail=0
_r54_main="agent-service/src/main/java/ascend/springai/service/runtime/resilience"
if [[ ! -d "$_r54_main" ]]; then
  fail_rule "skill_capacity_runtime_resolver_present" "$_r54_main directory missing — Rule 41.b runtime classes not landed"
  _r54_fail=1
else
  if [[ ! -f "$_r54_main/SkillCapacityRegistry.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SkillCapacityRegistry.java missing — Rule 41.b capacity tracking SPI absent"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_main/SkillResolution.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SkillResolution.java missing — Rule 41.b admit/reject envelope absent"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_main/SuspendReason.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "SuspendReason.java missing — Rule 41.b sealed reason taxonomy absent"
    _r54_fail=1
  fi
  if [[ ! -f "$_r54_main/DefaultSkillResilienceContract.java" ]]; then
    fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java missing — Rule 41.b production impl absent"
    _r54_fail=1
  else
    if ! grep -qE 'SkillResolution[[:space:]]+resolve\([[:space:]]*String[[:space:]]+\w+,[[:space:]]*String[[:space:]]+\w+[[:space:]]*\)' "$_r54_main/DefaultSkillResilienceContract.java"; then
      fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java missing two-arg resolve(String, String) returning SkillResolution"
      _r54_fail=1
    fi
    if ! grep -qE 'tryAcquire\(' "$_r54_main/DefaultSkillResilienceContract.java"; then
      fail_rule "skill_capacity_runtime_resolver_present" "DefaultSkillResilienceContract.java does not call SkillCapacityRegistry.tryAcquire — Rule 41.b runtime consultation missing"
      _r54_fail=1
    fi
  fi
fi
if [[ $_r54_fail -eq 0 ]]; then pass_rule "skill_capacity_runtime_resolver_present"; fi

# ---------------------------------------------------------------------------
