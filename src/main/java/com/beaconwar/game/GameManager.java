package com.beaconwar.game;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    
    private boolean gameActive = false;
    private boolean beaconsInitialized = false;
    
    private Team redTeam;
    private Team blueTeam;
    
    // Game phase tracking
    private GamePhase currentPhase = GamePhase.CAPTURING;
    private long phaseStartTime = 0;
    private long lastScoreTime = 0;
    private long lastAmmoSupplyTime = 0;
    private long lastStackCheckTime = 0;
    
    public GameManager(BeaconWarPlugin plugin) {
        this.plugin = plugin;
        setupTeams();
        setupScoreboard();
        scoreManager = new ScoreManager();
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
        
        // Calculate effective spacing (may be reduced in Nether)
        boolean isNether = player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
        int effectiveSpacing = isNether ? (int)(spacing * netherSpacingMultiplier) : spacing;
        
        spawnManager = new SpawnManager(beaconManager, effectiveSpacing);
        territoryManager = new TerritoryManager(beaconManager, effectiveSpacing);
        
        BeaconPlacer placer = new BeaconPlacer(player, beaconManager, spacing, beaconsPerSide, groundSearchStartY, spawnCastles, spawnCastleGates, netherSpacingMultiplier);
        boolean success = placer.placeAllBeacons();
        
        if (success) {
            beaconsInitialized = true;
            spawnManager.updateSpawns();
        }
        
        return success;
    }
    
    public void startGame() {
        if (!beaconsInitialized) {
            Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Cannot start game: beacons not set up!", NamedTextColor.YELLOW)));
            return;
        }
        
        gameActive = true;
        currentPhase = GamePhase.CAPTURING;
        phaseStartTime = System.currentTimeMillis();
        lastScoreTime = System.currentTimeMillis();
        lastAmmoSupplyTime = System.currentTimeMillis();
        scoreManager.reset();
        
        // Check if game is in the Nether and give portal supplies
        boolean isNether = beaconManager.getAllBeacons().iterator().next().getLocation().getWorld()
                .getEnvironment() == org.bukkit.World.Environment.NETHER;
        if (isNether) {
            supplyNetherPortalItems();
        }
        
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game started! ", NamedTextColor.GREEN))
                .append(Component.text("Phase: ", NamedTextColor.GRAY))
                .append(Component.text(currentPhase.getDisplayName(), currentPhase.getColor())));
        
        announcePhase();
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
    
    public void stopGame() {
        gameActive = false;
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Game stopped!", NamedTextColor.YELLOW)));
    }
    
    /**
     * Main game tick - called every server tick (20 times per second)
     */
    public void tick() {
        if (!beaconsInitialized || beaconManager == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        int phaseDuration = plugin.getConfig().getInt("phase-duration", 600) * 1000;
        int scoreInterval = plugin.getConfig().getInt("score-interval", 60) * 1000;
        int ammoInterval = 4 * 60 * 1000; // 1 minute
        
        // Check for phase change
        if (currentTime - phaseStartTime >= phaseDuration) {
            switchPhase();
        }
        
        // Check for score update
        if (currentTime - lastScoreTime >= scoreInterval) {
            awardScore();
            lastScoreTime = currentTime;
        }
        
        // Check for ammo supply
        if (currentTime - lastAmmoSupplyTime >= ammoInterval) {
            supplyAmmo();
            lastAmmoSupplyTime = currentTime;
        }
        
        // Check for wool/glass slot limits every 2 seconds
        if (currentTime - lastStackCheckTime >= 2000) {
            enforceSlotLimits();
            lastStackCheckTime = currentTime;
        }
        
        // Always update beacon ownership from glass blocks
        // The validator will reject changes during mining period
        beaconManager.updateAllBeaconOwnership();
        
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
    
    private void updateActionBar() {
        long currentTime = System.currentTimeMillis();
        int phaseDuration = plugin.getConfig().getInt("phase-duration", 600) * 1000;
        long timeLeft = (phaseStartTime + phaseDuration - currentTime) / 1000;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor playerTeam = getPlayerTeam(player);
            
            // Base action bar with phase timer
            Component actionBar = Component.text(currentPhase.getDisplayName() + " - ", currentPhase.getColor())
                    .append(Component.text(formatTime(timeLeft), NamedTextColor.WHITE));
            
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
        
        objective.getScore("§e§l" + currentPhase.getDisplayName()).setScore(line--);
        objective.getScore("").setScore(line--);
        
        objective.getScore("§c§lRed Team:").setScore(line--);
        objective.getScore("  Score: §f" + scoreManager.getScore(TeamColor.RED)).setScore(line--);
        objective.getScore("  Beacons: §f" + counts.get(TeamColor.RED)).setScore(line--);
        objective.getScore(" ").setScore(line--);
        
        objective.getScore("§9§lBlue Team:").setScore(line--);
        objective.getScore("  Score: §f" + scoreManager.getScore(TeamColor.BLUE)).setScore(line--);
        objective.getScore("  Beacons: §f" + counts.get(TeamColor.BLUE)).setScore(line--);
        objective.getScore("  ").setScore(line--);
        
        // Add THIS player's territory info only
        TeamColor playerTeam = getPlayerTeam(player);
        if (playerTeam != TeamColor.NEUTRAL) {
            TeamColor territory = territoryManager.getTerritoryAt(player.getLocation());
            boolean hasKeepInv = isInHomeTerritory(player) && currentPhase == GamePhase.CAPTURING;
            
            String territoryText = switch (territory) {
                case RED -> "§cRed";
                case BLUE -> "§9Blue";
                default -> "§7Neutral";
            };
            
            String keepInvText = hasKeepInv ? "§a✓" : "§7✗";
            
            objective.getScore("§7Your Status:").setScore(line--);
            objective.getScore("  Territory: " + territoryText).setScore(line--);
            objective.getScore("  KeepInv: " + keepInvText).setScore(line--);
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
        
        // Load resistance levels from config (index = beacon count, value = resistance level)
        List<Integer> resistanceLevels = plugin.getConfig().getIntegerList("comeback-resistance-levels");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor playerTeam = getPlayerTeam(player);
            if (playerTeam == TeamColor.NEUTRAL) {
                continue;
            }
            
            int teamBeacons = (playerTeam == TeamColor.RED) ? redBeacons : blueBeacons;
            int resistanceLevel = 0;
            
            // Look up resistance level from config array
            if (teamBeacons < resistanceLevels.size()) {
                resistanceLevel = resistanceLevels.get(teamBeacons);
            }
            
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
    
    public void addPlayerToTeam(Player player, TeamColor team) {
        // Remove from other teams first
        redTeam.removeEntry(player.getName());
        blueTeam.removeEntry(player.getName());
        
        // Add to selected team
        switch (team) {
            case RED -> {
                redTeam.addEntry(player.getName());
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                        .append(Component.text("You joined the Red Team!", NamedTextColor.RED)));
            }
            case BLUE -> {
                blueTeam.addEntry(player.getName());
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                        .append(Component.text("You joined the Blue Team!", NamedTextColor.BLUE)));
            }
            default -> player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Invalid team!", NamedTextColor.YELLOW)));
        }
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
        
        player.sendMessage(Component.text("Red Score: ", NamedTextColor.RED)
                .append(Component.text(String.valueOf(scoreManager.getScore(TeamColor.RED)), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Blue Score: ", NamedTextColor.BLUE)
                .append(Component.text(String.valueOf(scoreManager.getScore(TeamColor.BLUE)), NamedTextColor.WHITE)));
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

