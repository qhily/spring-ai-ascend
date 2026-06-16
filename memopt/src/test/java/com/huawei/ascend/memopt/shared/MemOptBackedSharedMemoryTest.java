package com.huawei.ascend.memopt.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.OwnershipViolationException;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * MemOpt as the pluggable backend: the unchanged a2a-shared-memory kit runs on
 * {@link MemOptSharedMemoryStore} and keeps the same semantics — proof the
 * SharedMemoryStore SPI swap (in-process kit store -> MemOpt engine) is invisible
 * to agents.
 */
class MemOptBackedSharedMemoryTest {

    @Test
    void agentsShareConclusionsThroughMemOptBackend() {
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(new MemOptSharedMemoryStore(), "bank", "c1");
        board.put("riskAssessment", "C3", "risk-agent");
        assertEquals(Optional.of("C3"), board.get("riskAssessment"), "advisor reads risk-agent's conclusion via MemOpt");
    }

    @Test
    void ownershipIsEnforcedThroughMemOptBackend() {
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(new MemOptSharedMemoryStore(), "bank", "c1");
        board.put("riskAssessment", "C3", "risk-agent");
        assertThrows(OwnershipViolationException.class, () -> board.put("riskAssessment", "x", "intruder"),
                "MemOpt backend enforces the ownership write rule");
    }

    @Test
    void memOptBackendEmitsInstrumentation() {
        Recording rec = new Recording();
        MemOptSharedMemoryStore store = new MemOptSharedMemoryStore(new InMemorySharedMemoryStore(() -> 1L), rec);
        SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, "bank", "c1");
        board.put("k", "v", "owner");
        board.get("k");
        assertTrue(rec.ops.contains("memopt.shared.append:true"), "append observed on the engine");
        assertTrue(rec.ops.contains("memopt.shared.latest:true"), "read observed on the engine");
    }

    private static final class Recording implements MemoryObserver {
        final List<String> ops = new ArrayList<>();

        @Override
        public void onOperation(String op, String scope, boolean ok, long latencyMs) {
            ops.add(op + ":" + ok);
        }

        @Override
        public void onDegraded(String op, String scope, String reason) {
        }
    }
}
