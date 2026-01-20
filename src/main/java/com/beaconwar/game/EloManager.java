package com.beaconwar.game;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;

import com.beaconwar.BeaconWarPlugin;
import com.beaconwar.model.TeamColor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * ELO-like rating system for Beacon War
 * 
 * Power calculation:
 *     team_power = (sum(player_rating / (1 - resistance/5)^beta))^alpha
 * 
 * Win probability:
 *     P(red wins) = red_power / (red_power + blue_power)
 * 
 * Loss (surprisal):
 *     -log(P(winner))
 * 
 * Update via numerical gradient descent.
 */
public class EloManager {
    
    // Default parameter values
    private static final double DEFAULT_ALPHA = 2.0;
    private static final double DEFAULT_BETA = 1.0 / 1.5;  // ~0.667
    private static final double DEFAULT_RATING = 1.0;
    
    // Gradient descent parameters
    private static final double EPSILON = 0.0001;
    private static final double LEARNING_RATE = 0.1;
    
    private final BeaconWarPlugin plugin;
    private final File saveFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Parameters (stored as log values for positivity)
    private Map<String, Double> logPlayerRatings = new HashMap<>();
    private double logAlpha;
    private double logBeta;
    
    /**
     * Represents a player with their assigned resistance level.
     */
    public static class PlayerResistance {
        public final String playerName;
        public final int resistance;
        
        public PlayerResistance(String playerName, int resistance) {
            this.playerName = playerName;
            this.resistance = resistance;
        }
        
        @Override
        public String toString() {
            if (resistance > 0) {
                return playerName + ":" + resistance;
            }
            return playerName;
        }
    }
    
    /**
     * Result of team balancing operation.
     */
    public static class BalancedMatch {
        public final List<PlayerResistance> redTeam;
        public final List<PlayerResistance> blueTeam;
        public final double pRedWins;
        
        public BalancedMatch(List<PlayerResistance> redTeam, List<PlayerResistance> blueTeam, double pRedWins) {
            this.redTeam = redTeam;
            this.blueTeam = blueTeam;
            this.pRedWins = pRedWins;
        }
    }
    
    public EloManager(BeaconWarPlugin plugin) {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "elo_ratings.json");
        
        // Initialize with default values
        this.logAlpha = Math.log(DEFAULT_ALPHA);
        this.logBeta = Math.log(DEFAULT_BETA);
        
        // Load existing ratings if available
        load();
    }
    
    /**
     * Get or create log rating for a player.
     */
    private double getLogRating(String playerName) {
        if (!logPlayerRatings.containsKey(playerName)) {
            double newLogRating;
            if (logPlayerRatings.isEmpty()) {
                // First player ever: start at DEFAULT_RATING (1.0)
                newLogRating = Math.log(DEFAULT_RATING);
            } else {
                // Subsequent players: start at lowest existing rating
                newLogRating = logPlayerRatings.values().stream()
                        .min(Double::compareTo)
                        .orElse(Math.log(DEFAULT_RATING));
            }
            logPlayerRatings.put(playerName, newLogRating);
            
            Bukkit.broadcast(Component.text("[Beacon War ELO] ", NamedTextColor.GOLD)
                    .append(Component.text("New player: " + playerName + " (rating: " + 
                            (int)(Math.exp(newLogRating) * 1000) + ")", NamedTextColor.YELLOW)));
            
            save();
        }
        return logPlayerRatings.get(playerName);
    }
    
    /**
     * Get the actual rating for a player.
     */
    public double getRating(String playerName) {
        return Math.exp(getLogRating(playerName));
    }
    
    /**
     * Get current alpha value.
     */
    public double getAlpha() {
        return Math.exp(logAlpha);
    }
    
    /**
     * Get current beta value.
     */
    public double getBeta() {
        return Math.exp(logBeta);
    }
    
    /**
     * Calculate team power.
     */
    private double calculateTeamPower(List<PlayerResistance> team, 
                                       Map<String, Double> ratings, 
                                       double alpha, double beta) {
        double total = 0.0;
        for (PlayerResistance pr : team) {
            double rating = Math.exp(ratings.getOrDefault(pr.playerName, Math.log(DEFAULT_RATING)));
            // Higher resistance = higher effective rating (harder to kill = more power)
            double divisor = Math.pow(1.0 - pr.resistance / 5.0, beta);
            double effectiveRating = rating / divisor;
            total += effectiveRating;
        }
        return Math.pow(total, alpha);
    }
    
    /**
     * Calculate team power using current parameters.
     */
    private double calculateTeamPower(List<PlayerResistance> team) {
        return calculateTeamPower(team, logPlayerRatings, getAlpha(), getBeta());
    }
    
    /**
     * Calculate probability that red team wins.
     */
    public double calculateWinProbability(List<PlayerResistance> redTeam, List<PlayerResistance> blueTeam) {
        double redPower = calculateTeamPower(redTeam);
        double bluePower = calculateTeamPower(blueTeam);
        return redPower / (redPower + bluePower);
    }
    
    /**
     * Calculate loss (surprisal) for a given outcome.
     */
    private double calculateLoss(List<PlayerResistance> redTeam, List<PlayerResistance> blueTeam,
                                 boolean redWon, Map<String, Double> ratings, 
                                 double alpha, double beta) {
        double redPower = calculateTeamPower(redTeam, ratings, alpha, beta);
        double bluePower = calculateTeamPower(blueTeam, ratings, alpha, beta);
        double pRed = redPower / (redPower + bluePower);
        
        double eps = 1e-10;  // Avoid log(0)
        if (redWon) {
            return -Math.log(pRed + eps);
        } else {
            return -Math.log(1.0 - pRed + eps);
        }
    }
    
    /**
     * Update ratings based on game result using numerical gradient descent.
     * 
     * @param redTeam Red team players with resistance levels
     * @param blueTeam Blue team players with resistance levels
     * @param winner The winning team (RED or BLUE). NEUTRAL means tie (no update).
     * @return The loss value
     */
    public double updateRatings(List<PlayerResistance> redTeam, List<PlayerResistance> blueTeam, 
                                TeamColor winner) {
        if (winner == TeamColor.NEUTRAL) {
            // Tie - no rating update
            return 0.0;
        }
        
        boolean redWon = (winner == TeamColor.RED);
        
        // Calculate current loss
        double currentLoss = calculateLoss(redTeam, blueTeam, redWon, 
                logPlayerRatings, getAlpha(), getBeta());
        
        // Calculate numerical gradients and update parameters
        
        // Gradient for logAlpha
        double lossPlus = calculateLoss(redTeam, blueTeam, redWon, 
                logPlayerRatings, Math.exp(logAlpha + EPSILON), getBeta());
        double lossMinus = calculateLoss(redTeam, blueTeam, redWon, 
                logPlayerRatings, Math.exp(logAlpha - EPSILON), getBeta());
        double gradAlpha = (lossPlus - lossMinus) / (2 * EPSILON);
        logAlpha -= LEARNING_RATE * gradAlpha;
        
        // Gradient for logBeta
        lossPlus = calculateLoss(redTeam, blueTeam, redWon, 
                logPlayerRatings, getAlpha(), Math.exp(logBeta + EPSILON));
        lossMinus = calculateLoss(redTeam, blueTeam, redWon, 
                logPlayerRatings, getAlpha(), Math.exp(logBeta - EPSILON));
        double gradBeta = (lossPlus - lossMinus) / (2 * EPSILON);
        logBeta -= LEARNING_RATE * gradBeta;
        
        // Gradient for player ratings (only players in this game)
        Set<String> allPlayers = new HashSet<>();
        for (PlayerResistance pr : redTeam) allPlayers.add(pr.playerName);
        for (PlayerResistance pr : blueTeam) allPlayers.add(pr.playerName);
        
        for (String playerName : allPlayers) {
            double originalLogRating = logPlayerRatings.get(playerName);
            
            // Plus epsilon
            logPlayerRatings.put(playerName, originalLogRating + EPSILON);
            lossPlus = calculateLoss(redTeam, blueTeam, redWon, 
                    logPlayerRatings, getAlpha(), getBeta());
            
            // Minus epsilon
            logPlayerRatings.put(playerName, originalLogRating - EPSILON);
            lossMinus = calculateLoss(redTeam, blueTeam, redWon, 
                    logPlayerRatings, getAlpha(), getBeta());
            
            // Restore and update
            double gradRating = (lossPlus - lossMinus) / (2 * EPSILON);
            logPlayerRatings.put(playerName, originalLogRating - LEARNING_RATE * gradRating);
        }
        
        // Save updated ratings
        save();
        
        return currentLoss;
    }
    
    /**
     * Find the most balanced team division with resistance adjustments.
     * 
     * Algorithm:
     * 1. Find team division (0 resistance) closest to 50% win probability
     * 2. Add resistance to disfavored team's weakest players until balanced
     */
    public BalancedMatch findBalancedMatch(List<String> players) {
        int n = players.size();
        // if (n < 2) {
        //     throw new IllegalArgumentException("Need at least 2 players");
        // }
        
        // Ensure all players exist in the system
        for (String p : players) {
            getLogRating(p);
        }
        
        // Step 1: Find best team division with 0 resistance
        List<PlayerResistance> bestRedTeam = null;
        List<PlayerResistance> bestBlueTeam = null;
        double bestProbDiff = Double.MAX_VALUE;
        double bestPRed = 0.5;
        
        // Try all ways to pick half (or floor(n/2)) players for red team
        int redSize = n / 2;
        List<int[]> combinations = generateCombinations(n, redSize);
        
        for (int[] redIndices : combinations) {
            Set<Integer> redSet = new HashSet<>();
            for (int idx : redIndices) redSet.add(idx);
            
            List<PlayerResistance> redTeam = new ArrayList<>();
            List<PlayerResistance> blueTeam = new ArrayList<>();
            
            for (int i = 0; i < n; i++) {
                if (redSet.contains(i)) {
                    redTeam.add(new PlayerResistance(players.get(i), 0));
                } else {
                    blueTeam.add(new PlayerResistance(players.get(i), 0));
                }
            }
            
            double pRed = calculateWinProbability(redTeam, blueTeam);
            double probDiff = Math.abs(pRed - 0.5);
            
            if (probDiff < bestProbDiff) {
                bestProbDiff = probDiff;
                bestRedTeam = new ArrayList<>(redTeam);
                bestBlueTeam = new ArrayList<>(blueTeam);
                bestPRed = pRed;
            }
        }
        
        // If already very close to 50%, we're done
        if (Math.abs(bestPRed - 0.5) < 0.001) {
            return new BalancedMatch(bestRedTeam, bestBlueTeam, bestPRed);
        }
        
        // Step 2: Add resistance to the disfavored team
        List<PlayerResistance> disfavoredTeam;
        List<PlayerResistance> favoredTeam;
        boolean isRedDisfavored;
        
        if (bestPRed < 0.5) {
            // Red is disfavored
            disfavoredTeam = new ArrayList<>(bestRedTeam);
            favoredTeam = bestBlueTeam;
            isRedDisfavored = true;
        } else {
            // Blue is disfavored
            disfavoredTeam = new ArrayList<>(bestBlueTeam);
            favoredTeam = bestRedTeam;
            isRedDisfavored = false;
        }
        
        // Sort disfavored team by rating (weakest first)
        disfavoredTeam.sort(Comparator.comparingDouble(pr -> getRating(pr.playerName)));
        
        // Track previous state for potential backtrack
        List<PlayerResistance> prevTeam = new ArrayList<>(disfavoredTeam);
        double prevDisfavoredProb = isRedDisfavored ? bestPRed : (1 - bestPRed);
        
        // Add resistance iteratively
        int maxResistance = 4;
        int playerIdx = 0;
        double currentPRed = bestPRed;
        
        while (true) {
            PlayerResistance current = disfavoredTeam.get(playerIdx);
            
            if (current.resistance >= maxResistance) {
                // This player is maxed out, try next
                playerIdx = (playerIdx + 1) % disfavoredTeam.size();
                // Check if all players are maxed
                boolean allMaxed = disfavoredTeam.stream()
                        .allMatch(pr -> pr.resistance >= maxResistance);
                if (allMaxed) break;
                continue;
            }
            
            // Add one resistance level
            disfavoredTeam.set(playerIdx, new PlayerResistance(current.playerName, current.resistance + 1));
            
            // Calculate new probability
            double newPRed;
            double newDisfavoredProb;
            if (isRedDisfavored) {
                newPRed = calculateWinProbability(disfavoredTeam, favoredTeam);
                newDisfavoredProb = newPRed;
            } else {
                newPRed = calculateWinProbability(favoredTeam, disfavoredTeam);
                newDisfavoredProb = 1 - newPRed;
            }
            
            // Check if we've crossed 50%
            if (newDisfavoredProb >= 0.5) {
                // Check if backtracking is closer to 50%
                double currentDiff = Math.abs(newDisfavoredProb - 0.5);
                double prevDiff = Math.abs(prevDisfavoredProb - 0.5);
                
                if (prevDiff < currentDiff) {
                    // Backtrack
                    disfavoredTeam = prevTeam;
                    currentPRed = isRedDisfavored ? prevDisfavoredProb : (1 - prevDisfavoredProb);
                } else {
                    currentPRed = newPRed;
                }
                break;
            }
            
            // Save state and continue
            prevTeam = new ArrayList<>(disfavoredTeam);
            prevDisfavoredProb = newDisfavoredProb;
            
            // Move to next player (round-robin)
            playerIdx = (playerIdx + 1) % disfavoredTeam.size();
        }
        
        // Return with red team first
        if (isRedDisfavored) {
            return new BalancedMatch(disfavoredTeam, favoredTeam, currentPRed);
        } else {
            return new BalancedMatch(favoredTeam, disfavoredTeam, currentPRed);
        }
    }
    
    /**
     * Generate all combinations of choosing k elements from n.
     */
    private List<int[]> generateCombinations(int n, int k) {
        List<int[]> result = new ArrayList<>();
        generateCombinationsHelper(n, k, 0, new int[k], 0, result);
        return result;
    }
    
    private void generateCombinationsHelper(int n, int k, int start, int[] current, int idx, List<int[]> result) {
        if (idx == k) {
            result.add(current.clone());
            return;
        }
        for (int i = start; i < n; i++) {
            current[idx] = i;
            generateCombinationsHelper(n, k, i + 1, current, idx + 1, result);
        }
    }
    
    /**
     * Get sorted leaderboard of all players.
     */
    public List<Map.Entry<String, Double>> getLeaderboard() {
        return logPlayerRatings.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), Math.exp(e.getValue())))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a player exists in the rating system.
     */
    public boolean hasPlayer(String playerName) {
        return logPlayerRatings.containsKey(playerName);
    }
    
    /**
     * Save ratings to JSON file.
     */
    public void save() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("logAlpha", logAlpha);
        data.put("logBeta", logBeta);
        data.put("logPlayerRatings", logPlayerRatings);
        
        try (Writer writer = new FileWriter(saveFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ELO ratings: " + e.getMessage());
        }
    }
    
    /**
     * Load ratings from JSON file.
     */
    public void load() {
        if (!saveFile.exists()) {
            return;
        }
        
        try (Reader reader = new FileReader(saveFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(reader, type);
            
            if (data.containsKey("logAlpha")) {
                logAlpha = ((Number) data.get("logAlpha")).doubleValue();
            }
            if (data.containsKey("logBeta")) {
                logBeta = ((Number) data.get("logBeta")).doubleValue();
            }
            if (data.containsKey("logPlayerRatings")) {
                @SuppressWarnings("unchecked")
                Map<String, Number> ratings = (Map<String, Number>) data.get("logPlayerRatings");
                logPlayerRatings = new HashMap<>();
                for (Map.Entry<String, Number> entry : ratings.entrySet()) {
                    logPlayerRatings.put(entry.getKey(), entry.getValue().doubleValue());
                }
            }
            
            plugin.getLogger().info("Loaded ELO ratings for " + logPlayerRatings.size() + " players");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load ELO ratings: " + e.getMessage());
        }
    }
    
    @Override
    public String toString() {
        return String.format("EloManager(alpha=%.3f, beta=%.3f, players=%d)",
                getAlpha(), getBeta(), logPlayerRatings.size());
    }
}

