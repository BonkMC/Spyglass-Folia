package net.medievalrp.spyglass.plugin.command;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TeleportWorldToken {

    private static final String PREFIX = "sgw:";

    private TeleportWorldToken() {
    }

    public static String encode(String worldName) {
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(worldName.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode(String token) {
        if (!token.startsWith(PREFIX)) {
            return token;
        }
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(token.substring(PREFIX.length()));
            return new String(encoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            return token;
        }
    }
}
