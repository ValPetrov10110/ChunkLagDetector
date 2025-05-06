package com.justvanilla.chunklagdetector;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ChunkLagDetector extends JavaPlugin implements Listener {

    private FileConfiguration config;
    private String webhookUrl;
    private int scanInterval;
    private int reportThreshold;
    private boolean debugMode;
    private Map<ChunkCoord, Long> chunkProcessingTimes = new ConcurrentHashMap<>();
    private List<ChunkCoord> heavyChunks = new ArrayList<>();
    private boolean isScanning = false;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig();
        
        // Load configuration
        loadConfiguration();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Schedule the chunk scanner
        scheduleChunkScanner();
        
        getLogger().info("ChunkLagDetector has been enabled!");
        getLogger().info("Scan interval: " + scanInterval + " minutes");
        getLogger().info("Report threshold: " + reportThreshold + " ms");
        getLogger().info("Debug mode: " + debugMode);
        
        // Register commands
        this.getCommand("chunklag").setExecutor(new ChunkLagCommands(this));
    }

    @Override
    public void onDisable() {
        getLogger().info("ChunkLagDetector has been disabled!");
    }
    
    private void loadConfiguration() {
        webhookUrl = config.getString("discord.webhook-url", "");
        scanInterval = config.getInt("settings.scan-interval-minutes", 15);
        reportThreshold = config.getInt("settings.report-threshold-ms", 50);
        debugMode = config.getBoolean("settings.debug-mode", false);
        
        // Validate configuration
        if (webhookUrl.isEmpty()) {
            getLogger().warning("Discord webhook URL is not configured! Please set it in the config.yml");
        }
        
        if (scanInterval < 1) {
            getLogger().warning("Scan interval must be at least 1 minute. Setting to default (15 minutes).");
            scanInterval = 15;
        }
        
        if (reportThreshold < 10) {
            getLogger().warning("Report threshold must be at least 10ms. Setting to default (50ms).");
            reportThreshold = 50;
        }
    }
    
    private void scheduleChunkScanner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isScanning) {
                    scanChunks();
                }
            }
        }.runTaskTimer(this, 20L * 60L, 20L * 60L * scanInterval);
    }
    
    public void scanChunks() {
        isScanning = true;
        heavyChunks.clear();
        chunkProcessingTimes.clear();
        
        if (debugMode) {
            getLogger().info("Starting chunk scan...");
        }
        
        // Process each world
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                analyzeChunk(chunk);
            }
        }
        
        // Schedule the reporting task to run after all chunks have been analyzed
        new BukkitRunnable() {
            @Override
            public void run() {
                reportHeavyChunks();
                isScanning = false;
                
                if (debugMode) {
                    getLogger().info("Chunk scan completed.");
                }
            }
        }.runTaskLaterAsynchronously(this, 60L);
    }
    
    private void analyzeChunk(Chunk chunk) {
        ChunkCoord coord = new ChunkCoord(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        
        // Measure chunk processing time (this is a simplified measurement)
        long startTime = System.nanoTime();
        
        // Simulate chunk analysis by checking entities and tile entities
        int entityCount = chunk.getEntities().length;
        int tileEntityCount = chunk.getTileEntities().length;
        
        // Perform some simple analysis (you could expand this)
        int totalElements = entityCount + tileEntityCount;
        
        // Delay proportional to the number of entities/tile entities (simulating processing time)
        try {
            Thread.sleep(1 + (totalElements / 50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.nanoTime();
        long processingTime = (endTime - startTime) / 1_000_000; // Convert to milliseconds
        
        chunkProcessingTimes.put(coord, processingTime);
        
        if (processingTime > reportThreshold) {
            coord.setEntities(entityCount);
            coord.setTileEntities(tileEntityCount);
            coord.setProcessingTime(processingTime);
            heavyChunks.add(coord);
            
            if (debugMode) {
                getLogger().info("Heavy chunk detected: " + coord + " - Processing time: " + processingTime + "ms");
            }
        }
    }
    
    private void reportHeavyChunks() {
        if (heavyChunks.isEmpty()) {
            if (debugMode) {
                getLogger().info("No heavy chunks found during this scan.");
            }
            return;
        }
        
        // Sort heavy chunks by processing time (descending)
        heavyChunks.sort(Comparator.comparingLong(ChunkCoord::getProcessingTime).reversed());
        
        // Prepare Discord webhook message
        String message = prepareDiscordMessage();
        
        // Send to Discord
        if (!webhookUrl.isEmpty()) {
            sendDiscordWebhook(message);
        } else {
            getLogger().warning("Discord webhook URL is not configured! Cannot send report.");
            getLogger().info(message);
        }
    }
    
    private String prepareDiscordMessage() {
        int chunkCount = Math.min(heavyChunks.size(), 10); // Report up to 10 heaviest chunks
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());
        
        StringBuilder embedDescription = new StringBuilder();
        embedDescription.append("**Detected ").append(heavyChunks.size()).append(" heavy chunks above threshold (")
                        .append(reportThreshold).append("ms)**\n\n");
        
        for (int i = 0; i < chunkCount; i++) {
            ChunkCoord chunk = heavyChunks.get(i);
            embedDescription.append("**#").append(i + 1).append(":** World: `").append(chunk.getWorld())
                           .append("`, X: `").append(chunk.getX()).append("`, Z: `").append(chunk.getZ())
                           .append("` - Processing: **").append(chunk.getProcessingTime()).append("ms**\n")
                           .append("Entities: ").append(chunk.getEntities())
                           .append(", Tile Entities: ").append(chunk.getTileEntities()).append("\n\n");
        }
        
        // Create the JSON payload for Discord webhook
        String json = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"Chunk Lag Report\"," 
                    + "\"description\": \"" + embedDescription.toString().replace("\"", "\\\"").replace("\n", "\\n") + "\","
                    + "\"color\": " + new Color(255, 0, 0).getRGB() + ","
                    + "\"footer\": {\"text\": \"Report generated at " + timestamp + "\"}"
                    + "}]"
                    + "}";
                    
        return json;
    }
    
    private void sendDiscordWebhook(String jsonPayload) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(webhookUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("User-Agent", "ChunkLagDetector/1.0");
                    connection.setDoOutput(true);
                    
                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonPayload.getBytes("UTF-8");
                        os.write(input, 0, input.length);
                    }
                    
                    int responseCode = connection.getResponseCode();
                    
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        if (debugMode) {
                            getLogger().info("Successfully sent report to Discord webhook!");
                        }
                    } else {
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                response.append(line);
                            }
                            getLogger().warning("Failed to send Discord webhook! Response code: " + responseCode);
                            getLogger().warning("Error message: " + response.toString());
                        }
                    }
                    
                    connection.disconnect();
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "Error sending Discord webhook", e);
                }
            }
        }.runTaskAsynchronously(this);
    }
    
    @EventHandler
    public void onServerTickEnd(ServerTickEndEvent event) {
        // Keep track of server performance metrics if needed
        // This is just a placeholder for potential future functionality
    }
    
    // Getter methods for command usage
    public List<ChunkCoord> getHeavyChunks() {
        return Collections.unmodifiableList(heavyChunks);
    }
    
    public int getReportThreshold() {
        return reportThreshold;
    }
    
    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isScanning() {
        return isScanning;
    }
    
    // Helper class to store chunk coordinates and metrics
    public static class ChunkCoord {
        private final String world;
        private final int x;
        private final int z;
        private int entities;
        private int tileEntities;
        private long processingTime;
        
        public ChunkCoord(String world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
        
        public String getWorld() {
            return world;
        }
        
        public int getX() {
            return x;
        }
        
        public int getZ() {
            return z;
        }
        
        public void setEntities(int entities) {
            this.entities = entities;
        }
        
        public int getEntities() {
            return entities;
        }
        
        public void setTileEntities(int tileEntities) {
            this.tileEntities = tileEntities;
        }
        
        public int getTileEntities() {
            return tileEntities;
        }
        
        public void setProcessingTime(long processingTime) {
            this.processingTime = processingTime;
        }
        
        public long getProcessingTime() {
            return processingTime;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z && Objects.equals(world, that.world);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
        
        @Override
        public String toString() {
            return world + " (" + x + ", " + z + ")";
        }
    }
}
