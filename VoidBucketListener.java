package de.donuttools;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Void-Eimer: nimmt Wasser auf, ohne jemals voll zu werden.
 * Das Wasser verschwindet, der Eimer bleibt leer (= unendlich Platz).
 */
public class VoidBucketListener implements Listener {

    private final DonutTools plugin;

    public VoidBucketListener(DonutTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();

        ItemStack custom = null;
        if (plugin.items().getType(main) == ItemManager.Type.VOID_BUCKET) custom = main;
        else if (plugin.items().getType(off) == ItemManager.Type.VOID_BUCKET) custom = off;
        if (custom == null) return;

        Material result = e.getItemStack() == null ? null : e.getItemStack().getType();
        if (result != Material.WATER_BUCKET) {
            e.setCancelled(true);
            p.sendMessage(DonutTools.color(DonutTools.PREFIX + "&cDer Void-Eimer kann nur Wasser aufnehmen."));
            return;
        }

        // Block wird normal entfernt, der Spieler behaelt aber den leeren Void-Eimer
        e.setItemStack(custom.clone());
    }
}
