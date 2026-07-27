package net.medievalrp.spyglass.plugin.util;

import java.util.Optional;
import java.util.UUID;
import net.medievalrp.spyglass.api.util.BlockLocation;
import org.bukkit.Bukkit;
import org.bukkit.World;

public record WorldReference(UUID worldId, String worldName) {

    public WorldReference {
        worldName = worldName == null ? "" : worldName;
    }

    public static WorldReference from(BlockLocation location) {
        return new WorldReference(location.worldId(), location.worldName());
    }

    public Optional<World> resolve() {
        World world = Bukkit.getWorld(worldId);
        if (world != null) {
            return Optional.of(world);
        }
        if (worldName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(Bukkit.getWorld(worldName));
    }
}
