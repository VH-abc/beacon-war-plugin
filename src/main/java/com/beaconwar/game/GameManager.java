package com.beaconwar.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.model.TeamColor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

/**
 * Main game manager. Coordinates all game systems.
 * Look how clean this is compared to function recursion hell!
 */
public class GameManager {
    
    private final BeaconWarPlugin plugin;
    private BeaconManager beaconManager;
    private SpawnManager spawnManager;
    private ScoreManager scoreManager;
    private TerritoryManager territoryManager;
    private EloManager eloManager;
    
    private boolean gameActive = false;
    private boolean beaconsInitialized = false;
    
    private Team redTeam;
    private Team blueTeam;
    
    // Persistent team assignments (survive disconnects)
    private final Map<String, TeamColor> playerTeamAssignment = new HashMap<>();
    private final Map<String, Integer> playerAssignedResistance = new HashMap<>();
    
    // Game roster for ELO updates (snapshot at game start)
    private List<EloManager.PlayerResistance> gameRedTeam = new ArrayList<>();
    private List<EloManager.PlayerResistance> gameBlueTeam = new ArrayList<>();
    
    // Game phase tracking
    private GamePhase currentPhase = GamePhase.CAPTURING;
    private long phaseStartTime = 0;
    private long lastScoreTime = 0;
    private long lastAmmoSupplyTime = 0;
    private long lastStackCheckTime = 0;
    
    // Game timer and pause tracking
    private long gameStartTime = 0;
    private long gameDurationMs = 0;  // 0 = no limit
    private boolean gamePaused = false;
    private long pauseStartTime = 0;
    private long totalPausedTime = 0;
    
    public GameManager(BeaconWarPlugin plugin) {
        this.plugin = plugin;
        setupTeams();
        setupScoreboard();
        scoreManager = new ScoreManager();
        eloManager = new EloManager(plugin);
    }
    
    private void setupTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        
        // Create or get Red team
        redTeam = scoreboard.getTeam("bw_red");
        if (redTeam == null) {
            redTeam = scoreboard.registerNewTeam("bw_red");
        }
        redTeam.displayName(Component.text("Red Team", NamedTextColor.RED));
        redTeam.color(NamedTextColor.RED);
        
        // Create or get Blue team
        blueTeam = scoreboard.getTeam("bw_blue");
        if (blueTeam == null) {
            blueTeam = scoreboard.registerNewTeam("bw_blue");
        }
        blueTeam.displayName(Component.text("Blue Team", NamedTextColor.BLUE));
        blueTeam.color(NamedTextColor.BLUE);
    }
    
    private void setupScoreboard() {
        // Cleanup any old beaconwar objectives from main scoreboard
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Objective old = scoreboard.getObjective("beaconwar");
        if (old != null) {
            old.unregister();
        }
        // Note: Per-player scoreboards are created in updatePlayerScoreboard()
    }
    
    public boolean setupBeacons(Player player) {
        if (beaconsInitialized) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Beacons are already set up!", NamedTextColor.YELLOW)));
            return false;
        }
        
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        beaconManager = new BeaconManager(allPlayers);
        beaconManager.setCurrentPhase(currentPhase);
        
        // Load config values
        int spacing = plugin.getConfig().getInt("beacon-spacing", 200);
        int beaconsPerSide = plugin.getConfig().getInt("beacons-per-side", 5);
        int groundSearchStartY = plugin.getConfig().getInt("ground-search-start-y", 150);
        boolean spawnCastles = plugin.getConfig().getBoolean("spawn-castles", true);
        boolean spawnCastleGates = plugin.getConfig().getBoolean("spawn-castle-gates", false);
        double netherSpacingMultiplier = plugin.getConfig().getDouble("nether-spacing-multiplier", 0.7);
        boolean spawnNetherPortals = plugin.getConfig().getBoolean("spawn-nether-portals", true);
        
        // Calculate effective spacing (may be reduced in Nether)
        boolean isNether = player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        int effectiveSpacing = isNether ? (int)(spacing * netherSpacingMultiplier) : spacing;
        
        spawnManager = new SpawnManager(beaconManager, effectiveSpacing);
        territoryManager = new TerritoryManager(beaconManager, effectiveSpacing);
        
        BeaconPlacer placer = new BeaconPlacer(player, beaconManager, spacing, beaconsPerSide, groundSearchStartY, spawnCastles, spawnCastleGates, netherSpacingMultiplier, spawnNetherPortals);
        boolean success = placer.placeAllBeacons();
        
        if (success) {
            beaconsInitialized = true;
            spawnManager.updateSpawns();
        }
        
        return success;
    }
    
    /**
     * Start the game with optional time limit.
     * @param minutes Game duration in minutes (0 = no limit)
     */
    public void startGame(int minutes) {
        if (!beaconsInitialized) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Cannot start game: beacons not set up!", NamedTextColor.YELLOW)));
            return;
        }
        
        gameActive = true;
        gamePaused = false;
        totalPausedTime = 0;
        currentPhase = GamePhase.CAPTURING;
        
        long now = System.currentTimeMillis();
        gameStartTime = now;
        phaseStartTime = now;
        lastScoreTime = now;
        lastAmmoSupplyTime = now;
        gameDurationMs = minutes * 60 * 1000L;
        
        scoreManager.reset();
        
        // Set all players to survival mode
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        }
        
        // Record game rosters for ELO updates (snapshot of current team assignments)
        recordGameRosters();
        
        // Check if game is in the Nether and give portal supplies
        boolean isNether = beaconManager.getAllBeacons().iterator().next().getLocation().getWorld()
                .getEnvironment() == org.bukkit.World.Environment.NETHER;
        if (isNether) {
            supplyNetherPortalItems();
        }
        
        String timeMsg = minutes > 0 ? " (" + minutes + " min)" : " (no time limit)";
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game started!" + timeMsg + " ", NamedTextColor.GREEN))
                .append(Component.text("Phase: ", NamedTextColor.GRAY))
                .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor())));
        
        announcePhase();
    }
    
    /**
     * Start game with no time limit.
     */
    public void startGame() {
        startGame(0);
    }
    
    /**
     * Record the game rosters at game start for ELO updates.
     */
    private void recordGameRosters() {
        gameRedTeam.clear();
        gameBlueTeam.clear();
        
        for (Map.Entry<String, TeamColor> entry : playerTeamAssignment.entrySet()) {
            String playerName = entry.getKey();
            TeamColor team = entry.getValue();
            int resistance = playerAssignedResistance.getOrDefault(playerName, 0);
            
            EloManager.PlayerResistance pr = new EloManager.PlayerResistance(playerName, resistance);
            if (team == TeamColor.RED) {
                gameRedTeam.add(pr);
            } else if (team == TeamColor.BLUE) {
                gameBlueTeam.add(pr);
            }
        }
    }
    
    /**
     * Give all players obsidian and flint-and-steel for Nether portal building
     */
    private void supplyNetherPortalItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().addItem(new ItemStack(Material.OBSIDIAN, 10));
            player.getInventory().addItem(new ItemStack(Material.FLINT_AND_STEEL, 1));
        }
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Nether game! All players received obsidian and flint-and-steel.", NamedTextColor.GOLD)));
    }
    
    /**
     * Stop the game without determining a winner (admin stop).
     */
    public void stopGame() {
        gameActive = false;
        gamePaused = false;
        
        // Clear team assignments for next game
        playerTeamAssignment.clear();
        playerAssignedResistance.clear();
        gameRedTeam.clear();
        gameBlueTeam.clear();
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game stopped!", NamedTextColor.YELLOW)));
    }
    
    /**
     * Full reset - clears game state AND beacon positions.
     * Does NOT affect ELO ratings.
     */
    public void resetGame() {
        // Stop any active game
        gameActive = false;
        gamePaused = false;
        
        // Clear team assignments
        playerTeamAssignment.clear();
        playerAssignedResistance.clear();
        gameRedTeam.clear();
        gameBlueTeam.clear();
        
        // Clear beacon state
        if (beaconManager != null) {
            beaconManager.clear();
        }
        beaconsInitialized = false;
        beaconManager = null;
        spawnManager = null;
        territoryManager = null;
        
        // Reset scores
        scoreManager.reset();
        
        // Reset phase tracking
        currentPhase = GamePhase.CAPTURING;
        phaseStartTime = 0;
        lastScoreTime = 0;
        lastAmmoSupplyTime = 0;
        lastStackCheckTime = 0;
        gameStartTime = 0;
        gameDurationMs = 0;
        totalPausedTime = 0;
        
        // Reset all players to main scoreboard
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game fully reset! Use /bw setup to place new beacons.", NamedTextColor.GREEN)));
    }
    
    /**
     * End the game with a winner, update ELO ratings.
     * @param winner The winning team (NEUTRAL = tie)
     */
    public void endGame(TeamColor winner) {
        gameActive = false;
        gamePaused = false;
        
        // Announce winner
        if (winner == TeamColor.NEUTRAL) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Game ended in a TIE!", NamedTextColor.YELLOW)));
        } else {
            NamedTextColor winnerColor = (winner == TeamColor.RED) ? NamedTextColor.RED : NamedTextColor.BLUE;
            String winnerName = (winner == TeamColor.RED) ? "RED TEAM" : "BLUE TEAM";
            
            // Show title to all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(
                        Component.text(winnerName + " WINS!", winnerColor),
                        Component.text(""),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMillis(1000))));
            }
            
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text(winnerName + " WINS!", winnerColor)));
        }
        
        // Update ELO ratings (only if not a tie and we have rosters)
        if (winner != TeamColor.NEUTRAL && !gameRedTeam.isEmpty() && !gameBlueTeam.isEmpty()) {
            double loss = eloManager.updateRatings(gameRedTeam, gameBlueTeam, winner);
            Bukkit.broadcast(Component.text("[Beacon War ELO] ", NamedTextColor.GOLD)
                    .append(Component.text("Ratings updated! Loss: " + String.format("%.3f", loss), NamedTextColor.YELLOW)));
        }
        
        // Show final results (scores only in score mode, beacons in beacon_count mode)
        String winCondition = plugin.getConfig().getString("win-condition", "score");
        if (winCondition.equalsIgnoreCase("score")) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Final Scores - Red: " + scoreManager.getScore(TeamColor.RED) + 
                            " | Blue: " + scoreManager.getScore(TeamColor.BLUE), NamedTextColor.WHITE)));
        } else {
            Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Final Beacons - Red: " + counts.get(TeamColor.RED) + 
                            " | Blue: " + counts.get(TeamColor.BLUE), NamedTextColor.WHITE)));
        }
    }
    
    /**
     * Determine the winner based on current state and win condition mode.
     */
    public TeamColor determineWinner() {
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        int redBeacons = counts.get(TeamColor.RED);
        int blueBeacons = counts.get(TeamColor.BLUE);
        int totalBeacons = redBeacons + blueBeacons + counts.get(TeamColor.NEUTRAL);
        
        // If one team has ALL beacons, they win immediately
        if (redBeacons == totalBeacons) return TeamColor.RED;
        if (blueBeacons == totalBeacons) return TeamColor.BLUE;
        
        // Determine winner by config mode
        String winCondition = plugin.getConfig().getString("win-condition", "score");
        
        if (winCondition.equalsIgnoreCase("beacon_count")) {
            if (redBeacons > blueBeacons) return TeamColor.RED;
            if (blueBeacons > redBeacons) return TeamColor.BLUE;
            return TeamColor.NEUTRAL;  // Tie
        } else {
            // Default: score mode
            int redScore = scoreManager.getScore(TeamColor.RED);
            int blueScore = scoreManager.getScore(TeamColor.BLUE);
            if (redScore > blueScore) return TeamColor.RED;
            if (blueScore > redScore) return TeamColor.BLUE;
            return TeamColor.NEUTRAL;  // Tie
        }
    }
    
    /**
     * Pause the game (stops all timers and beacon captures).
     */
    public void pauseGame() {
        if (!gameActive) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Cannot pause: no game is active!", NamedTextColor.YELLOW)));
            return;
        }
        if (gamePaused) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Game is already paused!", NamedTextColor.YELLOW)));
            return;
        }
        
        gamePaused = true;
        pauseStartTime = System.currentTimeMillis();
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game PAUSED!", NamedTextColor.YELLOW)));
    }
    
    /**
     * Unpause the game (resumes all timers).
     */
    public void unpauseGame() {
        if (!gameActive) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Cannot unpause: no game is active!", NamedTextColor.YELLOW)));
            return;
        }
        if (!gamePaused) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Game is not paused!", NamedTextColor.YELLOW)));
            return;
        }
        
        long pauseDuration = System.currentTimeMillis() - pauseStartTime;
        totalPausedTime += pauseDuration;
        gamePaused = false;
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game RESUMED!", NamedTextColor.GREEN)));
    }
    
    public boolean isGamePaused() {
        return gamePaused;
    }
    
    /**
     * Main game tick - called every server tick (20 times per second)
     */
    public void tick() {
        if (!beaconsInitialized || beaconManager == null) {
            return;
        }
        
        // Skip most updates when paused (only update display)
        if (gamePaused) {
            updateActionBar();  // Still show action bar (with PAUSED indicator)
            updateScoreboard();
            return;
        }
        
        // Update beacon ownership (only when not paused)
        beaconManager.updateAllBeaconOwnership();
        
        // Check for victory conditions
        if (gameActive) {
            checkVictoryConditions();
        }
        
        long currentTime = System.currentTimeMillis();
        long effectivePhaseStart = phaseStartTime + totalPausedTime;
        long effectiveScoreStart = lastScoreTime + totalPausedTime;
        long effectiveAmmoStart = lastAmmoSupplyTime + totalPausedTime;
        
        int phaseDuration = plugin.getConfig().getInt("phase-duration", 600) * 1000;
        int scoreInterval = plugin.getConfig().getInt("score-interval", 60) * 1000;
        int ammoInterval = 4 * 60 * 1000; // 4 minutes
        
        // Check for phase change
        if (currentTime - effectivePhaseStart >= phaseDuration) {
            switchPhase();
            // Reset phase start time relative to now (not including paused time)
            phaseStartTime = currentTime - totalPausedTime;
        }
        
        // Check for score update
        if (currentTime - effectiveScoreStart >= scoreInterval) {
            awardScore();
            lastScoreTime = currentTime - totalPausedTime;
        }
        
        // Check for ammo supply
        if (currentTime - effectiveAmmoStart >= ammoInterval) {
            supplyAmmo();
            lastAmmoSupplyTime = currentTime - totalPausedTime;
        }
        
        // Check for wool/glass slot limits every 2 seconds
        if (currentTime - lastStackCheckTime >= 2000) {
            enforceSlotLimits();
            lastStackCheckTime = currentTime;
        }
        
        // Apply mining fatigue near enemy beacons
        applyMiningFatigue();
        
        // Apply comeback resistance buffs based on beacon control
        applyResistanceBuffs();
        
        // Auto-supply team materials
        supplyTeamMaterials();
        
        // Update team spawn points
        spawnManager.updateSpawns();
        
        // Update action bar with phase timer
        updateActionBar();
        
        // Update scoreboard
        updateScoreboard();
    }
    
    /**
     * Check for victory conditions and end game if met.
     */
    private void checkVictoryConditions() {
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        int redBeacons = counts.get(TeamColor.RED);
        int blueBeacons = counts.get(TeamColor.BLUE);
        int totalBeacons = redBeacons + blueBeacons + counts.get(TeamColor.NEUTRAL);
        
        // Check for all-beacon capture (immediate win)
        if (redBeacons == totalBeacons && totalBeacons > 0) {
            endGame(TeamColor.RED);
            return;
        }
        if (blueBeacons == totalBeacons && totalBeacons > 0) {
            endGame(TeamColor.BLUE);
            return;
        }
        
        // Check for time expiry (if time limit is set)
        if (gameDurationMs > 0 && !gamePaused) {
            long elapsed = System.currentTimeMillis() - gameStartTime - totalPausedTime;
            if (elapsed >= gameDurationMs) {
                TeamColor winner = determineWinner();
                endGame(winner);
            }
        }
    }
    
    private void updateActionBar() {
        long currentTime = System.currentTimeMillis();
        int phaseDuration = plugin.getConfig().getInt("phase-duration", 600) * 1000;
        
        // Account for current pause time (if paused) in addition to completed pauses
        long effectivePausedTime = totalPausedTime;
        if (gamePaused) {
            effectivePausedTime += (currentTime - pauseStartTime);
        }
        
        long effectivePhaseStart = phaseStartTime + effectivePausedTime;
        long phaseTimeLeft = Math.max(0, (effectivePhaseStart + phaseDuration - currentTime) / 1000);
        
        // Calculate game time remaining (if time limit set)
        String gameTimeStr = "";
        if (gameDurationMs > 0) {
            long effectiveGameStart = gameStartTime + effectivePausedTime;
            long gameTimeLeft = Math.max(0, (effectiveGameStart + gameDurationMs - currentTime) / 1000);
            gameTimeStr = formatTime(gameTimeLeft) + " | ";
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor playerTeam = getPlayerTeam(player);
            
            // Base action bar: [Game Time] | [Phase Name] - [Phase Time]
            Component actionBar;
            if (gamePaused) {
                actionBar = Component.text("PAUSED", NamedTextColor.RED)
                        .append(Component.text(" - " + gameTimeStr, NamedTextColor.WHITE))
                        .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor()))
                        .append(Component.text(" - " + formatTime(phaseTimeLeft), NamedTextColor.WHITE));
            } else {
                actionBar = Component.text(gameTimeStr, NamedTextColor.WHITE)
                        .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor()))
                        .append(Component.text(" - " + formatTime(phaseTimeLeft), NamedTextColor.WHITE));
            }
            
            // Add target beacon coordinates (neutral first, then enemy front)
            if (playerTeam != TeamColor.NEUTRAL && beaconManager != null) {
                com.beaconwar.model.Beacon targetBeacon = null;
                String targetLabel = "";
                NamedTextColor targetColor = NamedTextColor.WHITE;
                
                // Check for neutral beacon first
                com.beaconwar.model.Beacon neutralBeacon = beaconManager.getAnyNeutralBeacon();
                if (neutralBeacon != null) {
                    targetBeacon = neutralBeacon;
                    targetLabel = "Neutral Beacon: ";
                    targetColor = NamedTextColor.WHITE;
                } else {
                    // Otherwise show enemy frontmost beacon
                    TeamColor enemyTeam = playerTeam == TeamColor.RED ? TeamColor.BLUE : TeamColor.RED;
                    com.beaconwar.model.Beacon frontBeacon = beaconManager.getFrontmostBeacon(enemyTeam);
                    if (frontBeacon != null) {
                        targetBeacon = frontBeacon;
                        targetLabel = "Enemy Front: ";
                        targetColor = enemyTeam.getChatColor();
                    }
                }
                
                if (targetBeacon != null) {
                    org.bukkit.Location loc = targetBeacon.getLocation();
                    actionBar = actionBar
                            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(targetLabel, NamedTextColor.GRAY))
                            .append(Component.text(String.format("(%d, %d, %d)", 
                                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), 
                                    targetColor));
                    
                    // Update compass to point to target
                    updateCompass(player, loc);
                }
            }
            
            player.sendActionBar(actionBar);
        }
    }
    
    private void updateScoreboard() {
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        
        // Update scoreboard for each player individually
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player, counts);
        }
    }
    
    private void updatePlayerScoreboard(Player player, Map<TeamColor, Integer> counts) {
        // Get or create a scoreboard for this player
        org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();
        
        // If player is using the main scoreboard, create a new one for them
        if (scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(scoreboard);
        }
        
        // Clear and recreate the objective
        org.bukkit.scoreboard.Objective objective = scoreboard.getObjective("beaconwar");
        if (objective != null) {
            objective.unregister();
        }
        
        objective = scoreboard.registerNewObjective(
                "beaconwar",
                "dummy",
                Component.text("Beacon War", NamedTextColor.AQUA)
        );
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        
        // Display format (reverse order because Minecraft displays bottom-up)
        int line = 15;
        
        // Check if win condition is "score" mode
        boolean isScoreMode = plugin.getConfig().getString("win-condition", "score").equalsIgnoreCase("score");
        
        objective.getScore("§e§l" + currentPhase.getDisplayName()).setScore(line--);
        objective.getScore("").setScore(line--);
        
        objective.getScore("§c§lRed Team:").setScore(line--);
        if (isScoreMode) {
            objective.getScore("  Score: §f" + scoreManager.getScore(TeamColor.RED)).setScore(line--);
        }
        objective.getScore("  Beacons: §f" + counts.get(TeamColor.RED)).setScore(line--);
        objective.getScore(" ").setScore(line--);
        
        objective.getScore("§9§lBlue Team:").setScore(line--);
        if (isScoreMode) {
            objective.getScore("  Score: §f" + scoreManager.getScore(TeamColor.BLUE)).setScore(line--);
        }
        objective.getScore("  Beacons: §f" + counts.get(TeamColor.BLUE)).setScore(line--);
        objective.getScore("  ").setScore(line--);
        
        // Add THIS player's status info
        TeamColor playerTeam = getPlayerTeam(player);
        if (playerTeam != TeamColor.NEUTRAL) {
            objective.getScore("§7Drop Status:").setScore(line--);
            
            // Check if player is in a different dimension than the beacons
            org.bukkit.World beaconWorld = beaconManager.getBeacon(0).getLocation().getWorld();
            boolean inDifferentDimension = !player.getWorld().equals(beaconWorld);
            
            if (inDifferentDimension) {
                // Player is in a different dimension - they always keep inventory
                objective.getScore("  KeepInv: §a✓ §7(other dim)").setScore(line--);
            } else {
                String dropMode = plugin.getConfig().getString("drop-mode", "territory");
                
                if (dropMode.equalsIgnoreCase("absolute_position")) {
                    // Absolute position mode: show beacon index and drop percentage
                    double beaconIndex = beaconManager.getInterpolatedBeaconIndex(player.getLocation().getX());
                    
                    // Calculate enemy distance (positive = in enemy territory)
                    double enemyDistance;
                    if (playerTeam == TeamColor.BLUE) {
                        enemyDistance = beaconIndex;  // Blue: positive = enemy
                    } else {
                        enemyDistance = -beaconIndex;  // Red: negative = enemy
                    }
                    
                    // Calculate drop probability
                    double progressiveFraction = plugin.getConfig().getDouble("progressive-drop-fraction", 0.04);
                    double dropProb = enemyDistance <= 0 ? 0.0 : Math.min(1.0, enemyDistance * progressiveFraction);
                    
                    // Format position with color (green = safe, red = danger)
                    String posColor = enemyDistance <= 0 ? "§a" : "§c";
                    String posText = String.format("%+.1f", beaconIndex);
                    
                    // Format drop probability
                    String dropText = String.format("%.0f%%", dropProb * 100);
                    String dropColor = dropProb == 0 ? "§a" : (dropProb < 0.2 ? "§e" : "§c");
                    
                    objective.getScore("  Position: " + posColor + posText).setScore(line--);
                    objective.getScore("  Drop: " + dropColor + dropText).setScore(line--);
                } else {
                    // Territory mode: show territory and keep inventory status
                    TeamColor territory = territoryManager.getTerritoryAt(player.getLocation());
                    boolean hasKeepInv = isInHomeTerritory(player) && currentPhase == GamePhase.CAPTURING;
                    
                    String territoryText = switch (territory) {
                        case RED -> "§cRed";
                        case BLUE -> "§9Blue";
                        default -> "§7Neutral";
                    };
                    
                    String keepInvText = hasKeepInv ? "§a✓" : "§7✗";
                    
                    objective.getScore("  Territory: " + territoryText).setScore(line--);
                    objective.getScore("  KeepInv: " + keepInvText).setScore(line--);
                }
            }
        }
    }
    
    /**
     * Enforce limit of one inventory slot per material for red/blue wool and glass
     * Hotbar slots are preferentially kept over other inventory slots
     */
    private void enforceSlotLimits() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            enforcePlayerSlotLimits(player);
        }
    }
    
    private void enforcePlayerSlotLimits(Player player) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        
        // Materials to limit - red/blue wool/glass and torches
        Material[] limitedMaterials = {
            Material.RED_WOOL,
            Material.BLUE_WOOL,
            Material.RED_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.TORCH
        };
        
        for (Material material : limitedMaterials) {
            List<Integer> slotsWithMaterial = new ArrayList<>();
            
            // Find all slots containing this material (0-35 covers hotbar + main inventory)
            for (int i = 0; i < 36; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() == material) {
                    slotsWithMaterial.add(i);
                }
            }
            
            // If more than one slot has this material, remove extras
            if (slotsWithMaterial.size() > 1) {
                // Find preferred slot to keep (hotbar = slots 0-8)
                Integer slotToKeep = null;
                
                // First check hotbar slots (0-8)
                for (Integer slot : slotsWithMaterial) {
                    if (slot < 9) {
                        slotToKeep = slot;
                        break;
                    }
                }
                
                // If no hotbar slot, keep the first one found
                if (slotToKeep == null) {
                    slotToKeep = slotsWithMaterial.get(0);
                }
                
                // Remove all other slots
                for (Integer slot : slotsWithMaterial) {
                    if (!slot.equals(slotToKeep)) {
                        inv.setItem(slot, null);
                    }
                }
                
                // Notify player
                String materialName = material.name().toLowerCase().replace("_", " ");
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Excess " + materialName + " removed (max 1 slot)", NamedTextColor.YELLOW)));
            }
        }
    }
    
    private void switchPhase() {
        currentPhase = currentPhase.next();
        phaseStartTime = System.currentTimeMillis();
        
        // Update beacon manager with current phase
        if (beaconManager != null) {
            beaconManager.setCurrentPhase(currentPhase);
        }
        
        announcePhase();
    }
    
    private void announcePhase() {
        Component title = Component.text(currentPhase.getDisplayName(), currentPhase.getColor());
        Component subtitle;
        
        if (currentPhase == GamePhase.MINING) {
            subtitle = Component.text("Mine resources! Beacons cannot be captured.", NamedTextColor.YELLOW);
        } else {
            subtitle = Component.text("Capture enemy beacons!", NamedTextColor.YELLOW);
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(title, subtitle, 
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Phase Changed: ", NamedTextColor.GRAY))
                .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor())));
    }
    
    private void awardScore() {
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        scoreManager.awardPoints(counts);
    }
    
    private void applyResistanceBuffs() {
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        int redBeacons = counts.get(TeamColor.RED);
        int blueBeacons = counts.get(TeamColor.BLUE);
        
        // Load comeback resistance levels from config (index = beacon count, value = resistance level)
        List<Integer> comebackResistanceLevels = plugin.getConfig().getIntegerList("comeback-resistance-levels");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor playerTeam = getPlayerTeam(player);
            if (playerTeam == TeamColor.NEUTRAL) {
                continue;
            }
            
            int teamBeacons = (playerTeam == TeamColor.RED) ? redBeacons : blueBeacons;
            int comebackResistance = 0;
            
            // Look up comeback resistance level from config array
            if (teamBeacons < comebackResistanceLevels.size()) {
                comebackResistance = comebackResistanceLevels.get(teamBeacons);
            }
            
            // Get player's assigned resistance (for team balancing)
            int assignedResistance = playerAssignedResistance.getOrDefault(player.getName(), 0);
            
            // Use the higher of comeback resistance or assigned resistance
            int resistanceLevel = Math.max(comebackResistance, assignedResistance);
            
            // Apply resistance if applicable
            if (resistanceLevel > 0) {
                PotionEffectType resistanceType = PotionEffectType.getByName("resistance");
                if (resistanceType != null) {
                    player.addPotionEffect(new PotionEffect(
                            resistanceType,
                            40, // 2 seconds in ticks (will refresh)
                            resistanceLevel - 1 // Level 2 = index 1
                    ));
                }
            }
        }
    }
    
    private void supplyTeamMaterials() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor team = getPlayerTeam(player);
            if (team == TeamColor.NEUTRAL) {
                continue;
            }
            
            Material glassType;
            Material woolType;
            
            if (team == TeamColor.RED) {
                glassType = Material.RED_STAINED_GLASS;
                woolType = Material.RED_WOOL;
            } else {
                glassType = Material.BLUE_STAINED_GLASS;
                woolType = Material.BLUE_WOOL;
            }
            
            ItemStack glassStack = new ItemStack(glassType, 64);
            ItemStack woolStack = new ItemStack(woolType, 64);
            
            // Supply team glass
            if (!player.getInventory().contains(glassType)) {
                player.getInventory().addItem(glassStack);
            }
            
            // Supply team wool
            if (!player.getInventory().contains(woolType)) {
                player.getInventory().addItem(woolStack);
            }
            
            // Supply torches
            if (!player.getInventory().contains(Material.TORCH)) {
                player.getInventory().addItem(new ItemStack(Material.TORCH, 64));
            }
            
            // Supply Fortune II iron pickaxe (with Efficiency II in Nether)
            if (!hasPickaxe(player.getInventory())) {
                ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
                Enchantment fortune = Enchantment.getByKey(NamespacedKey.minecraft("fortune"));
                if (fortune != null) {
                    pickaxe.addEnchantment(fortune, 2);
                }
                // Add Efficiency for Nether games (helps mine netherrack faster)
                boolean isNetherGame = beaconManager.getBeacon(0).getLocation().getWorld()
                        .getEnvironment() == org.bukkit.World.Environment.NETHER;
                if (isNetherGame) {
                    Enchantment efficiency = Enchantment.getByKey(NamespacedKey.minecraft("efficiency"));
                    if (efficiency != null) {
                        pickaxe.addEnchantment(efficiency, 4);
                    }
                }
                player.getInventory().addItem(pickaxe);
            }
            
            // Supply Infinity, Power II, Punch I bow
            if (!hasBow(player.getInventory())) {
                ItemStack bow = new ItemStack(Material.BOW);
                Enchantment infinity = Enchantment.getByKey(NamespacedKey.minecraft("infinity"));
                Enchantment power = Enchantment.getByKey(NamespacedKey.minecraft("power"));
                Enchantment punch = Enchantment.getByKey(NamespacedKey.minecraft("punch"));
                if (infinity != null) bow.addEnchantment(infinity, 1);
                if (power != null) bow.addEnchantment(power, 1); //12 damage (6 hearts)
                if (punch != null) bow.addEnchantment(punch, 1);
                player.getInventory().addItem(bow);
                
                // Also give 1 arrow for infinity bow
                if (!player.getInventory().contains(Material.ARROW)) {
                    player.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                }
            }
            
            // Supply Piercing I crossbow
            if (!hasCrossbow(player.getInventory())) {
                ItemStack crossbow = new ItemStack(Material.CROSSBOW);
                Enchantment piercing = Enchantment.getByKey(NamespacedKey.minecraft("piercing"));
                if (piercing != null) {
                    crossbow.addEnchantment(piercing, 1);
                }
                player.getInventory().addItem(crossbow);
            }

            // Supply Compass
            if (!player.getInventory().contains(Material.COMPASS)) {
                player.getInventory().addItem(new ItemStack(Material.COMPASS));
            }
        }
    }
    
    private boolean hasPickaxe(org.bukkit.inventory.PlayerInventory inv) {
        return inv.contains(Material.IRON_PICKAXE);
    }
    
    private boolean hasBow(org.bukkit.inventory.PlayerInventory inv) {
        return inv.contains(Material.BOW);
    }
    
    private boolean hasCrossbow(org.bukkit.inventory.PlayerInventory inv) {
        return inv.contains(Material.CROSSBOW);
    }
    
    /**
     * Supply gunpowder and firework rockets every minute
     */
    private void supplyAmmo() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor team = getPlayerTeam(player);
            if (team == TeamColor.NEUTRAL) {
                continue;
            }
            
            // Give 1 gunpowder
            player.getInventory().addItem(new ItemStack(Material.GUNPOWDER, 1));
            
            // Give 1 damage-bearing firework rocket (for crossbow)
            ItemStack firework = createDamageFirework();
            player.getInventory().addItem(firework);
        }
    }
    
    /**
     * Create a firework rocket with damage (for crossbow use)
     */
    private ItemStack createDamageFirework() {
        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET, 1);
        FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
        
        // Create explosion effects - each effect adds 5 damage (2.5 hearts)
        // 3 effects = 15 damage (7.5 hearts) - stronger than any axe!
        
        FireworkEffect effect1 = FireworkEffect.builder()
                .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                .withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build();

        // 50 damage (25 hearts); used to be 35
        for (int i = 0; i < 3; i++) {
            meta.addEffect(effect1);
        }
        
        meta.setPower(3);
        firework.setItemMeta(meta);
        
        return firework;
    }
    
    private void applyMiningFatigue() {
        // Different fatigue levels based on current phase
        int fatigueLevel;
        if (currentPhase == GamePhase.MINING) {
            fatigueLevel = plugin.getConfig().getInt("mining-fatigue-level", 3);
        } else {
            fatigueLevel = plugin.getConfig().getInt("capturing-fatigue-level", 2);
        }
        
        int range = plugin.getConfig().getInt("mining-fatigue-range", 20);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor playerTeam = getPlayerTeam(player);
            if (playerTeam == TeamColor.NEUTRAL) {
                continue;
            }
            
            // Check if near enemy beacon (must be in same world)
            boolean nearEnemyBeacon = beaconManager.getAllBeacons().stream()
                    .filter(beacon -> beacon.getOwner() != TeamColor.NEUTRAL && beacon.getOwner() != playerTeam)
                    .filter(beacon -> beacon.getLocation().getWorld().equals(player.getWorld()))
                    .anyMatch(beacon -> player.getLocation().distance(beacon.getLocation()) <= range);
            
            if (nearEnemyBeacon) {
                // Apply mining fatigue for 2 seconds (won't stack beyond existing)
                PotionEffectType fatigueType = PotionEffectType.getByName("mining_fatigue");
                if (fatigueType != null) {
                    player.addPotionEffect(new PotionEffect(
                            fatigueType, 
                            40, // 2 seconds in ticks
                            fatigueLevel - 1 // Level 3 = index 2
                    ));
                }
            }
        }
    }
    
    /**
     * Add a player to a team.
     */
    public void addPlayerToTeam(Player player, TeamColor team) {
        addPlayerToTeam(player.getName(), team);
        
        // Send confirmation message
        switch (team) {
            case RED -> player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                        .append(Component.text("You joined the Red Team!", NamedTextColor.RED)));
            case BLUE -> player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                        .append(Component.text("You joined the Blue Team!", NamedTextColor.BLUE)));
            default -> player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Invalid team!", NamedTextColor.YELLOW)));
        }
    }
    
    /**
     * Add a player to a team by name (for offline player support).
     */
    public void addPlayerToTeam(String playerName, TeamColor team) {
        // Remove from other teams first
        redTeam.removeEntry(playerName);
        blueTeam.removeEntry(playerName);
        playerTeamAssignment.remove(playerName);
        
        // Add to selected team
        switch (team) {
            case RED -> {
                redTeam.addEntry(playerName);
                playerTeamAssignment.put(playerName, TeamColor.RED);
            }
            case BLUE -> {
                blueTeam.addEntry(playerName);
                playerTeamAssignment.put(playerName, TeamColor.BLUE);
            }
            default -> {}
        }
    }
    
    /**
     * Set a player's assigned resistance level.
     * @param playerName The player's name
     * @param level Resistance level (0-4)
     */
    public void setAssignedResistance(String playerName, int level) {
        level = Math.max(0, Math.min(4, level));  // Clamp to 0-4
        playerAssignedResistance.put(playerName, level);
    }
    
    /**
     * Get a player's assigned resistance level.
     */
    public int getAssignedResistance(String playerName) {
        return playerAssignedResistance.getOrDefault(playerName, 0);
    }
    
    /**
     * Restore a player's team and resistance when they reconnect.
     */
    public void restorePlayerState(Player player) {
        String name = player.getName();
        
        // Restore team assignment
        TeamColor team = playerTeamAssignment.get(name);
        if (team != null) {
            switch (team) {
                case RED -> redTeam.addEntry(name);
                case BLUE -> blueTeam.addEntry(name);
                default -> {}
            }
            
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Welcome back! You're on the ", NamedTextColor.GREEN))
                    .append(Component.text(team == TeamColor.RED ? "Red" : "Blue", team.getChatColor()))
                    .append(Component.text(" team.", NamedTextColor.GREEN)));
        }
    }
    
    /**
     * Get the persistent team assignment for a player.
     */
    public TeamColor getPlayerTeamAssignment(String playerName) {
        return playerTeamAssignment.getOrDefault(playerName, TeamColor.NEUTRAL);
    }
    
    public TeamColor getPlayerTeam(Player player) {
        if (redTeam.hasEntry(player.getName())) {
            return TeamColor.RED;
        }
        if (blueTeam.hasEntry(player.getName())) {
            return TeamColor.BLUE;
        }
        return TeamColor.NEUTRAL;
    }
    
    public void showStatus(Player player) {
        if (!beaconsInitialized) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.YELLOW)
                    .append(Component.text("Beacons not initialized. Use /bw setup", NamedTextColor.GRAY)));
            return;
        }
        
        Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
        
        player.sendMessage(Component.text("=== Beacon War Status ===", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Game Active: ", NamedTextColor.GRAY)
                .append(Component.text(gameActive ? "Yes" : "No", 
                        gameActive ? NamedTextColor.GREEN : NamedTextColor.RED)));
        
        if (gameActive) {
            player.sendMessage(Component.text("Current Phase: ", NamedTextColor.GRAY)
                    .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor())));
            
            long timeLeft = (phaseStartTime + plugin.getConfig().getInt("phase-duration", 600) * 1000L - System.currentTimeMillis()) / 1000;
            player.sendMessage(Component.text("Time Until Next Phase: ", NamedTextColor.GRAY)
                    .append(Component.text(formatTime(timeLeft), NamedTextColor.WHITE)));
        }
        
        player.sendMessage(Component.text("Red Beacons: ", NamedTextColor.RED)
                .append(Component.text(String.valueOf(counts.get(TeamColor.RED)), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Blue Beacons: ", NamedTextColor.BLUE)
                .append(Component.text(String.valueOf(counts.get(TeamColor.BLUE)), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Neutral Beacons: ", NamedTextColor.YELLOW)
                .append(Component.text(String.valueOf(counts.get(TeamColor.NEUTRAL)), NamedTextColor.WHITE)));
        
        // Only show scores in score mode
        if (plugin.getConfig().getString("win-condition", "score").equalsIgnoreCase("score")) {
            player.sendMessage(Component.text("Red Score: ", NamedTextColor.RED)
                    .append(Component.text(String.valueOf(scoreManager.getScore(TeamColor.RED)), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Blue Score: ", NamedTextColor.BLUE)
                    .append(Component.text(String.valueOf(scoreManager.getScore(TeamColor.BLUE)), NamedTextColor.WHITE)));
        }
    }
    
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
    
    public void cleanup() {
        gameActive = false;
        if (beaconManager != null) {
            beaconManager.clear();
        }
        
        // Reset all players to main scoreboard
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
    
    public boolean isGameActive() {
        return gameActive;
    }
    
    public boolean areBeaconsInitialized() {
        return beaconsInitialized;
    }
    
    public SpawnManager getSpawnManager() {
        return spawnManager;
    }
    
    public GamePhase getCurrentPhase() {
        return currentPhase;
    }
    
    public ScoreManager getScoreManager() {
        return scoreManager;
    }
    
    public BeaconManager getBeaconManager() {
        return beaconManager;
    }
    
    public TerritoryManager getTerritoryManager() {
        return territoryManager;
    }
    
    public EloManager getEloManager() {
        return eloManager;
    }
    
    public long getGameTimeRemainingMs() {
        if (gameDurationMs <= 0) return -1;  // No time limit
        long elapsed = System.currentTimeMillis() - gameStartTime - totalPausedTime;
        return Math.max(0, gameDurationMs - elapsed);
    }
    
    public Team getRedTeam() {
        return redTeam;
    }
    
    public Team getBlueTeam() {
        return blueTeam;
    }
    
    /**
     * Get list of players on a team.
     */
    public List<String> getTeamPlayers(TeamColor team) {
        return playerTeamAssignment.entrySet().stream()
                .filter(e -> e.getValue() == team)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a player is in their own home territory
     */
    public boolean isInHomeTerritory(Player player) {
        if (territoryManager == null) {
            return false;
        }
        TeamColor playerTeam = getPlayerTeam(player);
        return territoryManager.isInTerritory(player.getLocation(), playerTeam);
    }
    
    /**
     * Update player's compass to point to target location
     */
    private void updateCompass(Player player, org.bukkit.Location target) {
        // Find or give the player a compass
        ItemStack compass = null;
        int compassSlot = -1;
        
        // Check if player already has a compass
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                compass = item;
                compassSlot = i;
                break;
            }
        }
        
        if (compass == null) {
            return;
        }
        
        // Set compass to point to target using lodestone mechanics
        org.bukkit.inventory.meta.CompassMeta meta = (org.bukkit.inventory.meta.CompassMeta) compass.getItemMeta();
        if (meta != null) {
            meta.setLodestone(target);
            meta.setLodestoneTracked(false); // Don't require actual lodestone block
            meta.displayName(Component.text("Beacon Tracker", NamedTextColor.AQUA));
            compass.setItemMeta(meta);
        }
        
        // Place compass in inventory
        player.getInventory().setItem(compassSlot, compass);
    }
}

