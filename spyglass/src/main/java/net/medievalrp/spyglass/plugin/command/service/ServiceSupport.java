package net.medievalrp.spyglass.plugin.command.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

/**
 * Main-thread scheduler indirection. Services that complete async work need
 * to bounce back to the Bukkit main thread before touching Bukkit state;
 * tests swap in {@link #synchronous()} to skip the scheduler entirely.
 *
 * <p>Sender feedback helpers (error/info/warn) moved to
 * {@link net.medievalrp.spyglass.plugin.command.render.Feedback}.
 */
@ApiStatus.Internal
public interface ServiceSupport {

    void onMainThread(Runnable runnable);

    /**
     * Defer a runnable to the main thread {@code delayTicks} ticks from
     * now (1 tick = 50ms). Used by the chunked rollback engine to yield
     * between per-tick batches: each batch finishes its work, then schedules
     * the next batch via this method so the main thread gets to process
     * other tasks (player movement, redstone, plugin events) before the
     * next chunk of block writes.
     */
    void onMainThreadLater(long delayTicks, Runnable runnable);

    /**
     * Hand work off to an async pool. Used by services that finished
     * their main-thread side (e.g. block-placement during rollback) and
     * still owe a slow I/O step (writing the undo stack to ClickHouse)
     * that must NOT block the tick. Fire-and-forget; the runnable is
     * responsible for its own error logging.
     */
    void onAsyncThread(Runnable runnable);

    default void onRegion(World world, int chunkX, int chunkZ, Runnable runnable) {
        onMainThread(runnable);
    }

    default void onRegionLater(
            World world, int chunkX, int chunkZ, long delayTicks, Runnable runnable) {
        onMainThreadLater(delayTicks, runnable);
    }

    default void onEntity(Entity entity, Runnable runnable) {
        onMainThread(runnable);
    }

    default void onEntityLater(Entity entity, long delayTicks, Runnable runnable) {
        onMainThreadLater(delayTicks, runnable);
    }

    default void onPlayer(Player player, Runnable runnable) {
        onEntity(player, runnable);
    }

    default void onPlayerLater(Player player, long delayTicks, Runnable runnable) {
        onEntityLater(player, delayTicks, runnable);
    }

    default boolean isRegionized() {
        return false;
    }

    default void onSender(CommandSender sender, Runnable runnable) {
        if (sender instanceof Player player) {
            onPlayer(player, runnable);
            return;
        }
        onMainThread(runnable);
    }

    static ServiceSupport bukkit(JavaPlugin plugin) {
        boolean isRegionized = isFolia();
        return new ServiceSupport() {
            @Override
            public void onMainThread(Runnable runnable) {
                if (!isRegionized && Bukkit.isPrimaryThread()) {
                    runnable.run();
                    return;
                }
                Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
            }

            @Override
            public void onMainThreadLater(long delayTicks, Runnable runnable) {
                Bukkit.getGlobalRegionScheduler().runDelayed(
                        plugin, task -> runnable.run(), Math.max(1L, delayTicks));
            }

            @Override
            public void onAsyncThread(Runnable runnable) {
                Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
            }

            @Override
            public void onRegion(
                    World world, int chunkX, int chunkZ, Runnable runnable) {
                if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                    runnable.run();
                    return;
                }
                Bukkit.getRegionScheduler().execute(plugin, world, chunkX, chunkZ, runnable);
            }

            @Override
            public void onRegionLater(
                    World world, int chunkX, int chunkZ, long delayTicks, Runnable runnable) {
                Bukkit.getRegionScheduler().runDelayed(
                        plugin, world, chunkX, chunkZ, task -> runnable.run(),
                        Math.max(1L, delayTicks));
            }

            @Override
            public void onEntity(Entity entity, Runnable runnable) {
                if (Bukkit.isOwnedByCurrentRegion(entity)) {
                    runnable.run();
                    return;
                }
                entity.getScheduler().run(plugin, task -> runnable.run(), null);
            }

            @Override
            public void onEntityLater(Entity entity, long delayTicks, Runnable runnable) {
                entity.getScheduler().runDelayed(
                        plugin, task -> runnable.run(), null, Math.max(1L, delayTicks));
            }

            @Override
            public boolean isRegionized() {
                return isRegionized;
            }
        };
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    static ServiceSupport synchronous() {
        return new ServiceSupport() {
            @Override
            public void onMainThread(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onMainThreadLater(long delayTicks, Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onAsyncThread(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onRegion(
                    World world, int chunkX, int chunkZ, Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onRegionLater(
                    World world, int chunkX, int chunkZ, long delayTicks, Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onEntity(Entity entity, Runnable runnable) {
                runnable.run();
            }

            @Override
            public void onEntityLater(Entity entity, long delayTicks, Runnable runnable) {
                runnable.run();
            }

            @Override
            public boolean isRegionized() {
                return false;
            }
        };
    }
}
