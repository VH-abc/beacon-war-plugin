package com.beaconwar.listeners;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.model.TeamColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player connections and disconnections during a game.
 * Ensures team assignments and resistance levels persist across disconnects.
 */
public class PlayerConnectionListener implements Listener {
    
    private final BeaconWarPlugin plugin;
    
    public PlayerConnectionListener(BeaconWarPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // If game is active and player has a team assignment, restore their state
        if (plugin.getGameManager().isGameActive()) {
            TeamColor team = plugin.getGameManager().getPlayerTeamAssignment(event.getPlayer().getName());
            if (team != TeamColor.NEUTRAL) {
                // Restore player to their team
                plugin.getGameManager().restorePlayerState(event.getPlayer());
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // No action needed - team assignments persist in GameManager maps
        // Player will be restored when they rejoin
    }
}

