package myplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> timeouts = new HashMap<>();
    private final Map<UUID, Integer> diamondMined = new HashMap<>();
    private final Map<UUID, Long> diamondWindow = new HashMap<>();

    private File timeoutsFile;
    private FileConfiguration timeoutsConfig;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        setupTimeoutFile();
        loadTimeouts();
        getLogger().info("AntiCheatPlugin enabled");
    }

    @Override
    public void onDisable() {
        saveTimeouts();
    }

    // ================= TIMEOUT SYSTEM =================

    private boolean isTimedOut(UUID uuid) {
        if (!timeouts.containsKey(uuid)) return false;

        long until = timeouts.get(uuid);
        if (System.currentTimeMillis() > until) {
            timeouts.remove(uuid);
            saveTimeouts();
            return false;
        }
        return true;
    }

    private void timeout(Player p, int seconds, String reason) {
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + (seconds * 1000L);
        timeouts.put(id, until);
        saveTimeouts();

        Bukkit.getScheduler().runTask(this, () ->
                p.kickPlayer(ChatColor.RED + "Timed out for " + (seconds / 60) + " minutes\nReason: " + reason)
        );

        String msg = "[AntiCheat] " + p.getName() + " was timed out for "
                + (seconds / 60) + " minutes. Reason: " + reason;

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("anticheat.alert")) {
                admin.sendMessage(ChatColor.RED + msg);
            }
        }

        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void cheatTimeout(Player p, String reason) {
        timeout(p, 300, reason);
    }

    // ================= JOIN CHECK =================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (isTimedOut(id)) {
            long remaining = (timeouts.get(id) - System.currentTimeMillis()) / 1000;
            long minutes = remaining / 60;

            Bukkit.getScheduler().runTask(this, () ->
                    p.kickPlayer(ChatColor.RED + "You are still timed out for " + minutes + " minutes.")
            );
        }
    }

    // ================= CHAT FILTER =================

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().toLowerCase()
                .replace("4", "a")
                .replace("@", "a")
                .replace("3", "e")
                .replace("1", "i")
                .replace("0", "o");

        if (msg.contains("hack") || msg.contains("cheat") || msg.contains("killaura")
                || msg.contains("xray") || msg.contains("flyhack")) {
            Bukkit.getScheduler().runTask(this, () ->
                    cheatTimeout(p, "Using hacks (chat)")
            );
        }
    }

    // ================= DIAMOND DETECTION =================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Material type = e.getBlock().getType();

        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        diamondWindow.putIfAbsent(id, now);
        diamondMined.putIfAbsent(id, 0);

        if (now - diamondWindow.get(id) > 60_000) {
            diamondWindow.put(id, now);
            diamondMined.put(id, 0);
        }

        int mined = diamondMined.get(id) + 1;
        diamondMined.put(id, mined);

        if (mined >= 8) {
            String alert = ChatColor.AQUA + "[AntiCheat] " + p.getName()
                    + " mined " + mined + " diamonds in 1 minute.";

            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.isOp() || admin.hasPermission("anticheat.alert")) {
                    admin.sendMessage(alert);
                }
            }

            Bukkit.getConsoleSender().sendMessage(ChatColor.stripColor(alert));
        }
    }

    // ================= FLY HACK DETECTION =================

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (p.hasPermission("anticheat.bypass")) return;
        if (p.getGameMode() != GameMode.SURVIVAL) return;
        if (p.isFlying() || p.getAllowFlight()) return;

        if (!p.isOnGround()
                && p.getLocation().subtract(0, 1, 0).getBlock().getType() == Material.AIR
                && p.getVelocity().getY() == 0) {
            cheatTimeout(p, "Fly hacking");
        }
    }

    // ================= COMMANDS =================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("timeout")) {
            if (!sender.hasPermission("anticheat.timeout")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /timeout <player> <minutes> [reason]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            int minutes;
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid number.");
                return true;
            }

            String reason = args.length >= 3
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : "Manual timeout";

            timeout(target, minutes * 60, reason);
            sender.sendMessage(ChatColor.GREEN + "Player timed out.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("untimeout")) {
            if (!sender.hasPermission("anticheat.untimeout")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /untimeout <player>");
                return true;
            }

            UUID uuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
            timeouts.remove(uuid);
            saveTimeouts();
            sender.sendMessage(ChatColor.GREEN + "Timeout removed.");
            return true;
        }

        return false;
    }

    // ================= FILE STORAGE =================

    private void setupTimeoutFile() {
        timeoutsFile = new File(getDataFolder(), "timeouts.yml");
        if (!timeoutsFile.exists()) {
            timeoutsFile.getParentFile().mkdirs();
        }
        timeoutsConfig = YamlConfiguration.loadConfiguration(timeoutsFile);
    }

    private void loadTimeouts() {
        if (!timeoutsConfig.contains("timeouts")) return;

        for (String key : timeoutsConfig.getConfigurationSection("timeouts").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            long until = timeoutsConfig.getLong("timeouts." + key);
            timeouts.put(uuid, until);
        }
    }

    private void saveTimeouts() {
        for (UUID uuid : timeouts.keySet()) {
            timeoutsConfig.set("timeouts." + uuid.toString(), timeouts.get(uuid));
        }
        try {
            timeoutsConfig.save(timeoutsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
