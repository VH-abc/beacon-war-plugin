package com.beaconwar.model;

import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Represents a single beacon in the game.
 * No more cursed scoreboards as variables!
 */
public class Beacon {
    
    private final int index;
    private final Location location;
    private TeamColor owner;
    private TeamColor previousOwner;
    
    public Beacon(int index, Location location) {
        this.index = index;
        this.location = location;
        this.owner = TeamColor.NEUTRAL;
        this.previousOwner = TeamColor.NEUTRAL;
    }
    
    public int getIndex() {
        return index;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public TeamColor getOwner() {
        return owner;
    }
    
    public TeamColor getPreviousOwner() {
        return previousOwner;
    }
    
    public void setOwner(TeamColor newOwner) {
        this.previousOwner = this.owner;
        this.owner = newOwner;
    }

    public void setPreviousOwner(TeamColor owner) {
        this.previousOwner = owner;
    }
    
    /**
     * Initialize owner without marking it as a change (for initial setup)
     */
    public void initializeOwner(TeamColor initialOwner) {
        this.owner = initialOwner;
        this.previousOwner = initialOwner; // Mark as unchanged
    }
    
    /**
     * Check the glass block above the beacon to determine current owner
     */
    public void updateOwnerFromGlass() {
        Location glassLocation = location.clone().add(0, 1, 0);
        Material material = glassLocation.getBlock().getType();
        
        TeamColor newOwner = switch (material) {
            case RED_STAINED_GLASS -> TeamColor.RED;
            case BLUE_STAINED_GLASS -> TeamColor.BLUE;
            default -> TeamColor.NEUTRAL;
        };
        
        if (newOwner != owner) {
            setOwner(newOwner);
        }
    }
    
    /**
     * Initialize ownership from glass block (for initial setup)
     */
    public void initializeOwnerFromGlass() {
        Location glassLocation = location.clone().add(0, 1, 0);
        Material material = glassLocation.getBlock().getType();
        
        TeamColor initialOwner = switch (material) {
            case RED_STAINED_GLASS -> TeamColor.RED;
            case BLUE_STAINED_GLASS -> TeamColor.BLUE;
            default -> TeamColor.NEUTRAL;
        };
        
        initializeOwner(initialOwner);
    }
    
    /**
     * Revert the glass block to match the previous owner
     */
    public void revertGlass() {
        owner = previousOwner;
        Location glassLocation = location.clone().add(0, 1, 0);
        glassLocation.getBlock().setType(previousOwner.getGlassMaterial());
    }
    
    /**
     * Set the glass block to match current owner
     */
    public void updateGlass() {
        Location glassLocation = location.clone().add(0, 1, 0);
        glassLocation.getBlock().setType(owner.getGlassMaterial());
    }
    
    public boolean hasOwnerChanged() {
        return owner != previousOwner;
    }
}

