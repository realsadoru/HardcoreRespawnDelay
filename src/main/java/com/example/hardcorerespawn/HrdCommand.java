package com.example.hardcorerespawn;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HrdCommand implements CommandExecutor, TabCompleter {

    private final HardcoreRespawnDelay plugin;
    private final RespawnManager respawnManager;

    public HrdCommand(HardcoreRespawnDelay plugin, RespawnManager respawnManager) {
        this.plugin = plugin;
        this.respawnManager = respawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatUtil.colorize("&aHardcoreRespawnDelay: configuration reloaded."));
            }
            case "revive" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatUtil.colorize("&cUsage: /hrd revive <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.colorize("&cPlayer " + args[1] + " is not online."));
                    return true;
                }
                if (!respawnManager.isWaiting(target.getUniqueId())) {
                    sender.sendMessage(ChatUtil.colorize("&e" + target.getName() + " is not currently waiting to respawn."));
                    return true;
                }
                respawnManager.forceRelease(target);
                sender.sendMessage(ChatUtil.colorize("&a" + target.getName() + " was immediately returned to the game."));
            }
            case "time" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatUtil.colorize("&cUsage: /hrd time <player> <seconds>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.colorize("&cPlayer " + args[1] + " is not online."));
                    return true;
                }
                int seconds;
                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtil.colorize("&cPlease enter a valid number of seconds."));
                    return true;
                }
                if (!respawnManager.isWaiting(target.getUniqueId())) {
                    sender.sendMessage(ChatUtil.colorize("&e" + target.getName() + " is not currently waiting to respawn."));
                    return true;
                }
                respawnManager.setRemainingSeconds(target, seconds);
                sender.sendMessage(ChatUtil.colorize("&aSet the remaining wait time for " + target.getName()
                        + " to " + seconds + "s."));
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtil.colorize("&6&l--- HardcoreRespawnDelay ---"));
        sender.sendMessage(ChatUtil.colorize("&e/hrd reload &7- reloads config.yml"));
        sender.sendMessage(ChatUtil.colorize("&e/hrd revive <player> &7- immediately ends a player's wait"));
        sender.sendMessage(ChatUtil.colorize("&e/hrd time <player> <seconds> &7- sets the remaining wait time"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(List.of("reload", "revive", "time"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("revive") || args[0].equalsIgnoreCase("time"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        }

        return options;
    }
}
