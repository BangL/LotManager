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

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import de.bangl.blessentials.utils.UUIDFetcher;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class LotManagerPlugin extends JavaPlugin {
    public PluginManager pm;
    public Server server;
    public LotManagerDatabase lots;
    public WorldGuardWrapper wg;
    public Essentials ess;
    public Economy eco;
    public String dataFolder = "plugins" + File.separator + "LotManager";
    public File signsFile = new File(this.dataFolder + File.separator + "signs");
    public boolean hasEssentials = false;

    private final LotManagerListener eventListener = new LotManagerListener(this);

    private LotManagerSignList Signs = new LotManagerSignList();

    private boolean setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> economyProvider;
            economyProvider = getServer().getServicesManager().getRegistration(
                              net.milkbowl.vault.economy.Economy.class);
            if (economyProvider != null) {
                eco = economyProvider.getProvider();
            }

            return (eco != null);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     *
     */
    @Override
    public void onEnable() {
        this.server = getServer();
        this.pm = this.server.getPluginManager();

        Plugin ess = pm.getPlugin("Essentials");
        if (ess != null && ess instanceof Essentials) {
            this.hasEssentials = true;
            this.ess = (Essentials) ess;
        }

        if (!setupEconomy() ) {
            logError("Disabled due to no Vault dependency found!");
            setEnabled(false);
            return;
        }
        try {
            this.lots = new LotManagerDatabase();
        } catch (Exception e3) {
            logError(e3.getMessage());
        }
        new File(this.dataFolder).mkdir();
        loadConfig();
        try {
            this.lots.sqlClass();
        } catch (ClassNotFoundException e) {
            logError("MySQL Class not loadable!");
            setEnabled(false);
        }
        try {
            this.lots.connect();
        } catch (SQLException e) {
            logError("MySQL connection failed!");
            setEnabled(false);
        }
        if (this.lots.getConnection() == null) {
            logInfo("Connection failed.");
            setEnabled(false);
            return;
        }
        try {
            if (!this.lots.checkTables()) {
                logInfo("Tables not found. created.");
                this.lots.Disconnect();
                setEnabled(false);
                return;
            }
        } catch (ClassNotFoundException e) {
            logError("Tablecheck failed!");
            setEnabled(false);
        } catch (SQLException e) {
            logError("Tablecheck failed!");
            setEnabled(false);
        }
        try {
            this.lots.load();
        } catch (ClassNotFoundException e) {
            logError(e.getMessage());
        } catch (SQLException e) {
            logError(e.getMessage());
        }
        try {
            this.wg = new WorldGuardWrapper(this.pm, this.server.getWorlds());
            if (this.wg == null) {
                logError("WorldGuardWrapper is null :O");
                setEnabled(false);
                return;
            }
            if (this.wg.getWG() == null) {
                logError("WorldGuardPlugin is null :O");
                setEnabled(false);
                return;
            }
            for (World world : this.server.getWorlds()) {
                if (world == null) {
                    logError("World is null :O");
                    setEnabled(false);
                    return;
                }

                if (this.wg.getRegionManager(world) == null) {
                    logError("RegionManager of " + world.getName() + " is null :O");
                    setEnabled(false);
                    return;
                }
            }
        } catch (ClassNotFoundException e1) {
            logError("Wasn't able get a connection to the WorldGuard API.");
            setEnabled(false);
            return;
        }
        if (this.signsFile.exists()) {
            loadSigns();
        }
        this.pm.registerEvents(this.eventListener, this);
    }

    public void loadConfig() {
        getConfig().addDefault("Database.URL", "localhost");
        getConfig().addDefault("Database.Port", "3306");
        getConfig().addDefault("Database.Username", "root");
        getConfig().addDefault("Database.Password", "password");
        getConfig().addDefault("Database.Database", "minecraft");
        getConfig().addDefault("Database.Table_Prefix", "lm_");
        getConfig().options().copyDefaults(true);
        saveConfig();
        this.lots.url = getConfig().getString("Database.URL");
        this.lots.port = getConfig().getString("Database.Port");
        this.lots.username = getConfig().getString("Database.Username");
        this.lots.password = getConfig().getString("Database.Password");
        this.lots.tablePref = getConfig().getString("Database.Table_Prefix");
        this.lots.database = getConfig().getString("Database.Database");
    }

    /**
     *
     */
    @Override
    public void onDisable() {
        try {
            this.getLogger().log(Level.INFO, "Saving WorldGuard regions...");
            this.wg.save();
            this.getLogger().log(Level.INFO, "Saving Lots...");
            this.lots.save();
            this.getLogger().log(Level.INFO, "Saving Signs...");
            saveSigns();
        } catch (ClassNotFoundException e) {
            logError(e.getMessage());
        } catch (SQLException e) {
            logError(e.getMessage());
        } catch (StorageException e) {
            logError(e.getMessage());
        }
    }

    /**
     *
     * @param sender
     * @param cmd
     * @param label
     * @param args
     * @return
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player;
        player = null;
        if (sender instanceof Player) {
            player = (Player)sender;
        }
        if (args.length == 0) {
            return false;
        }
        if (cmd.getName().equalsIgnoreCase("lotmanager")
                || cmd.getName().equalsIgnoreCase("lotadmin")
                || cmd.getName().equalsIgnoreCase("lm")) {
            String command = args[0];
            if (command.equalsIgnoreCase("save")) {
                if (!hasPermission(sender, "lotmanager.admin.save")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                saveLots(sender);
                return true;
            } else if (command.equalsIgnoreCase("reload")) {
                if (!hasPermission(sender, "lotmanager.admin.reload")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                reloadLots(sender);
                return true;
            } else if (command.equalsIgnoreCase("update")
                    || command.equalsIgnoreCase("redefine")) {
                if(!hasPermission(sender, "lotmanager.admin.update")) {
                        sendError(sender, "No Permission!");
                        return false;
                }
                if (args.length > 4) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 3) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                if (args.length == 4) {
                    updateLot(args[3], args[1], args[2], sender);
                } else {
                    updateLot("world", args[1], args[2], sender);
                }
                return true;
            } else if (command.equalsIgnoreCase("define")
                    || command.equalsIgnoreCase("create")) {
                if(!hasPermission(sender, "lotmanager.admin.define")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 4) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 3) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                if (args.length == 4) {
                    defineLot(args[3], args[1], args[2], sender);
                } else {
                    defineLot("world", args[1], args[2], sender);
                }
                return true;
            } else if (command.equalsIgnoreCase("undefine")
                    || command.equalsIgnoreCase("delete")
                    || command.equalsIgnoreCase("remove")) {
                if (!hasPermission(sender, "lotmanager.admin.undefine")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 3) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 2) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                if (args.length == 3) {
                    undefineLot(args[2], args[1], sender);
                } else {
                    undefineLot("world", args[1], sender);
                }
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("lotmod")) {
            String command = args[0];
            if (command.equalsIgnoreCase("clear")) {
                if (!hasPermission(sender, "lotmanager.mod.clear")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 3) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 2) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                if (args.length == 3) {
                    clearLot(args[2], args[1], sender);
                } else {
                    clearLot("world", args[1], sender);
                }
                return true;
            } else if (command.equalsIgnoreCase("list")) {
                if (!hasPermission(sender, "lotmanager.mod.list")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                listLots(sender);
                return true;
            } else if (command.equalsIgnoreCase("inactive")) {
                if (!hasPermission(sender, "lotmanager.mod.inactive")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                getInactiveLotList(sender);
                return true;
            } else if (command.equalsIgnoreCase("allocate")) {
                if (!hasPermission(sender, "lotmanager.mod.allocate")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 4) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 3) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }

                UUID uuid = null;
                try {
                    uuid = UUIDFetcher.getUUIDOf(args[2]);
                } catch (Exception ex) {
                    logError(ex.getMessage());
                }
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + args[2] + " could not be found.");
                } else {
                    if (args.length == 4) {
                        allocateLot(args[3], args[1], args[2], uuid, player);
                    } else {
                        allocateLot("world", args[1], args[2], uuid, player);
                    }
                }
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("lotgroup")) {
            String command = args[0];
            if (command.equalsIgnoreCase("update")
                    || command.equalsIgnoreCase("redefine")) {
                if(!hasPermission(sender, "lotmanager.admin.group.update")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 4) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 4) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                updateLotGroup(args[1], args[2], args[3], sender);
                return true;
            } else if (command.equalsIgnoreCase("define")
                    || command.equalsIgnoreCase("create")) {
                if(!hasPermission(sender, "lotmanager.admin.group.define")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 4) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 4) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                defineLotGroup(args[1], args[2], args[3], sender);
                return true;
            } else if (command.equalsIgnoreCase("undefine")
                    || command.equalsIgnoreCase("delete")
                    || command.equalsIgnoreCase("remove")) {
                if(!hasPermission(sender, "lotmanager.admin.group.undefine")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 2) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 2) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                undefineLotGroup(args[1], sender);
                return true;
            }
             else if (command.equalsIgnoreCase("list")) {
                if (!hasPermission(sender, "lotmanager.admin.group.list")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                listLotGroups(sender);
                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("lot")) {
            String command = args[0];
            if (command.equalsIgnoreCase("get")) {
                if (!hasPermission(sender, "lotmanager.user.get")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (player == null) {
                    sendError(sender, "this command can only be run by a player");
                    return false;
                }
                if (args.length > 3) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length == 1) {
                    Lot lot = getNextFreeLot("world");
                    if (lot == null) {
                        sendError(sender, "Kein freies Grundstueck gefunden.");
                    } else {
                        this.tpToLot(player, "world", lot);
                    }
                } else if (args.length == 2) {
                    String lotname = args[1];
                    this.getLot(player, lotname);
                } else {
                    String lotname = args[1];
                    String worldname = args[2];
                    this.getLot(player, lotname, worldname);
                }
                return true;
            } else if (command.equalsIgnoreCase("price")) {
                if (!hasPermission(sender, "lotmanager.user.get")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (player == null) {
                    sendError(sender, "this command can only be run by a player");
                    return false;
                }
                if (args.length > 3) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 2) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                if (args.length == 2) {
                    String lotname = args[1];
                    getLotPrice("world", lotname, player);
                } else {
                    String lotname = args[1];
                    String worldname = args[2];
                    getLotPrice(worldname, lotname, player);
                }
                return true;
            } else if (command.equalsIgnoreCase("home")
                    || command.equalsIgnoreCase("homes")) {
                if (!hasPermission(sender, "lotmanager.user.home")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (player == null) {
                    sender.sendMessage("this command can only be run by a player");
                    return false;
                }
                if (args.length > 1) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                myLots(player);
                return true;
            } else if (command.equalsIgnoreCase("has")) {
                if (!hasPermission(sender, "lotmanager.user.has")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (args.length > 2) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length < 2) {
                    sendError(sender, "Not enough arguments!");
                    return false;
                }
                UUID uuid = null;
                try {
                    uuid = UUIDFetcher.getUUIDOf(args[1]);
                } catch (Exception ex) {
                    logError(ex.getMessage());
                }
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + args[1] + " could not be found.");
                } else {
                    hasLot(sender, args[1], uuid);
                }
                return true;
            } else if (command.equalsIgnoreCase("tphome")
                    || command.equalsIgnoreCase("tp")) {
                if (!hasPermission(sender, "lotmanager.user.tp")) {
                    sendError(sender, "No Permission!");
                    return false;
                }
                if (player == null) {
                    sender.sendMessage("this command can only be run by a player");
                    return false;
                }
                if (args.length > 2) {
                    sendError(sender, "Too many arguments!");
                    return false;
                }
                if (args.length == 2) {
                    Lot home = getHomeLot(player, args[1]);
                    if (home == null) {
                        player.sendMessage("Kein Home-Grundstueck in " + args[1] + " gefunden");
                    } else {
                        tpToLot(player, args[1], home);
                    }
                } else {
                    Lot home = getHomeLot(player, "world");
                    if (home == null) {
                        player.sendMessage("Kein Home-Grundstueck gefunden");
                    } else {
                        tpToLot(player, "world", home);
                    }
                }
                return true;
            }
        }
        return false;
    }

    public void saveLots(CommandSender sender) {
        try {
            this.lots.save();
            sendInfo(sender, "Lots saved.");
        } catch (ClassNotFoundException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        } catch (SQLException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void reloadLots(CommandSender sender) {
        try {
            this.lots.load();
            sendInfo(sender, "Lots reloaded.");
        } catch (ClassNotFoundException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        } catch (SQLException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void listLots(CommandSender sender) {
        HashMap<String, LotGroup> thegroups = this.lots.getAllLotGroups();
        HashMap<String, Lot> thelots = this.lots.getAllLots();

        if (thegroups.isEmpty()) {
            sendInfo(sender, "No lotgroups defined.");
        }

        if (thelots.isEmpty()) {
            sendInfo(sender, "No lots defined.");
        }

        for (LotGroup lotgroup: thegroups.values()) {
            sendInfo(sender, lotgroup.getId() + ":");
            for (Lot lot : thelots.values()) {
                if (lotgroup.getId().equals(lot.getGroup().getId())) {
                    sendInfo(sender, " - " + lot.getId());
                }
            }
        }
    }

    public void listLotGroups(CommandSender sender) {
        HashMap<String, LotGroup> thegroups = this.lots.getAllLotGroups();

        if (thegroups.isEmpty()) {
            sendInfo(sender, "No lotgroups defined.");
        }

        for (LotGroup lotgroup: thegroups.values()) {
            sendInfo(sender, lotgroup.getId());
        }
    }

    public void updateLotGroup(String id, String limit, String price, CommandSender sender) {
        try {
            if (!this.lots.existsLotGroup(id)) {
                sendError(sender, "\"" + id + "\" is not defined as a LotGroup.");
                return;
            }
            if (!isInteger(limit)) {
                sendError(sender, "\"" + limit + "\" is not a number.");
                return;
            }
            if (!isDouble(price)) {
                sendError(sender, "\"" + price + "\" is not a valid price.");
                return;
            }
            this.lots.updateLotGroup(id, Integer.parseInt(limit), Double.parseDouble(price));
            sendInfo(sender, "LotGroup \"" + id + "\" is now updated.");
        } catch (NumberFormatException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void defineLotGroup(String id, String limit, String price, CommandSender sender) {
        try {
            if (this.lots.existsLotGroup(id)) {
                sendError(sender, "\"" + id + "\" is already defined as a lot group.");
                return;
            }
            if (!isInteger(limit)) {
                sendError(sender, "\"" + limit + "\" is not a number.");
                return;
            }
            if (!isDouble(price)) {
                sendError(sender, "\"" + price + "\" is not a valid price.");
                return;
            }
            this.lots.defineLotGroup(id, Integer.parseInt(limit), Double.parseDouble(price));
            if(this.Signs.containsKey(id)) {
                refreshSigns(id);
            }
            sendInfo(sender, "\"" + id + "\" is now defined as a lot group.");
        } catch (NumberFormatException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void undefineLotGroup(String id, CommandSender sender) {
        try {
            if (!this.lots.existsLotGroup(id)) {
                sendError(sender, "\"" + id + "\" is not a lot group.");
                return;
            }
            this.lots.undefineLotGroup(id);
            sendInfo(sender, "\"" + id + "\" is no longer defined as a lot group.");
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void updateLot(String world, String id, String group, CommandSender sender) {
        try {
            World tmpworld;
            try {
                tmpworld = this.server.getWorld(world);
            } catch(Exception e) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            if (tmpworld == null) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            ProtectedRegion region = wg.getRegionManager(this.server.getWorld(world)).getRegion(id);
            if (region == null) {
                sendError(sender, "\"" + id + "\" is not a region.");
                return;
            }
            Integer WorldId = this.lots.getWorldId(this.server.getWorld(world));
            if (!this.lots.existsLot(WorldId, id)) {
                sendError(sender, "\"" + id + "\" is not defined as a lot.");
                return;
            }
            this.lots.updateLot(WorldId, id, group);
            if(this.Signs.containsKey(id)) {
                refreshSigns(id);
            }
            sendInfo(sender, "Region \"" + id + "\" is now updated.");
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void defineLot(String world, String id, String group, CommandSender sender) {
        try {
            World tmpworld;
            try {
                tmpworld = this.server.getWorld(world);
            } catch(Exception e) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            if (tmpworld == null) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            ProtectedRegion region = wg.getRegionManager(this.server.getWorld(world)).getRegion(id);
            if (region == null) {
                sendError(sender, "\"" + id + "\" is not a region.");
                return;
            }
            Integer WorldId = this.lots.getWorldId(this.server.getWorld(world));
            if (this.lots.existsLot(WorldId, id)) {
                sendError(sender, "\"" + id + "\" is already defined as a lot.");
                return;
            }
            this.lots.defineLot(WorldId, id, group);
            if(this.Signs.containsKey(id)) {
                refreshSigns(id);
            }
            sendInfo(sender, "Region \"" + id + "\" is now defined as a lot.");
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void undefineLot(String world, String id, CommandSender sender) {
        try {
            World tmpworld;
            try {
                tmpworld = this.server.getWorld(world);
            } catch(Exception e) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            if (tmpworld == null) {
                sendError(sender, "\"" + world + "\" is not a world.");
                return;
            }
            ProtectedRegion region = wg.getRegionManager(this.server.getWorld(world)).getRegion(id);
            if (region == null) {
                sendError(sender, "\"" + id + "\" is not a region.");
                return;
            }
            Integer WorldId = this.lots.getWorldId(this.server.getWorld(world));
            if (!this.lots.existsLot(WorldId, id)) {
                sendError(sender, "\"" + id + "\" is not a lot.");
                return;
            }
            this.lots.undefineLot(id, WorldId);
            sendInfo(sender, "Region \"" + id + "\" is no longer defined as a lot.");
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void clearLot(String world, String id, CommandSender sender) {
        World tmpworld;
        try {
            tmpworld = this.server.getWorld(world);
        } catch(Exception e) {
            sendError(sender, "\"" + world + "\" is not a world.");
            return;
        }
        if (tmpworld == null) {
            sendError(sender, "\"" + world + "\" is not a world.");
            return;
        }

        Integer WorldId = this.lots.getWorldId(this.server.getWorld(world));
        if (!this.lots.existsLot(WorldId, id)) {
            sendError(sender, "\"" + id + "\" is not a lot.");
            return;
        }

        // Alte Owner auslesen (fürs log)
        DefaultDomain oldOwners = wg.getRegionManager(this.server.getWorld(world)).getRegion(id).getOwners();
        String oldOwnernames = "";
        
        // Get Name List
        for (UUID uuid: oldOwners.getUniqueIds()) {
            oldOwnernames = oldOwnernames + " " + getServer().getOfflinePlayer(uuid).getName();
        }
        oldOwnernames = oldOwnernames.trim();
        // Owner und Member löschen
        DefaultDomain owners = new DefaultDomain();
        wg.getRegionManager(this.server.getWorld(world)).getRegion(id).setOwners(owners);
        wg.getRegionManager(this.server.getWorld(world)).getRegion(id).setMembers(owners);
        //wg.getRegionManager(world).save();
        if(this.Signs.containsKey(id)) {
            refreshSigns(id);
        }
        sendInfo(sender, "\"" + id + "\" is now free again.");
        logInfo("\"" + id + "\" is now free again. Old owner(s): " + oldOwnernames);
    }

    public void hasLot(CommandSender sender, String name, UUID uuid) {
        try {
            List<Lot> userlots;
            userlots = new ArrayList<Lot>();
            List<World> worlds;
            worlds = this.getServer().getWorlds();
            for (World world: worlds) {
                Integer WorldId;
                WorldId = this.lots.getWorldId(world);
                Collection<ProtectedRegion> regions;
                regions = wg.getRegionManager(world).getRegions().values();
                for (ProtectedRegion region: regions) {
                    if (this.lots.existsLot(WorldId, region.getId())) {
                        Set<UUID> owners = region.getOwners().getUniqueIds();
                        for (UUID owner: owners) {
                            if (uuid.equals(owner)) {
                                userlots.add(this.lots.getLot(WorldId, region.getId()));
                            }
                        }
                    }
                }
            }
            if (!userlots.isEmpty()) {
                List<LotGroup> groups = new ArrayList<LotGroup>();
                for (Lot lot: userlots) {
                    LotGroup group = lot.getGroup();
                    if (!groups.contains(group.getId())) {
                        groups.add(group);
                    }
                }
                sendInfo(sender, name + " owns:");
                for (LotGroup group: groups) {
                    sendInfo(sender, group.getId() + ":");
                    for (Lot lot: userlots) {
                        if (lot.getGroup().getId().equals(group.getId())) {
                            sendInfo(sender, " - " + lot.getId());
                        }
                    }
                }
            } else {
                sendInfo(sender, name + " doesn't own any lot.");
            }
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void myLots(Player player) {
        try {
            List<Lot> userlots;
            userlots = new ArrayList<Lot>();
            List<World> worlds = server.getWorlds();
            for (World world: worlds) {
                Integer WorldId = this.lots.getWorldId(world);
                Collection<ProtectedRegion> regions = wg.getRegionManager(world).getRegions().values();
                for (ProtectedRegion region: regions) {
                    if (this.lots.existsLot(WorldId, region.getId())) {
                        Set<UUID> owners = region.getOwners().getUniqueIds();
                        for (UUID owner: owners) {
                            if (player.getUniqueId().equals(owner)) {
                                userlots.add(this.lots.getLot(WorldId, region.getId()));
                            }
                        }
                    }
                }
            }
            if (!userlots.isEmpty()) {
                List<LotGroup> groups = new ArrayList<LotGroup>();
                for (Lot lot: userlots) {
                    LotGroup group = lot.getGroup();
                    if (!groups.contains(group.getId())) {
                        groups.add(group);
                    }
                }
                sendInfo(player, "You're the proud owner of:");
                for (LotGroup group: groups) {
                    sendInfo(player, group.getId() + ":");
                    for (Lot lot: userlots) {
                        if (lot.getGroup().getId().equalsIgnoreCase(group.getId())) {
                            sendInfo(player, " - " + lot.getId());
                        }
                    }
                }
            } else {
                sendInfo(player, "You don't own any lot.");
            }
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(player, e.getMessage());
        }
    }

    public Lot getNextFreeLot(String worldname) {
        World world = getServer().getWorld(worldname);
        if (world != null) {
            Integer WorldId = this.lots.getWorldId(world);
            Collection<ProtectedRegion> regions = wg.getRegionManager(world).getRegions().values();
            for (ProtectedRegion region: regions) {
                if (this.lots.existsLot(WorldId, region.getId())) {
                    DefaultDomain owners = region.getOwners();
                    if (owners == null || owners.getUniqueIds().isEmpty()) {
                        return this.lots.getLot(WorldId, region.getId());
                    }
                }
            }
        }
        return null;
    }
    
    public Lot getHomeLot(Player player, String worldname) {
        World world = getServer().getWorld(worldname);
        if (world != null) {
            Integer WorldId = this.lots.getWorldId(world);
            Collection<ProtectedRegion> regions = wg.getRegionManager(world).getRegions().values();
            for (ProtectedRegion region: regions) {
                if (this.lots.existsLot(WorldId, region.getId())) {
                    Set<UUID> owners = region.getOwners().getUniqueIds();
                    for (UUID owner: owners) {
                        if (player.getUniqueId().equals(owner)) {
                            return this.lots.getLot(WorldId, region.getId());
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public void tpToLot(Player player, String worldname, Lot lot) {
        Location loc = getTpLocationOfLot(lot);
        if (loc == null) {
            sendError(player, "Es wurde ein Problem festgestellt: Das Grundstueck " + lot.getId() + " hat kein Teleport-Schild.");
        } else {
            player.teleport(loc);
        }
    }
    
    public Location getTpLocationOfLot(Lot lot) {
        ArrayList<LotManagerSign> signs = this.Signs.getSigns(lot.getId());
        if (signs == null) {
            return null;
        }
        for (LotManagerSign sign: signs) {
            if (sign.isTpSign()) {
                Block block = sign.getBlock();
                Location result = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
                switch (((org.bukkit.material.Sign) block.getState().getData()).getFacing()) {
                    case DOWN:
                    case UP:
                    case SELF:
                        break;
                    case EAST:
                    case EAST_NORTH_EAST:
                        result.setX(result.getX() + 1);
                        result.setYaw(90);
                        break;
                    case NORTH:
                    case NORTH_EAST:
                    case NORTH_NORTH_EAST:
                    case NORTH_NORTH_WEST:
                    case NORTH_WEST:
                        result.setZ(result.getZ() - 1);
                        result.setYaw(0);
                        break;
                    case SOUTH:
                    case SOUTH_EAST:
                    case SOUTH_SOUTH_EAST:
                    case SOUTH_SOUTH_WEST:
                    case SOUTH_WEST:
                        result.setZ(result.getZ() + 1);
                        result.setYaw(-180);
                        break;
                    case WEST:
                    case WEST_NORTH_WEST:
                    case WEST_SOUTH_WEST:
                        result.setX(result.getX() - 1);
                        result.setYaw(-90);
                        break;
                }
                return result;
            }
        }
        return null;
    }

    public void getLot(Player player, String id) {
        this.getLot(player, id, player.getWorld());
    }

    public void getLot(Player player, String id, String worldname) {
        // Valid world?
        World world = null;
        try {
            world = this.server.getWorld(worldname);
        } catch(Exception e) {
            sendError(player, "\"" + worldname + "\" is not a world.");
            return;
        }
        if (world == null) {
            sendError(player, "\"" + worldname + "\" is not a world.");
            return;
        }
        this.getLot(player, id, world);
    }

    public void getLot(Player player, String id, World world) {
        Integer WorldId = this.lots.getWorldId(world);

        // Valid lot?
        if (!this.lots.existsLot(WorldId, id)) {
            sendError(player, "\"" + id + "\" is not a lot.");
            return;
        }
        Lot lot = this.lots.getLot(WorldId, id);

        // Valid region?
        ProtectedRegion region = this.wg.getRegionManager(world).getRegion(id);
        if (region == null) {
            sendError(player, "\"" + id + "\" is not a region.");
            return;
        }

        // lot free?
        if (region.getOwners().size() > 0) {
            sendError(player, "The lot \"" + id + "\" is already given away.");
            return;
        }

        LotGroup group = lot.getGroup();
        if (!player.hasPermission("lotmanager.user.get." + group.getId().toLowerCase())
                && !player.hasPermission("lotmanager.user.get.*")) {
            sendError(player, "You don't have the permission to get a lot in the lot-group \"" + group.getId()  + "\".");
            return;
        }

        // can the user afford this?
        Double lotprice = group.getLotPrice();
        if (lotprice > 0) {
            try {
                if (eco.getBalance(player) < lotprice) {
                    sendError(player, "You cant afford this.");
                    return;
                }
            } catch(Exception e) {
                logError(e.getMessage());
                sendError(player, "We have a problem here, sry.");
                return;
            }
        }

        // limit reached?
        Integer count = 0;
        List<World> worlds = getServer().getWorlds();
        for (World _world: worlds) {
            Integer _WorldId = this.lots.getWorldId(_world);
            Collection<ProtectedRegion> regions = this.wg.getRegionManager(_world).getRegions().values();
            for (ProtectedRegion _region: regions) {
                if (this.lots.existsLot(_WorldId, _region.getId())) {
                    if (this.lots.getLot(_WorldId, _region.getId()).getGroup().getId().equalsIgnoreCase(group.getId())) {
                        Set<UUID> owners = _region.getOwners().getUniqueIds();
                        for (UUID owner: owners) {
                            if (player.getUniqueId().equals(owner)) {
                                count += 1;
                            }
                        }
                    }
                }
            }
        }
        if (count.intValue() >= group.getLimit().intValue()) {
            sendError(player, "You already reached the current lot limit for the lot-group \"" + group.getId()  + "\". (" + group.getLimit() + ")");
            return;
        }

        // charge price for lot
        try {
            eco.withdrawPlayer(player, group.getLotPrice()); 
        } catch(Exception e) {
            logError(e.getMessage());
            sendError(player, "We have a problem here, sry.");
            return;
        }

        // Add the user as owner to wg
        DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(this.wg.getWG().wrapPlayer(player));
        this.wg.getRegionManager(world).getRegion(id).setOwners(owners);
        //DependencyUtils.getRegionManager(world).save();

        // Refresh signs for the lot
        if (this.Signs.containsKey(id)) {
            refreshSigns(id);
        }

        // Success
        sendInfo(player, "You're now the proud owner of \"" + id + "\".");
        logInfo(player.getName() + " is now the proud owner of \"" + id + "\".");
    }

    public void allocateLot(String worldname, String id, String name, UUID uuid, Player sender) {
        // Valid world?
        World world = null;
        try {
            world = this.server.getWorld(worldname);
        } catch(Exception e) {
            sendError(sender, "\"" + worldname + "\" is not a world.");
            return;
        }
        if (world == null) {
            sendError(sender, "\"" + worldname + "\" is not a world.");
            return;
        }

        Integer WorldId = this.lots.getWorldId(world);
        // Valid lot?
        if (!this.lots.existsLot(WorldId, id)) {
            sendError(sender, "\"" + id + "\" is not a lot.");
            return;
        }
        Lot lot = this.lots.getLot(WorldId, id);

        // Valid region?
        ProtectedRegion region = this.wg.getRegionManager(world).getRegion(id);
        if (region == null) {
            sendError(sender, "\"" + id + "\" is not a region.");
            return;
        }

        // lot free?
        if (region.getOwners().size() > 0) {
            sendError(sender, "The lot \"" + id + "\" is already given away.");
            return;
        }

        // Add the user as owner to wg
        DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(uuid);
        this.wg.getRegionManager(world).getRegion(id).setOwners(owners);
        //DependencyUtils.getRegionManager(world).save();

        // Refresh signs for the lot
        if (this.Signs.containsKey(id)) {
            refreshSigns(id);
        }

        // Success
        sendInfo(sender, name + " is now the proud owner of \"" + id + "\".");
        logInfo(name + " is now the proud owner of \"" + id + "\".");
        if (getServer().getOfflinePlayer(uuid).isOnline()) {
            sendInfo(getServer().getPlayer(uuid), "You're now the proud owner of \"" + id + "\".");
        }
    }

    public void getLotPrice(String worldname, String id, Player player) {
        try {
            // Valid world?
            World world;
            try {
                world = this.server.getWorld(worldname);
            } catch(Exception e) {
                sendError(player, "\"" + worldname + "\" is not a world.");
                return;
            }
            if (world == null) {
                sendError(player, "\"" + worldname + "\" is not a world.");
                return;
            }
            Integer WorldId = this.lots.getWorldId(world);

            // Valid lot?
            if (!this.lots.existsLot(WorldId, id)) {
                sendError(player, "\"" + id + "\" is not a lot.");
                return;
            }
            Lot lot = this.lots.getLot(WorldId, id);

            // Valid region?
            ProtectedRegion region = this.wg.getRegionManager(world).getRegion(id);
            if (region == null) {
                sendError(player, "\"" + id + "\" is not a region.");
                return;
            }

            // lot free?
            if (region.getOwners().size() > 0) {
                sendError(player, "The lot \"" + id + "\" is already given away.");
                return;
            }

            // can the user afford this?
            Double lotprice = lot.getGroup().getLotPrice();
            if (lotprice > 0) {
                try {
                    sendInfo(player, "The lot \"" + id + "\" is availible for " + eco.format(lotprice) + ".");
                } catch(Exception e) {
                    logError(e.getMessage());
                    sendError(player, "We have a problem here, sry.");
                }
            } else {
                sendInfo(player, "The lot \"" + id + "\" is for free.");
            }
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(player, e.getMessage());
        }
    }

    public void getInactiveLotList(CommandSender sender) {
        try {
            if (this.hasEssentials) {

                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date());
                cal.add(Calendar.HOUR, -(24 * 7 * 2)); // Vor vier Wochen
                cal.set(Calendar.HOUR, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                Boolean foundone = false;
                for (World world : this.getServer().getWorlds()) {
                    for (Lot lot : this.lots.getAllLots().values()) {
                        final ProtectedRegion wgregion = this.wg.getRegionManager(world).getRegion(lot.getId());
                        if (wgregion != null) {
                            DefaultDomain owners = wgregion.getOwners();
                            if (owners != null) {
                                for (UUID player : owners.getUniqueIds()) {
                                    User essuser = this.ess.getUser(player);
                                    if (essuser != null) {
                                        final Date lastonline = new Date(essuser.getLastLogin());
                                        if (lastonline.before(cal.getTime())) {
                                            this.sendInfo(sender, lot.getId() + " - " + essuser.getDisplayName() + " (" + lastonline.toString() + ")");
                                            foundone = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!foundone) {
                    this.sendInfo(sender, "All lots given away seem to be actively used.");
                }
            } else {
                sendError(sender, "Sorry, you need \"Essentials\" to use this feature.");
            }
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void checkSigns() { 
        Set<String> keys = Signs.getKeys();
        Iterator<String> i = keys.iterator();
        while(i.hasNext()) {
            String lotName = i.next();
            ArrayList<Block> blocks = this.Signs.getBlocks(lotName);
            for(Block block : blocks) {
                Chunk chunk = block.getChunk();
                World world = block.getWorld();
                if(!world.isChunkLoaded(chunk)) {
                    world.loadChunk(chunk);
                }
                final BlockState bState = block.getState();
                if(!(bState instanceof Sign)) {
                    this.removeSign(lotName, block);
                    return;
                }
            }
        }
    }

    public void refreshSigns(String lotName) {
        refreshSigns(lotName, false);
    }

    public void refreshSigns(String lotName, Boolean newSign) {
        refreshSigns(this.Signs.getBlocks(lotName), lotName);
    }

    public void refreshSigns(ArrayList<Block> arrayList, String lotName) {
        for(Block block : arrayList) {
            Chunk chunk = block.getChunk();
            World world = block.getWorld();
            if(!world.isChunkLoaded(chunk)) {
                world.loadChunk(chunk);
            }

            final BlockState bState = block.getState();
            if(!(bState instanceof Sign)) {
                this.removeSign(lotName, block);
                return;
            }

            // Get all owners of the region
            Set<UUID> owners = wg.getRegionManager(world).getRegion(lotName).getOwners().getUniqueIds();

            // Cut the Lotname
            String tLotName = null;
            if(lotName.length() >= 15) {
                tLotName = lotName;
            } else {
                tLotName = lotName.replaceAll("(&([a-f0-9]))", "\u00A7$2");
            }
            final String mLotName = tLotName;

            final String mPlayerName;
            if (owners.size() > 0) {
                // Get just the first owner in the list
                String playerName = "Unknown";
                playerName = getServer().getOfflinePlayer(owners.toArray(new UUID[]{})[0]).getName();

                // Cut the Playername
                String tPlayerName = null;
                if(playerName.length() >= 15) {
                    tPlayerName = playerName;
                } else {
                    tPlayerName = playerName.replaceAll("(&([a-f0-9]))", "\u00A7$2");
                }

                mPlayerName = (ChatColor.RED + tPlayerName);
            } else {
                // Try to get this lot
                Lot lot = lots.getLot(lots.getWorldId(world), lotName);

                Double price = lot.getGroup().getLotPrice();
                if (price > 0) {
                    try {
                        mPlayerName = (ChatColor.GREEN + formatPrice(price));
                    } catch(Exception e) {
                        logError(e.getMessage());
                        return;
                    }
                } else {
                    mPlayerName = (ChatColor.GREEN + "Free!");
                }
            }

            getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    Sign sign = (Sign) bState;
                    sign.setLine(0, "");
                    sign.setLine(1, mLotName);
                    sign.setLine(2, mPlayerName);
                    sign.setLine(3, "");
                    sign.update();
                }
            });
        }
    }

    public boolean isLotSign(Block block) {
        for(String lotName : this.Signs.getKeys()) {
            ArrayList<Block> blocks = this.Signs.getBlocks(lotName);
            if(blocks.contains(block)) {
                return true;
            }
        }
        return false;
    }

    public UUID getLotSignOwner(String lotName, World world) {
        Set<UUID> owners = wg.getRegionManager(world).getRegion(lotName).getOwners().getUniqueIds();
        if (!owners.isEmpty()) {
            final UUID playeruuid = owners.toArray(new UUID[]{})[0];
            final OfflinePlayer player = getServer().getOfflinePlayer(playeruuid);
            if (player == null) {
                return playeruuid;
            } else {
                return player.getUniqueId();
            }
        }
        return null;
    }

    public String getLotSignLotName(Block block) {
        for(String lotName : this.Signs.getKeys()) {
            ArrayList<Block> blocks = this.Signs.getBlocks(lotName);
            if(blocks.contains(block)) {
                return lotName;
            }
        }
        return null;
    }

    public void removeSign(Block block) {
        for(String lotName : this.Signs.getKeys()) {
            ArrayList<Block> blocks = this.Signs.getBlocks(lotName);
            if(blocks.contains(block)) {
                removeSign(lotName, block);
                return;
            }
        }
    }

    public void removeSign(String lotName, Block block) {
        ArrayList<LotManagerSign> signs = this.Signs.getSigns(lotName);
        //How could this happen?
        if(signs == null) {
            logError("Unexpected Error 1.");
            return;
        }
        if(signs.size()-1 < 1) {
            this.Signs.remove(lotName);
        } else {
            signs.remove(block);
            this.Signs.put(lotName, signs);
        }
        this.saveSigns();
    }

    public void addSign(String lotName, LotManagerSign sign) {
        ArrayList<LotManagerSign> signs = this.Signs.getSigns(lotName);
        if(signs == null) {
            ArrayList<LotManagerSign> newSigns = new ArrayList<LotManagerSign>();
            newSigns.add(sign);
            this.Signs.put(lotName, newSigns);
        } else {
            signs.add(sign);
            this.Signs.put(lotName, signs);
        }
        this.saveSigns();
    }

    public void saveSigns() {
        String store = "<";
        Set<String> keys = this.Signs.getKeys();
        Iterator<String> i = keys.iterator();
        while(i.hasNext()) {
            String lot = i.next();
            store += lot + ";";
            ArrayList<LotManagerSign> signs = this.Signs.getSigns(lot);
            for(LotManagerSign sign : signs) {
                Block block = sign.getBlock();
                store += Integer.toString(block.getX()) + ";";
                store += Integer.toString(block.getY()) + ";";
                store += Integer.toString(block.getZ()) + ";";
                store += block.getWorld().getName() + ";";
                store += sign.isTpSign().toString() + ";";
            }
            store = store.substring(0, store.length()-1)+">" + System.getProperty("line.separator") + "<";
        }
        store = store.substring(0, store.length()-1);
        try {
            boolean createNewFile = signsFile.createNewFile();
            BufferedWriter vout;
            vout = new BufferedWriter(new FileWriter(signsFile));
            vout.write(store);
            vout.close();
        } catch (IOException ex) {
            logError(ex.getMessage());
        } catch (SecurityException ex) {
            logError(ex.getMessage());
        }
    }

    public void loadSigns() {
        byte[] buffer = new byte[(int) signsFile.length()];
        this.Signs.clear();
        BufferedInputStream f = null;
        try {
            f = new BufferedInputStream(new FileInputStream(signsFile));
            f.read(buffer);
        } catch (FileNotFoundException ex) {
            logError(ex.getMessage());
        } catch (IOException ex) {
            logError(ex.getMessage());
        } finally {
            if (f != null) {
                try { f.close(); } catch (IOException ignored) { }
            }
        }
        String store = new String(buffer);
        String[] data = new String[6];
        if(store.isEmpty()) {
            return;
        }
        while(store.contains("<")) {
            int cutBegin = store.indexOf('<');
            int cutEnd = store.indexOf('>');
            String storeEntry = store.substring(cutBegin+1, cutEnd);
            data[0] = storeEntry.substring(0, storeEntry.indexOf(';'));
            storeEntry = storeEntry.substring(storeEntry.indexOf(';')+1);
            int i = 1;
            while(storeEntry.contains(";") || i == 5 || i == 6) {
                if(i == 6) {
                    String lot = data[0];
                    int x = Integer.parseInt(data[1]);
                    int y = Integer.parseInt(data[2]);
                    int z = Integer.parseInt(data[3]);
                    String world = data[4];
                    Boolean isTpSign = Boolean.parseBoolean(data[5]);

                    Block dataBlock = getServer().getWorld(world).getBlockAt(x, y, z);
                    LotManagerSign sign = new LotManagerSign(dataBlock, isTpSign);

                    ArrayList<LotManagerSign> signs = this.Signs.getSigns(lot);
                    if(signs == null) {
                        ArrayList<LotManagerSign> newSignList = new ArrayList<LotManagerSign>();
                        newSignList.add(sign);
                        this.Signs.put(lot, newSignList);
                    } else {
                        signs.add(sign);
                        this.Signs.put(lot, signs);
                    }
                    refreshSigns(data[0]);
                    i = 1;

                    if(!storeEntry.contains(";")) {
                        break;
                    }
                }
                if(i == 5 && !storeEntry.contains(";")) {
                    data[i] = storeEntry;
                } else {
                    data[i] = storeEntry.substring(0, storeEntry.indexOf(';'));
                }
                storeEntry = storeEntry.substring(storeEntry.indexOf(';')+1);
                i++;
            }
            store = store.substring(cutEnd+1);
        }
    }

    public void logInfo(String message) {
        this.getLogger().log(Level.INFO, "[LotManager] {0}", message);
    }

    public void logError(String message) {
        this.getLogger().log(Level.SEVERE, "[LotManager] {0}", message);
    }

    public static boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public static void sendInfo(Player player, String message) {
        player.sendMessage(ChatColor.DARK_GREEN + "[LotManager] " + message);
    }

    public static void sendError(Player player, String message) {
        player.sendMessage(ChatColor.RED + "[LotManager] " + message);
    }

    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.DARK_GREEN + "[LotManager] " + message);
    }

    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + "[LotManager] " + message);
    }

    public boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    public boolean isDouble(String input) {
        try {
            Double.parseDouble(input);
            return true;
        } catch(Exception e) {
            return false;
        }
    }
    
    public String formatPrice(Double price) {
        String result = "";
        result = String.format(Locale.GERMANY, "%.2f", price);
        result = StringUtils.removeEnd(result, ".00");
        result = StringUtils.removeEnd(result, ",00");
        result = result + " " + eco.currencyNamePlural();
        return result;
    }
}
