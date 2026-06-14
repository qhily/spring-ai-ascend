package com.huawei.ascend.bus.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POM / module-metadata drift harness for {@code agent-bus} (MI-002 follow-up).
 *
 * <p>{@link AgentBusDependencyBoundaryTest} proves via ArchUnit that production
 * bytecode never <em>reaches into</em> a sibling module. It cannot detect
 * configuration drift: a freshly-added {@code <dependency>} in
 * {@code agent-bus/pom.xml}, or a forbidden entry in
 * {@code module-metadata.yaml#forbidden_dependencies} that has no bytecode
 * trigger yet. This harness reads {@code pom.xml} and asserts the declared
 * dependency graph agrees with the module metadata:
 *
 * <ul>
 *   <li>{@code module-metadata.yaml#allowed_dependencies} is {@code []} — no
 *       {@code com.huawei.ascend:*} sibling may appear at production scope.
 *   <li>{@code archunit-junit5} and {@code spring-boot-starter-test} stay
 *       test-scope; they must never enter the production dependency graph.
 * </ul>
 *
 * <p>Authority: {@code agent-bus/module-metadata.yaml}; CLAUDE.md Rule R-C
 * sub-clause .b (Independent Module Evolution). Assertion ID: HA-005.
 */
class AgentBusModuleMetadataDriftTest {

    private static final File POM_XML = new File("pom.xml");
    private static List<Dependency> dependencies;

    @BeforeAll
    static void parsePom() throws Exception {
        assertThat(POM_XML)
                .as("agent-bus/pom.xml must be reachable from the surefire working directory "
                  + "(module basedir)")
                .exists();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(POM_XML);
        List<Dependency> parsed = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("dependency");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            parsed.add(new Dependency(
                    text(el, "groupId"),
                    text(el, "artifactId"),
                    text(el, "scope")));
        }
        dependencies = List.copyOf(parsed);
    }

    @Test
    void pom_declared_at_least_one_dependency() {
        assertThat(dependencies)
                .as("pom.xml parse sanity — at least one <dependency> expected")
                .isNotEmpty();
    }

    @Test
    void no_spring_ai_ascend_sibling_at_production_scope() {
        // allowed_dependencies: [] — agent-bus must never depend on a sibling
        // platform module at compile/runtime scope. test-scope siblings (test
        // fixtures) are the only permitted form.
        List<Dependency> productionScope = dependencies.stream()
                .filter(d -> "com.huawei.ascend".equals(d.groupId()))
                .filter(d -> !isTestScope(d.scope()))
                .toList();
        assertThat(productionScope)
                .as("agent-bus allowed_dependencies is [] — no com.huawei.ascend:* sibling may "
                  + "appear at production (compile/runtime) scope")
                .isEmpty();
    }

    @Test
    void archunit_junit5_remains_test_scope() {
        Dependency archunit = find("com.tngtech.archunit", "archunit-junit5");
        assertThat(archunit).as("archunit-junit5 must be declared").isNotNull();
        assertThat(isTestScope(archunit.scope()))
                .as("archunit-junit5 must remain test-scope (MI-002) — it must never enter the "
                  + "production dependency graph")
                .isTrue();
    }

    @Test
    void spring_boot_starter_test_remains_test_scope() {
        Dependency starter = find("org.springframework.boot", "spring-boot-starter-test");
        assertThat(starter).as("spring-boot-starter-test must be declared").isNotNull();
        assertThat(isTestScope(starter.scope()))
                .as("spring-boot-starter-test must remain test-scope (MI-002)")
                .isTrue();
    }

    // ---- helpers -----------------------------------------------------------

    private static boolean isTestScope(String scope) {
        return scope != null && scope.trim().equalsIgnoreCase("test");
    }

    private static Dependency find(String groupId, String artifactId) {
        return dependencies.stream()
                .filter(d -> groupId.equals(d.groupId()) && artifactId.equals(d.artifactId()))
                .findFirst()
                .orElse(null);
    }

    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? "" : nl.item(0).getTextContent().trim();
    }

    private record Dependency(String groupId, String artifactId, String scope) {}
}
