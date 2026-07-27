package net.medievalrp.spyglass.plugin.network;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.medievalrp.spyglass.api.util.Duration;
import net.medievalrp.spyglass.plugin.pipeline.AsyncRecorder;
import net.medievalrp.spyglass.plugin.storage.MariaDbRecordStore;

public final class MySqlNetworkCoordinator implements NetworkReadBarrier, AutoCloseable {

    private static final long SYNC_DEBOUNCE_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final long MAINTENANCE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1L);

    private final String instanceId = UUID.randomUUID().toString();
    private final String serverName;
    private final int eventInstance;
    private final AsyncRecorder recorder;
    private final MySqlNetworkMessageStore messageStore;
    private final MySqlNetworkInstanceStore instanceStore;
    private final long pollIntervalMillis;
    private final long syncTimeoutMillis;
    private final long instanceTimeoutMillis;
    private final Logger logger;
    private final ScheduledExecutorService poller;
    private final AtomicBoolean isClosed = new AtomicBoolean();
    private volatile long latestRequestId;
    private volatile long nextHeartbeatAt;
    private volatile long nextMaintenanceAt;
    private volatile long lastSuccessfulSync;

    public MySqlNetworkCoordinator(
            MariaDbRecordStore recordStore,
            AsyncRecorder recorder,
            String serverName,
            long pollIntervalMillis,
            long syncTimeoutMillis,
            long instanceTimeoutMillis,
            Logger logger) {
        this.serverName = serverName;
        this.eventInstance = serverName.hashCode() & 0xff;
        this.recorder = recorder;
        this.messageStore = new MySqlNetworkMessageStore(recordStore);
        this.instanceStore = new MySqlNetworkInstanceStore(recordStore);
        this.pollIntervalMillis = Math.max(50L, pollIntervalMillis);
        this.syncTimeoutMillis = Math.max(500L, syncTimeoutMillis);
        this.instanceTimeoutMillis = Math.max(this.syncTimeoutMillis * 2L, instanceTimeoutMillis);
        this.logger = logger;
        this.poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Spyglass-Network-" + serverName);
            thread.setDaemon(true);
            return thread;
        });
        try {
            start();
        } catch (RuntimeException failure) {
            poller.shutdownNow();
            try {
                instanceStore.remove(instanceId);
            } catch (RuntimeException cleanupFailure) {
                logger.log(Level.FINE, "Spyglass network startup cleanup failed", cleanupFailure);
            }
            throw failure;
        }
    }

    private void start() {
        messageStore.initialize();
        instanceStore.initialize();
        long now = System.currentTimeMillis();
        instanceStore.register(
                instanceId, serverName, eventInstance, now - instanceTimeoutMillis, now);
        latestRequestId = messageStore.latestRequestId();
        nextHeartbeatAt = now + Math.max(500L, instanceTimeoutMillis / 3L);
        poller.scheduleWithFixedDelay(
                this::pollSafely, 0L, pollIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public int eventInstance() {
        return eventInstance;
    }

    @Override
    public synchronized void synchronize() {
        if (isClosed.get()) {
            throw new IllegalStateException("Spyglass network coordinator is closed");
        }
        long nowNanos = System.nanoTime();
        if (nowNanos - lastSuccessfulSync < SYNC_DEBOUNCE_NANOS) {
            return;
        }
        if (!flushLocal()) {
            throw new IllegalStateException("Local Spyglass event flush timed out");
        }
        long now = System.currentTimeMillis();
        heartbeat(now);
        Set<String> targets = instanceStore.activeInstances(
                instanceId, now - instanceTimeoutMillis);
        if (targets.isEmpty()) {
            lastSuccessfulSync = System.nanoTime();
            return;
        }
        UUID requestId = UUID.randomUUID();
        messageStore.publish(requestId, instanceId, now);
        awaitAcknowledgements(requestId, targets);
        lastSuccessfulSync = System.nanoTime();
    }

    private void awaitAcknowledgements(UUID requestId, Set<String> targets) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(syncTimeoutMillis);
        while (System.nanoTime() < deadline) {
            Map<String, Boolean> acknowledgements = messageStore.acknowledgements(requestId);
            if (acknowledgements.entrySet().stream()
                    .anyMatch(entry -> targets.contains(entry.getKey()) && !entry.getValue())) {
                throw new IllegalStateException("A remote Spyglass instance could not flush");
            }
            if (acknowledgements.keySet().containsAll(targets)) {
                return;
            }
            try {
                Thread.sleep(Math.min(50L, pollIntervalMillis));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for remote Spyglass instances", interrupted);
            }
        }
        throw new IllegalStateException(
                "Timed out waiting for " + targets.size() + " remote Spyglass instance(s)");
    }

    private void pollSafely() {
        if (isClosed.get()) {
            return;
        }
        try {
            poll();
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Spyglass network poll failed", failure);
        }
    }

    private void poll() {
        long now = System.currentTimeMillis();
        if (now >= nextHeartbeatAt) {
            heartbeat(now);
        }
        for (FlushRequest request : messageStore.requestsAfter(latestRequestId)) {
            latestRequestId = Math.max(latestRequestId, request.id());
            if (!instanceId.equals(request.senderId())) {
                messageStore.acknowledge(
                        request.requestId(), instanceId, flushLocal(), System.currentTimeMillis());
            }
        }
        if (now >= nextMaintenanceAt) {
            long olderThan = now - Math.max(instanceTimeoutMillis * 3L, 300_000L);
            messageStore.prune(olderThan);
            instanceStore.prune(olderThan);
            nextMaintenanceAt = now + MAINTENANCE_INTERVAL_MILLIS;
        }
    }

    private void heartbeat(long now) {
        instanceStore.heartbeat(instanceId, serverName, now);
        nextHeartbeatAt = now + Math.max(500L, instanceTimeoutMillis / 3L);
    }

    private boolean flushLocal() {
        long timeoutSeconds = Math.max(1L, (syncTimeoutMillis + 999L) / 1000L);
        return recorder.flush(new Duration(timeoutSeconds));
    }

    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        poller.shutdownNow();
        try {
            instanceStore.remove(instanceId);
        } catch (RuntimeException failure) {
            logger.log(Level.FINE, "Spyglass network instance cleanup failed", failure);
        }
    }
}
