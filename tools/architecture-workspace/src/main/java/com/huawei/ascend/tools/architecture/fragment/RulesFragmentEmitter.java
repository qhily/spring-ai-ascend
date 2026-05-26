package com.huawei.ascend.tools.architecture.fragment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W3 emitter: scans CLAUDE.md for `#### Rule X — title` headers and emits one
 * {@code element ... "SAA Rule"} per rule. The rule identifier is
 * {@code rule_<sanitized-id>} so that PrinciplesFragmentEmitter can target
 * each rule with an `operationalised_by` relationship.
 */
public final class RulesFragmentEmitter {

    private static final Pattern RULE_HEADER =
            Pattern.compile("^####\\s+Rule\\s+([A-Z]?-?[\\w.]+)\\s+—\\s+(.+?)$", Pattern.MULTILINE);

    // Map abbreviated rule ids (D-1 etc.) to the rule card paths under docs/governance/rules/.
    private static final Pattern RULE_LINK = Pattern.compile("rule-(\\S+?)\\.md");

    private RulesFragmentEmitter() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = Path.of(argValue(args, "--repo", "."));
        Path output = Path.of(argValue(args, "--output", "architecture/generated/rules.dsl"));

        String claudeMd = Files.readString(repoRoot.resolve("CLAUDE.md"), StandardCharsets.UTF_8);
        Matcher m = RULE_HEADER.matcher(claudeMd);

        // Use a TreeMap so output is sorted by id deterministically.
        Map<String, String> rules = new java.util.TreeMap<>();
        while (m.find()) {
            String id = m.group(1);
            String title = m.group(2).trim();
            rules.putIfAbsent(id, title);
        }

        try (FragmentWriter.StagedFragment frag = FragmentWriter.open(
                output,
                "CLAUDE.md #### Rule X headers",
                RulesFragmentEmitter.class.getName(),
                rules.size())) {

            StringBuilder buf = frag.buf();
            for (Map.Entry<String, String> e : rules.entrySet()) {
                String id = e.getKey();
                String title = e.getValue();
                String identifier = "rule_" + FragmentWriter.safeId(id);
                String saaId = "RULE-" + id;

                buf.append(identifier).append(" = element \"")
                        .append(FragmentWriter.escape("Rule " + id))
                        .append("\" \"Rule\" \"")
                        .append(FragmentWriter.escape(title))
                        .append("\" \"SAA Rule\" {\n");

                Map<String, String> props = new LinkedHashMap<>();
                props.put("saa.id", saaId);
                props.put("saa.kind", "rule");
                props.put("saa.level", classifyLevel(id));
                props.put("saa.view", "scenarios");
                props.put("saa.status", "shipped");
                props.put("saa.sourceAdr", "ADR-0086");
                FragmentWriter.writeProperties(buf, props);
                buf.append("}\n\n");
            }
        }

        System.out.println("RulesFragmentEmitter wrote " + rules.size() + " rules to " + output);
    }

    private static String classifyLevel(String id) {
        if (id.startsWith("D-") || id.startsWith("R-") || id.startsWith("M-")) {
            return "L1";
        }
        if (id.startsWith("G-")) {
            return "L0";
        }
        if (id.startsWith("P-")) {
            return "L0";
        }
        return "L1";
    }

    private static String argValue(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }
}
