package de.donuttools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Kauf-Orders: Spieler hinterlegen Geld, andere liefern per /sell bzw. Sell-Axe. */
public class OrderManager {

    public static class Order {
        public int id;
        public UUID owner;
        public Material material;
        public int remaining;   // noch gesucht
        public int delivered;   // geliefert, aber noch nicht abgeholt
        public double price;    // pro Stueck
    }

    private final DonutTools plugin;
    private final File file;
    private final Map<Integer, Order> orders = new LinkedHashMap<>();
    private int nextId = 1;

    public OrderManager(DonutTools plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("orders");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            ConfigurationSection o = s.getConfigurationSection(key);
            if (o == null) continue;
            Material m = Material.matchMaterial(o.getString("material", ""));
            if (m == null) continue;
            Order ord = new Order();
            ord.id = Integer.parseInt(key);
            ord.owner = UUID.fromString(o.getString("owner", ""));
            ord.material = m;
            ord.remaining = o.getInt("remaining");
            ord.delivered = o.getInt("delivered");
            ord.price = o.getDouble("price");
            orders.put(ord.id, ord);
            nextId = Math.max(nextId, ord.id + 1);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Order o : orders.values()) {
            String base = "orders." + o.id + ".";
            y.set(base + "owner", o.owner.toString());
            y.set(base + "material", o.material.name());
            y.set(base + "remaining", o.remaining);
            y.set(base + "delivered", o.delivered);
            y.set(base + "price", o.price);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte orders.yml nicht speichern: " + ex.getMessage());
        }
    }

    public Order create(UUID owner, Material m, int amount, double price) {
        Order o = new Order();
        o.id = nextId++;
        o.owner = owner;
        o.material = m;
        o.remaining = amount;
        o.price = price;
        orders.put(o.id, o);
        save();
        return o;
    }

    public Order get(int id) {
        return orders.get(id);
    }

    public void remove(int id) {
        orders.remove(id);
        save();
    }

    public List<Order> all(Material filter) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) {
            if (o.remaining <= 0) continue;
            if (filter != null && o.material != filter) continue;
            list.add(o);
        }
        list.sort(Comparator.comparingDouble((Order o) -> o.price).reversed());
        return list;
    }

    public List<Order> of(UUID owner) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) if (o.owner.equals(owner)) list.add(o);
        return list;
    }

    /**
     * Liefert Items an offene Orders (hoechster Preis zuerst, mindestens minPrice).
     * Das Geld kommt aus dem Escrow des Bestellers, die Auszahlung an den Verkaeufer macht der SellManager.
     *
     * @return Anzahl Items, die NICHT an Orders gingen
     */
    public int fill(Player seller, Material m, int amount, double minPrice, SellManager.Result r) {
        List<Order> candidates = new ArrayList<>();
        for (Order o : orders.values()) {
            if (o.material == m && o.remaining > 0
                    && !o.owner.equals(seller.getUniqueId()) && o.price >= minPrice) {
                candidates.add(o);
            }
        }
        candidates.sort(Comparator.comparingDouble((Order o) -> o.price).reversed());

        boolean changed = false;
        for (Order o : candidates) {
            if (amount <= 0) break;
            int take = Math.min(amount, o.remaining);
            o.remaining -= take;
            o.delivered += take;
            amount -= take;
            r.orderItems += take;
            r.orderMoney += take * o.price;
            changed = true;

            Player owner = Bukkit.getPlayer(o.owner);
            if (owner != null) {
                owner.sendMessage(DonutTools.color(DonutTools.PREFIX + "&a" + seller.getName() + " hat "
                        + take + "x " + m.name() + " an deine Order #" + o.id + " geliefert. &7(/order collect)"));
            }
        }
        if (changed) save();
        return amount;
    }
}
