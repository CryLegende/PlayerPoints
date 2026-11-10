package org.black_ixx.playerpoints.network;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.Player;

/**
 * What the network mode needs from SHNetwork. This interface mentions no SHNetwork class: the plugin handles it
 * without ever loading {@link ShNetworkLink}, so nothing happens without SHNetwork.
 */
public interface NetworkLink {

    /** Name of this server on the network. */
    String serverName();

    /** Completes (off the main thread) once the server the player comes from saved him. */
    CompletableFuture<Boolean> awaitSaved(UUID uuid, long timeoutMillis);

    /** Task run when a player leaves this server (main thread, during PlayerQuitEvent). */
    void registerSaveTask(Function<Player, CompletableFuture<Void>> task);

    /** Sends a message to the other servers. */
    void publish(String json);

    /** Receives the messages of the other servers, on the bus thread (never block it). */
    void subscribe(Consumer<String> handler);

}
