package myplugin;

import org.bukkit.*;
import org.bukkit.block.Block;
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
        getLogger().info("AntiCheatPlugin 1.4.1 enabled.");
    }

    // ================= TIMEOUT =================

    public void timeout(OfflinePlayer p, int seconds, String reason) {
        long end = System.currentTimeMillis() + (seconds * 1000L);
        timeouts.put(p.getUniqueId(), end);

        if (p.isOnline() && p.getPlayer() != null) {
            p.getPlayer().kickPlayer("§cYou have been timed out!\n\n" +
                    "§7Reason: §e" + reason + "\n" +
                    "§7Duration: §e" + formatTime(seconds));
        }
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

        // Wenn der Spieler schwimmt oder im Wasser/Lava ist, Fly-Check überspringen
        if (p.isInWater() || p.getLocation().getBlock().isLiquid()) {
            flyStart.remove(p.getUniqueId());
            return;
        }

        // FIX: Wenn der Spieler an einer Leiter oder Ranke klettert, Check überspringen
        Material blockType = p.getLocation().getBlock().getType();
        if (blockType == Material.LADDER || blockType == Material.VINE) {
            flyStart.remove(p.getUniqueId());
            return;
        }

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

        if (now - flyStart.get(uuid) > 7000) {

            Block currentBlock = p.getLocation().getBlock();
            Block feetBlock = p.getLocation().subtract(0, 0.1, 0).getBlock();

            boolean isInBlock = feetBlock.getType().isSolid() || currentBlock.getType().isSolid();

            if (isInBlock) {
                if (lastSafeLocation.containsKey(uuid)) {
                    p.teleport(lastSafeLocation.get(uuid));
                }
                flyStart.remove(uuid); 
                return;
            } else {
                alert(p.getName() + " suspected Fly Hack.");

                if (lastSafeLocation.containsKey(uuid)) {
                    p.teleport(lastSafeLocation.get(uuid));
                }

                timeout(p, 10, "Fly Hack");
                flyStart.remove(uuid);
            }
        }
    }

    // ================= DIAMOND CHECK =================

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        // Prüfen, ob ein Diamanterz abgebaut wurde
        if (e.getBlock().getType() != Material.DIAMOND_ORE &&
            e.getBlock().getType() != Material.DEEPSLATE_DIAMOND_ORE) return;

        Player p = e.getPlayer();

        // 1. OP-ABFRAGE: Server-Owner und Admins komplett ignorieren
        if (p.isOp()) return;

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        diamondMines.putIfAbsent(uuid, new ArrayList<>());
        List<Long> list = diamondMines.get(uuid);

        // Einträge entfernen, die älter als 60 Sekunden sind
        list.removeIf(t -> now - t > 60000);
        list.add(now);

        // 2. NUR EINE EINZIGE WARNUNG: 
        // Durch "==" wird der Alert exakt ein Mal pro Minute ausgelöst, wenn das Limit geknackt wird.
        if (list.size() == 20) {
            alert(p.getName() + " reached the diamond limit. (Mined: 20 blocks in < 60s)");
        }
    }

    // ================= UTILS & ALERTS =================

    public void alert(String message) {
        String prefix = "§c§l[AntiCheat] §7";

        Bukkit.getConsoleSender().sendMessage(prefix + message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("anticheat.alert")) {
                online.sendMessage(prefix + message);
            }
        }
    }

    private int addSuspicion(UUID uuid, int amount) {
        int points = suspicion.getOrDefault(uuid, 0) + amount;
        suspicion.put(uuid, points);

        if (points >= 15) {
            Player p = Bukkit.getPlayer(uuid);

            if (p != null) {
                alert(p.getName() + " auto-timeout for suspected X-Ray (" + points + "/15)");
                timeout(p, 120, "Suspected X-Ray");
            }

            suspicion.remove(uuid);
        }

        return points;
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

            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage("§cPlayer has never played on this server before.");
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
            sender.sendMessage("§aTimed out " + target.getName() + " for " + minutes + " minutes.");
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

            @SuppressWarnings("deprecation")
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
