package de.donuttools;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderCommand implements TabExecutor {

    private final DonutTools plugin;

    public OrderCommand(DonutTools plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String text) {
        s.sendMessage(DonutTools.color(DonutTools.PREFIX + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        Economy eco = plugin.economy();
        if (eco == null) {
            msg(p, "&cKein Economy-Plugin (Vault) gefunden.");
            return true;
        }
        if (args.length == 0) {
            usage(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(p, eco, args);
            case "list" -> list(p, eco, args);
            case "mine" -> mine(p, eco);
            case "cancel" -> cancel(p, eco, args);
            case "collect" -> collect(p);
            default -> usage(p);
        }
        return true;
    }

    private void usage(Player p) {
        msg(p, "&e/order create <item> <menge> <preis pro stueck>");
        msg(p, "&e/order list [item] &7- offene Orders ansehen");
        msg(p, "&e/order mine &7- deine Orders");
        msg(p, "&e/order cancel <id> &7- Order abbrechen (Restgeld zurueck)");
        msg(p, "&e/order collect &7- gelieferte Items abholen");
        msg(p, "&7Verkaufen an Orders: &f/sell&7 oder die &6Sell-Axe&7 (Orders zuerst).");
    }

    private void create(Player p, Economy eco, String[] args) {
        if (args.length < 4) {
            msg(p, "&cNutzung: /order create <item> <menge> <preis pro stueck>");
            return;
        }
        Material m = Material.matchMaterial(args[1]);
        if (m == null || m.isAir() || !m.isItem()) {
            msg(p, "&cUnbekanntes Item: " + args[1]);
            return;
        }
        int amount;
        double price;
        try {
            amount = Integer.parseInt(args[2]);
            price = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            msg(p, "&cMenge und Preis muessen Zahlen sein.");
            return;
        }
        int maxAmount = plugin.getConfig().getInt("orders.max-amount", 100000);
        double maxPrice = plugin.getConfig().getDouble("orders.max-price-each", 1_000_000.0);
        if (amount < 1 || amount > maxAmount) {
            msg(p, "&cMenge muss zwischen 1 und " + maxAmount + " liegen.");
            return;
        }
        if (!(price > 0) || price > maxPrice || Double.isNaN(price) || Double.isInfinite(price)) {
            msg(p, "&cPreis muss groesser 0 und hoechstens " + maxPrice + " sein.");
            return;
        }

        double total = amount * price;
        if (!eco.has(p, total)) {
            msg(p, "&cDu hast nicht genug Geld (" + eco.format(total) + " noetig).");
            return;
        }
        EconomyResponse resp = eco.withdrawPlayer(p, total);
        if (!resp.transactionSuccess()) {
            msg(p, "&cAbbuchung fehlgeschlagen: " + resp.errorMessage);
            return;
        }
        OrderManager.Order o = plugin.orders().create(p.getUniqueId(), m, amount, price);
        msg(p, "&aOrder #" + o.id + " erstellt: &f" + amount + "x " + m.name() + " &afuer je &6"
                + eco.format(price) + " &7(" + eco.format(total) + " hinterlegt)");
    }

    private void list(Player p, Economy eco, String[] args) {
        Material filter = null;
        if (args.length >= 2) {
            filter = Material.matchMaterial(args[1]);
            if (filter == null) {
                msg(p, "&cUnbekanntes Item: " + args[1]);
                return;
            }
        }
        List<OrderManager.Order> list = plugin.orders().all(filter);
        if (list.isEmpty()) {
            msg(p, "&7Keine offenen Orders.");
            return;
        }
        msg(p, "&6Offene Orders (hoechster Preis zuerst):");
        int shown = 0;
        for (OrderManager.Order o : list) {
            if (shown++ >= 20) break;
            String owner = Bukkit.getOfflinePlayer(o.owner).getName();
            p.sendMessage(DonutTools.color("&e#" + o.id + " &f" + o.remaining + "x " + o.material.name()
                    + " &7@ &6" + eco.format(o.price) + " &7von " + (owner == null ? "?" : owner)));
        }
        if (list.size() > 20) msg(p, "&7... und " + (list.size() - 20) + " weitere. Filtere mit /order list <item>.");
    }

    private void mine(Player p, Economy eco) {
        List<OrderManager.Order> list = plugin.orders().of(p.getUniqueId());
        if (list.isEmpty()) {
            msg(p, "&7Du hast keine Orders.");
            return;
        }
        for (OrderManager.Order o : list) {
            p.sendMessage(DonutTools.color("&e#" + o.id + " &f" + o.material.name() + " &7offen: &f" + o.remaining
                    + " &7abholbar: &a" + o.delivered + " &7@ &6" + eco.format(o.price)));
        }
    }

    private void cancel(Player p, Economy eco, String[] args) {
        if (args.length < 2) {
            msg(p, "&cNutzung: /order cancel <id>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1].replace("#", ""));
        } catch (NumberFormatException ex) {
            msg(p, "&cUngueltige ID.");
            return;
        }
        OrderManager.Order o = plugin.orders().get(id);
        if (o == null || !(o.owner.equals(p.getUniqueId()) || p.hasPermission("donuttools.admin"))) {
            msg(p, "&cOrder nicht gefunden.");
            return;
        }
        double refund = o.remaining * o.price;
        if (refund > 0) eco.depositPlayer(Bukkit.getOfflinePlayer(o.owner), refund);
        o.remaining = 0;
        if (o.delivered <= 0) plugin.orders().remove(o.id);
        else plugin.orders().save();
        msg(p, "&aOrder #" + id + " abgebrochen. &7Rueckerstattet: &6" + eco.format(refund)
                + (o.delivered > 0 ? " &7(gelieferte Items mit /order collect abholen)" : ""));
    }

    private void collect(Player p) {
        int totalGiven = 0;
        for (OrderManager.Order o : new ArrayList<>(plugin.orders().of(p.getUniqueId()))) {
            int d = o.delivered;
            while (d > 0) {
                int stack = Math.min(d, o.material.getMaxStackSize());
                Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(o.material, stack));
                if (!left.isEmpty()) {
                    int notFit = left.values().iterator().next().getAmount();
                    int fit = stack - notFit;
                    d -= fit;
                    totalGiven += fit;
                    break;
                }
                d -= stack;
                totalGiven += stack;
            }
            o.delivered = d;
            if (o.remaining <= 0 && o.delivered <= 0) plugin.orders().remove(o.id);
        }
        plugin.orders().save();
        if (totalGiven == 0) msg(p, "&7Nichts abzuholen (oder Inventar voll).");
        else msg(p, "&a" + totalGiven + " Items abgeholt.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "list", "mine", "cancel", "collect"), out);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("list"))) {
            List<String> mats = new ArrayList<>();
            for (Material m : Material.values()) if (m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_")) mats.add(m.name().toLowerCase(Locale.ROOT));
            StringUtil.copyPartialMatches(args[1], mats, out);
        }
        return out;
    }
}
