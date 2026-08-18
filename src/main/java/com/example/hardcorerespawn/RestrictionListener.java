package com.example.hardcorerespawn;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Extra safeguards: even if the server / another plugin tried to change the gamemode
 * of a waiting player, we block key actions. In spectator mode most of this is already
 * blocked by the game itself, but this is an extra layer of protection
 * (e.g. in case an admin manually switches the player back to survival with /gamemode).
 */
public class RestrictionListener implements Listener {

    private final HardcoreRespawnDelay plugin;
    private final RespawnManager respawnManager;

    public RestrictionListener(HardcoreRespawnDelay plugin, RespawnManager respawnManager) {
        this.plugin = plugin;
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

    /**
     * Restricts flying in spectator mode to a small radius around the location where
     * the player started waiting (they can freely look around/rotate the camera,
     * but can't fly off to explore the map / scout ores through walls).
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!respawnManager.isWaiting(player.getUniqueId())) {
            return;
        }
        if (!plugin.getConfig().getBoolean("restrict-spectator-movement", true)) {
            return;
        }

        Location anchor = respawnManager.getAnchorLocation(player.getUniqueId());
        if (anchor == null || event.getTo() == null) {
            return;
        }
        // Different worlds (e.g. anchor in the Nether, player teleported elsewhere) - do nothing here.
        if (!anchor.getWorld().equals(event.getTo().getWorld())) {
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("spectator-movement-radius", 5.0);
        if (anchor.distanceSquared(event.getTo()) > maxDistance * maxDistance) {
            // Teleport back to the anchor, but keep the player's look direction,
            // so the camera doesn't "snap" - only the position is reset.
            Location snapBack = anchor.clone();
            snapBack.setYaw(event.getTo().getYaw());
            snapBack.setPitch(event.getTo().getPitch());
            player.teleport(snapBack);
        }
    }

    /** If a waiting player logs out and comes back, handle it correctly
     *  (including the case where the wait time elapsed while they were offline / the server was down). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        respawnManager.handleRejoin(event.getPlayer());
    }
}
