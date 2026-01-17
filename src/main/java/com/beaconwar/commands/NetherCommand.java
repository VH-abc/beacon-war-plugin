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
 * Command to teleport player to the Nether
 */
public class NetherCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }
        
        // Get the nether world
        World nether = Bukkit.getWorld("world_nether");
        if (nether == null) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Nether world not found!", NamedTextColor.YELLOW)));
            return true;
        }
        
        // Teleport to nether spawn or player's current X/Z at a safe Y
        Location currentLoc = player.getLocation();
        Location netherLoc = new Location(nether, currentLoc.getX() / 8, 80, currentLoc.getZ() / 8);
        
        player.teleport(netherLoc);
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Teleported to the Nether!", NamedTextColor.GOLD)));
        
        return true;
    }
}

