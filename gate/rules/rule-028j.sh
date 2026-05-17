#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 28j — enforcer_artifact_paths_exist. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5 (D:/.claude/plans/spicy-mixing-galaxy.md).

# Rule 28j — enforcer_artifact_paths_exist (Phase K F6 + Phase L P0-2, E33+E35)
# Every `artifact:` path in docs/governance/enforcers.yaml MUST resolve to a
# real file on disk. `#anchor` suffixes (e.g. `RunHttpContractIT.java#cancel...`
# or `check_architecture_sync.sh#rule_10`) MUST also resolve to a real method
# (.java/.sh) or heading (.md) inside that file. Phase L strengthens the
# file-only check (which let E5/E6/E24 ship with anchors pointing at methods
# that did not exist — closes reviewer finding P0-2).
# ---------------------------------------------------------------------------
_r28j_fail=0
if [[ -f "$_efile" ]]; then
  while IFS= read -r _aline; do
    [[ -z "$_aline" ]] && continue
    _aval=${_aline#*artifact:}
    _aval=${_aval#"${_aval%%[![:space:]]*}"}    # ltrim
    _aval=${_aval%"${_aval##*[![:space:]]}"}     # rtrim
    _apath=${_aval%%#*}                          # path side
    _aanchor=""
    case "$_aval" in
      *'#'*) _aanchor=${_aval#*#} ;;
    esac
    [[ -z "$_apath" ]] && continue
    if [[ ! -e "$_apath" ]]; then
      fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact path '$_apath' which does not exist on disk. Per Rule 28j / enforcer E33."
      _r28j_fail=1
      continue
    fi
    if [[ -n "$_aanchor" ]]; then
      _aok=1
      case "$_apath" in
        *.java)
          # Method declaration: `void <anchor>(`, `<modifiers> <anchor>(`
          if ! grep -qE "(void|\)|\>|\>[[:space:]])[[:space:]]+${_aanchor}[[:space:]]*\(" "$_apath" 2>/dev/null; then
            if ! grep -qE "^[[:space:]]*[a-zA-Z_<>][^()]*[[:space:]]${_aanchor}[[:space:]]*\(" "$_apath" 2>/dev/null; then
              _aok=0
            fi
          fi
          ;;
        *.sh|*.bash)
          # Bash function definition: `<anchor>()` or `function <anchor>` or comment `# Rule N — <anchor>`
          if ! grep -qE "(^|[[:space:]])${_aanchor}[[:space:]]*\(\)" "$_apath" 2>/dev/null; then
            if ! grep -qE "^[[:space:]]*function[[:space:]]+${_aanchor}\b" "$_apath" 2>/dev/null; then
              if ! grep -qE "^#[[:space:]]*Rule[[:space:]]+[0-9a-z]+[[:space:]]+(—|--)[[:space:]]+${_aanchor}\b" "$_apath" 2>/dev/null; then
                if ! grep -qE "\b(pass_rule|fail_rule)[[:space:]]+\"${_aanchor}\"" "$_apath" 2>/dev/null; then
                  _aok=0
                fi
              fi
            fi
          fi
          ;;
        *.md)
          # Markdown heading: `^#+ ... <anchor> ...` (loose match — anchor can be slug or phrase)
          if ! grep -qE "^#+[[:space:]].*${_aanchor}" "$_apath" 2>/dev/null; then
            _aok=0
          fi
          ;;
        *.yaml|*.yml)
          # YAML anchor: any line containing the anchor literal (loose check)
          if ! grep -q "${_aanchor}" "$_apath" 2>/dev/null; then
            _aok=0
          fi
          ;;
        *)
          # Other file types: just require literal presence
          if ! grep -q "${_aanchor}" "$_apath" 2>/dev/null; then
            _aok=0
          fi
          ;;
      esac
      if [[ $_aok -eq 0 ]]; then
        fail_rule "enforcer_artifact_paths_exist" "enforcers.yaml declares artifact anchor '$_apath#$_aanchor' but no method/heading/rule with that name exists in the target file. Per Rule 28j / enforcer E33 (anchor validation added in Phase L, enforcer E35)."
        _r28j_fail=1
      fi
    fi
  done < <(grep -E '^[[:space:]]*artifact:' "$_efile" 2>/dev/null || true)
fi
if [[ $_r28j_fail -eq 0 ]]; then pass_rule "enforcer_artifact_paths_exist"; fi

# ---------------------------------------------------------------------------
