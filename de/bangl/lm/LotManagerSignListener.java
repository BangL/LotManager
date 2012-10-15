package de.bangl.lm;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

public class LotManagerSignListener implements Listener {
    public static LotManager plugin;

    public LotManagerSignListener(LotManager instance) {
        plugin = instance;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {

        Player player = event.getPlayer();

        BlockState state;
        state = event.getBlock().getState();
        if (state instanceof Sign) {
            Sign sign = (Sign)state;

            if (!event.getLine(0).equalsIgnoreCase("[lot]")) {
                return;
            }

            if (!LotManager.hasPermission(player, "lotmanager.sign.create")) {
                LotManager.sendError(player, "You don't have the permission to create lot signs!");
                event.setCancelled(true);
                return;
            }

            String lotName = event.getLine(1);

            if (lotName.equals("")) {
                LotManager.sendError(player, "Type a lot name on the second line, idiot!");
                event.setCancelled(true);
                return;
            }

            String worldName = event.getLine(2);
            if (worldName == null) {
                worldName = "world";
            } else if (worldName.isEmpty()) {
                worldName = "world";
            }

            World world;
            world = plugin.server.getWorld(worldName);
            if (world == null) {
                LotManager.sendError(player, "Invalid world name: \"" + worldName + "\"");
                event.setCancelled(true);
                return;
            }
            Integer worldId;
            worldId = plugin.lots.getWorldId(world);
            if (!plugin.lots.existsLot(worldId, lotName)) {
                LotManager.sendError(player, "\"" + lotName + "\" is not a valid lot, you fucking fool!");
                event.setCancelled(true);
                return;
            }

            event.setLine(1, lotName);
            sign.update(true);

            plugin.addSign(lotName, event.getBlock());
            plugin.refreshSigns(lotName, Boolean.valueOf(true));

            LotManager.sendInfo(player, "Lot sign created!");
        }
    }
}
