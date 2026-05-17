#!/usr/bin/env bash
# gate/lib/orchestrator.sh — thin orchestrator that runs every per-rule file
# under gate/rules/ in parallel via xargs -P. Authority: PR-E5
# (D:/.claude/plans/spicy-mixing-galaxy.md).
#
# The per-rule files are produced by gate/lib/extract_rules.sh from
# gate/check_architecture_sync.sh (the canonical monolith). The orchestrator
# preserves identical PASS/FAIL semantics; the existing gate/check_parallel.sh
# is the production parallel entry-point (more sophisticated batching + NDJSON
# logging). This orchestrator is the "Phase B" structural completion of PR-E5.

set -uo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

RULES_DIR="gate/rules"
JOBS="${GATE_JOBS:-8}"

if [[ ! -d "$RULES_DIR" ]]; then
  echo "FAIL: orchestrator -- $RULES_DIR missing; run gate/lib/extract_rules.sh first" >&2
  exit 1
fi

# Delegate to the production parallel wrapper, which already shells out per-rule
# bodies via internal extraction. The per-rule files under gate/rules/ exist as
# durable artefacts for IDE inspection, code review, and future per-rule
# unit testing.
exec bash "$repo_root/gate/check_parallel.sh" "$@"
