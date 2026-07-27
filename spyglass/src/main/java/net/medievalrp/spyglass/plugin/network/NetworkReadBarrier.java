package net.medievalrp.spyglass.plugin.network;

@FunctionalInterface
public interface NetworkReadBarrier {

    void synchronize();

    static NetworkReadBarrier none() {
        return () -> {
        };
    }
}
