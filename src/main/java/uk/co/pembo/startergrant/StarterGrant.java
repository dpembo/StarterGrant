package uk.co.pembo.startergrant;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.pembo.startergrant.listener.StarterGrantJoinListener;
import uk.co.pembo.startergrant.command.StarterGrantCommandExecutor;
import uk.co.pembo.startergrant.storage.*;

public final class StarterGrant extends JavaPlugin {

    private StarterGrantStorage storage;
    private StarterGrantJoinListener joinListener;

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
        if (command.getName().equalsIgnoreCase("startergrant")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                setupStarterGrant();
                sender.sendMessage("StarterGrant config reloaded.");
                return true;
            }
            sender.sendMessage("Usage: /startergrant reload");
            return true;
        }
        return false;
    }
}