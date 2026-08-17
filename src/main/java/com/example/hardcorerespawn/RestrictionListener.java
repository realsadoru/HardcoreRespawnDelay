package com.example.hardcorerespawn;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Dodatkowe zabezpieczenia: nawet gdyby serwer/inny plugin próbował zmienić gamemode
 * oczekującego gracza, blokujemy kluczowe akcje. W trybie spectator większość z tego
 * i tak jest domyślnie zablokowana przez samą grę, ale to dodatkowa warstwa bezpieczeństwa
 * (np. gdyby ktoś ręcznie przełączył gracza z powrotem na survival komendą /gamemode).
 */
public class RestrictionListener implements Listener {

    private final RespawnManager respawnManager;

    public RestrictionListener(RespawnManager respawnManager) {
        this.respawnManager = respawnManager;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (respawnManager.isWaiting(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (respawnManager.isWaiting(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (respawnManager.isWaiting(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && respawnManager.isWaiting(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && respawnManager.isWaiting(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Gdyby gracz wylogował się w trakcie oczekiwania i wrócił - upewniamy się, że nadal jest w spectator. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (respawnManager.isWaiting(player.getUniqueId())) {
            Integer remaining = respawnManager.getRemainingSeconds(player.getUniqueId());
            if (remaining != null) {
                respawnManager.setRemainingSeconds(player, remaining);
            }
        }
    }
}
