package com.beaconwar.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum TeamColor {
    NEUTRAL(0, Material.AIR, NamedTextColor.YELLOW),
    RED(1, Material.RED_STAINED_GLASS, NamedTextColor.RED),
    BLUE(2, Material.BLUE_STAINED_GLASS, NamedTextColor.BLUE);
    
    private final int id;
    private final Material glassMaterial;
    private final NamedTextColor chatColor;
    
    TeamColor(int id, Material glassMaterial, NamedTextColor chatColor) {
        this.id = id;
        this.glassMaterial = glassMaterial;
        this.chatColor = chatColor;
    }
    
    public int getId() {
        return id;
    }
    
    public Material getGlassMaterial() {
        return glassMaterial;
    }
    
    public NamedTextColor getChatColor() {
        return chatColor;
    }
    
    public String getDisplayName() {
        return switch (this) {
            case RED -> "Red Team";
            case BLUE -> "Blue Team";
            case NEUTRAL -> "Neutral";
        };
    }
}

