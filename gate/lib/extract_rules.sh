#!/usr/bin/env bash
# Extract per-rule bodies from gate/check_architecture_sync.sh into
# gate/rules/rule-<NN>.sh files. Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).
#
# Idempotent: re-running on an unchanged monolith produces byte-identical
# output. Used by Wave 3 to materialise the 82-file extraction the plan
# promised, while preserving the runtime parallelisation already provided
# by gate/check_parallel.sh.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

SOURCE_SCRIPT="gate/check_architecture_sync.sh"
RULES_DIR="gate/rules"

mkdir -p "$RULES_DIR"

# Build a manifest: rule_number<TAB>rule_slug<TAB>start_line<TAB>end_line
awk '
  function emit_prev(end) {
    if (prev_slug != "") {
      printf "%s\t%s\t%d\t%d\n", prev_num, prev_slug, prev_start, end
    }
  }
  BEGIN { prev_slug = ""; prev_start = 0 }
  /^# Rule [0-9]+[a-z]? — / {
    emit_prev(NR - 1)
    match($0, /^# Rule ([0-9]+[a-z]?) — ([a-z_0-9]+)/, arr)
    prev_num = arr[1]
    prev_slug = arr[2]
    prev_start = NR
    next
  }
  /^# Summary$/ {
    emit_prev(NR - 1)
    prev_slug = ""
    exit
  }
  END { emit_prev(NR) }
' "$SOURCE_SCRIPT" > /tmp/extract_rules.tsv

count=0
while IFS=$'\t' read -r num slug start end; do
  [[ -z "$num" ]] && continue
  num_padded=$(printf '%03d' "${num%%[a-z]}")
  letter=$(printf '%s' "$num" | sed -E 's/^[0-9]+//')
  out="$RULES_DIR/rule-${num_padded}${letter}.sh"
  {
    printf '#!/usr/bin/env bash\n'
    printf '# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh\n'
    printf '# Rule %s — %s. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.\n' "$num" "$slug"
    printf '# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).\n'
    printf '\n'
    sed -n "${start},${end}p" "$SOURCE_SCRIPT"
  } > "$out"
  count=$((count + 1))
done < /tmp/extract_rules.tsv

rm -f /tmp/extract_rules.tsv

echo "Extracted $count rules into $RULES_DIR/"
