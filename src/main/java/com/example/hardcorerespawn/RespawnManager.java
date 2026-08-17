package com.example.hardcorerespawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zarządza graczami oczekującymi na respawn: przechowuje pozostały czas,
 * uruchamia timer aktualizujący ekran gracza i decyduje kiedy przywrócić go do gry.
 */
public class RespawnManager {

    private final HardcoreRespawnDelay plugin;

    // Gracze aktualnie "martwi" i czekający -> pozostały czas w sekundach
    private final Map<UUID, Integer> waitingPlayers = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    public RespawnManager(HardcoreRespawnDelay plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /** Rejestruje gracza jako oczekującego na respawn i wysyła go do spectator mode. */
    public void beginWait(Player player) {
        int delaySeconds = plugin.getConfig().getInt("respawn-delay-seconds", 300);

        if (player.hasPermission("hrd.bypass")) {
            waitingPlayers.remove(player.getUniqueId());
            sendMessage(player, "bypass-message", Map.of());
            return;
        }

        waitingPlayers.put(player.getUniqueId(), delaySeconds);

        if (plugin.getConfig().getBoolean("use-spectator-mode", true)) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        updatePlayerDisplay(player, delaySeconds);
    }

    public boolean isWaiting(UUID uuid) {
        return waitingPlayers.containsKey(uuid);
    }

    public Integer getRemainingSeconds(UUID uuid) {
        return waitingPlayers.get(uuid);
    }

    /** Ustawia pozostały czas ręcznie (np. przez komendę admina). */
    public void setRemainingSeconds(Player player, int seconds) {
        if (seconds <= 0) {
            forceRelease(player);
        } else {
            waitingPlayers.put(player.getUniqueId(), seconds);
            updatePlayerDisplay(player, seconds);
        }
    }

    /** Natychmiast kończy oczekiwanie i przywraca gracza do gry (np. komenda admina lub bypass). */
    public void forceRelease(Player player) {
        waitingPlayers.remove(player.getUniqueId());
        releasePlayer(player);
    }

    private void startTickTask() {
        // Co 20 ticków (1 sekunda) zmniejszamy czas każdemu oczekującemu graczowi
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (waitingPlayers.isEmpty()) {
            return;
        }

        for (UUID uuid : waitingPlayers.keySet().toArray(new UUID[0])) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                // Gracz offline - usuwamy z mapy, doliczy się gdy wróci? Prościej: usuwamy,
                // czas dalej odlicza mu się dopiero po ponownym wejściu w RestrictionListener#onJoin.
                continue;
            }

            int remaining = waitingPlayers.getOrDefault(uuid, 0) - 1;

            if (remaining <= 0) {
                waitingPlayers.remove(uuid);
                releasePlayer(player);
            } else {
                waitingPlayers.put(uuid, remaining);
                updatePlayerDisplay(player, remaining);
            }
        }
    }

    private void updatePlayerDisplay(Player player, int remainingSeconds) {
        String timeFormatted = formatTime(remainingSeconds);

        if (plugin.getConfig().getBoolean("show-title-timer", true)) {
            String mainRaw = plugin.getConfig().getString("messages.title-main", "&c&lOCZEKIWANIE NA RESPAWN");
            String subRaw = plugin.getConfig().getString("messages.title-subtitle", "&fMożesz wrócić do gry za: &e&l{time}");

            Component main = ChatUtil.colorize(mainRaw);
            Component sub = ChatUtil.colorize(subRaw.replace("{time}", timeFormatted));

            Title title = Title.title(
                    main,
                    sub,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(200))
            );
            player.showTitle(title);
        }

        if (plugin.getConfig().getBoolean("show-actionbar-timer", true)) {
            String actionbarRaw = plugin.getConfig().getString("messages.actionbar-waiting", "&7Respawn za: &e{time}");
            Component actionbar = ChatUtil.colorize(actionbarRaw.replace("{time}", timeFormatted));
            player.sendActionBar(actionbar);
        }
    }

    private void releasePlayer(Player player) {
        if (plugin.getConfig().getBoolean("use-spectator-mode", true)) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        // Teleportujemy na spawn świata (typowe zachowanie po respawnie)
        player.teleport(player.getWorld().getSpawnLocation());

        if (plugin.getConfig().getBoolean("show-title-timer", true)) {
            String mainRaw = plugin.getConfig().getString("messages.title-ready-main", "&a&lMOŻESZ WRÓCIĆ DO GRY!");
            String subRaw = plugin.getConfig().getString("messages.title-ready-subtitle", "&fNaciśnij dowolny klawisz ruchu");

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
                plugin.getLogger().warning("Nieprawidłowa nazwa dźwięku w configu: " + soundName);
            }
        }

        if (plugin.getConfig().getBoolean("broadcast-return", true)) {
            String msgRaw = plugin.getConfig().getString("messages.return-broadcast", "&a✔ {player} wrócił do gry!");
            Component broadcast = ChatUtil.colorize(msgRaw.replace("{player}", player.getName()));
            plugin.getServer().broadcast(broadcast);
        }
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

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        waitingPlayers.clear();
    }
}
