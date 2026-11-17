package de.donuttools;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ItemManager {

    public enum Type {
        DRILL, TREE_AXE, TIMED_PICKAXE, VOID_BUCKET, SELL_AXE;

        public static Type parse(String s) {
            if (s == null) return null;
            try {
                return valueOf(s.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final DonutTools plugin;
    private final NamespacedKey keyType;
    private final NamespacedKey keyExpire;

    public ItemManager(DonutTools plugin) {
        this.plugin = plugin;
        this.keyType = new NamespacedKey(plugin, "tool_type");
        this.keyExpire = new NamespacedKey(plugin, "expire_at");
    }

    public ItemStack create(Type type) {
        Material mat = switch (type) {
            case DRILL -> Material.NETHERITE_PICKAXE;
            case TREE_AXE -> Material.NETHERITE_AXE;
            case TIMED_PICKAXE -> Material.DIAMOND_PICKAXE;
            case VOID_BUCKET -> Material.BUCKET;
            case SELL_AXE -> Material.GOLDEN_AXE;
        };
        String name = switch (type) {
            case DRILL -> "&b&lDrill";
            case TREE_AXE -> "&2&lBaumaxt";
            case TIMED_PICKAXE -> "&e&lZeit-Spitzhacke";
            case VOID_BUCKET -> "&9&lVoid-Eimer";
            case SELL_AXE -> "&6&lSell-Axe";
        };

        List<String> lore = new ArrayList<>();
        switch (type) {
            case DRILL -> lore.add("&7Baut einen 3x3-Bereich auf einmal ab.");
            case TREE_AXE -> lore.add("&7Faellt einen ganzen Baum mit einem Schlag.");
            case TIMED_PICKAXE -> lore.add("&7Zerfaellt nach " + plugin.getConfig().getInt("timed-pickaxe.days", 5) + " Tagen von selbst.");
            case VOID_BUCKET -> {
                lore.add("&7Hat unendlich Platz fuer Wasser.");
                lore.add("&7Das Wasser verschwindet einfach.");
            }
            case SELL_AXE -> {
                lore.add("&7Rechtsklick auf eine Kiste:");
                lore.add("&7verkauft den ganzen Inhalt.");
            }
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(DonutTools.color(name));

        switch (type) {
            case DRILL -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case TREE_AXE -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            }
            case TIMED_PICKAXE -> {
                meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                long expire = System.currentTimeMillis()
                        + plugin.getConfig().getInt("timed-pickaxe.days", 5) * 86_400_000L;
                meta.getPersistentDataContainer().set(keyExpire, PersistentDataType.LONG, expire);
                lore.add("&cLaeuft ab am: " + new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(expire)));
            }
            case SELL_AXE -> {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            default -> { }
        }

        meta.setEnchantmentGlintOverride(true);
        List<String> colored = new ArrayList<>();
        for (String l : lore) colored.add(DonutTools.color(l));
        meta.setLore(colored);
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public Type getType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
        return Type.parse(s);
    }

    public Long getExpiry(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyExpire, PersistentDataType.LONG);
    }

    public boolean isExpired(ItemStack item) {
        if (getType(item) != Type.TIMED_PICKAXE) return false;
        Long exp = getExpiry(item);
        return exp != null && exp <= System.currentTimeMillis();
    }

    /** Zieht Haltbarkeit vom Item in der Haupthand ab und zerstoert es bei Bedarf. */
    public void damageMainHand(Player p, int amount) {
        if (amount <= 0) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta m = hand.getItemMeta();
        if (!(m instanceof Damageable d) || m.isUnbreakable()) return;
        d.setDamage(d.getDamage() + amount);
        if (d.getDamage() >= hand.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
        } else {
            hand.setItemMeta(d);
            p.getInventory().setItemInMainHand(hand);
        }
    }

    public static String formatRemaining(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + (s % 60) + "s";
    }
}
