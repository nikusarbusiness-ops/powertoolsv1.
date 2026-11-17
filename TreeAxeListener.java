package de.donuttools;

import org.bukkit.GameMode;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeAxeListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final DonutTools plugin;

    public TreeAxeListener(DonutTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (BlockUtil.BUSY.contains(p.getUniqueId())) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (plugin.items().getType(hand) != ItemManager.Type.TREE_AXE) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isSneaking()) return;

        Block start = e.getBlock();
        if (!Tag.LOGS.isTagged(start.getType())) return;

        int max = plugin.getConfig().getInt("tree-axe.max-logs", 300);
        List<Block> logs = collectLogs(start, max);

        // Nur echte Baeume faellen (nicht gebaute Holzhaeuser)
        if (plugin.getConfig().getBoolean("tree-axe.require-leaves", true)
                && Tag.LOGS_THAT_BURN.isTagged(start.getType())
                && !hasNaturalLeaves(logs)) {
            return;
        }

        int broken = 0;
        BlockUtil.BUSY.add(p.getUniqueId());
        try {
            for (Block log : logs) {
                if (log.equals(start)) continue; // der Start-Block wird vom Event selbst abgebaut
                if (BlockUtil.breakAs(p, log, hand)) broken++;
            }
            if (plugin.getConfig().getBoolean("tree-axe.break-leaves", false)) {
                Set<Block> leaves = new HashSet<>();
                for (Block log : logs) {
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dz = -1; dz <= 1; dz++) {
                                Block n = log.getRelative(dx, dy, dz);
                                if (n.getBlockData() instanceof Leaves l && !l.isPersistent()) leaves.add(n);
                            }
                }
                for (Block leaf : leaves) BlockUtil.breakAs(p, leaf, hand);
            }
        } finally {
            BlockUtil.BUSY.remove(p.getUniqueId());
        }

        plugin.items().damageMainHand(p, (broken + 1) * plugin.getConfig().getInt("tree-axe.durability-per-log", 1));
    }

    private List<Block> collectLogs(Block start, int max) {
        List<Block> result = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && result.size() < max) {
            Block b = queue.poll();
            result.add(b);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.contains(n)) continue;
                        if (Tag.LOGS.isTagged(n.getType())) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
        }
        return result;
    }

    private boolean hasNaturalLeaves(List<Block> logs) {
        for (Block log : logs) {
            for (BlockFace f : FACES) {
                Block n = log.getRelative(f);
                if (n.getBlockData() instanceof Leaves l && !l.isPersistent()) return true;
            }
        }
        return false;
    }
}
