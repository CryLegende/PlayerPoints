package org.black_ixx.playerpoints.network;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.entity.Player;

/**
 * Bridge to SHNetwork. This class is only loaded when SHNetwork is installed.
 */
public final class ShNetworkLink implements NetworkLink {

    private static final String CHANNEL = "playerpoints";

    private final SHNetworkAPI api;

    private ShNetworkLink() {
        this.api = SHNetworkAPI.get();
    }

    /**
     * Entry point: the plugin only resolves this class here, so never without SHNetwork.
     */
    public static NetworkLink open() {
        return new ShNetworkLink();
    }

    @Override
    public String serverName() {
        return this.api.serverName();
    }

    @Override
    public CompletableFuture<Boolean> awaitSaved(UUID uuid, long timeoutMillis) {
        return this.api.awaitSaved(uuid, Duration.ofMillis(timeoutMillis));
    }

    @Override
    public void registerSaveTask(Function<Player, CompletableFuture<Void>> task) {
        this.api.registerSaveTask("PlayerPoints", task);
    }

    @Override
    public void publish(String json) {
        this.api.publish(CHANNEL, json);
    }

    @Override
    public void subscribe(Consumer<String> handler) {
        this.api.subscribe(CHANNEL, message -> handler.accept(message.json()), false);
    }

}
