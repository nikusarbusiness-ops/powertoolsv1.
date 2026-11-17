package de.donuttools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BlockUtil {

    /** Spieler, fuer die gerade ein Multi-Break laeuft (verhindert Endlosschleifen). */
    public static final Set<UUID> BUSY = new HashSet<>();

    private BlockUtil() { }

    /** Baut einen Block "als Spieler" ab und fragt vorher Schutz-Plugins per BlockBreakEvent. */
    public static boolean breakAs(Player p, Block b, ItemStack tool) {
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;

        int xp = ev.getExpToDrop();
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        if (ev.isDropItems()) {
            b.breakNaturally(tool);
        } else {
            b.setType(Material.AIR);
        }
        if (xp > 0) {
            b.getWorld().spawn(loc, ExperienceOrb.class, o -> o.setExperience(xp));
        }
        return true;
    }
}
