package com.example.hardcorerespawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages players waiting to respawn: stores the timestamp at which each player
 * becomes eligible to respawn, runs a timer that updates the player's screen,
 * decides when to return them to the game, and persists state to disk so it
 * survives a server restart.
 */
public class RespawnManager {

    private final HardcoreRespawnDelay plugin;

    // Players currently "dead" and waiting -> timestamp (epoch millis) at which they can respawn
    private final Map<UUID, Long> waitingPlayers = new ConcurrentHashMap<>();

    // Location where the player started waiting - used to "anchor" them in spectator mode (can't fly away)
    private final Map<UUID, Location> anchorLocations = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private final File stateFile;

    public RespawnManager(HardcoreRespawnDelay plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "waiting-players.yml");
        loadState();
        startTickTask();
    }

    /** Registers a player as waiting to respawn and puts them into spectator mode. */
    public void beginWait(Player player) {
        int delaySeconds = plugin.getConfig().getInt("respawn-delay-seconds", 300);

        if (player.hasPermission("hrd.bypass")) {
            waitingPlayers.remove(player.getUniqueId());
            anchorLocations.remove(player.getUniqueId());
            sendMessage(player, "bypass-message", Map.of());
            return;
        }

        long readyAt = System.currentTimeMillis() + (delaySeconds * 1000L);
        waitingPlayers.put(player.getUniqueId(), readyAt);
        anchorLocations.put(player.getUniqueId(), player.getLocation().clone());
        saveState();

        if (plugin.getConfig().getBoolean("use-spectator-mode", true)) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        updatePlayerDisplay(player, delaySeconds);
    }

    public boolean isWaiting(UUID uuid) {
        return waitingPlayers.containsKey(uuid);
    }

    /** Returns the location a waiting player is "anchored" to in spectator mode (can look around, can't fly off). */
    public Location getAnchorLocation(UUID uuid) {
        return anchorLocations.get(uuid);
    }

    /** Returns how many seconds are left until the player can respawn (never below 0). */
    public Integer getRemainingSeconds(UUID uuid) {
        Long readyAt = waitingPlayers.get(uuid);
        if (readyAt == null) return null;
        return secondsUntil(readyAt);
    }

    /** Manually sets the remaining wait time (e.g. via an admin command). */
    public void setRemainingSeconds(Player player, int seconds) {
        if (seconds <= 0) {
            forceRelease(player);
        } else {
            long readyAt = System.currentTimeMillis() + (seconds * 1000L);
            waitingPlayers.put(player.getUniqueId(), readyAt);
            saveState();
            updatePlayerDisplay(player, seconds);
        }
    }

    /** Immediately ends the wait and returns the player to the game (e.g. admin command or bypass permission). */
    public void forceRelease(Player player) {
        waitingPlayers.remove(player.getUniqueId());
        anchorLocations.remove(player.getUniqueId());
        saveState();
        releasePlayer(player);
    }

    /**
     * Called when a waiting player comes back online (e.g. after a server restart).
     * If the wait time already elapsed while the server was down / the player was offline,
     * releases them immediately. Otherwise re-applies spectator mode and shows the current timer.
     */
    public void handleRejoin(Player player) {
        Long readyAt = waitingPlayers.get(player.getUniqueId());
        if (readyAt == null) return;

        int remaining = secondsUntil(readyAt);
        if (remaining <= 0) {
            waitingPlayers.remove(player.getUniqueId());
            anchorLocations.remove(player.getUniqueId());
            saveState();
            releasePlayer(player);
        } else {
            if (plugin.getConfig().getBoolean("use-spectator-mode", true)) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            // If the anchor was lost (e.g. after a server restart, since it's only kept in memory),
            // anchor the player to wherever they just logged back in.
            anchorLocations.putIfAbsent(player.getUniqueId(), player.getLocation().clone());
            updatePlayerDisplay(player, remaining);
            sendMessage(player, "rejoin-still-waiting", Map.of("time", formatTime(remaining)));
        }
    }

    private void startTickTask() {
        // Every 20 ticks (1 second) check how much time is left for each waiting player
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (waitingPlayers.isEmpty()) {
            return;
        }

        for (UUID uuid : waitingPlayers.keySet().toArray(new UUID[0])) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // Player offline - time still passes (we count from a timestamp, not from ticks),
                // we just can't refresh their screen. This gets reconciled when they rejoin
                // (see handleRejoin), or auto-released in this loop once they're back online.
                continue;
            }

            Long readyAt = waitingPlayers.get(uuid);
            if (readyAt == null) continue;

            int remaining = secondsUntil(readyAt);

            if (remaining <= 0) {
                waitingPlayers.remove(uuid);
                anchorLocations.remove(uuid);
                saveState();
                releasePlayer(player);
            } else {
                updatePlayerDisplay(player, remaining);
            }
        }
    }

    private int secondsUntil(long readyAtMillis) {
        long diffMillis = readyAtMillis - System.currentTimeMillis();
        if (diffMillis <= 0) return 0;
        // Round up so e.g. 4.2s shows "5" instead of "4" (avoids flashing 0 right before actual release)
        return (int) Math.ceil(diffMillis / 1000.0);
    }

    private void updatePlayerDisplay(Player player, int remainingSeconds) {
        String timeFormatted = formatTime(remainingSeconds);

        if (plugin.getConfig().getBoolean("show-title-timer", true)) {
            String mainRaw = plugin.getConfig().getString("messages.title-main", "&c&lWAITING TO RESPAWN");
            String subRaw = plugin.getConfig().getString("messages.title-subtitle", "&fYou can respawn in: &e&l{time}");

            Component main = ChatUtil.colorize(mainRaw);
            Component sub = ChatUtil.colorize(subRaw.replace("{time}", timeFormatted));

            Title title = Title.title(
                    main,
                    sub,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(200))
            );
            player.showTitle(title);
        }

        if (plugin.getConfig().getBoolean("show-actionbar-timer", false)) {
            String actionbarRaw = plugin.getConfig().getString("messages.actionbar-waiting", "&7Respawn in: &e{time}");
            Component actionbar = ChatUtil.colorize(actionbarRaw.replace("{time}", timeFormatted));
            player.sendActionBar(actionbar);
        }
    }

    private void releasePlayer(Player player) {
        if (plugin.getConfig().getBoolean("use-spectator-mode", true)) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        player.teleport(resolveRespawnLocation(player));

        if (plugin.getConfig().getBoolean("show-title-timer", true)) {
            String mainRaw = plugin.getConfig().getString("messages.title-ready-main", "&a&lYOU CAN RESPAWN NOW!");
            String subRaw = plugin.getConfig().getString("messages.title-ready-subtitle", "&fPress any movement key");

            Title title = Title.title(
                    ChatUtil.colorize(mainRaw),
                    ChatUtil.colorize(subRaw),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))
            );
            player.showTitle(title);
        }

        sendMessage(player, "respawn-message", Map.of());

        if (plugin.getConfig().getBoolean("play-sound-on-respawn-ready", true)) {
            String soundName = plugin.getConfig().getString("sound-on-respawn-ready", "ENTITY_PLAYER_LEVELUP");
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid sound name in config: " + soundName);
            }
        }

        if (plugin.getConfig().getBoolean("broadcast-return", true)) {
            String msgRaw = plugin.getConfig().getString("messages.return-broadcast", "&a✔ {player} is back in the game!");
            Component broadcast = ChatUtil.colorize(msgRaw.replace("{player}", player.getName()));
            plugin.getServer().broadcast(broadcast);
        }
    }

    /**
     * Returns where the player should respawn: their bed/respawn anchor if set and still valid,
     * otherwise the world spawn as a fallback.
     */
    private Location resolveRespawnLocation(Player player) {
        Location bedSpawn = player.getRespawnLocation();
        if (bedSpawn != null) {
            return bedSpawn;
        }
        return player.getWorld().getSpawnLocation();
    }

    private void sendMessage(Player player, String path, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString("messages." + path, "");
        if (raw.isEmpty()) return;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        player.sendMessage(ChatUtil.colorize(raw));
    }

    public static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** Saves the current state of waiting players to disk so it survives a server restart. */
    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : waitingPlayers.entrySet()) {
            yaml.set("waiting." + entry.getKey(), entry.getValue());
        }
        try {
            File parent = stateFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save waiting players state: " + e.getMessage());
        }
    }

    /** Loads the waiting players state from disk on plugin startup (e.g. after a server restart). */
    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        if (!yaml.isConfigurationSection("waiting")) {
            return;
        }

        for (String key : yaml.getConfigurationSection("waiting").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long readyAt = yaml.getLong("waiting." + key);
                waitingPlayers.put(uuid, readyAt);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipped invalid entry in waiting-players.yml: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + waitingPlayers.size() + " waiting players from the previous session.");
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        saveState();
    }
}
