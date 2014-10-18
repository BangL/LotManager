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

import java.util.UUID;
import org.bukkit.Material;
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
        if ((event.getBlock().getType() == Material.SIGN
                || event.getBlock().getType() == Material.SIGN_POST)
                && this.plugin.isLotSign(event.getBlock())) {
            Player player = event.getPlayer();
            if (player == null) {
                plugin.removeSign(event.getBlock());
            } else if (LotManagerPlugin.hasPermission(player, "lotmanager.admin.sign.break")) {
                LotManagerPlugin.sendInfo(player, "Grundstueck-Schild entfernt.");
                plugin.removeSign(event.getBlock());
            } else {
                LotManagerPlugin.sendError(player, "Du bist nicht berechtigt Grundstueck-Schilder zu entfernen.");
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {

        Player player = event.getPlayer();

        BlockState state;
        state = event.getBlock().getState();
        if (state instanceof Sign) {
            Sign sign = (Sign)state;

            if (!event.getLine(0).equalsIgnoreCase("[lot]")
                    && !event.getLine(0).equalsIgnoreCase("[tplot]")) {
                return;
            }

            if (!LotManagerPlugin.hasPermission(player, "lotmanager.admin.sign.create")) {
                LotManagerPlugin.sendError(player, "Du bist nicht berechtigt Grundstueck-Schilder aufzustellen.");
                event.setCancelled(true);
                return;
            }

            String lotName = event.getLine(1);

            if (lotName.equals("")) {
                LotManagerPlugin.sendError(player, "Kein Grundstueckname angegeben.");
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
                LotManagerPlugin.sendError(player, "Ungueltiger Weltname: \"" + worldName + "\"");
                event.setCancelled(true);
                return;
            }
            Integer worldId;
            worldId = plugin.lots.getWorldId(world);
            if (!plugin.lots.existsLot(worldId, lotName)) {
                LotManagerPlugin.sendError(player, "\"" + lotName + "\" ist kein Grundstueck!");
                event.setCancelled(true);
                return;
            }

            event.setLine(1, lotName);
            sign.update(true);

            if (event.getLine(0).equalsIgnoreCase("[tplot]")) {
                plugin.addSign(lotName, new LotManagerSign(event.getBlock(), true));
            } else {
                plugin.addSign(lotName, new LotManagerSign(event.getBlock(), false));
            }

            plugin.refreshSigns(lotName, true);

            LotManagerPlugin.sendInfo(player, "Grundstueck-Schild aufgestellt.");
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
                final UUID lotOwner = plugin.getLotSignOwner(lotName, block.getWorld());
                if (lotOwner != null) {
                    if (plugin.hasEssentials
                            && LotManagerPlugin.hasPermission(player, "essentials.seen")
                            && LotManagerPlugin.hasPermission(player, "lotmanager.user.sign.seen")) {
                        plugin.getServer().dispatchCommand(player, "seen " + plugin.getServer().getOfflinePlayer(lotOwner).getName());
                    } else {
                        LotManagerPlugin.sendInfo(player, "Das Grundstueck \"" + lotName + "\" gehoert \"" + sign.getLine(2) + "\".");
                    }
                } else {
                    if (LotManagerPlugin.hasPermission(player, "lotmanager.user.get")) {
                        LotManagerPlugin.sendInfo(player, "Dieses Grundstueck ist unbewohnt. Schreib \"/lot get " + lotName + "\" um es zu beanspruchen.");
                    } else {
                        LotManagerPlugin.sendInfo(player, "Dieses Grundstueck ist unbewohnt.");
                    }
                }
            }
        }
    }
}
