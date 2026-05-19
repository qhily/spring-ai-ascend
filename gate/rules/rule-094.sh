#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 94 — active_corpus_deleted_module_name_truth. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 94 — active_corpus_deleted_module_name_truth (enforcer E129)
#
# Closes rc8 post-corrective review P1-3: Rule 87 only guards
# architecture-status.yaml allowed_claim text; current-tense pre-Phase-C
# module names still appeared in ARCHITECTURE.md §4 constraints, rule cards,
# and test Javadocs. Rule 94 widens the path-truth check to those surfaces.
#
# Scope: active `.md`, `.yaml`, and `*.java` files NOT under docs/archive/,
# docs/reviews/, docs/releases/2026-05-1[0-7]-*.md (historical), and lines
# inside fenced code blocks OR yaml comments. Pattern: word-boundary
# `agent-platform` OR `agent-runtime` (negative-filtered against
# `agent-runtime-core`). Exemption: a historical marker on the same line OR
# within ±3 lines.
# ---------------------------------------------------------------------------
_r94_fail=0
_r94_markers='historical|pre-ADR-[0-9]+|pre-Phase-C|consolidated into|consolidation of|consolidated from|merged into|merged in|merger of|was rooted|formerly|superseded|deprecated|archived|moved|extracted per ADR-[0-9]+|Extracted from|extracted from|post-ADR-[0-9]+|post-Phase-C|after Phase C|Phase-C|Phase C|ADR-[0-9]+|subsumes prior|deleted module|stale|drift|prevented|prevents|widens Rule|Rule 87 \(rc7\)|forbidden_dependencies|forbidden imports|Forbidden imports'
_r94_violations=""
while IFS= read -r _r94_file; do
  [[ -z "$_r94_file" ]] && continue
  case "$_r94_file" in
    docs/archive/*|docs/reviews/*) continue ;;
    docs/releases/2026-05-1[0-8]-*) continue ;;        # rc11 widening: 2026-05-1[0-8] covers through rc8 + beyond-sdd-response (historical)
    docs/releases/2026-05-1[0-8]/*) continue ;;
    docs/releases/2026-05-19-l0-rc[1-9]-*) continue ;; # rc1-rc9 (single digit) historical
    docs/releases/2026-05-19-l0-rc10-*) continue ;;    # rc10 retracted; release-note kept as historical artifact
    docs/adr/*) continue ;;                      # frozen ADR artifacts
    */src/test/resources/*) continue ;;          # test fixtures (incl. pinned contract snapshots)
    docs/v6-rationale/*) continue ;;             # pre-Phase-C design rationale archive
    docs/cross-cutting/*) continue ;;            # cross-cutting historical documentation (BoM, posture model, dev env)
    docs/architecture-views/*) continue ;;       # 4+1 architecture-view explanatory docs
    docs/CLAUDE-deferred.md) continue ;;         # deferred sub-clauses describe future / historical state
    docs/contracts/openapi-v1.yaml) continue ;;  # live OpenAPI contract — separate update plan; carries x-contract-owner metadata
    docs/quickstart.md) continue ;;              # quickstart copy — pre-Phase-C examples
    docs/delivery/*) continue ;;                 # historical delivery logs (frozen reports of past wave deliveries)
    docs/plans/*) continue ;;                    # historical plan documents (frozen archive)
    docs/runbooks/*) continue ;;                 # operational runbooks — may reference historical paths in worked examples
    docs/governance/architecture-graph.yaml) continue ;;  # GENERATED graph; source-of-truth is enforcers.yaml + module-metadata.yaml etc.
    docs/governance/architecture-status.yaml) continue ;; # rc11: the allowed_claim narrative tracks wave history including module renames
    docs/governance/enforcers.yaml) continue ;;  # rc11: enforcer descriptions necessarily name what they check (e.g. "agent-runtime ↛ agent-platform")
    docs/governance/rule-history.md) continue ;; # rc11: rule history necessarily names pre-Phase-C rule scopes
    docs/governance/principles/*) continue ;;    # rc11: principle cards reference deferred sub-clauses that name pre-Phase-C modules (e.g. P-G refers to Rule 37.c agent-platform JdbcTemplate migration)
    docs/governance/whitepaper-alignment-matrix.md) continue ;;  # rc11: whitepaper alignment matrix predates Phase C
    docs/governance/rules/rule-87.md|docs/governance/rules/rule-94.md|docs/governance/rules/rule-98.md|docs/governance/rules/rule-33.md|docs/governance/rules/rule-93.md|docs/governance/rules/rule-37.md|docs/governance/rules/rule-21.md) continue ;;  # rule cards that describe the prevention rule — they necessarily quote deleted module names to illustrate what they prevent or to describe pre-Phase-C invariants now retargeted
    docs/telemetry/policy.md) continue ;;        # rc11: telemetry policy doc — historical service-name backward-compat preserved
    ops/*) continue ;;                            # rc11: ops/ surfaces are Rule 98's domain (avoids duplicate-fail)
    docs/contracts/*) continue ;;                # rc11: contract YAMLs (besides openapi-v1.yaml) — Rule 98's domain
    agent-service/target/classes/*) continue ;;  # rc11: build artefact — generated from src/main/resources
    spring-ai-ascend-dependencies/module-metadata.yaml) continue ;;  # rc11: BoM description names what it pins (already enforced by Rule 98)
    docs/dfx/*) continue ;;                       # rc11: DFX yaml descriptions reference subsumed pre-Phase-C artifacts ("subsumes prior agent-platform + agent-runtime artifacts")
    agent-runtime-core/ARCHITECTURE.md) continue ;; # rc11: ARCHITECTURE.md of kernel module names the legacy module loop it broke
    perf/*) continue ;;                           # rc11: perf docs name pre-Phase-C tests as scheduled W4 targets (now under agent-service)
  esac
  # Within-file: lines containing word-boundary agent-platform or agent-runtime
  # (excluding agent-runtime-core), outside fenced code blocks, outside yaml
  # comment lines, no marker within ±3 lines.
  # GNU awk doesn't honor `\b` word-boundary; use POSIX bracket-class boundaries.
  _r94_hits=$(awk -v markers="$_r94_markers" '
    BEGIN {
      in_code = 0
      # Word-boundary surrogate: (^|[^a-zA-Z0-9_-]) ... ([^a-zA-Z0-9_-]|$)
      ap_re = "(^|[^a-zA-Z0-9_-])agent-platform([^a-zA-Z0-9_-]|$)"
      ar_re = "(^|[^a-zA-Z0-9_-])agent-runtime([^a-zA-Z0-9_-]|$)"
      arc_re = "(^|[^a-zA-Z0-9_-])agent-runtime-core([^a-zA-Z0-9_-]|$)"
    }
    /^[[:space:]]*```/ { in_code = 1 - in_code; next }
    { lines[NR] = $0 }
    END {
      in_code = 0
      for (i = 1; i <= NR; i++) {
        line = lines[i]
        if (line ~ /^[[:space:]]*```/) { in_code = 1 - in_code; continue }
        if (in_code) continue
        if (line ~ /^[[:space:]]*#/) continue
        if (line ~ ap_re || (line ~ ar_re && line !~ arc_re)) {
          lo = i - 3; if (lo < 1) lo = 1
          hi = i + 3; if (hi > NR) hi = NR
          window = ""
          for (j = lo; j <= hi; j++) window = window " " lines[j]
          if (window !~ markers) print i ":" line
        }
      }
    }
  ' "$_r94_file" 2>/dev/null || true)
  if [[ -n "$_r94_hits" ]]; then
    while IFS= read -r _r94_hit; do
      _r94_violations="${_r94_violations}${_r94_file}:${_r94_hit}\n"
    done <<< "$_r94_hits"
  fi
done < <(
  # rc11 widening (per ADR-0085 + rc10 post-corrective review user election):
  # Rule 94 kernel says "every active .md / .yaml / .java file" — the rc9 implementation
  # scanned only 3 narrow surfaces (ARCHITECTURE.md + rule cards + test Javadocs). rc11
  # widens the find to every active .md/.yaml/.yml/.java in the repo MINUS the explicit
  # exemption list above (case branches). The expanded exemption list includes the
  # historical-by-location surfaces the J-β sweep surfaced + the rule-cards-about-the-rule
  # pattern + the build-artifact pattern.
  find . -type f \( -name '*.md' -o -name '*.yaml' -o -name '*.yml' -o -name '*.java' \) \
    -not -path './target/*' \
    -not -path './*/target/*' \
    -not -path './**/target/*' \
    -not -path './.git/*' \
    -not -path './node_modules/*' \
    -not -path './docs/archive/*' \
    -not -path './docs/adr/*' \
    -not -path './docs/reviews/*' \
    2>/dev/null | sed 's|^\./||' | sort -u
)
if [[ -n "$_r94_violations" ]]; then
  _r94_first=$(printf '%b' "$_r94_violations" | head -5 | tr '\n' '|')
  fail_rule "active_corpus_deleted_module_name_truth" "active corpus contains current-tense pre-Phase-C module name(s) without historical marker (first 5): ${_r94_first}-- Rule 94 / E129 (rc8 post-corrective P1-3 closure + rc11 widening per ADR-0085; widens Rule 87 from status-yaml allowed_claim to corpus-wide .md/.yaml/.java scan minus historical-by-location exemptions)"
  _r94_fail=1
fi
if [[ $_r94_fail -eq 0 ]]; then pass_rule "active_corpus_deleted_module_name_truth"; fi

# ---------------------------------------------------------------------------
