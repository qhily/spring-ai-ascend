---
level: L1
view: scenarios
module: agent-service
affects_level: L0, L1
affects_view: logical, process, scenarios
status: proposed
---

# Architecture Review Proposal: agent-service L1 Domain Expansion (Polymorphic Deployment, Reactive Dispatch, A2A Protocol)

> **Date:** 2026-05-21
> **Author:** LucioIT (Core Architect) & 急急 (Agent)
> **Target Wave:** W0/W1 (Immediate Enforcement)
> **Related Rules:** Rule R-G (Reactive External I/O), Rule #6 (OSS-first)

## 1. Executive Summary

This proposal outlines the immediate L1 architectural expansion for the `agent-service` module. It transitions the architecture from a monolithic, tightly-coupled state machine to a **Polymorphic, Reactive, and Federation-Ready** agent runtime. We introduce "Polymorphic Backpressure" to handle variable deployment topologies, and we formally adopt the **A2A (Agent-to-Agent) Protocol** (via `a2a-java` SDK) to replace internal proprietary task states and swarm communication mechanisms.

## 2. Polymorphic Backpressure & Reactive Dispatch

To support both **Platform-Centric** (Cloud) and **Business-Centric** (Edge) deployment modes without code duplication, `agent-service` MUST implement a unified Reactive Dispatch model:

### 2.1 The Push-Pull Continuum
- **Local Push (Business-Centric):** Direct API calls are written into a non-blocking in-memory queue (e.g., Reactor `Sinks`). The local Orchestrator acts as a subscriber, applying backpressure (`request(N)`) based on local CPU/Memory and `SkillCapacityRegistry` limits. This prevents local overloads.
- **Distributed Pull (Platform-Centric):** When deployed behind the `agent-bus` in a distributed setup, the same Reactive Subscriber transparently converts its backpressure demands into explicit "Pull" signals sent to the global bus, transforming local backpressure into global system-level traffic shaping.

## 3. A2A Protocol Integration & Swarm Execution

Instead of reinventing federation protocols (retiring ADR-0016 deferral), `agent-service` MUST embed the official `a2a-java` SDK to handle all multi-agent collaborations.

### 3.1 Embedded A2A Subsystems
- **A2A-Server:** Embedded within the `agent-service` HTTP edge to expose the local agent's capabilities to the broader network, natively supporting Synchronous, Streaming (SSE), and Callback invocation modes.
- **A2A-Client:** Replaces bare HTTP clients for all `SwarmDelegation` actions. When an Orchestrator spawns a child task on a remote agent, it dispatches the request via the A2A-Client.

## 4. Centralized Task State Management (Replacing Legacy SuspendSignal)

The current internal `RunStatus` DFA and the bloated `SuspendSignal` exception are insufficient for distributed, asynchronous agentic execution. We will pivot to an A2A-aligned state model.

### 4.1 A2A State Alignment
- **Deprecating Proprietary Enums:** We will align our internal execution states with the A2A `TaskState` and `TaskStatus` models. This allows rich intermediate progress reporting without mapping layers.
- **Event-Driven Suspension:** The monolithic `SuspendSignal` exception MUST be refactored into a lightweight `Yield` primitive. The actual context of the suspension (e.g., Waiting for Remote Delegate, Waiting for Human) will be modeled as A2A-standard Intent Events.
- **Streaming Progress:** The platform will native proxy A2A `TaskStatusUpdateEvent` and `TaskArtifactUpdateEvent` via SSE/Webhooks, fulfilling the W2 Streaming API requirement organically.

## 5. Next Steps

1. Merge this L1 proposal to lock the design direction for `agent-service`.
2. Core Architecture Team to initiate PRs replacing the legacy `SuspendSignal` with the A2A event model.
3. Update `agent-service/ARCHITECTURE.md` strictly following the Layered 4+1 template dictated by Rule G-1.c.

## 6. Stateless Context Injection & Lifecycle Definitions

To guarantee idempotency and support service-level deployments, the Agent Execution Engine MUST be entirely stateless. The architecture distinguishes four distinct lifecycle scopes (Run ≤ Task ≤ Session ≤ Memory) and enforces Context Injection:

### 6.1 Lifecycle Definitions
- **Run (Runtime Snapshot):** The most ephemeral scope. It represents a single stateless execution iteration (a node transition in a graph or a single loop in a ReAct cycle). It handles the immediate compute pointer and yields a `StateDelta`.
- **Task (Control State - Short Term):** Governs the deterministic execution of a specific intent (from trigger to completion). It maintains the "Control State" (A2A status, pointers, suspension reasons). A single Task may spawn multiple ephemeral Runs across its lifetime (e.g., yielding and resuming).
- **Session (Data Context - Mid Term):** Spans multiple Tasks and maintains the semantic alignment of user interaction. It holds the "Context Data" (conversation history, temporal variables). Session state is decoupled from Task control.
- **Memory (Knowledge State - Long Term):** The longest-lived scope (Agent deployment lifecycle). It represents cross-session knowledge, personalized rules, and embeddings, enabling evolutionary capabilities.

### 6.2 Context Injection Protocol (SPI)
The Execution Engine is prohibited from initiating direct I/O to fetch its own historical state. Instead, the `agent-service` host MUST employ a Context Injection Protocol:
- **Stateless Invocation:** `Execute(TaskMetadata, InjectedContext) -> StateDelta`
- **Context Projection:** For long-running sessions, the `SessionManager` dynamically projects (filters/summarizes) the relevant subset of Session Data into the `InjectedContext` before dispatching the Task.
- **Delta Persistence:** The Engine computes the next step and returns a `StateDelta` (and/or `Yield` primitive). The host service commits this delta to the state store before resuming or completing.
