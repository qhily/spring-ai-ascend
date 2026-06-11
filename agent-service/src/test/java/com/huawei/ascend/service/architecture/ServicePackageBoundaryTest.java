package com.huawei.ascend.service.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the serviceization module boundaries: the SPI and its reference
 * implementations stay Spring-free, never reach into the frozen bus SPI plane,
 * and touch agent-runtime only through its public engine SPI.
 */
class ServicePackageBoundaryTest {

    private static final JavaClasses SERVICE_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.huawei.ascend.service");

    @Test
    void serviceModuleIsSpringFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .allowEmptyShould(false);
        rule.check(SERVICE_CLASSES);
    }

    @Test
    void serviceModuleDoesNotDependOnBusSpi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.service..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.huawei.ascend.bus.spi..")
                .allowEmptyShould(false);
        rule.check(SERVICE_CLASSES);
    }

    @Test
    void serviceModuleTouchesRuntimeOnlyThroughEngineSpi() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.huawei.ascend.service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.huawei.ascend.runtime.access..",
                        "com.huawei.ascend.runtime.app..",
                        "com.huawei.ascend.runtime.boot..",
                        "com.huawei.ascend.runtime.common..",
                        "com.huawei.ascend.runtime.control..",
                        "com.huawei.ascend.runtime.queue..",
                        "com.huawei.ascend.runtime.run..",
                        "com.huawei.ascend.runtime.session..")
                .allowEmptyShould(false);
        rule.check(SERVICE_CLASSES);
    }
}
