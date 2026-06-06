package myplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class AdminGUI implements Listener {

    private static AntiCheatPlugin plugin;

    public AdminGUI(AntiCheatPlugin pl) {
        plugin = pl;
    }

    // ================= MAIN MENU =================

    public static void openMainGUI(Player player) {

        Inventory inv = Bukkit.createInventory(null, 27, "§cAntiCheat");

        // Timeout Player (Clock)
        ItemStack timeout = new ItemStack(Material.CLOCK);
        ItemMeta timeoutMeta = timeout.getItemMeta();
        timeoutMeta.setDisplayName("§cTimeout Player");
        timeout.setItemMeta(timeoutMeta);

        // Active Timeouts (Book)
        ItemStack active = new ItemStack(Material.BOOK);
        ItemMeta activeMeta = active.getItemMeta();
        activeMeta.setDisplayName("§eActive Timeouts");
        active.setItemMeta(activeMeta);

        inv.setItem(11, timeout);
        inv.setItem(15, active);

        player.openInventory(inv);
    }

    // ================= PLAYER SELECT =================

    public static void openPlayerSelectGUI(Player admin) {

        Inventory inv = Bukkit.createInventory(null, 54, "§cSelect Player");

        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.setDisplayName("§e" + p.getName());
            head.setItemMeta(meta);
            inv.addItem(head);
        }

        admin.openInventory(inv);
    }

    // ================= TIME SELECT =================

    public static void openTimeSelectGUI(Player admin, Player target) {

        Inventory inv = Bukkit.createInventory(null, 27,
                "§cSelect Timeout: " + target.getName());

        inv.setItem(10, createItem(Material.GREEN_WOOL, "§a5 Minutes"));
        inv.setItem(12, createItem(Material.YELLOW_WOOL, "§e10 Minutes"));
        inv.setItem(14, createItem(Material.ORANGE_WOOL, "§630 Minutes"));
        inv.setItem(16, createItem(Material.RED_WOOL, "§c60 Minutes"));
        inv.setItem(22, createItem(Material.BARRIER, "§cRemove Timeout"));

        admin.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // ================= CLICK HANDLER =================

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player)) return;

        Player player = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        if (title == null || !title.startsWith("§c")) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // ===== MAIN MENU =====
        if (title.equals("§cAntiCheat")) {

            if (name.equalsIgnoreCase("Timeout Player")) {
                openPlayerSelectGUI(player);
                return;
            }

            if (name.equalsIgnoreCase("Active Timeouts")) {

                player.closeInventory();
                player.sendMessage("§e§lActive Timeouts:");

                for (UUID uuid : plugin.getTimeouts().keySet()) {

                    long remaining =
                            (plugin.getTimeouts().get(uuid) - System.currentTimeMillis()) / 1000;

                    if (remaining <= 0) continue;

                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

                    player.sendMessage("§7- §c" + op.getName() +
                            " §7(" + plugin.formatTime(remaining) + ")");
                }
                return;
            }
        }

        // ===== PLAYER SELECT =====
        if (title.equals("§cSelect Player")) {

            if (clicked.getType() != Material.PLAYER_HEAD) return;

            String targetName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("§cPlayer is offline.");
                return;
            }

            openTimeSelectGUI(player, target);
            return;
        }

        // ===== TIME SELECT =====
        if (title.startsWith("§cSelect Timeout: ")) {

            String targetName = title.replace("§cSelect Timeout: ", "");
            Player target = Bukkit.getPlayer(targetName);

            if (target == null) {
                player.sendMessage("§cPlayer is offline.");
                player.closeInventory();
                return;
            }

            if (name.equalsIgnoreCase("Remove Timeout")) {
                plugin.untimeout(target.getUniqueId());
                player.sendMessage("§aTimeout removed.");
                player.closeInventory();
                return;
            }

            int minutes = 5;

            if (name.contains("10")) minutes = 10;
            if (name.contains("30")) minutes = 30;
            if (name.contains("60")) minutes = 60;

            plugin.timeout(target, minutes * 60, "Manual Timeout");
            player.sendMessage("§aTimed out for " + minutes + " minutes.");
            player.closeInventory();
        }
    }

    // ================= DRAG PROTECTION =================

    @EventHandler
    public void onDrag(InventoryDragEvent e) {

        String title = e.getView().getTitle();

        if (title != null && title.startsWith("§c")) {
            e.setCancelled(true);
        }
    }
}