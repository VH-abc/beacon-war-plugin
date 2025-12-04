package com.beaconwar.listeners;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.game.GameManager;
import com.beaconwar.game.GamePhase;
import com.beaconwar.model.Beacon;
import com.beaconwar.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Handles events related to beacon changes and player respawning
 */
public class BeaconChangeListener implements Listener {
    
    private final BeaconWarPlugin plugin;
    
    public BeaconChangeListener(BeaconWarPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Prevent players from placing beacons manually (only allow plugin placement)
     * Also prevent block placement near beacons (with exceptions)
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        Material type = placedBlock.getType();
        Player player = event.getPlayer();
        GameManager gameManager = plugin.getGameManager();
        
        // Prevent manual beacon placement
        if (type == Material.BEACON) {
            if (!player.hasPermission("beaconwar.admin")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Beacons can only be placed via /bw setup", NamedTextColor.YELLOW)));
            }
            return;
        }
        
        // Check beacon protection if game is initialized
        if (!gameManager.areBeaconsInitialized()) {
            return;
        }
        
        // Check if placing near any beacon
        if (isNearBeaconProtectedZone(placedBlock.getLocation(), type)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Cannot place blocks within 5 blocks of a beacon!", NamedTextColor.YELLOW)));
        }
    }
    
    /**
     * Check if a location is in a beacon's protected zone
     * Rules:
     * - Stained glass can be placed directly above beacon (1 block above)
     * - Blocks can be placed below the beacon's y-level
     * - Otherwise, must be at least 5 blocks away horizontally
     */
    private boolean isNearBeaconProtectedZone(Location loc, Material blockType) {
        GameManager gameManager = plugin.getGameManager();
        int protectionRadius = plugin.getConfig().getInt("beacon-protection-radius", 5);
        
        for (Beacon beacon : gameManager.getBeaconManager().getAllBeacons()) {
            Location beaconLoc = beacon.getLocation();
            
            // Check if in same world
            if (!loc.getWorld().equals(beaconLoc.getWorld())) {
                continue;
            }
            
            // Exception 1: Blocks below beacon y-level are always allowed
            if (loc.getBlockY() < beaconLoc.getBlockY()) {
                continue;
            }
            
            // Calculate horizontal distance (ignore Y)
            double dx = loc.getBlockX() - beaconLoc.getBlockX();
            double dz = loc.getBlockZ() - beaconLoc.getBlockZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            
            // Check if within protected radius
            if (horizontalDistance <= protectionRadius) {
                // Exception 2: Stained glass directly above beacon (exactly 1 block up, same x/z)
                if (isStainedGlass(blockType) && 
                    loc.getBlockX() == beaconLoc.getBlockX() &&
                    loc.getBlockZ() == beaconLoc.getBlockZ() &&
                    loc.getBlockY() == beaconLoc.getBlockY() + 1) {
                    continue;
                }
                
                // Protected - cannot place here
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if material is stained glass
     */
    private boolean isStainedGlass(Material material) {
        return material.name().endsWith("STAINED_GLASS");
    }
    
    /**
     * Handle player respawning based on team
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        GameManager gameManager = plugin.getGameManager();
        
        if (!gameManager.isGameActive() || !gameManager.areBeaconsInitialized()) {
            return;
        }
        
        Player player = event.getPlayer();
        TeamColor team = gameManager.getPlayerTeam(player);
        
        if (team == TeamColor.NEUTRAL) {
            return; // Not on a team, use default spawn
        }
        
        Location spawn = gameManager.getSpawnManager().getSpawn(team);
        if (spawn != null) {
            event.setRespawnLocation(spawn);
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                    .append(Component.text("Respawning at team spawn", team.getChatColor())));
        }
    }
}

