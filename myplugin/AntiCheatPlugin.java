package myplugin;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> timeouts = new HashMap<>();
    private final Map<UUID, Integer> violations = new HashMap<>();

    private static final long BASE_TIMEOUT = 5L * 60L * 1000L; // 5 Minuten

    private final List<String> keywords = List.of(
            "wurst",
            "wurstclient",
            "hack",
            "hacks",
            "cheat",
            "cheats",
            "fly",
            "killaura"
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AntiCheatPlugin v1.2.0 enabled");
    }

    /* =========================
       CHAT DETECTION
       ========================= */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("anticheat.bypass")) return;

        String msg = event.getMessage().toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (msg.contains(keyword)) {
                event.setCancelled(true);
                applyTimeout(player, "Suspicious chat message");
                break;
            }
        }
    }

    /* =========================
       JOIN CHECK
       ========================= */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!timeouts.containsKey(uuid)) return;

        long until = timeouts.get(uuid);

        if (System.currentTimeMillis() < until) {
            player.kick(
                    Component.text("You are temporarily blocked from this server.", NamedTextColor.RED)
            );
        } else {
            timeouts.remove(uuid);
        }
    }

    /* =========================
       TIMEOUT LOGIC
       ========================= */
    private void applyTimeout(Player player, String reason) {
        UUID uuid = player.getUniqueId();

        int count = violations.getOrDefault(uuid, 0) + 1;
        violations.put(uuid, count);

        long duration = BASE_TIMEOUT * count;
        long until = System.currentTimeMillis() + duration;

        timeouts.put(uuid, until);

        Bukkit.getScheduler().runTask(this, () -> {
            player.kick(
                    Component.text("Timeout (" + (duration / 60000) +
                            " min): " + reason, NamedTextColor.RED)
            );
        });

        Component broadcast = Component.text(
                "[AntiCheat] " + player.getName() +
                        " was timed out for " + (duration / 60000) + " minutes.",
                NamedTextColor.RED
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            Audience audience = online;
            audience.sendMessage(broadcast);
        }
    }

    /* =========================
       COMMANDS
       ========================= */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Audience audience)) return true;

        if (!sender.hasPermission("anticheat.admin")) {
            audience.sendMessage(
                    Component.text("No permission.", NamedTextColor.RED)
            );
            return true;
        }

        if (command.getName().equalsIgnoreCase("timeout")) {
            if (args.length < 2) {
                audience.sendMessage(
                        Component.text("Usage: /timeout <player> <minutes> [reason]", NamedTextColor.RED)
                );
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                audience.sendMessage(
                        Component.text("Player not found.", NamedTextColor.RED)
                );
                return true;
            }

            int minutes;
            try {
                minutes = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                audience.sendMessage(
                        Component.text("Invalid number.", NamedTextColor.RED)
                );
                return true;
            }

            String reason = args.length >= 3
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : "Manual timeout";

            long until = System.currentTimeMillis() + minutes * 60L * 1000L;
            timeouts.put(target.getUniqueId(), until);

            target.kick(
                    Component.text("Timeout (" + minutes + " min): " + reason, NamedTextColor.RED)
            );

            audience.sendMessage(
                    Component.text("Player timed out.", NamedTextColor.GREEN)
            );
            return true;
        }

        if (command.getName().equalsIgnoreCase("untimeout")) {
            if (args.length != 1) {
                audience.sendMessage(
                        Component.text("Usage: /untimeout <player>", NamedTextColor.RED)
                );
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                audience.sendMessage(
                        Component.text("Player not found.", NamedTextColor.RED)
                );
                return true;
            }

            timeouts.remove(target.getUniqueId());
            violations.remove(target.getUniqueId());

            audience.sendMessage(
                    Component.text("Timeout removed.", NamedTextColor.GREEN)
            );
            return true;
        }

        return false;
    }
}



