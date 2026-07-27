package net.medievalrp.spyglass.plugin.salvage;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

public final class SalvageWithdrawalDispatcher {

    private final SalvageStore store;
    private final SalvageWithdrawals withdrawals;
    private final Executor storeExecutor;
    private final BiConsumer<Player, Runnable> playerExecutor;
    private final Logger logger;

    public SalvageWithdrawalDispatcher(
            SalvageStore store,
            SalvageWithdrawals withdrawals,
            Executor storeExecutor,
            BiConsumer<Player, Runnable> playerExecutor,
            Logger logger) {
        this.store = store;
        this.withdrawals = withdrawals;
        this.storeExecutor = storeExecutor;
        this.playerExecutor = playerExecutor;
        this.logger = logger;
    }

    InFlightTracker inFlight() {
        return withdrawals.inFlight();
    }

    void withdraw(
            Player player,
            SalvageSnapshot staleSnapshot,
            int index,
            Consumer<SalvageWithdrawals.Outcome> completion) {
        if (index < 0 || index >= staleSnapshot.items().size()) {
            completion.accept(new SalvageWithdrawals.Outcome(
                    SalvageWithdrawals.Status.SKIPPED, staleSnapshot));
            return;
        }
        int slot = staleSnapshot.items().get(index).slot();
        readFresh(staleSnapshot, player, fresh -> completion.accept(
                fresh.map(snapshot -> withdrawals.withdrawFresh(player, snapshot, slot))
                        .orElseGet(() -> new SalvageWithdrawals.Outcome(
                                SalvageWithdrawals.Status.EMPTIED, null))));
    }

    public void withdrawAll(
            Player player,
            SalvageSnapshot staleSnapshot,
            Consumer<SalvageWithdrawals.BulkResult> completion) {
        readFresh(staleSnapshot, player, fresh -> completion.accept(
                fresh.map(snapshot -> withdrawals.withdrawAllFresh(player, snapshot))
                        .orElseGet(() -> new SalvageWithdrawals.BulkResult(
                                0, 0, true, false))));
    }

    private void readFresh(
            SalvageSnapshot staleSnapshot,
            Player player,
            Consumer<Optional<SalvageSnapshot>> completion) {
        storeExecutor.execute(() -> {
            Optional<SalvageSnapshot> fresh;
            try {
                fresh = store.get(staleSnapshot.id());
            } catch (RuntimeException failure) {
                logger.warning("Spyglass salvage read failed: " + failure.getMessage());
                fresh = Optional.empty();
            }
            Optional<SalvageSnapshot> resolved = fresh;
            playerExecutor.accept(player, () -> {
                if (player.isOnline()) {
                    completion.accept(resolved);
                }
            });
        });
    }
}
