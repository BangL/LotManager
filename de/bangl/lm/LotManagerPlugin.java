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

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    public Economy eco;
    public String dataFolder = "plugins" + File.separator + "LotManager";
    public File signsFile = new File(this.dataFolder + File.separator + "signs");

    private final LotManagerSignListener signListener = new LotManagerSignListener(this);
    private final LotManagerBlockListener blockListener = new LotManagerBlockListener(this);

    private HashMap<String, ArrayList<Block>> signs = new HashMap<>();

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
        } catch (ClassNotFoundException | SQLException e) {
            logError("Tablecheck failed!");
            setEnabled(false);
        }
        try {
            this.lots.load();
        } catch (ClassNotFoundException | SQLException e2) {
            logError(e2.getMessage());
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
        this.pm.registerEvents(this.signListener, this);
        this.pm.registerEvents(this.blockListener, this);
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
            this.wg.save();
            this.lots.save();
            saveSigns();
        }
        catch (ClassNotFoundException | SQLException | ProtectionDatabaseException e) {
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
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        Player player;
        player = null;
        if (sender instanceof Player) {
            player = (Player)sender;
        }

        if (cmd.getName().equalsIgnoreCase("getlot")) {
            if (!hasPermission(sender, "lotmanager.getlot")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (player == null) {
                sendError(sender, "this command can only be run by a player");
                return false;
            }
            if (args.length > 2) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            if (args.length == 1) {
                this.getLot("world", args[0], player);
            } else {
                this.getLot(args[1], args[0], player);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotprice")) {
            if (!hasPermission(sender, "lotmanager.getlot")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (player == null) {
                sendError(sender, "this command can only be run by a player");
                return false;
            }
            if (args.length > 2) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            if (args.length == 1) {
                getLotPrice("world", args[0], player);
            } else {
                getLotPrice(args[1], args[0], player);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("mylot") || cmd.getName().equalsIgnoreCase("mylots")) {
            if (!hasPermission(sender, "lotmanager.mylot")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (player == null) {
                sender.sendMessage("this command can only be run by a player");
                return false;
            }
            if (args.length > 0) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            myLots(player);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotgroupupdate")) {
            if(!hasPermission(sender, "lotmanager.groupupdate")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 3) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 3) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            updateLotGroup(args[0], args[1], args[2], sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotgroupdefine")) {
            if(!hasPermission(sender, "lotmanager.groupdefine")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 3) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 3) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            defineLotGroup(args[0], args[1], args[2], sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotgroupundefine")) {
            if(!hasPermission(sender, "lotmanager.groupundefine")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 1) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            undefineLotGroup(args[0], sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotupdate")) {
            if(!hasPermission(sender, "lotmanager.update")) {
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
            if (args.length == 2) {
                updateLot("world", args[0], args[1], sender);
            } else {
                updateLot(args[2], args[0], args[1], sender);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotdefine")) {
            if(!hasPermission(sender, "lotmanager.define")) {
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
            if (args.length == 2) {
                defineLot("world", args[0], args[1], sender);
            } else {
                defineLot(args[2], args[0], args[1], sender);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lotundefine")) {
            if (!hasPermission(sender, "lotmanager.undefine")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 1) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            if (args.length == 1) {
                undefineLot("world", args[0], sender);
            } else {
                undefineLot(args[1], args[0], sender);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("clearlot")) {
            if (!hasPermission(sender, "lotmanager.clearlot")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 1) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            if (args.length == 1) {
                clearLot("world", args[0], sender);
            } else {
                clearLot(args[1], args[0], sender);
            }
            return true;
        } else if (cmd.getName().equalsIgnoreCase("haslot") || cmd.getName().equalsIgnoreCase("haslots")) {
            if (!hasPermission(sender, "lotmanager.haslot")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 1) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            if (args.length < 1) {
                sendError(sender, "Not enough arguments!");
                return false;
            }
            hasLot(sender, getServer().getPlayer(args[0]));
            return true;
        } else if (cmd.getName().equalsIgnoreCase("listlots")) {
            if (!hasPermission(sender, "lotmanager.listlots")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 0) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            listLots(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("listlotgroups")) {
            if (!hasPermission(sender, "lotmanager.listlotgroups")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 0) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            listLotGroups(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("savelots")) {
            if (!hasPermission(sender, "lotmanager.savelots")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 0) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            saveLots(sender);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("reloadlots")) {
            if (!hasPermission(sender, "lotmanager.reloadlots")) {
                sendError(sender, "No Permission!");
                return false;
            }
            if (args.length > 0) {
                sendError(sender, "Too many arguments!");
                return false;
            }
            reloadLots(sender);
            return true;
        }
        return false;
    }

    public void saveLots(CommandSender sender)
    {
        try
        {
            this.lots.save();
            sendInfo(sender, "Lots saved.");
        } catch (ClassNotFoundException | SQLException e) {
            logError(e.getMessage());
            sendError(sender, e.getMessage());
        }
    }

    public void reloadLots(CommandSender sender) {
        try {
            this.lots.load();
            sendInfo(sender, "Lots reloaded.");
        } catch (ClassNotFoundException | SQLException e) {
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
            if(signs.containsKey(id)) {
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
            if(signs.containsKey(id)) {
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
            if(signs.containsKey(id)) {
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
        DefaultDomain owners = new DefaultDomain();
        wg.getRegionManager(this.server.getWorld(world)).getRegion(id).setOwners(owners);
        //wg.getRegionManager(world).save();
        if(signs.containsKey(id)) {
            refreshSigns(id);
        }
        sendInfo(sender, "\"" + id + "\" is now free again.");
    }

    public void hasLot(CommandSender sender, Player player) {
        try {
            List<Lot> userlots;
            userlots = new ArrayList<>();
            List<World> worlds;
            worlds = this.getServer().getWorlds();
            for (World world: worlds) {
                Integer WorldId;
                WorldId = this.lots.getWorldId(world);
                Collection<ProtectedRegion> regions;
                regions = wg.getRegionManager(world).getRegions().values();
                for (ProtectedRegion region: regions) {
                    if (this.lots.existsLot(WorldId, region.getId())) {
                        Set<String> owners = region.getOwners().getPlayers();
                        for (String owner: owners) {
                            if (player.getName().equalsIgnoreCase(owner)) {
                                userlots.add(this.lots.getLot(WorldId, region.getId()));
                            }
                        }
                    }
                }
            }
            if (!userlots.isEmpty()) {
                List<LotGroup> groups = new ArrayList<>();
                for (Lot lot: userlots) {
                    LotGroup group = lot.getGroup();
                    if (!groups.contains(group.getId())) {
                        groups.add(group);
                    }
                }
                sendInfo(sender, player.getName() + " owns:");
                for (LotGroup group: groups) {
                    sendInfo(sender, group.getId() + ":");
                    for (Lot lot: userlots) {
                        if (lot.getGroup().getId().equals(group.getId())) {
                            sendInfo(sender, " - " + lot.getId());
                        }
                    }
                }
            } else {
                sendInfo(sender, player.getName() + " doesn't own any lot.");
            }
        } catch (Exception e) {
            logError(e.getMessage());
            sendError(player, e.getMessage());
        }
    }

    public void myLots(Player player) {
        try {
            List<Lot> userlots;
            userlots = new ArrayList<>();
            List<World> worlds = server.getWorlds();
            for (World world: worlds) {
                Integer WorldId = this.lots.getWorldId(world);
                Collection<ProtectedRegion> regions = wg.getRegionManager(world).getRegions().values();
                for (ProtectedRegion region: regions) {
                    if (this.lots.existsLot(WorldId, region.getId())) {
                        Set<String> owners = region.getOwners().getPlayers();
                        for (String owner: owners) {
                            if (player.getName().equalsIgnoreCase(owner)) {
                                userlots.add(this.lots.getLot(WorldId, region.getId()));
                            }
                        }
                    }
                }
            }
            if (!userlots.isEmpty()) {
                List<LotGroup> groups = new ArrayList<>();
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

    public void getLot(String worldname, String id, Player player) {
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
                if (eco.getBalance(player.getName()) < lotprice) {
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
        LotGroup group = lot.getGroup();
        for (World _world: worlds) {
            Integer _WorldId = this.lots.getWorldId(_world);
            Collection<ProtectedRegion> regions = this.wg.getRegionManager(_world).getRegions().values();
            for (ProtectedRegion _region: regions) {
                if (this.lots.existsLot(_WorldId, _region.getId())) {
                    if (this.lots.getLot(_WorldId, _region.getId()).getGroup().getId().equalsIgnoreCase(group.getId())) {
                        Set<String> owners = _region.getOwners().getPlayers();
                        for (String owner: owners) {
                            if (player.getName().equalsIgnoreCase(owner)) {
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
            eco.withdrawPlayer(player.getName(), group.getLotPrice()); 
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
        if (this.signs.containsKey(id)) {
            refreshSigns(id);
        }

        // Success
        sendInfo(player, "You're now the proud owner of \"" + id + "\".");
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

    public void checkSigns() { 
        Set<String> keys = signs.keySet();
        Iterator<String> i = keys.iterator();
        while(i.hasNext()) {
            String lotName = i.next();
            ArrayList<Block> blocks = signs.get(lotName);
            for(Block b : blocks) {
                Chunk chunk = b.getChunk();
                World world = b.getWorld();
                if(!world.isChunkLoaded(chunk)) {
                    world.loadChunk(chunk);
                }
                final BlockState bState = b.getState();
                if(!(bState instanceof Sign)) {
                    this.removeSign(lotName, b);
                    return;
                }
            }
        }
    }

    public void refreshSigns(String lotName) {
        refreshSigns(lotName, false);
    }

    public void refreshSigns(String lotName, Boolean newSign) {
        refreshSigns(signs.get(lotName), lotName);
    }

    public void refreshSigns(ArrayList<Block> arrayList, String lotName) {
        for(Block b : arrayList) {
            Chunk chunk = b.getChunk();
            World world = b.getWorld();
            if(!world.isChunkLoaded(chunk)) {
                world.loadChunk(chunk);
            }

            final BlockState bState = b.getState();
            if(!(bState instanceof Sign)) {
                this.removeSign(lotName, b);
                return;
            }

            // Get all owners of the region
            Set<String> owners = wg.getRegionManager(world).getRegion(lotName).getOwners().getPlayers();

            // Try to get this lot
            Lot lot = lots.getLot(lots.getWorldId(world), lotName);

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
                String playerName = owners.toArray(new String[owners.size()])[0];

                // Cut the Playername
                String tPlayerName = null;
                if(playerName.length() >= 15) {
                    tPlayerName = playerName;
                } else {
                    tPlayerName = playerName.replaceAll("(&([a-f0-9]))", "\u00A7$2");
                }

                mPlayerName = (ChatColor.RED + tPlayerName);
            } else {
                Double price = lot.getGroup().getLotPrice();
                if (price > 0) {
                    try {
                        mPlayerName = (ChatColor.GREEN + eco.format(price));
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

    public void removeSign(Block block) {
        for(String lotName : signs.keySet()) {
            ArrayList<Block> blocks = signs.get(lotName);
            if(blocks.contains(block)) {
                removeSign(lotName, block);
                return;
            }
        }
    }

    public void removeSign(String lotName, Block block) {
        ArrayList<Block> blocks = signs.get(lotName);
        //How could this happen?
        if(blocks == null) {
            logError("Unexpected Error 1.");
            return;
        }
        if(blocks.size()-1 < 1) {
            signs.remove(lotName);
        } else {
            blocks.remove(block);
            signs.put(lotName, blocks);
        }
        this.saveSigns();
    }

    public void addSign(String lotName, Block block) {
        ArrayList<Block> blocks = signs.get(lotName);
        if(blocks == null) {
            ArrayList<Block> newBlocks = new ArrayList<>();
            newBlocks.add(block);
            signs.put(lotName, newBlocks);
        } else {
            blocks.add(block);
            signs.put(lotName, blocks);
        }
        this.saveSigns();
    }

    public void saveSigns() {
        String store = "<";
        Set<String> keys = signs.keySet();
        Iterator<String> i = keys.iterator();
        while(i.hasNext()) {
            String lot = i.next();
            store += lot + ";";
            ArrayList<Block> blocks = signs.get(lot);
            for(Block b : blocks) {
                store += Integer.toString(b.getX()) + ";";
                store += Integer.toString(b.getY()) + ";";
                store += Integer.toString(b.getZ()) + ";";
                store += b.getWorld().getName() + ";";
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
        } catch (IOException | SecurityException ex) {
            logError(ex.getMessage());
        }
    }

    public void loadSigns() {
        byte[] buffer = new byte[(int) signsFile.length()];
        signs.clear();
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
        String[] data = new String[5];
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
            while(storeEntry.contains(";") || i == 4 || i == 5) {
                if(i == 5) {
                    String lot = data[0];
                    int x = Integer.parseInt(data[1]);
                    int y = Integer.parseInt(data[2]);
                    int z = Integer.parseInt(data[3]);
                    String world = data[4];

                    Block dataBlock = getServer().getWorld(world).getBlockAt(x, y, z);

                    ArrayList<Block> blocks = signs.get(lot);
                    if(blocks == null) {
                        ArrayList<Block> newBlocksList = new ArrayList<>();
                        newBlocksList.add(dataBlock);
                        signs.put(lot, newBlocksList);
                    } else {
                        blocks.add(dataBlock);
                        signs.put(lot, blocks);
                    }
                    refreshSigns(data[0]);
                    i = 1;

                    if(!storeEntry.contains(";")) {
                        break;
                    }
                }
                if(i == 4 && !storeEntry.contains(";")) {
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

    public void logInfo(String message)
    {
        this.getLogger().log(Level.INFO, "[LotManager] {0}", message);
    }

    public void logError(String message)
    {
        this.getLogger().log(Level.SEVERE, "[LotManager] {0}", message);
    }

    public static boolean hasPermission(Player player, String permission)
    {
        return player.hasPermission(permission);
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public static void sendInfo(Player player, String message) {
        player.sendMessage(ChatColor.DARK_GREEN + "[LotManager] " + message);
    }

    public static void sendError(Player player, String message)
    {
        player.sendMessage(ChatColor.RED + "[LotManager] " + message);
    }

    public static void sendInfo(CommandSender sender, String message)
    {
        sender.sendMessage(ChatColor.DARK_GREEN + "[LotManager] " + message);
    }

    public static void sendError(CommandSender sender, String message)
    {
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
}
