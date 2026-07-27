package net.medievalrp.spyglass.plugin.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.EnumSet;
import java.util.List;
import net.medievalrp.spyglass.api.query.Flag;
import net.medievalrp.spyglass.api.query.QueryRequest;
import net.medievalrp.spyglass.api.query.Sort;
import net.medievalrp.spyglass.plugin.storage.RecordStore;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NetworkSynchronizedRecordStoreTest {

    private final RecordStore delegate = mock(RecordStore.class);
    private final NetworkReadBarrier barrier = mock(NetworkReadBarrier.class);
    private final NetworkSynchronizedRecordStore store =
            new NetworkSynchronizedRecordStore(delegate, barrier);
    private final QueryRequest request =
            new QueryRequest(List.of(), Sort.NEWEST_FIRST, 100, EnumSet.noneOf(Flag.class), false);

    @Test
    void synchronizesBeforeEveryReadPath() {
        store.query(request);
        store.querySummary(request);
        store.queryPage(request, null, 100);
        store.streamRollback(request, null, 100, record -> {
        });
        store.streamRollbackEffects(request, null, 100, true, sink());

        InOrder calls = inOrder(barrier, delegate);
        calls.verify(barrier).synchronize();
        calls.verify(delegate).query(request);
        calls.verify(barrier).synchronize();
        calls.verify(delegate).querySummary(request);
        calls.verify(barrier).synchronize();
        calls.verify(delegate).queryPage(request, null, 100);
        calls.verify(barrier).synchronize();
        calls.verify(delegate).streamRollback(
                org.mockito.ArgumentMatchers.eq(request), isNull(), anyInt(), any());
        calls.verify(barrier).synchronize();
        calls.verify(delegate).streamRollbackEffects(
                org.mockito.ArgumentMatchers.eq(request), isNull(), anyInt(), anyBoolean(), any());
    }

    @Test
    void writesDoNotWaitForAReadBarrier() {
        store.save(List.of());
        store.flushPendingWrites();
        store.resolvePlayerId("Alice");

        verify(barrier, times(0)).synchronize();
        verify(delegate).save(List.of());
        verify(delegate).flushPendingWrites();
        verify(delegate).resolvePlayerId("Alice");
    }

    private static RecordStore.RollbackEffectSink sink() {
        return new RecordStore.RollbackEffectSink() {
            @Override
            public void complex(
                    net.medievalrp.spyglass.api.rollback.RollbackEffect effect,
                    java.time.Instant occurred,
                    java.util.UUID id) {
            }

            @Override
            public void skip(java.time.Instant occurred, java.util.UUID id) {
            }
        };
    }
}
