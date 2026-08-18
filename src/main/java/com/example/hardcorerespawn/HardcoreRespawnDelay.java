package com.example.hardcorerespawn;

import org.bukkit.plugin.java.JavaPlugin;

public final class HardcoreRespawnDelay extends JavaPlugin {

    private static HardcoreRespawnDelay instance;
    private RespawnManager respawnManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.respawnManager = new RespawnManager(this);

        getServer().getPluginManager().registerEvents(new DeathListener(this, respawnManager), this);
        getServer().getPluginManager().registerEvents(new RestrictionListener(this, respawnManager), this);

        HrdCommand hrdCommand = new HrdCommand(this, respawnManager);
        getCommand("hrd").setExecutor(hrdCommand);
        getCommand("hrd").setTabCompleter(hrdCommand);

        getLogger().info("HardcoreRespawnDelay enabled. Respawn delay: "
                + getConfig().getInt("respawn-delay-seconds") + "s.");
    }

    @Override
    public void onDisable() {
        if (respawnManager != null) {
            respawnManager.shutdown();
        }
        getLogger().info("HardcoreRespawnDelay disabled.");
    }

    public static HardcoreRespawnDelay getInstance() {
        return instance;
    }

    public RespawnManager getRespawnManager() {
        return respawnManager;
    }
}
