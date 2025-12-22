package myplugin;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    // UUID → Zeitpunkt (Millis), bis wann Timeout gilt
    private final HashMap<UUID, Long> timeouts = new HashMap<>();

    // 5 Minuten
    private static final long TIMEOUT_TIME = 5 * 60 * 1000;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AntiCheatPlugin aktiviert");
    }

    // 🧠 Chat-Überwachung
    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        long now = System.currentTimeMillis();

        // ⛔ Wenn Spieler getimeoutet ist
        if (timeouts.containsKey(p.getUniqueId())) {
            long until = timeouts.get(p.getUniqueId());
            if (now < until) {
                event.setCancelled(true);
                Audience audience = p;
           audience.sendMessage(Component.text("Du bist noch getimeoutet."));
                return;
            } else {
                timeouts.remove(p.getUniqueId());
            }
        }

        String msg = event.message().toString().toLowerCase();

        // 🔍 „KI“-Wörter (regelbasiert)
        if (msg.contains("wurst") || msg.contains("hack") || msg.contains("fly")) {
            event.setCancelled(true);
            timeouts.put(p.getUniqueId(), now + TIMEOUT_TIME);
            Audience audience = p;
            audience.sendMessage(Component.text("Du wurdest für 5 Minuten getimeoutet."));        
       Bukkit.broadcastMessage("§c[AntiCheat] wurde automatisch getimeoutet.");
        }
    }

    // 👮 Admin-Befehle
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player admin)) {
            ((Audience) sender).sendMessage(Component.text("Nur Spieler."));
            return true;
        }

        if (!admin.hasPermission("anticheat.admin")) {
            ((Audience) admin).sendMessage(Component.text("§cKeine Rechte."));
            return true;
        }

        if (command.getName().equalsIgnoreCase("untimeout")) {
            if (args.length != 1) {
                ((Audience) admin).sendMessage(Component.text("§e/untimeout <spieler>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                ((Audience) admin).sendMessage(Component.text("§cSpieler nicht online."));
                return true;
            }

            timeouts.remove(target.getUniqueId());
            ((Audience) admin).sendMessage(Component.text("§aTimeout aufgehoben"));
            ((Audience) target).sendMessage(Component.text("§aDein Timeout wurde aufgehoben."));
            return true;
        }

        return false;
    }
}
