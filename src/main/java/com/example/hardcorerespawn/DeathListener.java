package com.example.hardcorerespawn;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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

        // Intentionally NOT changing anything about the death event itself:
        // item drop, exp, death message - all stay standard (requirement).
        // The vanilla death message is left untouched here on purpose.
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
