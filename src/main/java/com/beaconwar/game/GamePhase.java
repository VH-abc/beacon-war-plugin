package com.beaconwar.game;

import net.kyori.adventure.text.format.NamedTextColor;

public enum GamePhase {
    MINING("Mining Period", NamedTextColor.GOLD),
    CAPTURING("Capturing Period", NamedTextColor.GREEN);
    
    private final String displayName;
    private final NamedTextColor color;
    
    GamePhase(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public NamedTextColor getColor() {
        return color;
    }
    
    public GamePhase next() {
        return this == MINING ? CAPTURING : MINING;
    }
}

