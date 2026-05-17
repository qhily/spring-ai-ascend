#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 18 — deleted_spi_starter_names_outside_catalog. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 18 — deleted_spi_starter_names_outside_catalog
# ADR-0041 extends Rule 13: deleted SPI/starter names must not appear in
# third_party/MANIFEST.md, docs/cross-cutting/oss-bill-of-materials.md, README.md.
# ---------------------------------------------------------------------------
_r18_fail=0
_deleted_names18=(
  'LongTermMemoryRepository' 'ToolProvider' 'LayoutParser' 'DocumentSourceConnector'
  'PolicyEvaluator' 'IdempotencyRepository' 'ArtifactRepository'
  'spring-ai-ascend-memory-starter' 'spring-ai-ascend-skills-starter'
  'spring-ai-ascend-knowledge-starter' 'spring-ai-ascend-governance-starter'
  'spring-ai-ascend-persistence-starter' 'spring-ai-ascend-resilience-starter'
  'spring-ai-ascend-mem0-starter' 'spring-ai-ascend-docling-starter'
  'spring-ai-ascend-langchain4j-profile'
)
# Widened to full ACTIVE_NORMATIVE_DOCS corpus (ADR-0043)
while IFS= read -r _t18; do
  [[ -z "$_t18" ]] && continue
  for _dn18 in "${_deleted_names18[@]}"; do
    if grep -qF "$_dn18" "$_t18" 2>/dev/null; then
      fail_rule "deleted_spi_starter_names_outside_catalog" "$_t18 references deleted name '$_dn18'. Per ADR-0043 Gate Rule 18 (widened) this is a contract-surface truth violation."
      _r18_fail=1
    fi
  done
done < <(find . -name '*.md' -o -name '*.yaml' | grep -v '/docs/archive/' | grep -v '/docs/reviews/' | \
  grep -v '/docs/adr/' | grep -v '/docs/delivery/' | grep -v '/docs/v6-rationale/' | \
  grep -v '/docs/plans/' | grep -v '/third_party/' | grep -v '/target/' | grep -v '/.git/' | sort 2>/dev/null || true)
if [[ $_r18_fail -eq 0 ]]; then pass_rule "deleted_spi_starter_names_outside_catalog"; fi

# ---------------------------------------------------------------------------
