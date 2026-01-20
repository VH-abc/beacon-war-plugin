package com.beaconwar.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Command to teleport player to the Overworld
 */
public class OverworldCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        // Get the overworld
        World overworld = Bukkit.getWorld("world");
        if (overworld == null) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Overworld not found!", NamedTextColor.YELLOW)));
            return true;
        }
        
        // Teleport to overworld - scale coordinates back up (reverse of nether scaling)
        Location currentLoc = player.getLocation();
        Location overworldLoc = new Location(overworld, currentLoc.getX() * 8, 80, currentLoc.getZ() * 8);
        
        player.teleport(overworldLoc);
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.GREEN)
                .append(Component.text("Teleported to the Overworld!", NamedTextColor.GOLD)));
        
        return true;
    }
}

