package de.donuttools;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Spitzhacke, die nach X Tagen (Echtzeit) von selbst kaputtgeht. */
public class TimedPickaxeManager implements Listener {

    private final DonutTools plugin;
    private final ItemManager items;

    public TimedPickaxeManager(DonutTools plugin) {
        this.plugin = plugin;
        this.items = plugin.items();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            scan(p);
            showTimer(p);
        }
    }

    private void scan(Player p) {
        boolean removed = removeExpired(p.getInventory());
        removed |= removeExpired(p.getEnderChest());
        if (removed) notifyExpired(p);
    }

    private boolean removeExpired(Inventory inv) {
        boolean removed = false;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (items.isExpired(contents[i])) {
                inv.setItem(i, null);
                removed = true;
            }
        }
        return removed;
    }

    private void notifyExpired(Player p) {
        p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cDeine Zeit-Spitzhacke ist zerfallen!"));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
    }

    private void showTimer(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (items.getType(hand) != ItemManager.Type.TIMED_PICKAXE) return;
        Long exp = items.getExpiry(hand);
        if (exp == null) return;
        String msg = DonutTools.color("&e\u23F3 Zeit-Spitzhacke zerfaellt in &f"
                + ItemManager.formatRemaining(exp - System.currentTimeMillis()));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scan(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (items.isExpired(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            p.getInventory().setItemInMainHand(null);
            notifyExpired(p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (items.isExpired(p.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            p.getInventory().setItemInMainHand(null);
            notifyExpired(p);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (items.isExpired(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        removeExpired(e.getInventory());
    }
}
