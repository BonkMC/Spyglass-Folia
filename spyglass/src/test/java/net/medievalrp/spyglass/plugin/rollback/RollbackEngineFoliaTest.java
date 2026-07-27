package net.medievalrp.spyglass.plugin.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import net.medievalrp.spyglass.api.event.BlockSnapshot;
import net.medievalrp.spyglass.api.rollback.RollbackEffect;
import net.medievalrp.spyglass.api.rollback.RollbackResult;
import net.medievalrp.spyglass.api.util.BlockLocation;
import net.medievalrp.spyglass.plugin.command.service.ServiceSupport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class RollbackEngineFoliaTest {

    @Test
    void writesThroughBukkitOnTheMatchingLocalWorld() {
        UUID remoteWorldId = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        World localWorld = mock(World.class);
        Block block = mock(Block.class);
        BlockData blockData = mock(BlockData.class);
        when(localWorld.getBlockAt(12, 64, -4)).thenReturn(block);
        BlockSnapshot replacement = new BlockSnapshot(
                Material.STONE,
                "minecraft:stone",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null);
        RollbackEffect effect = new RollbackEffect.BlockReplace(
                new BlockLocation(remoteWorldId, "world", 12, 64, -4),
                null,
                replacement);
        RollbackEngine engine = new RollbackEngine();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(remoteWorldId)).thenReturn(null);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(localWorld);
            bukkit.when(() -> Bukkit.createBlockData("minecraft:stone")).thenReturn(blockData);

            List<RollbackResult> applied = engine.applyAllChunked(
                    List.of(effect), mock(CommandSender.class), regionizedSupport(), 100).join();

            assertThat(applied).singleElement().isInstanceOf(RollbackResult.Applied.class);
            verify(block).setBlockData(blockData, false);
        }
    }

    private static ServiceSupport regionizedSupport() {
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
            public boolean isRegionized() {
                return true;
            }
        };
    }
}
