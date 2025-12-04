package com.beaconwar.game;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.beaconwar.model.Beacon;
import com.beaconwar.model.TeamColor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Manages all beacons in the game.
 * Look ma, no more execute as @e[type=marker] chains!
 */
public class BeaconManager {
    
    private final Map<Integer, Beacon> beacons = new HashMap<>();
    private final List<Player> allPlayers;
    private GamePhase currentPhase = GamePhase.CAPTURING;
    
    public BeaconManager(List<Player> allPlayers) {
        this.allPlayers = allPlayers;
    }
    
    public void setCurrentPhase(GamePhase phase) {
        this.currentPhase = phase;
    }
    
    public void addBeacon(Beacon beacon) {
        beacons.put(beacon.getIndex(), beacon);
    }
    
    public Beacon getBeacon(int index) {
        return beacons.get(index);
    }
    
    public Collection<Beacon> getAllBeacons() {
        return beacons.values();
    }
    
    public void clear() {
        beacons.clear();
    }
    
    /**
     * Update all beacon ownership based on glass blocks
     */
    public void updateAllBeaconOwnership() {
        for (Beacon beacon : beacons.values()) {
            beacon.updateOwnerFromGlass();
            
            // If ownership changed, validate it
            if (beacon.hasOwnerChanged()) {
                validateCapture(beacon);
            }
        }
    }
    
    /**
     * Validate that a beacon capture is legal.
     * Rules:
     * - Cannot capture during mining period
     * - Can only capture if an adjacent beacon is owned by your team
     * This replaces all that cursed scoreboard math!
     */
    private void validateCapture(Beacon beacon) {
        TeamColor newOwner = beacon.getOwner();
        TeamColor previousOwner = beacon.getPreviousOwner();
        
        // Reject any capture during mining period
        if (currentPhase == GamePhase.MINING) {
            revertCapture(beacon, "Cannot capture beacons during Mining Period!");
            return;
        }
        
        int index = beacon.getIndex();
        Beacon leftBeacon = beacons.get(index - 1);
        Beacon rightBeacon = beacons.get(index + 1);
        
        boolean valid = true;

        // Neutral captures: need to have red on right and blue on left
        // Other captures are always allowed
        if (newOwner == TeamColor.NEUTRAL) {
            if (leftBeacon != null && leftBeacon.getOwner() != TeamColor.BLUE) {
                valid = false;
            }
            if (rightBeacon != null && rightBeacon.getOwner() != TeamColor.RED) {
                valid = false;
            }
        }
        
        if (valid) {
            announceCapture(beacon);
            //set previousOwner to current owner
            beacon.setPreviousOwner(newOwner);
        } else {
            revertCapture(beacon, "Cannot capture beacon " + beacon.getIndex() + ": invalid capture!");
        }
    }
    
    private void announceCapture(Beacon beacon) {
        Component message = Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text(beacon.getOwner().getDisplayName() + " captured beacon ", 
                        beacon.getOwner().getChatColor()))
                .append(Component.text(String.valueOf(beacon.getIndex()), 
                        beacon.getOwner().getChatColor()))
                .append(Component.text("!", beacon.getOwner().getChatColor()));
        
        allPlayers.forEach(p -> p.sendMessage(message));
    }
    
    private void revertCapture(Beacon beacon, String reason) {
        beacon.revertGlass();
        
        Component message = Component.text("[Beacon War] ", NamedTextColor.RED)
                .append(Component.text(reason, NamedTextColor.YELLOW));
        
        // Notify nearby players
        Location loc = beacon.getLocation();
        allPlayers.stream()
                .filter(p -> p.getWorld().equals(loc.getWorld()))
                .filter(p -> p.getLocation().distance(loc) <= 20)
                .forEach(p -> p.sendMessage(message));
    }
    
    /**
     * Count how many beacons each team controls
     */
    public Map<TeamColor, Integer> getTeamCounts() {
        Map<TeamColor, Integer> counts = new HashMap<>();
        counts.put(TeamColor.RED, 0);
        counts.put(TeamColor.BLUE, 0);
        counts.put(TeamColor.NEUTRAL, 0);
        
        for (Beacon beacon : beacons.values()) {
            TeamColor owner = beacon.getOwner();
            counts.put(owner, counts.get(owner) + 1);
        }
        
        return counts;
    }
    
    /**
     * Get sorted list of beacons by index
     */
    public List<Beacon> getSortedBeacons() {
        return beacons.values().stream()
                .sorted(Comparator.comparingInt(Beacon::getIndex))
                .collect(Collectors.toList());
    }
    
    /**
     * Get the frontmost beacon for a team (closest to enemy territory)
     * - For Red: highest index Red beacon (furthest right, closest to Blue)
     * - For Blue: lowest index Blue beacon (furthest left, closest to Red)
     * Returns null if team has no beacons
     */
    public Beacon getFrontmostBeacon(TeamColor team) {
        if (team == TeamColor.NEUTRAL) {
            return null;
        }
        
        return beacons.values().stream()
                .filter(b -> b.getOwner() == team)
                .max(team == TeamColor.RED 
                        ? Comparator.comparingInt(Beacon::getIndex).reversed()  // Red: LOWEST index
                        : Comparator.comparingInt(Beacon::getIndex))  // Blue: HIGHEST index
                .orElse(null);
    }
    
    /**
     * Get any neutral beacon (prioritizes middle beacons)
     * Returns null if no neutral beacons exist
     */
    public Beacon getAnyNeutralBeacon() {
        return beacons.values().stream()
                .filter(b -> b.getOwner() == TeamColor.NEUTRAL)
                .min(Comparator.comparingInt(b -> Math.abs(b.getIndex())))  // Prefer beacons near index 0
                .orElse(null);
    }
}

