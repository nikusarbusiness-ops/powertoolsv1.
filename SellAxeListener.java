package de.donuttools;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sell-Axe: Rechtsklick auf Kiste/Fass/etc. verkauft den kompletten Inhalt (Orders zuerst, dann Shop). */
public class SellAxeListener implements Listener {

    private final DonutTools plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public SellAxeListener(DonutTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (plugin.items().getType(item) != ItemManager.Type.SELL_AXE) return;

        Block b = e.getClickedBlock();
        if (b == null || !(b.getState() instanceof Container container)) return;

        // Schutz-Plugins (Claims etc.) haben Vorrang
        if (e.useInteractedBlock() == Event.Result.DENY) return;

        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        Player p = e.getPlayer();
        if (!p.hasPermission("donuttools.sellaxe")) {
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cDafuer hast du keine Berechtigung."));
            return;
        }

        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("sell-axe.cooldown-ms", 500);
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) return;
        cooldown.put(p.getUniqueId(), now);

        Economy eco = plugin.economy();
        if (eco == null) {
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cKein Economy-Plugin (Vault) gefunden."));
            return;
        }

        Inventory inv = container.getInventory();
        SellManager.Result r = plugin.sell().sell(p, inv, inv.getSize());
        if (r == null || r.items() == 0) {
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&7In dieser Kiste ist nichts Verkaufbares."));
            return;
        }

        StringBuilder sb = new StringBuilder(DonutTools.PREFIX + "&a" + r.items() + " Items verkauft fuer &6"
                + eco.format(r.total()) + "&a.");
        if (r.orderItems > 0) {
            sb.append(" &7(davon ").append(r.orderItems).append(" an Orders fuer ")
                    .append(eco.format(r.orderMoney)).append(")");
        }
        p.sendMessage(DonutTools.color(sb.toString()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }
}
