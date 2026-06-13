package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.ErrorCategory;
import org.junit.jupiter.api.Test;

class AgentScopeEventTest {

    @Test
    void failedWithNullCodeUsesExplicitUnclassifiedSentinel() {
        AgentScopeEvent event = AgentScopeEvent.failed(null, "something went wrong");

        assertThat(event.errorCode())
                .isEqualTo(AgentScopeEvent.UNCLASSIFIED_ERROR_CODE)
                .isNotEqualTo("AGENTSCOPE_ERROR");
    }

    @Test
    void failedWithBlankCodeUsesExplicitUnclassifiedSentinel() {
        AgentScopeEvent event = AgentScopeEvent.failed("   ", "something went wrong");

        assertThat(event.errorCode())
                .isEqualTo(AgentScopeEvent.UNCLASSIFIED_ERROR_CODE)
                .isNotEqualTo("AGENTSCOPE_ERROR");
    }

    @Test
    void failedWithRealCodePreservesItExactly() {
        AgentScopeEvent event = AgentScopeEvent.failed("AGENTSCOPE_RUNTIME_IO", "network failure");

        assertThat(event.errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
    }

    @Test
    void unclassifiedSentinelMapsToUnknownCategory() {
        assertThat(AgentScopeErrorCategories.categorize(AgentScopeEvent.UNCLASSIFIED_ERROR_CODE))
                .isEqualTo(ErrorCategory.UNKNOWN);
    }

    @Test
    void nonFailedTypesDoNotReceiveSentinelFill() {
        AgentScopeEvent output = AgentScopeEvent.output("hello");
        AgentScopeEvent completed = AgentScopeEvent.completed("done");
        AgentScopeEvent interrupted = AgentScopeEvent.interrupted("need input");

        assertThat(output.errorCode()).isNull();
        assertThat(completed.errorCode()).isNull();
        assertThat(interrupted.errorCode()).isNull();
    }
}
