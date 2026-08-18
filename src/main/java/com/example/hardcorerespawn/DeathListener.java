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
 * Handles the sequence: player dies (standard item drop, standard death screen)
 * -> player clicks "Respawn" (PlayerRespawnEvent) -> instead of returning to the
 * game normally, they enter the waiting state (spectator + timer).
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

        // Intentionally NOT changing anything about the death event itself:
        // item drop, exp, death message - all stay standard (requirement).

        if (player.hasPermission("hrd.bypass")) {
            return; // bypass players respawn normally, without delay
        }

        if (plugin.getConfig().getBoolean("broadcast-death", true)) {
            int delaySeconds = plugin.getConfig().getInt("respawn-delay-seconds", 300);
            String timeFormatted = RespawnManager.formatTime(delaySeconds);

            String msgRaw = plugin.getConfig().getString("messages.death-broadcast",
                    "&c☠ {player} died! They will be able to respawn in {time}.");
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
            return; // normal respawn
        }

        // The player just clicked "Respawn" on the standard death screen.
        // We intercept this: instead of letting them return to the game normally,
        // we immediately put them into the waiting state right after respawn.
        respawnManager.beginWait(player);
    }
}
