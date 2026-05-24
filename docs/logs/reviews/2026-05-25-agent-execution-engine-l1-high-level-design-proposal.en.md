---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-execution-engine
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: designing
---

# Architecture Review Proposal: agent-execution-engine L1 High-Level Design (Wave 1.2)

> **Date:** 2026-05-25
> **Author:** LucioIT (Core Architect) & Flash (Agent)
> **Target Wave:** W0/W1 (Immediate Execution)
> **Associated Guidelines:** Rule G-1.c (L1 Depth & Grounding)

## 1. Background & Principles

### 1.1 Top-Level Design Background (L0 Architecture)

#### 1.1.1 Six Core Modules
1. **Agent Client (agent-client)**: Integrated within SaaS and desktop applications; responsible for sensing business knowledge and states, operating business environments and tools, distributing and managing Agent configurations, and invoking execution services.
2. **Agent Service (agent-service)**: Responsible for encapsulating Workflow Agents (Graph-mode execution) and ReAct Agents (Loop-mode execution) as microservices.
3. **Agent Execution Engine (agent-execution-engine)**: **(Core Boundary of This Module)** Responsible for providing the executors for both types of Agents and supplying various components for developer use, such as Workflow nodes, and ReAct tools and hooks.
4. **Agent Bus (agent-bus)**: Connects North-South C/S traffic and East-West A2A (Agent-to-Agent) communication traffic.
5. **Agent Middleware (agent-middleware)**: Provides foundational services required by Agents, such as memory services, skill services, knowledge services, and sandbox execution environments.
6. **Agent Evolution Platform (agent-evolve)**: Responsible for online and offline autonomous self-evolution of Agents.

#### 1.1.2 Two Core Deployment/Integration Modes
- **Platform-Centric Mode**: The business side integrates only `agent-client`, while all other modules are deployed on the platform side (centralized hosting and execution to reduce integration complexity).
- **Business-Centric Mode**: The business side integrates `agent-client` and also deploys `agent-service` and `agent-execution-engine` locally (within the business physical boundaries) for localized computing; the platform side only provides unified governance, interconnectivity, and basic public services.

### 1.2 Project Phase Background & Evolution Roadmap

#### 1.2.1 Positioning: Toolset Engine Based on Open-Source Foundations (Open-Source Foundation & Core Engine)
According to the **L0 top-level architecture positioning**, the `agent-execution-engine` does not participate in the development of domain-specific vertical business Agents, but acts as the "general physical compute chip" for Agent execution.
- **Iterative Growth on Open-Source Baseline**: This module is not built from scratch. Instead, it is **based on the mature open-source project `openJiuwen/agent-core-java` as a starting point and physical baseline for deep refactoring, augmentation, and incremental iteration**. We will reuse its existing core execution capabilities while focusing our enhancements on distributed, stateless, reactive, and A2A protocol-level requirements of our platform.
- **Tools, Not Built-in Agents**: The engine only provides foundational executors, generic components, and developer tools. The business logic itself is assembled and customized by downstream developers (platform users) who leverage this engine.

#### 1.2.2 Developer Experience (DX) Roadmap (Three-Stage Evolution)
To lower development friction and unlock developer productivity, we establish a clear roadmap for DX evolution:
1. **Phase 1 (Current Wave 1 Focus): Configuration-Driven Development**
   - Decouple Agent business logic entirely from compiled code. Whether it is a Workflow or a ReAct Agent, its core running topology, tool bindings, and lifecycle hooks are fully defined using **declarative standard schemas (JSON/YAML)**, allowing on-the-fly parsing, dynamic loading, and direct production deployments.
2. **Phase 2: Graphical Development (Graphical Flow Canvas)**
   - Abstract and deliver a low-code visual drag-and-drop canvas (Flow Chart), enabling developers to visually connect Nodes, Tools, and Hooks, and export standard configuration packages with a single click.
3. **Phase 3 (Ultimate Killer Feature): Natural Language-Driven Development**
   - **Conversational Building**: Developer users can discuss and refine business SOPs interactively with a "Meta Agent."
   - **Interactive Assembly**: Similar to the openClaw experience, users can use natural language commands to "install this tool / inject that hook," which the Meta Agent translates in real-time into engine-level configuration directives.
   - **Auto-Generation & Debugging**: The system automatically synthesizes, choreographs, and debugs the agent in a sandboxed runtime in the background, exporting production-ready configuration packages instantly.

### 1.3 Design Principles & Core Forms

#### 1.3.1 Core Forms: Dual-Engine Architecture
1. **Workflow Engine (Workflow / Graph-Mode Executor)**:
   - **Topology Execution**: Supports directed acyclic graphs (DAG) as well as complex graphs containing conditional branching, back-loops, and multi-node jumps.
   - **Modular Nodes (Standard Nodes)**: Standardize common computational steps into atomic Node components (such as LLM reasoning nodes, Prompt rendering nodes, conditional routers, data mappers, etc.), allowing developers to orchestrate them easily.
2. **ReAct Engine (ReAct / Loop-Mode Executor)**:
   - **Iterative Loops (Reasoning-Action Loop)**: Drives the "Thought -> Action -> Observation" autonomous reasoning cycle.
   - **Component-Level Support (Tools & Hooks)**: Provides standard **Tools (physical invocations)** and **Hooks (lifecycle interceptors)**. Tools shield the LLM from physical environment complexities; Hooks support pre-, mid-, and post-loop injection of security audits, policy gates, and telemetry tracing.

#### 1.3.2 Dual Operations Mode: Privileged Dev vs. Read-Only Prod (Dual-Mode & Registry Lock)
The engine has diametrically opposed environmental demands during development and production. These must be isolated using a strict, unidirectional pipeline to ensure that developer flexibility never compromises production safety:
1. **Development Mode (DEV Mode) — Dynamic, Generative & Highly Privileged**:
   - Focuses on rapid prototyping and interactive building.
   - The engine unlocks full privileges, enabling the **In-Memory Compiler**, **component metadata introspection**, and the **Mock Registry**. It records verbose execution trace-trees, supports step-by-step breakpoints, pausing, state injection, and live hot-swapping.
2. **Standardized Agent Package (.apg)**:
   - The sole physical output of DEV mode. It encapsulates the declarative YAML topology config alongside the **immutable compiled Java bytecode files (.class) synthesized during the development phase** into a standardized, cryptographically signed ZIP archive.
3. **Production Mode (PROD Mode) — Read-Only, Frozen & High Performance**:
   - Focuses on stability, isolation, and deterministic latency. It **exclusively loads** immutable `.apg` packages produced in the DEV phase.
   - **Strict Registry Lock**: Once PROD engine finishes boot-loading the package, it executes **`lockRegistry()`** on its core. The In-Memory Compiler and introspection endpoints are physically cut off/disabled, configuration changes are blocked, and live code injection is strictly barred, ensuring zero-day safety in production.

#### 1.3.3 Generative Component Self-Creation
- **Beyond Basic Block Stitching**: If natural language development is limited to connecting pre-baked blocks, it fails to deliver a true paradigm shift compared to simple visual low-code canvases.
- **Full-Stack Code Creation**: Under DEV mode, the engine supports the Meta Agent's ability to write Java source files (implementing `ToolSPI` or `NodeExecutor`) on-the-fly to handle previously undefined physical system integrations.
- **Instant In-Memory Compilation**: The engine features an built-in **In-Memory Compiler**, compiling generated source codes to bytecode array in-memory instantly, loading them into the active JVM ClassPath for immediate sandboxed debugging, and finally serializing them into the immutable `.apg` package.

#### 1.3.4 Completely Stateless & Configuration-Code Decoupling (Stateless & Generative Tenets)
1. **Completely Stateless (Stateless Compute Kernel)**:
   - The engine core acts as a pure "computation chip," bypassing direct database writes or A2A network routing.
   - Every execution is a pure state mapping function. Node, Tool, and Hook components are **strictly prohibited from blocking physical execution threads or directly communicating with external middleware**.
   - All external blocking events (tool runs, user approvals, A2A coordination) are signaled by throwing a strong-typed **`InterruptSignal`**, freeing physical threads instantly for massive concurrency.
2. **Configuration-Code Decoupling**:
   - Agent logic is represented entirely as "configuration" rather than "pre-compiled static classes." Workflows and ReAct decision-trees are declarative, enabling runtime serialization, parsing, and translation.
3. **Natural Language-Dev Friendly**:
   - The registry and topology must expose unified introspection interfaces, enabling the Meta Agent to query capabilities and dynamically assemble configurations during conversation.

## 2. Scenarios View

### 2.1 High-Performance Embedded Execution Scenario (Co-Process Mode)
- **Typical Path**: `agent-service` receives an execution request -> Assembles the `InjectedContext` -> Directly invokes the co-located `agent-execution-engine` via JVM memory -> Execution core processes the state transit and returns `StateDelta` with sub-millisecond latencies, bypassing inter-process network overhead.

### 2.2 Declarative Compilation & Hot-Deployment Scenario (DEV Mode)
- **Typical Path**: Developer edits or updates the Agent YAML config -> Service detects changes on disk or receives an API request -> Engine's `ConfigCompiler` intercepts, performs schema validation -> Fetches Node/Tool/Hook classes from `ComponentRegistry` -> Compiles a fresh in-memory Graph -> Atomic-swaps (Hot-swap) the old Graph reference with zero downtime.

### 2.3 Step-Debugging & State Mocking Scenario (DEV Mode)
- **Typical Path**: Agent runs in DEV mode -> `DebuggerMonitor` tracks execution trace-tree -> Halts at a specified Node/Tool breakpoint -> Developer inspects variables, modifies Prompt on-the-fly, or registers a mock response in `MockRegistry` to skip real financial APIs -> Calls `resume` with injected state -> Core continues execution.

### 2.4 Generative Component Self-Creation Scenario (DEV Mode)
- **Typical Path**: User describes a custom integration in plain English -> Meta Agent translates intent into a Java class implementing `ToolSPI` -> Invokes `InMemoryCompiler` to compile class in-memory -> Dynamically registers the new tool in `ComponentRegistry` -> Sandbox immediately runs and tests the tool without JVM restarts.

### 2.5 Dev-to-Prod Unidirectional Compilation & Delivery Scenario (Pipeline)
- **Typical Path**: DEV testing completes -> Trigger build command on `AgentPackager` -> Aggregates YAML configuration and newly-synthesized `.class` files into a signed, immutable `.apg` ZIP archive -> PROD container boots and loads `.apg` -> Executes `lockRegistry()`, shuts down compilation/introspection, and starts execution with maximum performance and strict sandboxing.

## 3. Logical View

### 3.1 Unified SPI Gateway
- The sole physical interface exposed by the engine. Implements the `StatelessEngineExecutor` contract, receiving `TaskSpec` and `InjectedContext`, dispatching workloads to the corresponding cores, and wrapping results as `StateDelta`.

### 3.2 Config Compiler & Assembler
- Compiles declarative JSON/YAML schemas into executable runtime graph instances.
- **Config Compiler**: Conducts structural syntax, semantics, and safety validations on raw schemas.
- **Dynamic Assembler**: Queries the Component Registry, instantiates designated Node, Tool, and Hook classes, maps dependencies, and manages runtime hot-swapping under DEV mode.

### 3.3 Dual-Engine Execution Cores
- Deeply refactored stateless runtimes derived from the mature `openJiuwen/agent-core-java` codebase.
- **Workflow Core (DAG Core)**: Manages topological node sorting, conditional routing, branching, and cyclic iterations in Workflow Agents.
- **ReAct Core**: Drives the "Thought -> Action -> Observation" autonomous reasoning loops and handles bi-directional parsing of LLM outputs and Tool Call structures.

### 3.4 Generative Component Registry & In-Memory Compiler
- The unified registry of Agent building blocks.
- **Component Registry (with Read-Only Lock)**: Maintains registered class maps of Nodes, Tools, and Hooks. Exposes `lockRegistry()` which freezes registrations permanently (enforced in PROD).
- **In-Memory Compiler (DEV Mode Exclusive)**: Leverages `javax.tools.JavaCompiler` to compile dynamic Java code inputs into `byte[]` streams on-the-fly and loads them into JVM runtime.
- **Introspection Service (DEV Mode Exclusive)**: Exposes APIs for the Meta Agent to query descriptions and JSON-schemas of all registered tools and nodes in the registry.

### 3.5 Debugger Monitor & Mock Registry (DEV Mode Exclusive)
- **Debugger Monitor**: Records verbose execution traces for each Task and manages breakpoint registration, execution pausing, and stack-frame reporting.
- **Mock Registry**: Allows developers/Meta Agents to bind mock return values to specific high-cost or external APIs for local, deterministic debugging.

### 3.6 Heterogeneous Config Transpiler
- Non-runtime utility tool; translates configurations from third-party frameworks (such as LangChain, LlamaIndex, Spring AI) into the engine's standardized declarative schema files.

## 4. Process View

### 4.1 Declarative Compilation & Hot-Loading Sequence (DEV Mode)
1. **Config Modification**: Service detects Agent config edits, triggers `ConfigCompiler`.
2. **Schema Validation**: Compiler validates syntax and schema integrity.
3. **Graph Assembly**: `ConfigAssembler` parses topology, matches classes from `ComponentRegistry`, and instantiates components.
4. **Reference Hot-swap**: Engine atomically switches active Graph reference of the Agent ID, routing subsequent requests to the new Graph instance.

### 4.2 Step-Debugging & Variable Injection Sequence (DEV Mode)
1. **Trace Activation**: Task boots in DEV mode; Debugger Monitor begins recording trace and hooks onto designated breakpoints.
2. **Breakpoint Encountered**: Execution hits a breakpoint in a Node or Tool.
3. **Halting & Yielding**: Debugger Monitor pauses calculation, serializes current stack state into `StateDelta`, sets `breakpoint_halt` flag to `true`, and returns to Service. Thread is freed.
4. **Mock Injection**: User/Meta Agent registers mock values in `MockRegistry` for the target component.
5. **Resume**: Service calls execute with `resume: true`. Debugger Monitor injects the mock value and instructs the core to continue from the suspended frame.

### 4.3 Engine Interruption & Dehydration Sequence (Stateless Yield)
1. **Boundary Reach**: Core reaches a boundary (e.g., ReAct decides to invoke a tool, or Workflow reaches manual approval).
2. **Signal Throwing**: The component immediately throws a strong-typed `InterruptSignal(TOOL_EXECUTION, GoogleSearch)` to the Core, strictly avoiding blocking calls.
3. **Core Freezing**: Core halts, packages current execution variables, session variables, and the interrupt signal into `StateDelta`.
4. **Thread Release**: Gateway returns `StateDelta` to `agent-service`. Service dehydrates state to persistent stores, and the compute thread is immediately returned to the JVM thread-pool.

### 4.4 Generative Component Self-Creation Sequence (DEV Mode)
1. **Integration Need**: User requests a non-existent API tool -> Meta Agent writes `ToolSPI.java` source code.
2. **In-Memory Compiling**: Meta Agent posts code to `InMemoryCompiler`.
3. **Class Loading**: Compiler compiles code to bytecode array and loads it into the current JVM.
4. **Live Registration**: Dynamic class loader registers the Class into `ComponentRegistry`.
5. **Sandboxed Testing**: Config Compiler hot-swaps the test Agent's config to bind the new tool for immediate execution.

### 4.5 End-to-End Generative Dev-to-Prod Deployment Sequence (Lifecycle Pipeline)
```text
[Development Mode (DEV Mode)]               [Artifact (.apg)]            [Production Mode (PROD Mode)]
 MetaAgent      InMemoryCompiler                 Packager                   ProdEngine      ComponentRegistry
   │               │                                │                           │                  │
   ├── 1. Code Synthesis ──────────────────────────>│                           │                  │
   │   (ToolSPI.java source)                        │                           │                  │
   │               │                                │                           │                  │
   ├── 2. Compile in Memory ───────────────────────>│                           │                  │
   │   (JavaCompiler API -> .class)                 │                           │                  │
   │               │                                │                           │                  │
   ├── 3. Sandboxed Debug (Success) ────────────────>│                           │                  │
   │               │                                │                           │                  │
   ├── 4. Package Artifact ────────────────────────>│                           │                  │
   │                                                ├── 5. Sign & Compress ─────>│                  │
   │                                                │   (YAML + .class)         │                  │
   │                                                │                           ├── 6. Cold Boot ─>│
   │                                                │                           │   (Decrypt APG)  │
   │                                                │                           │                  ├── 7. Freeze Core
   │                                                │                           │                  │  (lockRegistry)
   │                                                │                           │                  │  Read-Only,
   │                                                │                           │                  │  Prune Compiler
```

## 5. Development View

### 5.1 Open-Source Reuse & Custom Boundaries (Based on openJiuwen Core)
This module does not invent the wheel; it directly inherits and overlays the **`openJiuwen/agent-core-java`** codebase. We establish a clear boundaries model:
- **Reused Open-Source Modules**: Directly reuse its topological DAG sorting algorithms and standard ReAct reasoning-loop controllers.
- **Stateless & Generative Augmentations (100% Custom-Written / Refactored)**:
  - **Stateless Refactoring of Executors**: Heavily rewrite `WorkflowEngine` and `ReActEngine` main loop stack. Instead of synchronous thread blocks or sleeps during execution, intercept and translate blocked states into `InterruptSignal` exceptions, yielding threads instantly.
  - **Dual-Mode Controller**: Implement PROD mode hard-locks, registry read-only restrictions, and dynamic compiler physical cutting.
  - **Generative Compiler (InMemoryCompiler)**: Develop the `javax.tools` compile-to-memory compiler.
  - **Interactive Debugger Suite**: Develop Tracing, Step-break, and Mock registries.
  - **APG Packager**: Develop the immutable `.apg` packaging and bootloader tool.

### 5.2 Source Package Structure & Dependencies
```text
agent-execution-engine/src/main/java/com/huawei/ascend/agent/engine/
├── spi/                        # Unified SPI Contract Boundaries
│   └── StatelessEngineExecutor.java # Sole stateless gateway interface between Service and Engine
├── compiler/                   # Declarative Compiler & Packager
│   ├── ConfigCompiler.java     # Config parsing and structural validation
│   ├── ConfigAssembler.java    # Nodes, tools, hooks assembly and live hot-swapping
│   ├── AgentPackager.java      # APG package signing, packing, and bootloader utilities
│   └── schema/                 # Declarative JSON/YAML Schema validator files
├── core/                       # Dual-Engine Stateless Runtimes (Overlay on openJiuwen)
│   ├── workflow/               # Refactored stateless Workflow DAG router
│   └── react/                  # Refactored stateless ReAct loop controller
├── debug/                      # DEV Debugger & Mocking Suite
│   ├── DebuggerMonitor.java    # Tracing, Breakpoint managing, and state reporting
│   └── MockRegistry.java       # Mock value registry and local injection
├── registry/                   # Generative Registry & Introspection (The Lego Box)
│   ├── ComponentRegistry.java  # Class Registry with read-only lock (Registry Lock)
│   ├── InMemoryCompiler.java   # DEV mode in-memory compiler
│   ├── IntrospectionService.java # Exposes component specs & schemas to Meta Agent
│   └── loader/                 # Dynamic hot-plug class loaders
└── transpiler/                 # Third-Party Migration Tools
    ├── LangChainTranspiler.java # LangChain schema parser
    └── LlamaIndexTranspiler.java # LlamaIndex schema parser
```

## 6. Physical View

### 6.1 Embedded Co-Process Deployment
- `agent-execution-engine.jar` is shipped as a lightweight library embedded directly within `agent-service.jar` JVM process.
- **In-Memory Binding**: Config compilers, registry collections, and executors share the same JVM heap. Hot-swapping, compilation loading, and execution flows incur zero serialization or network latencies, keeping latency down to sub-milliseconds.

### 6.2 APG Bundle Sandboxed Deployment
- **DEV Compiling**: Dynamically generated classes are loaded into isolated transient ClassLoaders.
- **PROD Immutable Lockdown**: On production startup, the bootloader decrypts `.apg`, feeds class bytes to isolated PROD ClassLoaders, instantiates components, discards references to `InMemoryCompiler`, and executes `lockRegistry()`. The class map becomes strictly read-only, establishing a bulletproof safety boundary against zero-day exploits.

## 7. Appendix: Core SPI Interfaces

### 7.1 StatelessEngineExecutor Core Contract
```java
package com.huawei.ascend.agent.engine.spi;

import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

/**
 * Core stateless execution interface (Engine SPI Contract)
 */
public interface StatelessEngineExecutor {
    /**
     * Stateless execution entry point: feeds task specification and injected context, returns state delta
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

public class TaskSpec {
    private String taskId;
    private String agentId;                   // Reference to Agent config ID
    private String taskType;                  // WORKFLOW or REACT
    private RunMode runMode;                  // PROD or DEV mode (controls debugger and registry locks)
    private Map<String, Object> parameters;   // Execution parameters
    // Getters and Setters...
}

public enum RunMode {
    PROD,   // Production: High-performance, read-only registry, debugger & compiler disabled
    DEV     // Development: Privileged state, tracing, mocking, and in-memory compilation enabled
}

public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // Semantic-projected historical messages
    private List<Map<String, Object>> tools;   // Available tool schemas
    private Map<String, Object> sessionVars;   // Snapshot of runtime session variables
    // Getters and Setters...
}

public class StateDelta {
    private List<Message> newMessages;         // New message outputs
    private Map<String, Object> updatedVars;   // Modifications to session variables
    private InterruptSignal interruptSignal;   // Populated if core yielded, null if completed
    private boolean isBreakpointHalt;          // (DEV Only) Flag indicating breakpoint halting
    // Getters and Setters...
}

public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // Natural language payload
    private long timestamp;
    // Getters and Setters...
}
```

### 7.2 InterruptSignal Specifications
```java
package com.huawei.ascend.agent.engine.spi;

import java.util.Map;

/**
 * Strong-typed interrupt categories
 */
public enum InterruptType {
    INPUT_REQUIRED,   // Requires human-in-the-loop input or approval
    TOOL_EXECUTION,   // Triggered when a physical tool run is requested
    SUB_TASK_AWAIT    // Triggered when a child-agent collaboration is dispatched
}

/**
 * Unified interrupt signal thrown by components to yield threads instantly
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload(); // Parameters for resolving the interrupt (e.g., tool arguments)
}
```

### 7.3 Debugger & Mocking SPI (DEV Mode Exclusive)
```java
package com.huawei.ascend.agent.engine.debug;

import java.util.Map;

/**
 * Tracing monitor for DEV mode breakpoints
 */
public interface DebuggerMonitor {
    /**
     * Registers a breakpoint at specified component boundary
     */
    void setBreakpoint(String agentId, String componentId);
    
    /**
     * Clears a registered breakpoint
     */
    void clearBreakpoint(String agentId, String componentId);

    /**
     * Returns detailed execution trace-tree of a task
     */
    Object getExecutionTrace(String taskId);
}

/**
 * DEV mode mocking registry for deterministic testing
 */
public interface MockRegistry {
    /**
     * Registers a mock response for a target tool
     */
    void registerMockValue(String agentId, String toolName, Map<String, Object> mockResponse);
    
    /**
     * Retrives the registered mock value
     */
    Map<String, Object> getMockValue(String agentId, String toolName);
    
    /**
     * Clears mock configurations of an agent
     */
    void clearMockRules(String agentId);
}
```

### 7.4 ComponentRegistry & InMemoryCompiler SPI
```java
package com.huawei.ascend.agent.engine.registry;

import java.util.List;

/**
 * Lockable Component Registry
 */
public interface ComponentRegistry {
    /**
     * Registers a customized component dynamically
     * @throws IllegalStateException if registry is locked (PROD mode)
     */
    void registerNode(String nodeType, Class<?> nodeClass) throws IllegalStateException;
    void registerTool(String toolName, Class<?> toolClass) throws IllegalStateException;
    void registerHook(String hookName, Class<?> hookClass) throws IllegalStateException;

    /**
     * Permanently freezes registration maps to guarantee production immutability
     */
    void lockRegistry();

    /**
     * Returns whether the registry is locked
     */
    boolean isLocked();

    /**
     * Introspection: lists available component catalogs & schemas
     */
    List<ComponentMetadata> listAvailableComponents();
}

/**
 * In-memory compiler for on-the-fly component compilation (DEV Mode Exclusive)
 */
public interface InMemoryCompiler {
    /**
     * Compiles Java source inputs to Class objects and loads them into JVM ClassPath
     * @param className target full-qualified class name
     * @param javaSourceCode Java source string
     * @return the compiled Class object reference
     * @throws CompileException if compilation or safety scans fail
     */
    Class<?> compileAndLoad(String className, String javaSourceCode) throws CompileException;
}

public class ComponentMetadata {
    private String name;                       // Component ID (e.g., DatabaseQueryNode)
    private String category;                   // NODE, TOOL, or HOOK
    private String description;                // NL description for Meta Agent reasoning
    private String inputSchema;                // Input JSON Schema specifications
    // Getters and Setters...
}
```

### 7.5 ConfigCompiler & AgentPackager SPI
```java
package com.huawei.ascend.agent.engine.compiler;

import java.io.File;

/**
 * Configuration compiler and hot-swapping controller
 */
public interface ConfigCompiler {
    /**
     * Syntax and schema validation
     */
    boolean validateAndCompile(String configContent) throws IllegalArgumentException;

    /**
     * DEV mode hot-swapping
     */
    void hotSwapAgent(String agentId, String newConfigContent);
}

/**
 * Packaging tool to assemble configurations and compiled bytecode arrays into standard .apg packages
 */
public interface AgentPackager {
    /**
     * Assembles agent YAML and class files into standard encrypted, signed .apg files
     * @param agentId targeted Agent ID
     * @param destApgFile path to save the .apg archive
     */
    void packageAgent(String agentId, File destApgFile) throws Exception;

    /**
     * PROD cold-boot handler; verifies signatures, decrypts .apg packages, and boots instances
     */
    void bootstrapApg(File sourceApgFile) throws Exception;
}
```

## 8. Architectural Annotations & Pending Refinements
During the architectural review of this L1 design proposal, the Core Architect and the Agent jointly identified three major pending gaps. These areas are documented as high-level TODO annotations and will be deeply addressed and closed in the subsequent L2 Detailed Design and implementation phases:

### 8.1 Pending Refinement 1: Codebase Gap Analysis & Physical Patching Design (Development View Gap)
* **Context**:
  Given that `agent-execution-engine` will build upon the mature open-source repository `openJiuwen/agent-core-java`, our development view cannot be modeled in isolation from its existing class layouts.
* **Refinement Plan**:
  In the L2 phase, we must first audit the physical package layouts of `openJiuwen/agent-core-java`'s executors (`WorkflowEngine` / `ReActEngine`). We must supply an explicit "micro-refactoring roadmap" showing exactly which classes we alter and which existing synchronous blocking logic we intercept and convert into standard `InterruptSignal` thread yielding mechanisms.

### 8.2 Pending Refinement 2: Component Taxonomy & Fundamental Out-of-the-Box Definitions
* **Context**:
  As the foundation of configuration-driven development, we must establish a clear taxonomy of what components (lego blocks) are available in the engine's toolbox. Without this, declarative DSL configuration schemas remain undefined, and detailed developers cannot align.
* **Refinement Plan**:
  In the L2 phase, we must formally define and document three core component families along with their properties and schemas:
  1. *Workflow Nodes (NodeExecutor)*: standardizing `LLMNode`, `PromptTemplateNode`, `ConditionalRouterNode`, and `APIConnectorNode` (with immediate yield constraints).
  2. *ReAct Tools (ToolSPI)*: standardizing `HttpTool`, `ShellTool`, and `WebSearchTool`.
  3. *Lifecycle Hooks (LifecycleHook)*: standardizing `SecurityGuardHook` and `TokenRateLimiterHook`.

### 8.3 Pending Refinement 3: Configuration Schema (DSL) Specs & .apg Archive Layout Specifications
* **Context**:
  To enforce strict unidirectional pipelines from DEV to PROD, we must formalize the boundaries of declarative configurations and build artifacts.
* **Refinement Plan**:
  The L2 phase must deliver two concrete spec sheets:
  1. *Declarative Agent YAML Spec*: the unified JSON Schema of the Agent configuration file.
  2. *APG Archive File Layout*: the formal file structures inside `.apg` packages (such as `/manifest.json`, `/agent.yaml`, `/classes/`, `/assets/`), establishing the sole physical interface contract for `AgentPackager` and the PROD bootloader.
