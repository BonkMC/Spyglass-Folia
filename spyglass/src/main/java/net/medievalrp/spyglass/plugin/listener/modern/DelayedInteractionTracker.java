package net.medievalrp.spyglass.plugin.listener.modern;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import net.medievalrp.spyglass.plugin.command.service.ServiceSupport;

public final class DelayedInteractionTracker {

    private final ServiceSupport scheduler;

    public DelayedInteractionTracker(ServiceSupport scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedule a callback to fire `delayTicks` later on the main thread. The
     * callback receives the block state as it stands at that time and the
     * player, and is responsible for deciding whether anything changed.
     */
    public void scheduleAfter(int delayTicks, Location location,
                              Consumer<DelayedContext> callback) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        scheduler.onRegionLater(
                world, location.getBlockX() >> 4, location.getBlockZ() >> 4,
                delayTicks, () -> {
            Block current = location.getBlock();
            Material nowMaterial = current.getType();
            callback.accept(new DelayedContext(location, nowMaterial));
        });
    }

    public record DelayedContext(Location location, Material currentMaterial) {
    }
}
