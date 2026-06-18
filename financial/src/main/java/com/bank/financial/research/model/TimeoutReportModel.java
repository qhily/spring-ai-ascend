package com.bank.financial.research.model;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds a delegate {@link ReportModel} with a hard per-call timeout. The live
 * LLM path can otherwise hang indefinitely — the framework's model invoke is
 * called with no timeout — so a single stuck generation would block a whole
 * report run past its budget. This decorator runs each generation on a daemon
 * worker and abandons it after {@code timeout}, throwing a
 * {@link ModelTimeoutException} the sub-agents catch and degrade on.
 */
public final class TimeoutReportModel implements ReportModel {

    private final ReportModel delegate;
    private final long timeoutMs;
    private final ExecutorService pool;

    public TimeoutReportModel(ReportModel delegate, Duration timeout) {
        this.delegate = delegate;
        this.timeoutMs = timeout.toMillis();
        ThreadFactory daemon = r -> {
            Thread t = new Thread(r, "report-model-call");
            t.setDaemon(true); // never block JVM shutdown on a stuck model call
            return t;
        };
        this.pool = Executors.newCachedThreadPool(daemon);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String generate(ModelTask task) {
        Future<String> future = pool.submit(() -> delegate.generate(task));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ModelTimeoutException(
                    "report model '" + delegate.name() + "' timed out after " + timeoutMs + "ms (role="
                            + task.role() + ")");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModelTimeoutException("report model call interrupted (role=" + task.role() + ")");
        } catch (java.util.concurrent.ExecutionException e) {
            // Surface the delegate's real failure for the caller to degrade on.
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException("report model '" + delegate.name() + "' failed: " + cause.getMessage(), cause);
        }
    }

    /** Thrown when a generation exceeds its hard time budget. */
    public static final class ModelTimeoutException extends RuntimeException {
        public ModelTimeoutException(String message) {
            super(message);
        }
    }
}
