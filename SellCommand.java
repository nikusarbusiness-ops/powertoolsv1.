package de.donuttools;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** /sell = GUI (Items reinlegen, beim Schliessen wird verkauft), /sell hand = Item in der Hand. */
public class SellCommand implements CommandExecutor, Listener {

    public static class SellHolder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final DonutTools plugin;

    public SellCommand(DonutTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        if (plugin.economy() == null) {
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cKein Economy-Plugin (Vault) gefunden."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cDu haeltst nichts in der Hand."));
                return true;
            }
            Inventory tmp = Bukkit.createInventory(null, 9);
            tmp.setItem(0, hand.clone());
            SellManager.Result r = plugin.sell().sell(p, tmp, 9);
            ItemStack left = tmp.getItem(0);
            p.getInventory().setItemInMainHand(left == null || left.getType().isAir() ? null : left);
            report(p, r);
            return true;
        }

        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, DonutTools.color("&6&lVerkaufen &7- Items hineinlegen"));
        holder.inv = inv;
        p.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof SellHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        Inventory inv = e.getInventory();
        SellManager.Result r = plugin.sell().sell(p, inv, inv.getSize());
        giveBack(p, inv);
        report(p, r);
    }

    private void giveBack(Player p, Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }
        inv.clear();
    }

    private void report(Player p, SellManager.Result r) {
        Economy eco = plugin.economy();
        if (r == null || eco == null) return;
        if (r.items() == 0) {
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&7Nichts Verkaufbares dabei."));
            return;
        }
        StringBuilder sb = new StringBuilder(DonutTools.PREFIX + "&a" + r.items() + " Items verkauft fuer &6"
                + eco.format(r.total()) + "&a.");
        if (r.orderItems > 0) {
            sb.append(" &7(davon ").append(r.orderItems).append(" an Orders)");
        }
        p.sendMessage(DonutTools.color(sb.toString()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    /** Beim Abschalten: Items aus offenen Verkaufs-GUIs zurueckgeben. */
    public void returnAllOpenGuis() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof SellHolder) {
                giveBack(p, top);
            }
        }
    }
}
