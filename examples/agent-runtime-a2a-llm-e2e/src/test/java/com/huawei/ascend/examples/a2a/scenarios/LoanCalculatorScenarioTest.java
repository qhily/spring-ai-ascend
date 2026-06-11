package com.huawei.ascend.examples.a2a.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.factory.AgentHandlerFactory;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Loan calculator — a YAML-defined agent exposing a Java amortization
 * calculator as a {@code file:Class#method} tool with a JSON-Schema input
 * contract. The scenario proves the skill/file-tool form at its contract
 * surface: the YAML resolves and registers the tool, the declared
 * {@code inputSchema} rejects malformed requests BEFORE the calculator runs,
 * and a valid request yields the exact {@code BigDecimal} annuity schedule.
 * No runtime boot is needed: the tool registry and schema validation are the
 * seams under test (the YAML → boot → A2A wire path is covered by
 * {@link FxRateDeskScenarioTest}).
 *
 * <p>Expected figures are the textbook annuity for 1,000,000 at 4.8% nominal
 * over 240 months (monthly rate exactly 0.004): payment
 * 1,000,000 × 0.004 × 1.004^240 / (1.004^240 − 1) = 6489.57 to the cent.
 */
class LoanCalculatorScenarioTest {

    @Test
    void yamlFileToolComputesTheExactAnnuitySchedule() throws Exception {
        Tool tool = resolveLoanTool();

        Object result = tool.invoke(inputs(Map.of(
                "principal", 1_000_000,
                "annualRatePercent", 4.8,
                "termMonths", 240)), new LinkedHashMap<>());

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> schedule = (Map<?, ?>) result;
        assertThat(schedule.get("monthlyPayment")).isEqualTo("6489.57");
        assertThat(schedule.get("totalPayment")).isEqualTo("1557496.80");
        assertThat(schedule.get("totalInterest")).isEqualTo("557496.80");
    }

    @Test
    void inputSchemaRejectsAMissingRequiredField() throws Exception {
        Tool tool = resolveLoanTool();

        assertThatThrownBy(() -> tool.invoke(inputs(Map.of(
                "principal", 500_000,
                "annualRatePercent", 4.8)), new LinkedHashMap<>()))
                .hasStackTraceContaining("termMonths");
    }

    @Test
    void inputSchemaRejectsANonIntegerTerm() throws Exception {
        Tool tool = resolveLoanTool();

        assertThatThrownBy(() -> tool.invoke(inputs(Map.of(
                "principal", 500_000,
                "annualRatePercent", 4.8,
                "termMonths", "twenty")), new LinkedHashMap<>()))
                .hasStackTraceContaining("integer");
    }

    @Test
    void interestFreeLoanDividesThePrincipalEvenly() {
        Map<String, Object> schedule = LoanAmortizationCalculator.calculate(inputs(Map.of(
                "principal", 120_000,
                "annualRatePercent", 0,
                "termMonths", 12)));

        assertThat(schedule.get("monthlyPayment")).isEqualTo("10000.00");
        assertThat(schedule.get("totalInterest")).isEqualTo("0.00");
    }

    /**
     * Builds the agent from the YAML and looks the registered tool up the way
     * the framework itself does — through openJiuwen's global tool registry.
     */
    private static Tool resolveLoanTool() {
        String suffix = ScenarioYamls.uniqueSuffix();
        Path yaml = ScenarioYamls.materialize("loan-calculator.yaml", Map.of("@SUFFIX@", suffix));

        AgentRuntimeHandler handler = AgentHandlerFactory.fromYaml(yaml);
        assertThat(handler.agentId()).isEqualTo("loanCalculator" + suffix);

        Object tool = Runner.resourceMgr().getTool("loanAmortization" + suffix);
        assertThat(tool).isInstanceOf(Tool.class);
        return (Tool) tool;
    }

    /** Mutable copies: openJiuwen's schema validation normalizes the input map in place. */
    private static Map<String, Object> inputs(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }
}
