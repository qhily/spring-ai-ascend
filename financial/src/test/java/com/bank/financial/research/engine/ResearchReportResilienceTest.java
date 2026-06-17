package com.bank.financial.research.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.DataIngestionService;
import com.bank.financial.research.data.FreshnessPolicy;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.stub.StubResearchDataSource;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import org.junit.jupiter.api.Test;

/**
 * Resilience layer: the pipeline must degrade gracefully, never abort. A model
 * that always fails (the live-LLM failure mode) must still yield a complete
 * report — every section present, the deterministic figures intact — with the
 * failures surfaced as recorded degradations.
 */
class ResearchReportResilienceTest {

    private static final long AS_OF = 1_750_000_000_000L;

    /** A model that always throws — stands in for a down / timing-out LLM. */
    private static final class ThrowingModel implements ReportModel {
        @Override
        public String name() {
            return "throwing";
        }

        @Override
        public String generate(ModelTask task) {
            throw new RuntimeException("simulated model outage");
        }
    }

    private ResearchReport runWithFailingModel() {
        ResearchDataSource source = new StubResearchDataSource(AS_OF);
        ResearchReportEngine engine = new ResearchReportEngine(
                new DataIngestionService(source, FreshnessPolicy.days(90)),
                source.name(), new ThrowingModel(), null, MemoryObserver.NOOP, () -> AS_OF);
        return engine.generate(ReportRequest.equity("DEMO", "t-test", AS_OF));
    }

    @Test
    void failingModel_stillProducesCompleteReport() {
        ResearchReport r = runWithFailingModel();
        // All sections present despite every generation failing.
        assertEquals(7, r.sections().size());
        for (ReportSection s : r.sections()) {
            assertFalse(s.body().isBlank(), "section blank: " + s.id());
        }
    }

    @Test
    void failingModel_keepsDeterministicFiguresAndHouseView() {
        ResearchReport r = runWithFailingModel();
        // Valuation/rating are computed deterministically — unaffected by the model outage.
        assertTrue(r.priceTarget() > 0, "price target must survive a model outage");
        assertFalse(r.rating().isBlank());
        assertTrue(r.toMarkdown().contains("77.65"), "computed DCF should still appear");
    }

    @Test
    void failingModel_recordsDegradationsTransparently() {
        ResearchReport r = runWithFailingModel();
        assertFalse(r.metadata().degradations().isEmpty(), "model failures must be recorded, not hidden");
        // Each section's writer fallback + the thesis fallback should be logged.
        assertTrue(r.metadata().degradations().stream().anyMatch(d -> d.startsWith("writer:")));
    }

    @Test
    void healthyRun_hasNoDegradations() {
        ResearchReport r = ResearchReports.offline(AS_OF)
                .generate(ReportRequest.equity("DEMO", "t-test", AS_OF));
        assertTrue(r.metadata().degradations().isEmpty(),
                () -> "unexpected degradations: " + r.metadata().degradations());
    }
}
