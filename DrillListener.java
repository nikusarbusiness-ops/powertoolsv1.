package de.donuttools;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;

public class DrillListener implements Listener {

    private final DonutTools plugin;

    public DrillListener(DonutTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (BlockUtil.BUSY.contains(p.getUniqueId())) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (plugin.items().getType(hand) != ItemManager.Type.DRILL) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.isSneaking()) return; // Schleichen = normal einzeln abbauen

        Block center = e.getBlock();
        BlockFace face = getHitFace(p, center);
        boolean onlyPreferred = plugin.getConfig().getBoolean("drill.only-preferred-tool", true);

        List<Block> targets = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue;
                Block b = switch (face) {
                    case UP, DOWN -> center.getRelative(i, 0, j);
                    case NORTH, SOUTH -> center.getRelative(i, j, 0);
                    default -> center.getRelative(0, j, i);
                };
                Material t = b.getType();
                if (t.isAir() || b.isLiquid() || t.getHardness() < 0) continue;
                if (onlyPreferred && !b.isPreferredTool(hand)) continue;
                if (b.getState() instanceof Container) continue;
                targets.add(b);
            }
        }

        int broken = 0;
        BlockUtil.BUSY.add(p.getUniqueId());
        try {
            for (Block b : targets) {
                if (BlockUtil.breakAs(p, b, hand)) broken++;
            }
        } finally {
            BlockUtil.BUSY.remove(p.getUniqueId());
        }

        plugin.items().damageMainHand(p, broken * plugin.getConfig().getInt("drill.durability-per-block", 1));
    }

    private BlockFace getHitFace(Player p, Block center) {
        RayTraceResult r = p.rayTraceBlocks(7.0);
        if (r != null && center.equals(r.getHitBlock()) && r.getHitBlockFace() != null) {
            return r.getHitBlockFace();
        }
        float pitch = p.getLocation().getPitch();
        if (pitch > 60) return BlockFace.UP;
        if (pitch < -60) return BlockFace.DOWN;
        return p.getFacing().getOppositeFace();
    }
}
