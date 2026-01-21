package com.beaconwar.commands;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.game.EloManager;
import com.beaconwar.game.GameManager;
import com.beaconwar.model.TeamColor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/**
 * Command handler for /beaconwar
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
        
        GameManager gameManager = plugin.getGameManager();
        String commandName = command.getName().toLowerCase();
        
        // Handle direct commands (not via /bw)
        if (!commandName.equals("beaconwar")) {
            // Shift args to include command name as first argument for handlers that expect it
            String[] shiftedArgs = new String[args.length + 1];
            shiftedArgs[0] = commandName;
            System.arraycopy(args, 0, shiftedArgs, 1, args.length);
            
            switch (commandName) {
                case "setup" -> handleSetup(player, gameManager);
                case "start" -> handleStart(player, gameManager, shiftedArgs);
                case "stop" -> handleStop(player, gameManager);
                case "reset" -> handleReset(player, gameManager);
                case "end" -> handleEnd(player, gameManager);
                case "pause" -> handlePause(player, gameManager);
                case "unpause" -> handleUnpause(player, gameManager);
                case "join" -> handleJoin(player, gameManager, shiftedArgs);
                case "resistance" -> handleResistance(player, gameManager, shiftedArgs);
                case "elo" -> handleElo(player, gameManager, shiftedArgs);
                case "quicklaunch" -> handleQuicklaunch(player, gameManager, shiftedArgs);
                case "balancedteams" -> handleBalancedTeams(player, gameManager);
                case "status" -> gameManager.showStatus(player);
            }
            return true;
        }
        
        // Handle /bw subcommands
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "setup" -> handleSetup(player, gameManager);
            case "start" -> handleStart(player, gameManager, args);
            case "stop" -> handleStop(player, gameManager);
            case "reset" -> handleReset(player, gameManager);
            case "end" -> handleEnd(player, gameManager);
            case "pause" -> handlePause(player, gameManager);
            case "unpause" -> handleUnpause(player, gameManager);
            case "join" -> handleJoin(player, gameManager, args);
            case "resistance" -> handleResistance(player, gameManager, args);
            case "elo" -> handleElo(player, gameManager, args);
            case "quicklaunch" -> handleQuicklaunch(player, gameManager, args);
            case "balancedteams" -> handleBalancedTeams(player, gameManager);
            case "status" -> gameManager.showStatus(player);
            case "help" -> showHelp(player);
            default -> player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Unknown command! Use /bw help", NamedTextColor.YELLOW)));
        }
        
        return true;
    }
    
    private void handleSetup(Player player, GameManager gameManager) {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
                }
                gameManager.setupBeacons(player);
            }
            
    private void handleStart(Player player, GameManager gameManager, String[] args) {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        int minutes = 0;
        if (args.length >= 2) {
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Invalid time! Usage: /bw start [minutes]", NamedTextColor.YELLOW)));
                return;
            }
        }
        
        gameManager.startGame(minutes);
    }
    
    private void handleStop(Player player, GameManager gameManager) {
                if (!player.hasPermission("beaconwar.admin")) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
                }
                gameManager.stopGame();
            }
    
    private void handleReset(Player player, GameManager gameManager) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        gameManager.resetGame();
    }
            
    private void handleEnd(Player player, GameManager gameManager) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        if (!gameManager.isGameActive()) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("No game is active!", NamedTextColor.YELLOW)));
            return;
        }
        
        TeamColor winner = gameManager.determineWinner();
        gameManager.endGame(winner);
    }
    
    private void handlePause(Player player, GameManager gameManager) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        if (!gameManager.isGameActive()) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("No game is active!", NamedTextColor.YELLOW)));
            return;
        }
        
        gameManager.pauseGame();
    }
    
    private void handleUnpause(Player player, GameManager gameManager) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        if (!gameManager.isGameActive()) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("No game is active!", NamedTextColor.YELLOW)));
            return;
        }
        
        gameManager.unpauseGame();
    }
    
    private void handleJoin(Player player, GameManager gameManager, String[] args) {
                if (args.length < 2) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("Usage: /bw join <red|blue>", NamedTextColor.YELLOW)));
            return;
                }
                
                TeamColor team = switch (args[1].toLowerCase()) {
                    case "red" -> TeamColor.RED;
                    case "blue" -> TeamColor.BLUE;
                    default -> null;
                };
                
                if (team == null || team == TeamColor.NEUTRAL) {
                    player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                            .append(Component.text("Invalid team! Use 'red' or 'blue'", NamedTextColor.YELLOW)));
            return;
                }
                
                gameManager.addPlayerToTeam(player, team);
            }
            
    private void handleResistance(Player player, GameManager gameManager, String[] args) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        if (args.length < 3) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Usage: /bw resistance <player> <0-4>", NamedTextColor.YELLOW)));
            return;
        }
        
        String targetPlayer = args[1];
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Invalid level! Use 0-4", NamedTextColor.YELLOW)));
            return;
        }
        
        if (level < 0 || level > 4) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Level must be 0-4!", NamedTextColor.YELLOW)));
            return;
        }
        
        gameManager.setAssignedResistance(targetPlayer, level);
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Set " + targetPlayer + "'s resistance to " + level, NamedTextColor.GREEN)));
    }
    
    private void handleElo(Player player, GameManager gameManager, String[] args) {
        EloManager eloManager = gameManager.getEloManager();
        
        if (args.length < 2) {
            // Show own rating
            double rating = eloManager.getRating(player.getName());
            player.sendMessage(Component.text("[Beacon War ELO] ", NamedTextColor.GOLD)
                    .append(Component.text("Your rating: " + (int)(rating * 1000), NamedTextColor.YELLOW)));
            return;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "leaderboard" -> {
                List<Map.Entry<String, Double>> leaderboard = eloManager.getLeaderboard();
                player.sendMessage(Component.text("=== ELO Leaderboard ===", NamedTextColor.GOLD));
                if (leaderboard.isEmpty()) {
                    player.sendMessage(Component.text("  No players yet.", NamedTextColor.GRAY));
                } else {
                    int rank = 1;
                    for (Map.Entry<String, Double> entry : leaderboard) {
                        player.sendMessage(Component.text("  " + rank + ". ", NamedTextColor.GRAY)
                                .append(Component.text(entry.getKey(), NamedTextColor.WHITE))
                                .append(Component.text(" - " + (int)(entry.getValue() * 1000), NamedTextColor.YELLOW)));
                        rank++;
                        if (rank > 10) break;  // Only show top 10
                    }
                }
            }
            
            case "predict" -> {
                // Show predicted win probability for current teams
                List<String> redPlayers = gameManager.getTeamPlayers(TeamColor.RED);
                List<String> bluePlayers = gameManager.getTeamPlayers(TeamColor.BLUE);
                
                if (redPlayers.isEmpty() || bluePlayers.isEmpty()) {
                    player.sendMessage(Component.text("[Beacon War ELO] ", NamedTextColor.RED)
                            .append(Component.text("Both teams need players!", NamedTextColor.YELLOW)));
                    return;
                }
                
                List<EloManager.PlayerResistance> redTeam = redPlayers.stream()
                        .map(p -> new EloManager.PlayerResistance(p, gameManager.getAssignedResistance(p)))
                        .collect(Collectors.toList());
                List<EloManager.PlayerResistance> blueTeam = bluePlayers.stream()
                        .map(p -> new EloManager.PlayerResistance(p, gameManager.getAssignedResistance(p)))
                        .collect(Collectors.toList());
                
                double pRed = eloManager.calculateWinProbability(redTeam, blueTeam);
                
                player.sendMessage(Component.text("[Beacon War ELO] ", NamedTextColor.GOLD)
                        .append(Component.text("Win probability:", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text("  Red: ", NamedTextColor.RED)
                        .append(Component.text(String.format("%.1f%%", pRed * 100), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Blue: ", NamedTextColor.BLUE)
                        .append(Component.text(String.format("%.1f%%", (1 - pRed) * 100), NamedTextColor.WHITE)));
            }
            
            default -> {
                // Assume it's a player name
                String targetPlayer = args[1];
                if (eloManager.hasPlayer(targetPlayer)) {
                    double rating = eloManager.getRating(targetPlayer);
                    player.sendMessage(Component.text("[Beacon War ELO] ", NamedTextColor.GOLD)
                            .append(Component.text(targetPlayer + "'s rating: " + (int)(rating * 1000), NamedTextColor.YELLOW)));
                } else {
                    player.sendMessage(Component.text("[Beacon War ELO] ", NamedTextColor.RED)
                            .append(Component.text("Player not found: " + targetPlayer, NamedTextColor.YELLOW)));
                }
            }
        }
    }
    
    private void handleQuicklaunch(Player player, GameManager gameManager, String[] args) {
        if (!player.hasPermission("beaconwar.admin")) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("You don't have permission!", NamedTextColor.YELLOW)));
            return;
        }
        
        // Parse arguments: /bw quicklaunch [manual] [minutes]
        boolean manual = false;
        int minutes = 0;
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("manual")) {
                manual = true;
            } else {
                try {
                    minutes = Integer.parseInt(args[i]);
                } catch (NumberFormatException ignored) {
                    // Not a number, ignore
                }
            }
        }
        
        // Step 1: Setup beacons
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Starting quicklaunch...", NamedTextColor.GREEN)));
        
        if (!gameManager.setupBeacons(player)) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Quicklaunch failed: could not setup beacons!", NamedTextColor.YELLOW)));
            return;
        }
        
        // Step 2: Balance teams (unless manual mode)
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!manual) {
            List<String> playerNames = onlinePlayers.stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            
            if (playerNames.size() < 2) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Need at least 2 players for quicklaunch!", NamedTextColor.YELLOW)));
                return;
            }
            
            // Balance teams using ELO
            EloManager.BalancedMatch match = gameManager.getEloManager().findBalancedMatch(playerNames);
            
            // Assign teams and resistances
            for (EloManager.PlayerResistance pr : match.redTeam) {
                gameManager.addPlayerToTeam(pr.playerName, TeamColor.RED);
                gameManager.setAssignedResistance(pr.playerName, pr.resistance);
            }
            for (EloManager.PlayerResistance pr : match.blueTeam) {
                gameManager.addPlayerToTeam(pr.playerName, TeamColor.BLUE);
                gameManager.setAssignedResistance(pr.playerName, pr.resistance);
            }
            
            // Broadcast team assignments
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Teams balanced!", NamedTextColor.GREEN)));
            Bukkit.broadcast(Component.text("  RED: ", NamedTextColor.RED)
                    .append(Component.text(formatTeam(match.redTeam), NamedTextColor.WHITE)));
            Bukkit.broadcast(Component.text("  BLUE: ", NamedTextColor.BLUE)
                    .append(Component.text(formatTeam(match.blueTeam), NamedTextColor.WHITE)));
            Bukkit.broadcast(Component.text("  Predicted win: ", NamedTextColor.GRAY)
                    .append(Component.text("Red " + String.format("%.1f%%", match.pRedWins * 100), NamedTextColor.RED))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text("Blue " + String.format("%.1f%%", (1 - match.pRedWins) * 100), NamedTextColor.BLUE)));
        }
        
        // Step 3: Show team titles to each player
        for (Player p : onlinePlayers) {
            TeamColor team = gameManager.getPlayerTeam(p);
            if (team != TeamColor.NEUTRAL) {
                List<String> teammates = gameManager.getTeamPlayers(team).stream()
                        .filter(name -> !name.equals(p.getName()))
                        .collect(Collectors.toList());
                
                String teammatesStr = teammates.isEmpty() ? "" : "with " + String.join(", ", teammates);
                NamedTextColor teamColor = (team == TeamColor.RED) ? NamedTextColor.RED : NamedTextColor.BLUE;
                
                p.showTitle(Title.title(
                        Component.text(team.name() + " TEAM", teamColor),
                        Component.text(teammatesStr, NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
            }
        }
        
        // Step 4: 10-second countdown, then start and kill all
        final int gameMinutes = minutes;
        startCountdown(gameManager, 10, gameMinutes);
    }
    
    private void startCountdown(GameManager gameManager, int seconds, int gameMinutes) {
        if (seconds <= 0) {
            // Start game and kill all players
            gameManager.startGame(gameMinutes);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.setHealth(0);  // Kill player to respawn at team spawn
            }
            return;
        }
        
        // Show countdown
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(Component.text("Game starts in " + seconds + "...", NamedTextColor.YELLOW));
        }
        
        // Schedule next tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            startCountdown(gameManager, seconds - 1, gameMinutes);
        }, 20L);  // 20 ticks = 1 second
    }
    
    private String formatTeam(List<EloManager.PlayerResistance> team) {
        return team.stream()
                .map(pr -> pr.resistance > 0 ? pr.playerName + ":" + pr.resistance : pr.playerName)
                .collect(Collectors.joining(", "));
    }
    
    private void handleBalancedTeams(Player player, GameManager gameManager) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<String> playerNames = onlinePlayers.stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        
        if (playerNames.size() < 2) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Need at least 2 players!", NamedTextColor.YELLOW)));
            return;
        }
        
        // Calculate balanced teams using ELO
        EloManager.BalancedMatch match = gameManager.getEloManager().findBalancedMatch(playerNames);
        
        // Display the results
        player.sendMessage(Component.text("=== Balanced Teams Preview ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  RED: ", NamedTextColor.RED)
                .append(Component.text(formatTeam(match.redTeam), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  BLUE: ", NamedTextColor.BLUE)
                .append(Component.text(formatTeam(match.blueTeam), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Predicted win: ", NamedTextColor.GRAY)
                .append(Component.text("Red " + String.format("%.1f%%", match.pRedWins * 100), NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Blue " + String.format("%.1f%%", (1 - match.pRedWins) * 100), NamedTextColor.BLUE)));
    }
    
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Beacon War Commands ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("All commands work with or without /bw prefix", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/setup", NamedTextColor.YELLOW)
                .append(Component.text(" - Set up beacons at your location", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/start [minutes]", NamedTextColor.YELLOW)
                .append(Component.text(" - Start the game (optional time limit)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/stop", NamedTextColor.YELLOW)
                .append(Component.text(" - Stop the game (no winner)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/reset", NamedTextColor.YELLOW)
                .append(Component.text(" - Full reset (clears beacons, keeps ELO)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/end", NamedTextColor.YELLOW)
                .append(Component.text(" - End game and determine winner", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/pause", NamedTextColor.YELLOW)
                .append(Component.text(" - Pause all timers", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/unpause", NamedTextColor.YELLOW)
                .append(Component.text(" - Resume the game", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/join <red|blue>", NamedTextColor.YELLOW)
                .append(Component.text(" - Join a team", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/resistance <player> <0-4>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set player's resistance", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/elo [player|leaderboard|predict]", NamedTextColor.YELLOW)
                .append(Component.text(" - View ratings", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/quicklaunch [manual] [minutes]", NamedTextColor.YELLOW)
                .append(Component.text(" - Auto setup, balance, countdown, start", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/balancedteams", NamedTextColor.YELLOW)
                .append(Component.text(" - Preview balanced team assignments", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/status", NamedTextColor.YELLOW)
                .append(Component.text(" - Show game status", NamedTextColor.GRAY)));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();
        
        // Handle direct commands (not via /bw)
        if (!commandName.equals("beaconwar")) {
            return getTabCompletionsForCommand(commandName, args);
        }
        
        // Handle /bw subcommands
        if (args.length == 1) {
            return Arrays.asList("setup", "start", "stop", "reset", "end", "pause", "unpause", 
                    "join", "resistance", "elo", "quicklaunch", "balancedteams", "status", "help");
        }
        
        return getTabCompletionsForCommand(args[0].toLowerCase(), Arrays.copyOfRange(args, 1, args.length));
    }
    
    private List<String> getTabCompletionsForCommand(String commandName, String[] args) {
        if (args.length == 1) {
            switch (commandName) {
                case "join" -> {
                    return Arrays.asList("red", "blue");
                }
                case "elo" -> {
                    List<String> suggestions = new ArrayList<>(Arrays.asList("leaderboard", "predict"));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        suggestions.add(p.getName());
                    }
                    return suggestions;
                }
                case "quicklaunch" -> {
                    return Arrays.asList("manual");
                }
                case "resistance" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
                }
            }
        }
        
        if (args.length == 2 && commandName.equals("resistance")) {
            return Arrays.asList("0", "1", "2", "3", "4");
        }
        
        return new ArrayList<>();
    }
}
