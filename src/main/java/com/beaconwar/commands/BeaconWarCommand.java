package com.beaconwar.commands;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.game.GameManager;
import com.beaconwar.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for /beaconwar
 * So much cleaner than function files!
 */
public class BeaconWarCommand implements CommandExecutor, TabCompleter {
    
    private final BeaconWarPlugin plugin;
    
    public BeaconWarCommand(BeaconWarPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        GameManager gameManager = plugin.getGameManager();
        
        switch (args[0].toLowerCase()) {
            case "setup" -> {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
                    return true;
                }
                gameManager.setupBeacons(player);
            }
            
            case "start" -> {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
                    return true;
                }
                gameManager.startGame();
            }
            
            case "stop" -> {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
                    return true;
                }
                gameManager.stopGame();
            }
            
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("Usage: /bw join <red|blue>", NamedTextColor.YELLOW)));
                    return true;
                }
                
                TeamColor team = switch (args[1].toLowerCase()) {
                    case "red" -> TeamColor.RED;
                    case "blue" -> TeamColor.BLUE;
                    default -> null;
                };
                
                if (team == null || team == TeamColor.NEUTRAL) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("Invalid team! Use 'red' or 'blue'", NamedTextColor.YELLOW)));
                    return true;
                }
                
                gameManager.addPlayerToTeam(player, team);
            }
            
            case "status" -> gameManager.showStatus(player);
            
            case "help" -> showHelp(player);
            
            default -> {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Unknown command! Use /bw help", NamedTextColor.YELLOW)));
            }
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Beacon War Commands ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/bw setup", NamedTextColor.YELLOW)
                .append(Component.text(" - Set up beacons at your location", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bw start", NamedTextColor.YELLOW)
                .append(Component.text(" - Start the game", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bw stop", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop the game", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bw join <red|blue>", NamedTextColor.YELLOW)
                .append(Component.text(" - Join a team", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bw status", NamedTextColor.YELLOW)
                .append(Component.text(" - Show game status", NamedTextColor.GRAY)));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setup", "start", "stop", "join", "status", "help");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return Arrays.asList("red", "blue");
        }
        
        return new ArrayList<>();
    }
}

