package net.medievalrp.spyglass.plugin.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.bukkit.Bukkit;

public final class ServerThreading {

    private static final boolean FOLIA = detectFolia();
    private static final Method TICK_THREAD_CHECK = tickThreadCheck();

    private ServerThreading() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean isTickThread() {
        if (Bukkit.isPrimaryThread() || Bukkit.isGlobalTickThread()) {
            return true;
        }
        if (TICK_THREAD_CHECK != null) {
            try {
                return Boolean.TRUE.equals(TICK_THREAD_CHECK.invoke(null));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (!FOLIA) {
            return false;
        }
        String threadName = Thread.currentThread().getName();
        return threadName.contains("Region") && threadName.contains("Thread");
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return "Folia".equalsIgnoreCase(Bukkit.getName());
        }
    }

    private static Method tickThreadCheck() {
        for (String className : tickThreadClassNames()) {
            try {
                Class<?> tickThread = Class.forName(className);
                Method method = tickThread.getDeclaredMethod("isTickThread");
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static String[] tickThreadClassNames() {
        return new String[] {
                "io.papermc.paper.util.TickThread",
                "ca.spottedleaf.moonrise.common.util.TickThread"
        };
    }
}
