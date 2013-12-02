/*
 * Copyright (C) 2012 BangL <henno.rickowski@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.bangl.lm;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class LotManagerListener implements Listener {

    public static LotManagerPlugin plugin;

    public LotManagerListener(LotManagerPlugin instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        int changedMaterial;
        changedMaterial = event.getBlock().getTypeId();
        if (changedMaterial == 63 || changedMaterial == 68) {
            plugin.removeSign(event.getBlock());
            //plugin.logInfo("Lot sign removed.");
        }
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

            if (!LotManagerPlugin.hasPermission(player, "lotmanager.sign.create")) {
                LotManagerPlugin.sendError(player, "You don't have the permission to create lot signs!");
                event.setCancelled(true);
                return;
            }

            String lotName = event.getLine(1);

            if (lotName.equals("")) {
                LotManagerPlugin.sendError(player, "Type a lot name on the second line next time!");
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
                LotManagerPlugin.sendError(player, "Invalid world name: \"" + worldName + "\"");
                event.setCancelled(true);
                return;
            }
            Integer worldId;
            worldId = plugin.lots.getWorldId(world);
            if (!plugin.lots.existsLot(worldId, lotName)) {
                LotManagerPlugin.sendError(player, "\"" + lotName + "\" is not a valid lot!");
                event.setCancelled(true);
                return;
            }

            event.setLine(1, lotName);
            sign.update(true);

            plugin.addSign(lotName, event.getBlock());
            plugin.refreshSigns(lotName, Boolean.valueOf(true));

            LotManagerPlugin.sendInfo(player, "Lot sign created!");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Block block = event.getClickedBlock();
        final Player player = event.getPlayer();
        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && block != null
                && plugin.isLotSign(block)) {
            final BlockState state = block.getState();
            if (state instanceof Sign) {
                final Sign sign = (Sign) state;
                final String lotName = sign.getLine(1);
                final String lotOwner = plugin.getLotSignOwner(lotName, block.getWorld());
                if (lotOwner != null) {
                    if (plugin.hasEssentials
                            && LotManagerPlugin.hasPermission(player, "essentials.seen")) {
                        plugin.getServer().dispatchCommand(player, "seen " + lotOwner);
                    } else {
                        LotManagerPlugin.sendError(player, "\"" + lotName + "\" is already in use.");
                    }
                } else {
                    LotManagerPlugin.sendInfo(player, "This lot is still free! Type \"/getlot " + lotName + "\" to claim this lot.");
                }
            }
        }
    }
}
