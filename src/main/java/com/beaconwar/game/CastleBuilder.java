package com.beaconwar.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;

/**
 * Builds castle structures around beacons with biome-appropriate materials
 */
public class CastleBuilder {
    
    private static final int WALL_RADIUS = 7;
    private static final int WALL_HEIGHT = 4;
    private static final int TOWER_HEIGHT = 6;
    private static final int GATE_WIDTH = 3;
    
    private final Location beaconLocation;
    private final World world;
    private final Material wallMaterial;
    private final Material detailMaterial;
    private final boolean spawnGates;
    
    public CastleBuilder(Location beaconLocation, boolean spawnGates) {
        this.beaconLocation = beaconLocation;
        this.world = beaconLocation.getWorld();
        this.spawnGates = spawnGates;
        
        // Detect biome and select materials
        Biome biome = world.getBiome(beaconLocation);
        MaterialSet materials = getMaterialsForBiome(biome);
        this.wallMaterial = materials.wall;
        this.detailMaterial = materials.detail;
    }
    
    /**
     * Build the complete castle structure
     */
    public void build() {
        int centerX = beaconLocation.getBlockX();
        int centerY = beaconLocation.getBlockY();
        int centerZ = beaconLocation.getBlockZ();
        
        // Build walls in a square pattern
        buildWalls(centerX, centerY, centerZ);
        
        // Build corner towers
        buildTower(centerX + WALL_RADIUS, centerY, centerZ + WALL_RADIUS); // NE
        buildTower(centerX + WALL_RADIUS, centerY, centerZ - WALL_RADIUS); // SE
        buildTower(centerX - WALL_RADIUS, centerY, centerZ + WALL_RADIUS); // NW
        buildTower(centerX - WALL_RADIUS, centerY, centerZ - WALL_RADIUS); // SW
        
        // Create gate entrances on north and south sides for symmetry (if enabled)
        if (spawnGates) {
            createGate(centerX, centerY, centerZ + WALL_RADIUS); // North gate
            createGate(centerX, centerY, centerZ - WALL_RADIUS); // South gate
        }
    }
    
    private void buildWalls(int centerX, int centerY, int centerZ) {
        // Build four walls in a square
        
        // North wall (positive Z)
        for (int x = -WALL_RADIUS; x <= WALL_RADIUS; x++) {
            buildWallSegment(centerX + x, centerY, centerZ + WALL_RADIUS);
        }
        
        // South wall (negative Z)
        for (int x = -WALL_RADIUS; x <= WALL_RADIUS; x++) {
            buildWallSegment(centerX + x, centerY, centerZ - WALL_RADIUS);
        }
        
        // East wall (positive X)
        for (int z = -WALL_RADIUS; z <= WALL_RADIUS; z++) {
            buildWallSegment(centerX + WALL_RADIUS, centerY, centerZ + z);
        }
        
        // West wall (negative X)
        for (int z = -WALL_RADIUS; z <= WALL_RADIUS; z++) {
            buildWallSegment(centerX - WALL_RADIUS, centerY, centerZ + z);
        }
    }
    
    private void buildWallSegment(int x, int baseY, int z) {
        // Find actual ground level at this position
        int groundY = findGroundLevel(x, baseY, z);
        
        // Build wall from ground level up to WALL_HEIGHT above base
        for (int y = groundY; y <= baseY + WALL_HEIGHT; y++) {
            world.getBlockAt(x, y, z).setType(wallMaterial);
        }
        
        // Add crenellation (alternating pattern on top)
        int crenelPattern = (x + z) % 2;
        if (crenelPattern == 0) {
            world.getBlockAt(x, baseY + WALL_HEIGHT, z).setType(wallMaterial);
        }
    }
    
    private void buildTower(int x, int baseY, int z) {
        // Build 2x2 tower base extending down to ground
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                // Find ground level for this tower block
                int groundY = findGroundLevel(x + dx, baseY, z + dz);
                
                // Tower foundation (from ground to base)
                for (int y = groundY; y < baseY; y++) {
                    world.getBlockAt(x + dx, y, z + dz).setType(wallMaterial);
                }
                
                // Tower body
                for (int y = baseY; y < baseY + TOWER_HEIGHT; y++) {
                    Material material = (y == baseY || y == baseY + TOWER_HEIGHT - 1) ? detailMaterial : wallMaterial;
                    world.getBlockAt(x + dx, y, z + dz).setType(material);
                }
                
                // Battlements on top (corners only)
                if ((dx == 0 && dz == 0) || (dx == 1 && dz == 1)) {
                    world.getBlockAt(x + dx, baseY + TOWER_HEIGHT, z + dz).setType(wallMaterial);
                }
            }
        }
    }
    
    private void createGate(int centerX, int baseY, int gateZ) {
        // Create a 3-block wide opening in the wall, clearing from ground to top
        int halfWidth = GATE_WIDTH / 2;
        for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
            // Find ground level for this gate column
            int groundY = findGroundLevel(x, baseY, gateZ);
            
            // Clear from ground to near top of wall
            for (int y = groundY; y < baseY + WALL_HEIGHT - 1; y++) {
                world.getBlockAt(x, y, gateZ).setType(Material.AIR);
            }
        }
        
        // Add gate arch detail at top
        world.getBlockAt(centerX, baseY + WALL_HEIGHT - 1, gateZ).setType(detailMaterial);
    }
    
    /**
     * Find the ground level at a specific position by searching downward
     */
    private int findGroundLevel(int x, int startY, int z) {
        // Search downward from startY to find first solid block
        for (int y = startY - 1; y > world.getMinHeight(); y--) {
            Material type = world.getBlockAt(x, y, z).getType();
            
            // Found solid ground (not air, water, lava, or leaves)
            if (!type.isAir() && 
                type != Material.WATER && 
                type != Material.LAVA &&
                !type.name().contains("LEAVES")) {
                return y + 1; // Return position above the solid block
            }
        }
        
        // Fallback if no ground found (shouldn't happen)
        return startY - 10;
    }
    
    /**
     * Select materials based on biome
     * Wall material is the predominant biome-specific material
     * Detail material is used for accents (tower corners, gate arches)
     */
    private MaterialSet getMaterialsForBiome(Biome biome) {
        String biomeName = biome.name().toLowerCase();
        
        // Desert biomes - sandstone dominant
        if (biomeName.contains("desert")) {
            return new MaterialSet(Material.SANDSTONE, Material.SMOOTH_SANDSTONE);
        }
        
        // Snow/Ice biomes - packed ice dominant
        if (biomeName.contains("snow") || biomeName.contains("ice") || biomeName.contains("frozen")) {
            return new MaterialSet(Material.PACKED_ICE, Material.SPRUCE_PLANKS);
        }
        
        // Mountain/Hill biomes - stone/cobblestone dominant
        if (biomeName.contains("mountain") || biomeName.contains("hill") || biomeName.contains("peak") || 
            biomeName.contains("stony")) {
            return new MaterialSet(Material.STONE, Material.COBBLESTONE);
        }
        
        // Mangrove Swamp - mangrove wood dominant
        if (biomeName.contains("mangrove")) {
            return new MaterialSet(Material.MANGROVE_PLANKS, Material.MUD_BRICKS);
        }
        
        // Regular Swamp - mossy cobblestone with dark oak
        if (biomeName.contains("swamp")) {
            return new MaterialSet(Material.MOSSY_COBBLESTONE, Material.DARK_OAK_PLANKS);
        }
        
        // Dark Forest - dark oak dominant
        if (biomeName.contains("dark_forest") || biomeName.contains("dark_oak")) {
            return new MaterialSet(Material.DARK_OAK_PLANKS, Material.COBBLESTONE);
        }
        
        // Birch Forest - birch wood dominant
        if (biomeName.contains("birch")) {
            return new MaterialSet(Material.BIRCH_PLANKS, Material.COBBLESTONE);
        }
        
        // Taiga (spruce) - spruce wood dominant
        if (biomeName.contains("taiga")) {
            return new MaterialSet(Material.SPRUCE_PLANKS, Material.COBBLESTONE);
        }
        
        // Cherry Grove - cherry wood dominant
        if (biomeName.contains("cherry")) {
            return new MaterialSet(Material.CHERRY_PLANKS, Material.STONE_BRICKS);
        }
        
        // Jungle - jungle wood dominant
        if (biomeName.contains("jungle")) {
            return new MaterialSet(Material.JUNGLE_PLANKS, Material.MOSSY_COBBLESTONE);
        }
        
        // Savanna - acacia wood dominant
        if (biomeName.contains("savanna")) {
            return new MaterialSet(Material.ACACIA_PLANKS, Material.COBBLESTONE);
        }
        
        // Regular Forest - oak wood dominant
        if (biomeName.contains("forest")) {
            return new MaterialSet(Material.OAK_PLANKS, Material.COBBLESTONE);
        }
        
        // Plains - oak wood with stone bricks
        if (biomeName.contains("plains")) {
            return new MaterialSet(Material.OAK_PLANKS, Material.STONE_BRICKS);
        }
        
        // Badlands/Mesa - terracotta dominant
        if (biomeName.contains("badlands") || biomeName.contains("mesa")) {
            return new MaterialSet(Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA);
        }
        
        // === NETHER BIOMES ===
        
        // Nether Wastes - classic nether fortress style
        if (biomeName.contains("nether_wastes")) {
            return new MaterialSet(Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS);
        }
        
        // Crimson Forest - crimson fungal wood
        if (biomeName.contains("crimson")) {
            return new MaterialSet(Material.CRIMSON_PLANKS, Material.NETHER_WART_BLOCK);
        }
        
        // Warped Forest - warped fungal wood
        if (biomeName.contains("warped")) {
            return new MaterialSet(Material.WARPED_PLANKS, Material.WARPED_WART_BLOCK);
        }
        
        // Basalt Deltas - volcanic basalt and blackstone
        if (biomeName.contains("basalt")) {
            return new MaterialSet(Material.POLISHED_BASALT, Material.BLACKSTONE);
        }
        
        // Soul Sand Valley - eerie blackstone with soul sand
        if (biomeName.contains("soul")) {
            return new MaterialSet(Material.POLISHED_BLACKSTONE_BRICKS, Material.SOUL_SAND);
        }
        
        // Default fallback - stone bricks
        return new MaterialSet(Material.STONE_BRICKS, Material.COBBLESTONE);
    }
    
    /**
     * Simple material pair for castle construction
     */
    private static class MaterialSet {
        final Material wall;
        final Material detail;
        
        MaterialSet(Material wall, Material detail) {
            this.wall = wall;
            this.detail = detail;
        }
    }
}

