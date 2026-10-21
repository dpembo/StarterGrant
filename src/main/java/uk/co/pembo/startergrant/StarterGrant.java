package uk.co.pembo.startergrant;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.pembo.startergrant.listener.StarterGrantJoinListener;
import uk.co.pembo.startergrant.command.StarterGrantCommandExecutor;
import uk.co.pembo.startergrant.storage.*;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class StarterGrant extends JavaPlugin {

    private StarterGrantStorage storage;
    private StarterGrantJoinListener joinListener;
    private final AtomicBoolean migrating = new AtomicBoolean(false);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupStarterGrant();

        var logo = "\r\n";
        logo += """
  $$$$    StarterGrant
 $$       (c)2026 Pembo.co.uk
  $$$     Globeworks.uk Plugin
    $$
 $$$$""";
        getLogger().info(logo);
    }

    @Override
    public void onDisable() {
        teardown();
    }

    private void teardown() {
        if (joinListener != null) {
            HandlerList.unregisterAll(joinListener);
            joinListener = null;
        }
        if (storage != null) {
            storage.shutdown();
            storage = null;
        }
    }

    private void setupStarterGrant() {
        teardown();

        boolean enabled = getConfig().getBoolean("enabled", true);
        if (!enabled) {
            getLogger().info("StarterGrant is disabled in config.");
            return;
        }

        ConfigurationSection grantConfig = getConfig();

        storage = StarterGrantStorageFactory.create(
                grantConfig, getDataFolder().toPath(), getLogger(), null);
        storage.init();

        StarterGrantCommandExecutor commandExecutor =
                new StarterGrantCommandExecutor(grantConfig.getStringList("commands"), getLogger());

        int delaySeconds = Math.max(0, grantConfig.getInt("command-delay-seconds", 3));
        joinListener = new StarterGrantJoinListener(this, storage, commandExecutor, delaySeconds);
        getServer().getPluginManager().registerEvents(joinListener, this);

        getLogger().info("Starter grant enabled (storage: "
                + grantConfig.getString("storage.type", "file")
                + ", command delay: " + delaySeconds + "s).");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("startergrant")) {
            return false;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (migrating.get()) {
                sender.sendMessage("Cannot reload while a migration is running.");
                return true;
            }
            reloadConfig();
            setupStarterGrant();
            sender.sendMessage("StarterGrant config reloaded.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("migrate")) {
            migrate(sender);
            return true;
        }
        sender.sendMessage("Usage: /startergrant <reload|migrate>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("startergrant") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("reload", "migrate").stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }

    private void migrate(CommandSender sender) {
        if (storage == null) {
            sender.sendMessage("StarterGrant is disabled; nothing to migrate.");
            return;
        }
        if (!migrating.compareAndSet(false, true)) {
            sender.sendMessage("A migration is already running.");
            return;
        }

        boolean fromDatabase = "mariadb".equals(
                getConfig().getString("storage.type", "file").toLowerCase(Locale.ROOT));
        StarterGrantStorage source = storage;
        String filePath = getDataFolder().toPath()
                .resolve(getConfig().getString("storage.file.path", "starter_grants.txt"))
                .toString();
        String table = getConfig().getString("storage.mariadb.table", "starter_grants");

        sender.sendMessage(fromDatabase
                ? "Copying starter grant records from MariaDB to " + filePath + "..."
                : "Copying starter grant records from file to table " + table + "...");

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            StarterGrantStorage target = null;
            try {
                target = fromDatabase
                        ? StarterGrantStorageFactory.createFile(getConfig(), getDataFolder().toPath(), getLogger())
                        : StarterGrantStorageFactory.createMariaDb(getConfig(), getLogger(), null);
                if (!fromDatabase) {
                    target.init();
                }
                Set<UUID> records = source.exportGranted();
                int inserted = Math.min(records.size(), target.importGranted(records));
                int alreadyPresent = records.size() - inserted;
                String summary = fromDatabase
                        ? "Migrated " + records.size() + " starter grant records from MariaDB to " + filePath + "."
                        : "Migrated " + records.size() + " starter grant records from file to table " + table
                        + " (" + inserted + " inserted, " + alreadyPresent + " already stored).";
                String nextStep = "Set storage.type to " + (fromDatabase ? "file" : "mariadb")
                        + " and run /startergrant reload to switch.";
                getLogger().info(summary);
                sendLater(sender, summary, nextStep);
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Migration failed", e);
                sendLater(sender, "Migration failed: " + e.getMessage());
            } finally {
                if (!fromDatabase && target != null) {
                    target.shutdown();
                }
                migrating.set(false);
            }
        });
    }

    private void sendLater(CommandSender sender, String... messages) {
        if (!isEnabled()) {
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            for (String message : messages) {
                sender.sendMessage(message);
            }
        });
    }
}