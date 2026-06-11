package com.huawei.ascend.examples.a2a;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps the LLM egress gateway a wire-level interposition for sample code:
 * agents reach it through the OpenAI-compatible {@code /v1} HTTP surface with
 * a minted token (the {@code sample.llm.via-gateway} flip selects that path
 * purely in configuration), never by importing the gateway's in-process
 * classes. A direct dependency from {@code com.huawei.ascend.examples..} on
 * {@code com.huawei.ascend.runtime.llm.gateway..} would bypass the
 * interposition point — auth, alias routing, usage metering and the
 * listener seam — and turn the gateway into a library call.
 */
class LlmGatewayWireSeamArchTest {

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .importPackages("com.huawei.ascend");

    @Test
    void examples_must_not_import_llm_gateway_classes_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.examples..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.runtime.llm.gateway..")
                .allowEmptyShould(true);
        rule.check(ALL_CLASSES);
    }
}
