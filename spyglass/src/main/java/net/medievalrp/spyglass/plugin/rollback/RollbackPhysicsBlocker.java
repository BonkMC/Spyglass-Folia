package net.medievalrp.spyglass.plugin.rollback;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.medievalrp.spyglass.plugin.util.WorldReference;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

// Cancels falling-block transitions inside an active rollback region.
//
// When the rollback restores sand/gravel/concrete-powder via
// setBlockData, the onPlace path still schedules a gravity tick. On
// the next world tick the block becomes a FallingBlock entity and
// drops. Cancelling the resulting EntityChangeBlockEvent keeps it
// in place.
//
// Callers register a bounding box via enter() and release with the
// returned handle in exit(). Multiple concurrent rollbacks register
// independent regions.
public final class RollbackPhysicsBlocker implements Listener {

    private final ConcurrentHashMap<Long, RollbackRegion> activeRegions = new ConcurrentHashMap<>();
    private final AtomicLong nextHandle = new AtomicLong();

    public long enter(UUID worldId,
                      int minX, int minY, int minZ,
                      int maxX, int maxY, int maxZ) {
        return enter(new WorldReference(worldId, ""), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public long enter(WorldReference world,
                      int minX, int minY, int minZ,
                      int maxX, int maxY, int maxZ) {
        long handle = nextHandle.getAndIncrement();
        activeRegions.put(handle, new RollbackRegion(
                world, minX, minY, minZ, maxX, maxY, maxZ));
        return handle;
    }

    public void exit(long handle) {
        activeRegions.remove(handle);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        if (activeRegions.isEmpty()) {
            return;
        }
        UUID worldId = event.getBlock().getWorld().getUID();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        for (RollbackRegion region : activeRegions.values()) {
            if (region.contains(worldId, event.getBlock().getWorld().getName(), x, y, z)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private record RollbackRegion(WorldReference world,
                                  int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ) {
        boolean contains(UUID worldId, String worldName, int x, int y, int z) {
            return (world.worldId().equals(worldId) || world.worldName().equals(worldName))
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }
}
