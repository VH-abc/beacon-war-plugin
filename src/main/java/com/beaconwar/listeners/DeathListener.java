package com.beaconwar.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import com.beaconwar.BeaconWarPlugin;

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
     * Full keepInventory if in home territory during capturing period
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Check if in home territory during capturing period
        boolean inHomeTerritory = plugin.getGameManager().isInHomeTerritory(player);
        boolean isCapturingPeriod = plugin.getGameManager().getCurrentPhase() == 
                com.beaconwar.game.GamePhase.CAPTURING;
        
        // Full keepInventory in home territory during capturing period
        double dropProbability;
        if (inHomeTerritory && isCapturingPeriod) {
            dropProbability = 0.0; // Keep everything!
            // announce to all players that the player has kept their inventory
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.GREEN + player.getName() + " has kept their inventory!");
            }
        } else {
            dropProbability = plugin.getConfig().getDouble("death-drop-probability", 0.5);
            // announce to all players that the player has dropped their inventory
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + player.getName() + " has dropped part of their inventory!");
            }
        }
        
        // Get all items from drops (vanilla behavior)
        List<ItemStack> originalDrops = new ArrayList<>(event.getDrops());
        
        // Clear the drops - we'll handle them manually
        event.getDrops().clear();
        
        // Process each item stack
        for (ItemStack item : originalDrops) {
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
            
            // Keep the remaining items (they stay in inventory after respawn)
            if (keepAmount > 0) {
                ItemStack keepStack = item.clone();
                keepStack.setAmount(keepAmount);
                // Add back to player inventory on next tick (after death processing)
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(keepStack);
                });
                // announce to all players that the player has kept an item
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(ChatColor.GREEN + player.getName() + " has kept " + keepAmount + " " + item.getType().name() + "!");
                }
            }
        }
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
}

