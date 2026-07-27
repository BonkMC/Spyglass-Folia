package net.medievalrp.spyglass.plugin.network;

import java.util.List;
import java.util.UUID;
import net.medievalrp.spyglass.api.event.EventRecord;
import net.medievalrp.spyglass.api.query.QueryRequest;
import net.medievalrp.spyglass.api.query.QueryResult;
import net.medievalrp.spyglass.plugin.storage.QueryPage;
import net.medievalrp.spyglass.plugin.storage.RecordStore;
import org.jetbrains.annotations.Nullable;

public final class NetworkSynchronizedRecordStore implements RecordStore {

    private final RecordStore recordStore;
    private final NetworkReadBarrier readBarrier;

    public NetworkSynchronizedRecordStore(
            RecordStore recordStore, NetworkReadBarrier readBarrier) {
        this.recordStore = recordStore;
        this.readBarrier = readBarrier;
    }

    @Override
    public void save(List<EventRecord> records) {
        recordStore.save(records);
    }

    @Override
    public void flushPendingWrites() {
        recordStore.flushPendingWrites();
    }

    @Override
    public QueryResult query(QueryRequest request) {
        readBarrier.synchronize();
        return recordStore.query(request);
    }

    @Override
    public QueryResult querySummary(QueryRequest request) {
        readBarrier.synchronize();
        return recordStore.querySummary(request);
    }

    @Override
    public QueryPage queryPage(
            QueryRequest request, QueryPage.Cursor cursor, int pageSize) {
        readBarrier.synchronize();
        return recordStore.queryPage(request, cursor, pageSize);
    }

    @Override
    public QueryPage.Cursor streamRollback(
            QueryRequest request, QueryPage.Cursor cursor, int windowLimit, RecordSink sink) {
        readBarrier.synchronize();
        return recordStore.streamRollback(request, cursor, windowLimit, sink);
    }

    @Override
    public QueryPage.Cursor streamRollbackEffects(
            QueryRequest request,
            QueryPage.Cursor cursor,
            int windowLimit,
            boolean rollback,
            RollbackEffectSink sink) {
        readBarrier.synchronize();
        return recordStore.streamRollbackEffects(
                request, cursor, windowLimit, rollback, sink);
    }

    @Override
    public @Nullable UUID resolvePlayerId(String playerName) {
        return recordStore.resolvePlayerId(playerName);
    }

    @Override
    public void close() {
        recordStore.close();
    }
}
