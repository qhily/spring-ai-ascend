package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.singleagent.BaseAgent;

/** OpenJiuwen-local hook for installing runtime-owned tools into one agent instance. */
public interface OpenJiuwenRuntimeToolInstaller {
    void install(BaseAgent agent, AgentExecutionContext context);
}
