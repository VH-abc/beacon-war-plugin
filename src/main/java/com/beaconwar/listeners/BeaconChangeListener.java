package com.beaconwar.listeners;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.game.GameManager;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
     * Prevent breaking of beacon blocks and their emerald bases
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtectedBeaconBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Beacon structures are indestructible!", NamedTextColor.YELLOW)));
        }
    }
    
    /**
     * Protect beacon structures from entity explosions (creepers, TNT minecarts, withers, etc.)
     */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtectedBeaconBlock(block.getLocation()));
    }
    
    /**
     * Protect beacon structures from block explosions (TNT, beds in nether/end, respawn anchors)
     */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtectedBeaconBlock(block.getLocation()));
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
     * Check if a block location is part of a protected beacon structure.
     * Protected blocks are:
     * - The beacon block itself
     * - The 3x3 emerald block base (one block below beacon, within ±1 x/z)
     */
    private boolean isProtectedBeaconBlock(Location loc) {
        GameManager gameManager = plugin.getGameManager();
        
        if (!gameManager.areBeaconsInitialized()) {
            return false;
        }
        
        int blockX = loc.getBlockX();
        int blockY = loc.getBlockY();
        int blockZ = loc.getBlockZ();
        
        for (Beacon beacon : gameManager.getBeaconManager().getAllBeacons()) {
            Location beaconLoc = beacon.getLocation();
            
            // Check if in same world
            if (!loc.getWorld().equals(beaconLoc.getWorld())) {
                continue;
            }
            
            int beaconX = beaconLoc.getBlockX();
            int beaconY = beaconLoc.getBlockY();
            int beaconZ = beaconLoc.getBlockZ();
            
            // Check if this is the beacon block itself
            if (blockX == beaconX && blockY == beaconY && blockZ == beaconZ) {
                return true;
            }
            
            // Check if this is part of the 3x3 emerald base (one block below beacon)
            if (blockY == beaconY - 1 &&
                blockX >= beaconX - 1 && blockX <= beaconX + 1 &&
                blockZ >= beaconZ - 1 && blockZ <= beaconZ + 1) {
                return true;
            }
        }
        
        return false;
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

