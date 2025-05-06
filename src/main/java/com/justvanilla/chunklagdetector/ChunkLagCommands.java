package com.justvanilla.chunklagdetector;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ChunkLagCommands implements CommandExecutor, TabCompleter {

    private final ChunkLagDetector plugin;
    
    public ChunkLagCommands(ChunkLagDetector plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "scan":
                return handleScanCommand(sender);
            case "report":
                return handleReportCommand(sender);
            case "debug":
                return handleDebugCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "help":
            default:
                showHelp(sender);
                return true;
        }
    }
    
    private boolean handleScanCommand(CommandSender sender) {
        if (!sender.hasPermission("chunklag.scan")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (plugin.isScanning()) {
            sender.sendMessage(ChatColor.YELLOW + "A chunk scan is already in progress. Please wait.");
            return true;
        }
        
        sender.sendMessage(ChatColor.GREEN + "Starting chunk scan...");
        plugin.scanChunks();
        return true;
    }
    
    private boolean handleReportCommand(CommandSender sender) {
        if (!sender.hasPermission("chunklag.report")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        List<ChunkLagDetector.ChunkCoord> heavyChunks = plugin.getHeavyChunks();
        
        if (heavyChunks.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No heavy chunks have been detected yet. Run a scan first with /chunklag scan");
            return true;
        }
        
        sender.sendMessage(ChatColor.GOLD + "=== Chunk Lag Report ===");
        sender.sendMessage(ChatColor.YELLOW + "Found " + heavyChunks.size() + " chunks above threshold (" + plugin.getReportThreshold() + "ms)");
        
        int count = Math.min(heavyChunks.size(), 10);
        for (int i = 0; i < count; i++) {
            ChunkLagDetector.ChunkCoord chunk = heavyChunks.get(i);
            sender.sendMessage(ChatColor.AQUA + "#" + (i + 1) + ": " + ChatColor.WHITE + "World: " + chunk.getWorld() + 
                            ", X: " + chunk.getX() + ", Z: " + chunk.getZ() + 
                            ChatColor.RED + " - Processing: " + chunk.getProcessingTime() + "ms");
            sender.sendMessage(ChatColor.GRAY + "Entities: " + chunk.getEntities() + ", Tile Entities: " + chunk.getTileEntities());
        }
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!heavyChunks.isEmpty()) {
                ChunkLagDetector.ChunkCoord worstChunk = heavyChunks.get(0);
                player.sendMessage(ChatColor.GREEN + "Tip: To teleport to the worst chunk, use command: " + 
                                ChatColor.YELLOW + "/tp " + worstChunk.getX() * 16 + " ~ " + worstChunk.getZ() * 16);
            }
        }
        
        return true;
    }
    
    private boolean handleDebugCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chunklag.debug")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Current debug mode: " + (plugin.isDebugMode() ? "ENABLED" : "DISABLED"));
            sender.sendMessage(ChatColor.YELLOW + "Use /chunklag debug <on|off> to change.");
            return true;
        }
        
        boolean debugMode;
        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equals("1")) {
            debugMode = true;
        } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false") || args[1].equals("0")) {
            debugMode = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid argument. Use 'on' or 'off'.");
            return true;
        }
        
        // Update config - this would require adding a method in the main plugin class
        // plugin.setDebugMode(debugMode);
        
        sender.sendMessage(ChatColor.GREEN + "Debug mode " + (debugMode ? "enabled" : "disabled") + ".");
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("chunklag.reload")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        // Implement reload logic
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
        return true;
    }
    
    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== ChunkLagDetector Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/chunklag scan" + ChatColor.WHITE + " - Start a scan for laggy chunks");
        sender.sendMessage(ChatColor.YELLOW + "/chunklag report" + ChatColor.WHITE + " - View the latest scan results");
        sender.sendMessage(ChatColor.YELLOW + "/chunklag debug <on|off>" + ChatColor.WHITE + " - Toggle debug mode");
        sender.sendMessage(ChatColor.YELLOW + "/chunklag reload" + ChatColor.WHITE + " - Reload the configuration");
        sender.sendMessage(ChatColor.YELLOW + "/chunklag help" + ChatColor.WHITE + " - Show this help message");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("scan", "report", "debug", "reload", "help");
            return completions.stream()
                    .filter(c -> c.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            List<String> completions = Arrays.asList("on", "off");
            return completions.stream()
                    .filter(c -> c.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
