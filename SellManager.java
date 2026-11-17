package de.donuttools;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.Map;

public class SellManager {

    public static class Result {
        public double shopMoney;
        public int shopItems;
        public double orderMoney;
        public int orderItems;

        public double total() { return shopMoney + orderMoney; }
        public int items() { return shopItems + orderItems; }
    }

    private final DonutTools plugin;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public SellManager(DonutTools plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("prices");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m != null) prices.put(m, s.getDouble(key));
        }
    }

    public Double getPrice(Material m) {
        return prices.get(m);
    }

    /**
     * Verkauft Items aus den ersten {@code size} Slots. Erst werden passende Orders
     * (ab Shop-Preis aufwaerts) bedient, der Rest geht zum Shop-Preis.
     * Nicht verkaufte Items bleiben im Inventar. Gibt null zurueck, wenn keine Economy da ist.
     */
    public Result sell(Player seller, Inventory inv, int size) {
        Economy eco = plugin.economy();
        if (eco == null) return null;

        Result r = new Result();
        for (int i = 0; i < size && i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (!isPlain(it)) continue;

            Double shop = prices.get(it.getType());
            int amount = it.getAmount();

            amount = plugin.orders().fill(seller, it.getType(), amount, shop == null ? 0.0 : shop, r);

            if (shop != null && amount > 0) {
                r.shopMoney += shop * amount;
                r.shopItems += amount;
                amount = 0;
            }

            if (amount <= 0) {
                inv.setItem(i, null);
            } else if (amount != it.getAmount()) {
                it.setAmount(amount);
                inv.setItem(i, it);
            }
        }

        if (r.total() > 0) eco.depositPlayer(seller, r.total());
        return r;
    }

    /** Nur "normale" Items verkaufen: keine Plugin-Items, keine Namen/Verzauberungen, keine Shulker mit Inhalt. */
    private boolean isPlain(ItemStack it) {
        if (plugin.items().getType(it) != null) return false;
        if (!it.hasItemMeta()) return true;
        ItemMeta m = it.getItemMeta();
        if (m == null) return true;
        if (m instanceof BlockStateMeta) return false;
        return !(m.hasDisplayName() || m.hasLore() || m.hasEnchants());
    }
}
