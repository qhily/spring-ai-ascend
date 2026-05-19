#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 69 — every_active_rule_has_card. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 69 — every_active_rule_has_card (enforcer E99)
#
# Every "#### Rule NN" heading in CLAUDE.md MUST have a sibling
# docs/governance/rules/rule-NN.md (zero-padded). Every card MUST either
# (a) appear as a heading in CLAUDE.md, or
# (b) appear as a "Rule NN" reference in docs/CLAUDE-deferred.md.
# Orphan cards that satisfy neither are a fail.
#
# Initial PR1 mode (loose): if docs/governance/rules/ does not exist yet,
# the rule is vacuously true so the budget-gate and other rules can land first.
# ---------------------------------------------------------------------------
_r69_fail=0
_r69_claude='CLAUDE.md'
_r69_deferred='docs/CLAUDE-deferred.md'
_r69_cards_dir='docs/governance/rules'
if [[ ! -d "$_r69_cards_dir" ]]; then
  pass_rule "every_active_rule_has_card"
else
  # rc9 hardening: use temp files instead of multi-line shell variables to avoid
  # SIGPIPE races under the parallel orchestrator (`xargs -P8` + nested subshells +
  # in-loop `echo "$var" | grep -qxF` can truncate the producer's output, producing
  # flaky false-positive "active rules with no card: NN" / "orphan cards: NN"
  # failures on Linux CI even when local WSL passes consistently).
  _r69_active_f=$(mktemp 2>/dev/null || echo "/tmp/r69_active.$$")
  _r69_cards_f=$(mktemp 2>/dev/null || echo "/tmp/r69_cards.$$")
  # Active rule IDs: extract the identifier after `#### Rule ` (can be integer
  # OR namespaced: D-1, R-C.a, G-3.f, M-2.b). Normalise zero-padding away for
  # comparison with card filenames (which may also still have integer form during
  # transition).
  grep -oE '^#### Rule [A-Za-z0-9.-]+' "$_r69_claude" 2>/dev/null \
    | sed -E 's/^#### Rule //; s/^0*([0-9])/\1/' | sort -u > "$_r69_active_f"
  # Card filenames: rule-<id>.md where <id> may be integer or namespaced.
  find "$_r69_cards_dir" -maxdepth 1 -name 'rule-*.md' -type f 2>/dev/null \
    | sed -E 's|.*/rule-(.+)\.md$|\1|; s/^0*([0-9])/\1/' | sort -u > "$_r69_cards_f"
  # Missing cards: active - cards (set difference via comm).
  _r69_missing=$(comm -23 "$_r69_active_f" "$_r69_cards_f" | tr '\n' ' ' | sed 's/[[:space:]]*$//')
  if [[ -n "$_r69_missing" ]]; then
    fail_rule "every_active_rule_has_card" "active rules with no card: $_r69_missing"
    _r69_fail=1
  fi
  # Orphan cards: card exists but rule is neither active nor deferred.
  _r69_orphans=""
  while IFS= read -r _n; do
    [[ -z "$_n" ]] && continue
    if grep -qxF "$_n" "$_r69_active_f"; then
      continue
    fi
    if [[ -f "$_r69_deferred" ]] && grep -qE "Rule[[:space:]]+${_n}([.][a-z])?\b" "$_r69_deferred"; then
      continue
    fi
    _r69_orphans+="$_n "
  done < "$_r69_cards_f"
  rm -f "$_r69_active_f" "$_r69_cards_f"
  if [[ -n "$_r69_orphans" ]]; then
    fail_rule "every_active_rule_has_card" "orphan cards (no active or deferred reference): $_r69_orphans"
    _r69_fail=1
  fi
  if [[ $_r69_fail -eq 0 ]]; then
    pass_rule "every_active_rule_has_card"
  fi
fi

# ---------------------------------------------------------------------------
