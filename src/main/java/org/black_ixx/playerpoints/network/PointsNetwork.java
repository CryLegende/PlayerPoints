package org.black_ixx.playerpoints.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.config.SettingKey;
import org.black_ixx.playerpoints.manager.DataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Network mode, when SHNetwork links several servers sharing the same MySQL database.
 * <ul>
 *   <li>a player leaving a server: his pending transactions are written before the next server reads him
 *       (SHNetwork save task);</li>
 *   <li>a player joining a server: his points are read once the server he comes from wrote them
 *       ({@code awaitSaved}, never waited for during the login), so a balance spent there can't be spent again here;
 *       until then his balance is unknown: it reads as 0 and can't be spent;</li>
 *   <li>after each write, the other servers read that player again (SHNetwork bus, which reaches every server,
 *       instead of the BungeeCord message that needs a player online on both sides).</li>
 * </ul>
 * Points changes stay atomic deltas in the database ({@code points = GREATEST(0, points + ?)}), as without this class.
 * Dormant without SHNetwork, with {@code reseau.actif: false}, or with the SQLite storage: the plugin then behaves
 * exactly as without this class.
 */
public final class PointsNetwork {

    private final PlayerPoints plugin;
    private final NetworkLink link;
    private final long waitMillis;

    private PointsNetwork(PlayerPoints plugin, NetworkLink link, long waitMillis) {
        this.plugin = plugin;
        this.link = link;
        this.waitMillis = waitMillis;
    }

    /**
     * @return the running network mode, or null when it stays off
     */
    public static PointsNetwork open(PlayerPoints plugin) {
        if (!SettingKey.NETWORK_ENABLED.get() || !Bukkit.getPluginManager().isPluginEnabled("SHNetwork"))
            return null;

        DataManager dataManager = plugin.getManager(DataManager.class);
        if (dataManager == null || !dataManager.isSharedDatabase()) {
            plugin.getLogger().warning("SHNetwork is installed but PlayerPoints uses SQLite: network mode stays off (enable mysql-settings)");
            return null;
        }

        NetworkLink link;
        try {
            link = ShNetworkLink.open();
        } catch (Throwable e) {
            plugin.getLogger().warning("SHNetwork is installed but couldn't be reached, network mode stays off: " + e);
            return null;
        }

        PointsNetwork network = new PointsNetwork(plugin, link, Math.max(1, SettingKey.NETWORK_WAIT.get()) * 1000L);
        link.registerSaveTask(network::saveTask);
        link.subscribe(network::onMessage);
        plugin.getLogger().info("Network mode on (server " + link.serverName() + "): points read once the server a player comes from wrote them");
        return network;
    }

    /**
     * AsyncPlayerPreLogin: never waits here. The server the player comes from only writes his points once the proxy
     * closed its connection, which happens after this login is over: waiting on this thread would hold the login until
     * the maximum wait. {@code then} runs once that server wrote them (or the wait ran out), on a SHNetwork thread that
     * must not be blocked: it only hands the read over to another thread.
     */
    public void whenSaved(UUID uuid, String name, Runnable then) {
        this.link.awaitSaved(uuid, this.waitMillis).whenComplete((saved, error) -> {
            if (error != null)
                this.plugin.getLogger().warning("Departure of " + name + " not confirmed (" + error + "): points read anyway");
            else if (!Boolean.TRUE.equals(saved))
                this.plugin.getLogger().warning("Departure of " + name + " not confirmed by the server he comes from: points read anyway");
            then.run();
        });
    }

    /**
     * SHNetwork save task: writes the pending transactions of the leaving player.
     */
    private CompletableFuture<Void> saveTask(Player player) {
        DataManager dataManager = this.plugin.getManager(DataManager.class);
        return dataManager == null ? CompletableFuture.completedFuture(null) : dataManager.flush(player.getUniqueId());
    }

    /**
     * A player's points were written: the other servers read them again.
     */
    public void changed(UUID uuid) {
        JsonObject message = new JsonObject();
        message.addProperty("uuid", uuid.toString());
        this.link.publish(message.toString());
    }

    /**
     * Every account was offset (giveall): the other servers read all the points they hold again.
     */
    public void changedAll() {
        JsonObject message = new JsonObject();
        message.addProperty("all", true);
        this.link.publish(message.toString());
    }

    /**
     * Bus thread: only schedules the reads.
     */
    private void onMessage(String json) {
        DataManager dataManager = this.plugin.getManager(DataManager.class);
        if (dataManager == null)
            return;

        JsonObject message = JsonParser.parseString(json).getAsJsonObject();
        if (message.has("all")) {
            dataManager.refreshAllCached();
        } else if (message.has("uuid")) {
            dataManager.refreshIfCached(UUID.fromString(message.get("uuid").getAsString()));
        }
    }

}
