package com.beaconwar.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.beaconwar.BeaconWarPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Listener to block vanilla team commands that would bypass BeaconWar tracking.
 */
public class CommandBlockListener implements Listener {
    
    private final BeaconWarPlugin plugin;
    
    public CommandBlockListener(BeaconWarPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        
        // Block /team join Red and /team join Blue commands
        if (message.startsWith("/team join red") || message.startsWith("/team join blue")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Use /join red or /join blue instead!", NamedTextColor.YELLOW)));
        }
    }
}

