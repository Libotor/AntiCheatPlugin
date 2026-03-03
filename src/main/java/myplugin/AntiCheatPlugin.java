package myplugin;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> timeouts = new HashMap<>();
    private final Map<UUID, Long> flyStart = new HashMap<>();
    private final Map<UUID, Location> lastSafeLocation = new HashMap<>();
    private final Map<UUID, List<Long>> diamondMines = new HashMap<>();
    private final Map<UUID, Integer> suspicion = new HashMap<>();

    // ================= ENABLE =================

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new AdminGUI(this), this);
        getLogger().info("AntiCheatPlugin 1.4.0 enabled.");
    }

    // ================= TIMEOUT =================

    public void timeout(Player p, int seconds, String reason) {

    long end = System.currentTimeMillis() + (seconds * 1000L);
    timeouts.put(p.getUniqueId(), end);

    p.kickPlayer("§cYou have been timed out!\n\n" +
            "§7Reason: §e" + reason + "\n" +
            "§7Duration: §e" + formatTime(seconds));
}

    public void untimeout(UUID uuid) {
        timeouts.remove(uuid);
    }

    public boolean isTimedOut(UUID uuid) {
        if (!timeouts.containsKey(uuid)) return false;

        long remaining = timeouts.get(uuid) - System.currentTimeMillis();
        if (remaining <= 0) {
            timeouts.remove(uuid);
            return false;
        }
        return true;
    }

    public Map<UUID, Long> getTimeouts() {
        return timeouts;
    }

    public String formatTime(long seconds) {
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) return hours + "h";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    // ================= JOIN CHECK =================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        if (isTimedOut(p.getUniqueId())) {
            long remaining = (timeouts.get(p.getUniqueId()) - System.currentTimeMillis()) / 1000;
            p.kickPlayer("§cYou are still timed out for " + formatTime(remaining));
        }
    }

    // ================= FLY CHECK =================

    @EventHandler
public void onMove(PlayerMoveEvent e) {

    Player p = e.getPlayer();
    if (p.isOp()) return;

    UUID uuid = p.getUniqueId();

    if (p.isOnGround()) {
        lastSafeLocation.put(uuid, p.getLocation());
        flyStart.remove(uuid);
        return;
    }

    long now = System.currentTimeMillis();

    if (!flyStart.containsKey(uuid)) {
        flyStart.put(uuid, now);
        return;
    }

    
    if (now - flyStart.get(uuid) > 4000) {

        alert(p.getName() + " suspected Fly Hack.");

        if (lastSafeLocation.containsKey(uuid)) {
            p.teleport(lastSafeLocation.get(uuid));
        }

        timeout(p, 10, "Fly Hack");
        flyStart.remove(uuid);
    }
}
    // ================= DIAMOND CHECK =================

    @EventHandler
    public void onBreak(BlockBreakEvent e) {

        if (e.getBlock().getType() != Material.DIAMOND_ORE &&
            e.getBlock().getType() != Material.DEEPSLATE_DIAMOND_ORE) return;

        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        diamondMines.putIfAbsent(uuid, new ArrayList<>());
        List<Long> list = diamondMines.get(uuid);

        list.removeIf(t -> now - t > 60000);
        list.add(now);

        if (list.size() >= 5) {

    int points = addSuspicion(uuid, 3);

    alert(p.getName() + " mined many diamonds quickly. " +
            "(Suspicion: " + points + "/15)");
}
    }

    private int addSuspicion(UUID uuid, int amount) {

    int points = suspicion.getOrDefault(uuid, 0) + amount;
    suspicion.put(uuid, points);

    if (points >= 15) {

        Player p = Bukkit.getPlayer(uuid);

        if (p != null) {
            alert(p.getName() + " auto-timeout for suspected X-Ray " +
                    "(" + points + "/15)");

            timeout(p, 120, "Suspected X-Ray");
        }

        suspicion.remove(uuid);
    }

    return points;
}

    private void alert(String message) {
        String prefix = "§c§l[AntiCheat] §7";

        Bukkit.getConsoleSender().sendMessage(prefix + message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("anticheat.alert")) {
                online.sendMessage(prefix + message);
            }
        }
    }

    // ================= COMMANDS =================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (cmd.getName().equalsIgnoreCase("timeout")) {

            if (!sender.hasPermission("anticheat.timeout")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("§cUsage: /timeout <player> <minutes>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }

            int minutes;
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (Exception ex) {
                sender.sendMessage("§cInvalid number.");
                return true;
            }

            timeout(target, minutes * 60, "Manual Timeout");
            sender.sendMessage("§aTimed out for " + minutes + " minutes.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("untimeout")) {

            if (!sender.hasPermission("anticheat.untimeout")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage("§cUsage: /untimeout <player>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            untimeout(target.getUniqueId());
            sender.sendMessage("§aTimeout removed.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("acgui")) {
            if (!(sender instanceof Player)) return true;
            Player p = (Player) sender;
            if (!p.hasPermission("anticheat.admin")) {
                p.sendMessage("§cNo permission.");
                return true;
            }
            AdminGUI.openMainGUI(p);
            return true;
        }

        return false;
    }
}