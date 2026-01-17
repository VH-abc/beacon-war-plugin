package com.beaconwar.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.game.BeaconManager;
import com.beaconwar.game.GamePhase;
import com.beaconwar.model.TeamColor;

/**
 * Handles partial keep inventory on death
 */
public class DeathListener implements Listener {
    
    private final BeaconWarPlugin plugin;
    private final Random random = new Random();
    
    // Items that should never drop on death
    private static final Set<Material> NEVER_DROP_ITEMS = Set.of(
        Material.BOW,
        Material.CROSSBOW,
        Material.BLUE_STAINED_GLASS,
        Material.RED_STAINED_GLASS,
        Material.COMPASS,
        Material.BLUE_WOOL,
        Material.RED_WOOL,
        Material.IRON_PICKAXE
    );
    
    public DeathListener(BeaconWarPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle player death with probabilistic item dropping
     * Drop probability depends on configured drop-mode
     * Preserves armor and offhand slot positions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerInventory inv = player.getInventory();
        
        double dropProbability = calculateDropProbability(player);
        
        // Announce drop status
        if (dropProbability == 0.0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.GREEN + player.getName() + " has kept their inventory!");
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + player.getName() + " has dropped part of their inventory! (" + 
                        String.format("%.0f%%", dropProbability * 100) + ")");
            }
        }
        
        // Capture inventory contents with slot positions BEFORE death clears them
        // Slots 0-35: main inventory, 36-39: armor (boots, legs, chest, helm), 40: offhand
        Map<Integer, ItemStack> keepItems = new HashMap<>();
        
        // Clear the drops - we'll handle them manually
        event.getDrops().clear();
        
        // Process each inventory slot to preserve positions
        for (int slot = 0; slot < 41; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getAmount() == 0) {
                continue;
            }
            
            // Check if this item should never drop
            boolean neverDrop = NEVER_DROP_ITEMS.contains(item.getType());
            
            int totalAmount = item.getAmount();
            int dropAmount;
            int keepAmount;
            
            if (neverDrop) {
                // Never drop these items - always keep them
                dropAmount = 0;
                keepAmount = totalAmount;
            } else {
                // Normal drop calculation for other items
                dropAmount = calculateDropAmount(totalAmount, dropProbability);
                keepAmount = totalAmount - dropAmount;
            }
            
            // Drop the calculated amount
            if (dropAmount > 0) {
                ItemStack dropStack = item.clone();
                dropStack.setAmount(dropAmount);
                event.getDrops().add(dropStack);
                // announce to all players that the player has dropped an item
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.RED + player.getName() + " has dropped " + dropAmount + " " + item.getType().name() + "!");
                }
            }
            
            // Store items to keep with their original slot
            if (keepAmount > 0) {
                ItemStack keepStack = item.clone();
                keepStack.setAmount(keepAmount);
                keepItems.put(slot, keepStack);
                // announce to all players that the player has kept an item
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.GREEN + player.getName() + " has kept " + keepAmount + " " + item.getType().name() + "!");
                }
            }
        }
        
        // Restore items to their original slots on next tick (after death processing)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Map.Entry<Integer, ItemStack> entry : keepItems.entrySet()) {
                inv.setItem(entry.getKey(), entry.getValue());
            }
        });
    }
    
    /**
     * Calculate how many items should drop based on probability
     * Formula: floor(p*N) drops deterministically, 
     *          plus 1 more with probability (p*N - floor(p*N))
     */
    private int calculateDropAmount(int totalAmount, double probability) {
        double expected = probability * totalAmount;
        int deterministic = (int) Math.floor(expected);
        double fractional = expected - deterministic;
        
        // Roll for the extra item
        int extra = (random.nextDouble() < fractional) ? 1 : 0;
        
        return deterministic + extra;
    }
    
    /**
     * Calculate drop probability based on configured drop mode.
     * 
     * Modes:
     * - "territory": 0% in home territory during capturing phase, else death-drop-probability
     * - "absolute_position": linear scaling based on distance into enemy territory (always applies)
     */
    private double calculateDropProbability(Player player) {
        String dropMode = plugin.getConfig().getString("drop-mode", "territory");
        
        if (dropMode.equalsIgnoreCase("absolute_position")) {
            return calculateAbsolutePositionDropProbability(player);
        } else {
            // Default: territory mode
            return calculateTerritoryDropProbability(player);
        }
    }
    
    /**
     * Territory mode: 0% drops in home territory during capturing phase
     */
    private double calculateTerritoryDropProbability(Player player) {
        boolean inHomeTerritory = plugin.getGameManager().isInHomeTerritory(player);
        boolean isCapturingPeriod = plugin.getGameManager().getCurrentPhase() == GamePhase.CAPTURING;
        
        if (inHomeTerritory && isCapturingPeriod) {
            return 0.0;
        }
        return plugin.getConfig().getDouble("death-drop-probability", 0.5);
    }
    
    /**
     * Absolute position mode: drops scale linearly with distance into enemy territory.
     * - Blue team: positive beacon index = enemy territory
     * - Red team: negative beacon index = enemy territory
     */
    private double calculateAbsolutePositionDropProbability(Player player) {
        TeamColor playerTeam = plugin.getGameManager().getPlayerTeam(player);
        if (playerTeam == TeamColor.NEUTRAL) {
            return 0.0;
        }
        
        BeaconManager beaconManager = plugin.getGameManager().getBeaconManager();
        if (beaconManager == null) {
            return 0.0;
        }
        
        double playerX = player.getLocation().getX();
        double beaconIndex = beaconManager.getInterpolatedBeaconIndex(playerX);
        
        // Calculate distance into enemy territory
        // Blue pushes toward positive indices, Red pushes toward negative indices
        double enemyDistance;
        if (playerTeam == TeamColor.BLUE) {
            // Blue: positive index = enemy territory
            enemyDistance = beaconIndex;
        } else {
            // Red: negative index = enemy territory
            enemyDistance = -beaconIndex;
        }
        
        // Only drop if in enemy territory (beyond center beacon 0)
        if (enemyDistance <= 0) {
            return 0.0;
        }
        
        double progressiveFraction = plugin.getConfig().getDouble("progressive-drop-fraction", 0.04);
        return Math.min(1.0, enemyDistance * progressiveFraction);
    }
    
    /**
     * Give fire resistance when spawning in the Nether to help survive lava
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Schedule for next tick since respawn location may not be finalized yet
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
                // Give 10 seconds (200 ticks) of fire resistance
                PotionEffectType fireResistance = PotionEffectType.getByName("fire_resistance");
                if (fireResistance != null) {
                    player.addPotionEffect(new PotionEffect(fireResistance, 200, 0));
                }
            }
        });
    }
}

