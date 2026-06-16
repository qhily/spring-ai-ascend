package com.huawei.ascend.memopt.hook;

import com.huawei.ascend.memopt.experience.CollaborationSignature;
import com.huawei.ascend.memopt.shared.SharedMemoryKit;

/**
 * The run-end integration seam (ADR-0162): the collaboration coordinator calls
 * this when a collaboration finishes, so MemOpt can distill the run's blackboard
 * into cross-run experience. The dependency points <b>collaboration &rarr;
 * MemOpt</b> (MemOpt never imports the collaboration module), which is what keeps
 * MemOpt an independent engine that builds on its own.
 */
public interface CollaborationMemoryHook {

    /**
     * Called once a collaboration ends. Implementations distill the blackboard
     * into experience (PII-stripped) under {@code signature}, and may release the
     * blackboard.
     *
     * @param signature  capability-set + task-type of the finished collaboration
     * @param blackboard the run's shared blackboard (read its keys to distill)
     */
    void onCollaborationEnd(CollaborationSignature signature, SharedMemoryKit blackboard);

    /** No-op hook (memory integration disabled). */
    CollaborationMemoryHook NOOP = (signature, blackboard) -> {
    };
}
