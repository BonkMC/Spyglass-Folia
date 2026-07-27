package net.medievalrp.spyglass.plugin.command.param;

import java.util.List;
import net.medievalrp.spyglass.api.query.QueryPredicate;
import net.medievalrp.spyglass.api.util.BlockLocation;
import org.bukkit.World;

final class WorldPredicates {

    private WorldPredicates() {
    }

    static QueryPredicate matches(BlockLocation location) {
        if (location.worldName() == null || location.worldName().isBlank()) {
            return new QueryPredicate.Eq("location.worldId", location.worldId());
        }
        return new QueryPredicate.Or(List.of(
                new QueryPredicate.Eq("location.worldId", location.worldId()),
                new QueryPredicate.Eq("location.worldName", location.worldName())));
    }

    static QueryPredicate matches(World world) {
        return new QueryPredicate.Or(List.of(
                new QueryPredicate.Eq("location.worldId", world.getUID()),
                new QueryPredicate.Eq("location.worldName", world.getName())));
    }
}
