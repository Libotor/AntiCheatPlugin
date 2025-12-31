package myplugin;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AntiCheatPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> rejoinTimeouts = new HashMap<>();
    private List<String> keywords;
    private long timeoutMillis;
    private String kickMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        keywords = getConfig().getStringList("keywords");
        timeoutMillis = getConfig().getLong("rejoin-timeout-seconds") * 1000;
        kickMessage = getConfig().getString("kick-message");

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AntiCheatPlugin v1.1.0 enabled");
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();

        if (p.hasPermission("anticheat.bypass")) return;

        String msg = event.message().toString().toLowerCase();

        for (String word : keywords) {
            if (msg.contains(word.toLowerCase())) {
                event.setCancelled(true);

                rejoinTimeouts.put(
                        p.getUniqueId(),
                        System.currentTimeMillis() + timeoutMillis
                );

                p.kick(Component.text(kickMessage));

                Bukkit.broadcast(
                        Component.text("§c[AntiCheat] " + p.getName() + " was kicked.")
                );
                return;
            }
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (!rejoinTimeouts.containsKey(uuid)) return;

        long until = rejoinTimeouts.get(uuid);

        if (System.currentTimeMillis() < until) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_OTHER,
                    Component.text("§cYou are temporarily blocked from joining.")
            );
        } else {
            rejoinTimeouts.remove(uuid);
        }
    }
}



