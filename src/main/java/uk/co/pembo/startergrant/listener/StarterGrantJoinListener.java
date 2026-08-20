package uk.co.pembo.startergrant.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import uk.co.pembo.startergrant.command.StarterGrantCommandExecutor;
import uk.co.pembo.startergrant.storage.StarterGrantStorage;

import java.util.UUID;

/**
 * Grants the one-time starter kit (whatever is configured under
 * commands, e.g. a KeepInvTokens token + starting balance)
 * the first time a player joins, checked against StarterGrantStorage rather
 * than hasPlayedBefore() so the flag is consistent across backends when
 * using the mariadb storage type.
 *
 * Storage check/mark runs immediately (async). Command dispatch is delayed
 * by {@code command-delay-seconds} so the client has time to finish
 * connecting before the grant commands fire.
 */
public class StarterGrantJoinListener implements Listener {

    private final Plugin plugin;
    private final StarterGrantStorage storage;
    private final StarterGrantCommandExecutor commandExecutor;
    /** Delay in ticks (20 ticks = 1 second). */
    private final long delayTicks;

    public StarterGrantJoinListener(Plugin plugin, StarterGrantStorage storage,
                                     StarterGrantCommandExecutor commandExecutor,
                                     int delaySeconds) {
        this.plugin = plugin;
        this.storage = storage;
        this.commandExecutor = commandExecutor;
        this.delayTicks = Math.max(0, delaySeconds) * 20L;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BukkitScheduler scheduler = plugin.getServer().getScheduler();

        scheduler.runTaskAsynchronously(plugin, () -> {
            if (storage.hasReceivedStarterGrant(uuid)) {
                return;
            }
            // Set the flag before scheduling commands so a fast double-login
            // or backend hop can't race past the check twice.
            storage.markStarterGrantGiven(uuid);

            // Schedule on the main thread after the configured delay so the
            // connection (and any Velocity / pack / other-plugin join work)
            // has settled. Zero delay still runs next tick via runTaskLater.
            scheduler.runTaskLater(plugin, () -> {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    commandExecutor.execute(player);
                }
            }, delayTicks);
        });
    }
}