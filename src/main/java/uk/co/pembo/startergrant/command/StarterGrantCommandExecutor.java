package uk.co.pembo.startergrant.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.logging.Logger;

public class StarterGrantCommandExecutor {

    private final List<String> commandTemplates;
    private final Logger logger;

    public StarterGrantCommandExecutor(List<String> commandTemplates, Logger logger) {
        this.commandTemplates = commandTemplates;
        this.logger = logger;
    }

    public void execute(Player player) {
        logger.info("Executing starter grant commands for player: " + player.getName());
        CommandSender console = Bukkit.getConsoleSender();
        for (String template : commandTemplates) {
            String command = template.replace("%player%", player.getName());
            //logger.info("Executing command: " + command + " for player: " + player.getName());
            try {
                boolean success = Bukkit.dispatchCommand(console, command);
                if (!success) {
                    logger.warning("Starter grant command returned false (unknown command?): " + command);
                }
            } catch (Exception e) {
                logger.severe("Starter grant command threw an exception: " + command + " — " + e.getMessage());
            }
        }
    }
}