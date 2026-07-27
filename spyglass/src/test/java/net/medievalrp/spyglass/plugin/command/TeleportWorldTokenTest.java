package net.medievalrp.spyglass.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeleportWorldTokenTest {

    @Test
    void preservesWorldNamesThatAreUnsafeAsCommandArguments() {
        String worldName = "Survival End; event";

        String token = TeleportWorldToken.encode(worldName);

        assertThat(token).doesNotContain(" ", ";");
        assertThat(TeleportWorldToken.decode(token)).isEqualTo(worldName);
    }

    @Test
    void leavesManualWorldArgumentsUntouched() {
        assertThat(TeleportWorldToken.decode("world_nether")).isEqualTo("world_nether");
    }
}
