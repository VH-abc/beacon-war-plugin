package com.beaconwar.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.beaconwar.model.Beacon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles beacon placement with water avoidance.
 * No more recursive function calls that make your head hurt!
 */
public class BeaconPlacer {
    
    private static final int MAX_FALLBACK_ATTEMPTS = 8;
    
    private final Player player;
    private final BeaconManager beaconManager;
    private final int spacing;
    private final int groundSearchStartY;
    private final int beaconsPerSide;
    private final boolean spawnCastles;
    private final boolean spawnCastleGates;
    
    public BeaconPlacer(Player player, BeaconManager beaconManager, int spacing, int beaconsPerSide, int groundSearchStartY, boolean spawnCastles, boolean spawnCastleGates) {
        this.player = player;
        this.beaconManager = beaconManager;
        this.spacing = spacing;
        this.beaconsPerSide = beaconsPerSide;
        this.groundSearchStartY = groundSearchStartY;
        this.spawnCastles = spawnCastles;
        this.spawnCastleGates = spawnCastleGates;
    }
    
    /**
     * Place all 11 beacons in a line
     */
    public boolean placeAllBeacons() {
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.GRAY)
                .append(Component.text("Starting beacon placement...", NamedTextColor.YELLOW)));
        
        Location spawnPoint = player.getLocation();
        World world = spawnPoint.getWorld();
        
        // Place beacon 0 at spawn
        Location beacon0Loc = findGround(world, spawnPoint.getBlockX(), groundSearchStartY, spawnPoint.getBlockZ());
        if (beacon0Loc == null) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Failed to find ground for beacon 0!", NamedTextColor.YELLOW)));
            return false;
        }
        
        // Beacon 0 location is ground block, place beacon one above
        Location beacon0BeaconLoc = beacon0Loc.clone().add(0, 1, 0);
        buildBeacon(beacon0BeaconLoc, 0);
        Beacon beacon0 = new Beacon(0, beacon0BeaconLoc);
        beacon0.initializeOwnerFromGlass(); // Initialize ownership from the glass we just placed
        beaconManager.addBeacon(beacon0);
        
        // Place positive side (1 to beaconsPerSide)
        Location prevLoc = beacon0Loc; // Keep track of ground location for next beacon
        for (int i = 1; i <= beaconsPerSide; i++) {
            Location loc = placeNextBeacon(prevLoc, i, true);
            if (loc == null) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Failed to place beacon " + i, NamedTextColor.YELLOW)));
                return false;
            }
            prevLoc = loc;
        }
        
        // Place negative side (-1 to -beaconsPerSide)
        prevLoc = beacon0Loc;
        for (int i = -1; i >= -beaconsPerSide; i--) {
            Location loc = placeNextBeacon(prevLoc, i, false);
            if (loc == null) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Failed to place beacon " + i, NamedTextColor.YELLOW)));
                return false;
            }
            prevLoc = loc;
        }
        
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Beacon line deployed with water avoidance!", NamedTextColor.GREEN)));
        
        return true;
    }
    
    /**
     * Place next beacon relative to previous, avoiding water
     */
    private Location placeNextBeacon(Location prevLoc, int index, boolean positive) {
        World world = prevLoc.getWorld();
        int prevX = prevLoc.getBlockX();
        int prevZ = prevLoc.getBlockZ();
        int direction = positive ? 1 : -1;
        
        // List of (xMultiplier, zMultiplier, requireDry, message)
        record Attempt(double xMultiplier, double zMultiplier, boolean requireDry, String message) {}
        
        java.util.List<Attempt> attemptsList = new java.util.ArrayList<>();
        
        // Initial attempts
        attemptsList.add(new Attempt(1, 0, true, null));
        attemptsList.add(new Attempt(2, 0, true, "Water detected, trying 2x spacing for beacon " + index));
        attemptsList.add(new Attempt(1, 1, true, "Water detected, trying Z+ offset for beacon " + index));
        attemptsList.add(new Attempt(1, -1, true, "Water detected, trying Z- offset for beacon " + index));
        
        // Extended search: (1.1, 0), (1.2, 0), ..., (2.9, 0)
        for (double x = 1.1; x <= 2.9; x += 0.1) {
            attemptsList.add(new Attempt(x, 0, true, null));
        }
        
        // Extended search: (1, -0.8), (1, -0.6), ..., (1, 0.8)
        for (double z = -0.8; z <= 0.8; z += 0.2) {
            attemptsList.add(new Attempt(1, z, true, null));
        }
        
        // Extended search: (1.5, -1.5), (1.5, -1.3), ..., (1.5, 1.5)
        for (double z = -1.5; z <= 1.5; z += 0.2) {
            attemptsList.add(new Attempt(1.5, z, true, null));
        }
        
        // Extended search: (2, -2), (2, -1.8), ..., (2, 2)
        for (double z = -2.0; z <= 2.0; z += 0.2) {
            attemptsList.add(new Attempt(2, z, true, null));
        }
        
        // Last resort - place regardless of water
        attemptsList.add(new Attempt(3, 0, false, "All positions have water! Placing beacon " + index + " at 3x spacing"));
        
        for (Attempt attempt : attemptsList) {
            int tryX = (int)(prevX + (attempt.xMultiplier * spacing * direction));
            int tryZ = (int)(prevZ + (attempt.zMultiplier * spacing));
            Location groundLoc = findGround(world, tryX, groundSearchStartY, tryZ);
            
            if (groundLoc != null && (!attempt.requireDry || !isWater(groundLoc))) {
                if (attempt.message != null) {
                    NamedTextColor msgColor = attempt.requireDry ? NamedTextColor.GRAY : NamedTextColor.RED;
                    player.sendMessage(Component.text("[Beacon War] ", msgColor)
                            .append(Component.text(attempt.message, NamedTextColor.YELLOW)));
                }
                return placeBeaconAt(groundLoc, index);
            }
        }
        
        return null;
    }
    
    /**
     * Place beacon at the given ground location and register it
     * Returns the ground location for the next beacon to use as reference
     */
    private Location placeBeaconAt(Location groundLoc, int index) {
        // Ground location is the solid block itself, beacon goes one block above
        Location beaconLoc = groundLoc.clone().add(0, 1, 0);
        buildBeacon(beaconLoc, index);
        Beacon beacon = new Beacon(index, beaconLoc);
        beacon.initializeOwnerFromGlass();
        beaconManager.addBeacon(beacon);
        return groundLoc; // Return ground location for next beacon
    }
    
    /**
     * Find solid ground below a position
     * Returns the location of the ground block itself (not the block above)
     */
    public static Location findGround(World world, int x, int startY, int z) {
        for (int y = startY; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            
            // Valid ground = any block except air, leaves, and wood
            if (!type.isAir() && 
                !type.name().contains("LEAVES") && 
                !type.name().contains("LOG") &&
                !type.name().contains("WOOD")) {
                // Return the ground block itself
                return new Location(world, x, y, z);
            }
        }
        return null;
    }
    
    /**
     * Check if the ground block is water
     */
    private boolean isWater(Location groundLoc) {
        Block block = groundLoc.getBlock();
        return block.getType() == Material.WATER;
    }
    
    /**
     * Build beacon structure at location
     * loc is the location WHERE THE BEACON BLOCK GOES (one block above ground)
     */
    private void buildBeacon(Location loc, int index) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Create 3x3 emerald block base (one block below beacon)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.EMERALD_BLOCK);
            }
        }
        
        // Place beacon at this location
        world.getBlockAt(x, y, z).setType(Material.BEACON);
        
        // Clear column above for beam
        for (int dy = 1; dy < 320 - y; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.AIR);
        }
        
        // Place colored glass based on index
        Material glassType = Material.AIR;
        if (index > 0) {
            glassType = Material.RED_STAINED_GLASS;
        } else if (index < 0) {
            glassType = Material.BLUE_STAINED_GLASS;
        }
        world.getBlockAt(x, y + 1, z).setType(glassType);
        
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.GRAY)
                .append(Component.text("Placed beacon " + index + " at (" + x + ", " + z + ")", 
                        NamedTextColor.GREEN)));
        
        // Build castle around beacon if enabled
        if (spawnCastles) {
            CastleBuilder castle = new CastleBuilder(loc, spawnCastleGates);
            castle.build();
        }
    }
}

