package net.medievalrp.spyglass.plugin.salvage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SalvageWithdrawalDispatcherTest {

    @Test
    void readsOffThreadBeforeMutatingOnThePlayerScheduler() {
        SalvageStore store = mock(SalvageStore.class);
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        SalvageSnapshot snapshot = new SalvageSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "world",
                1,
                64,
                1,
                "CHEST",
                "Alice",
                Instant.EPOCH,
                List.of());
        when(store.get(snapshot.id())).thenReturn(Optional.empty());
        List<Runnable> storeTasks = new ArrayList<>();
        List<Runnable> playerTasks = new ArrayList<>();
        SalvageWithdrawals withdrawals =
                new SalvageWithdrawals(store, Runnable::run, null, Logger.getLogger("test"));
        SalvageWithdrawalDispatcher dispatcher = new SalvageWithdrawalDispatcher(
                store,
                withdrawals,
                storeTasks::add,
                (scheduledPlayer, runnable) -> playerTasks.add(runnable),
                Logger.getLogger("test"));
        AtomicReference<SalvageWithdrawals.BulkResult> completion = new AtomicReference<>();

        dispatcher.withdrawAll(player, snapshot, completion::set);

        assertThat(completion).hasValue(null);
        assertThat(storeTasks).hasSize(1);
        storeTasks.getFirst().run();
        assertThat(completion).hasValue(null);
        assertThat(playerTasks).hasSize(1);
        playerTasks.getFirst().run();
        assertThat(completion.get().emptied()).isTrue();
    }
}
