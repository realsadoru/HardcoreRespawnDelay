package com.example.hardcorerespawn;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;

/**
 * Obsługuje sekwencję: gracz umiera (standardowy drop itemów, standardowy ekran śmierci)
 * -> gracz klika "Respawn" (PlayerRespawnEvent) -> zamiast normalnego powrotu do gry,
 * trafia do trybu oczekiwania (spectator + timer).
 */
public class DeathListener implements Listener {

    private final HardcoreRespawnDelay plugin;
    private final RespawnManager respawnManager;

    public DeathListener(HardcoreRespawnDelay plugin, RespawnManager respawnManager) {
        this.plugin = plugin;
        this.respawnManager = respawnManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return;

        Player player = event.getPlayer();

        // Celowo NIC nie zmieniamy w samym evencie śmierci:
        // drop itemów, exp, wiadomość śmierci - wszystko zostaje standardowe (wymaganie).

        if (player.hasPermission("hrd.bypass")) {
            return; // gracz z bypassem odradza się normalnie, bez opóźnienia
        }

        if (plugin.getConfig().getBoolean("broadcast-death", true)) {
            int delaySeconds = plugin.getConfig().getInt("respawn-delay-seconds", 300);
            String timeFormatted = RespawnManager.formatTime(delaySeconds);

            String msgRaw = plugin.getConfig().getString("messages.death-broadcast",
                    "&c☠ {player} zginął! Będzie mógł wrócić do gry za {time}.");
            msgRaw = msgRaw.replace("{player}", player.getName()).replace("{time}", timeFormatted);

            Component broadcast = ChatUtil.colorize(msgRaw);
            plugin.getServer().broadcast(broadcast);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfig().getBoolean("enabled", true)) return;

        Player player = event.getPlayer();

        if (player.hasPermission("hrd.bypass")) {
            return; // normalny respawn
        }

        // Gracz właśnie kliknął "Respawn" na standardowym ekranie śmierci.
        // Przechwytujemy to: zamiast pozwolić mu normalnie wrócić do gry,
        // od razu po respawnie wrzucamy go w tryb oczekiwania.
        respawnManager.beginWait(player);
    }
}
