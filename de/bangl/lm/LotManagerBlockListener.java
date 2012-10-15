package de.bangl.lm;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class LotManagerBlockListener implements Listener {
    public LotManager plugin;

    public LotManagerBlockListener(LotManager instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        int changedMaterial;
        changedMaterial = event.getBlock().getTypeId();
        if (changedMaterial == 63 || changedMaterial == 68) {
            plugin.removeSign(event.getBlock());
            plugin.logInfo("Lot sign removed.");
        }
    }
}
