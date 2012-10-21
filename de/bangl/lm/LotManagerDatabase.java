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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import org.bukkit.World;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class LotManagerDatabase {
    // Initialize database variables
    String url, username, password, tablePref, database, port;

    private HashMap<String, Lot> lots;
    private HashMap<String, LotGroup> lotgroups;
    private HashMap<String, Integer> worlds;

    Connection mConn;

    public boolean checkTables() throws ClassNotFoundException, SQLException {
        sqlClass();
        try {
            connect();
        } catch (SQLException e) {
            return false;
        }

        String query;

        DatabaseMetaData dbm;
        dbm = this.mConn.getMetaData();
        ResultSet tmp;

        // Check for the "lots" table
        tmp = dbm.getTables(null, null, tablePref + "lots", null);
        if (!tmp.next()) {
            query = "CREATE TABLE IF NOT EXISTS `" + tablePref + "lots` ("
                    + "`id` VARCHAR(128) NOT NULL,"
                    + "`world` INT(10) NOT NULL,"
                    + "`group` VARCHAR(128) DEFAULT 'Default',"
                    + "PRIMARY KEY (id, world)"
                    + ") ENGINE = INNODB "
                    + "CHARACTER SET utf8 "
                    + "COLLATE utf8_general_ci ";

            INSERT(query);
        }
        // Check end

        // Check for the "group" column, in the "lots" table
        tmp = dbm.getColumns(null, null, tablePref + "lots", "group");
        if (!tmp.next()) {
            query = "ALTER TABLE `" + tablePref + "lots` ADD "
                    + "`group` VARCHAR(128) DEFAULT 'Default' "
                    + "AFTER `world`;";
            UPDATE(query);
        }
        // Check end

        // Check for the "lotgroups" table
        tmp = dbm.getTables(null, null, tablePref + "lotgroups", null);
        if (!tmp.next()) {
            query = "CREATE TABLE IF NOT EXISTS `" + tablePref + "lotgroups` ("
                    + "`id` VARCHAR(128) NOT NULL,"
                    + "`limit` INT(10) DEFAULT '1',"
                    + "PRIMARY KEY (`id`)"
                    + ") ENGINE = INNODB "
                    + "CHARACTER SET utf8 "
                    + "COLLATE utf8_general_ci ";

            INSERT(query);
        }
        // Check end

        // Check for the "lot_price" column, in the "lotgroups" table
        tmp = dbm.getColumns(null, null, tablePref + "lotgroups", "lot_price");
        if (!tmp.next()) {
            query = "ALTER TABLE `" + tablePref + "lotgroups` ADD "
                    + "`lot_price` DECIMAL(11,2) NOT NULL DEFAULT '0.0';";
            UPDATE(query);
        }
        // Check end

        Disconnect();
        return true;
    }

    public void load() throws ClassNotFoundException, SQLException {
        sqlClass();
        connect();

        String query;

        // Load LotGroups
        lotgroups = new HashMap<>();
        query = "SELECT * FROM `" + tablePref + "lotgroups`";
        ResultSet resultSet = SELECT(query);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            Integer limit = resultSet.getInt("limit");
            Double lotprice = resultSet.getDouble("lot_price");
            lotgroups.put(id, new LotGroup(id, limit, lotprice));
        }
        // Add default group
        lotgroups.put("Default", new LotGroup("Default", 1, 0.0));

        // Load Lots
        this.lots = new HashMap<>();
        query = "SELECT * FROM `" + tablePref + "lots`";
         resultSet = SELECT(query);
        while (resultSet.next()) {
            String id = resultSet.getString("id");
            Integer world = resultSet.getInt("world");
            String lotgroupid = resultSet.getString("group");
            if (lotgroupid.equals("")) {
                lotgroupid = "Default";
            }
            lots.put(id + world.toString(), new Lot(id, world, this.getLotGroup(lotgroupid)));
        }

        // Load Worlds
        worlds = new HashMap<>();
        query = "SELECT * FROM `world`";
        resultSet = SELECT(query);
        while (resultSet.next()) {
            String name = resultSet.getString("name");
            Integer id = resultSet.getInt("id");
            worlds.put(name, id);
        }

        Disconnect();
    }

    public void save() throws ClassNotFoundException, SQLException {
        sqlClass();
        connect();

        String query;

        // Neue Lotsgroups hinzuf�gen / aktualisieren
        for (LotGroup lotgroup: lotgroups.values()) {
            query = "SELECT * FROM `" + tablePref + "lotgroups`" +
                    "WHERE `id` = '" + lotgroup.getId() + "';";
            ResultSet rs = SELECT(query);
            if (!rs.next()) {
                // Lotgroup nicht in der Datenbank, also einf�gen...
                query = "INSERT INTO `" + tablePref + "lotgroups` SET "
                        + "`id` = '" + lotgroup.getId() + "', "
                        + "`lot_price` = '" + lotgroup.getLotPrice() + "', "
                        + "`limit` = '" + lotgroup.getLimit() + "';";
                INSERT(query);
            } else {
                // Lotgroup in der Datenbank. evtl. Limit aktualisieren...
                query = "UPDATE `" + tablePref + "lotgroups` SET "
                        + "`lot_price` = '" + lotgroup.getLotPrice() + "', "
                        + "`limit` = '" + lotgroup.getLimit() + "' "
                        + "WHERE `id` = '" + lotgroup.getId() + "';";
                UPDATE(query);
            }
        }

        HashMap<String, LotGroup> tmplotgroups;
        tmplotgroups = new HashMap<>();
        query = "SELECT * FROM `" + tablePref + "lotgroups`";
        ResultSet rs = SELECT(query);
        while (rs.next()) {
            tmplotgroups.put( rs.getString("id"), new LotGroup(
                    rs.getString("id"),
                    rs.getInt("limit"),
                    rs.getDouble("lot_price")) );
        }

        for (LotGroup lotgroup: tmplotgroups.values()) {
            if (!existsLotGroup(lotgroup.getId())) {
                // Lotgroup nicht im RAM, also l�schen...
                query = "DELETE FROM `" + tablePref + "lotgroups`" +
                        "WHERE `id` = '" + lotgroup.getId() + "'" +
                        "AND `limit` = '" + lotgroup.getLimit() + "';";
                DELETE(query);
            }
        }

        // Neue Lots hinzuf�gen / aktualisieren
        for (Lot lot: lots.values()) {
            query = "SELECT * FROM `" + tablePref + "lots`" +
                    "WHERE `id` = '" + lot.getId() + "'" +
                    "AND `world` = '" + lot.getWorldId() + "';";
            rs = SELECT(query);
            if (!rs.next()) {
                // Lot nicht in der Datenbank, also einf�gen...
                query = "INSERT INTO `" + tablePref + "lots` SET "
                        + "`id` = '" + lot.getId() + "', "
                        + "`group` = '" + lot.getGroup().getId() + "', "
                        + "`world` = '" + lot.getWorldId() + "';";
                INSERT(query);
            } else {
                // Lot in der Datenbank. evtl. LotGroup-ID aktualisieren...
                query = "UPDATE `" + tablePref + "lots` SET "
                        + "`group` = '" + lot.getGroup().getId() + "' "
                        + "WHERE `id` = '" + lot.getId() + "' "
                        + "AND `world` = '" + lot.getWorldId() + "';";
                UPDATE(query);
            }
        }

        HashMap<String, Lot> tmplots;
        tmplots = new HashMap<>();
        query = "SELECT * FROM `" + tablePref + "lots`";
        rs = SELECT(query);
        while (rs.next()) {
            tmplots.put(rs.getString("id"), new Lot(
                    rs.getString("id"),
                    rs.getInt("world"),
                    null));
        }

        for (Lot lot: tmplots.values()) {
            if (!existsLot(lot.getWorldId(), lot.getId())) {
                // Lot nicht im RAM, also l�schen...
                query = "DELETE FROM `" + tablePref + "lots`" +
                        "WHERE `id` = '" + lot.getId() + "'" +
                        "AND `world` = '" + lot.getWorldId() + "';";
                DELETE(query);
            }
        }

        Disconnect();
    }

    public Integer getWorldId(World world) {
        return (Integer)this.worlds.get(world.getName());
    }

    public Integer getWorldId(String world) {
        return (Integer)this.worlds.get(world);
    }

    public boolean existsLot(Integer worldId, String id) {
        return this.lots.containsKey(id + worldId.toString());
    }

    public boolean existsLotGroup(String id) {
        return this.lotgroups.containsKey(id);
    }

    public Lot getLot(Integer worldId, String id) {
        return this.lots.get(id + worldId.toString());
    }

    public LotGroup getLotGroup(String id) {
        return this.lotgroups.get(id);
    }

    public void updateLot(Integer worldId, String id, String groupid) throws Exception {
        LotGroup group = this.lotgroups.get(groupid);
        if (this.lots.containsKey(id + worldId.toString())) {
            this.lots.remove(id + worldId.toString());
            this.lots.put(id + worldId.toString(), new Lot(id, worldId, group));
        } else {
            throw new Exception("\"" + id + "\" is not defined as a lot.");
        }
    }

    public void updateLotGroup(String id, Integer limit, Double lotPrice) throws Exception {
        if (this.lotgroups.containsKey(id)) {
            this.lotgroups.remove(id);
            this.lotgroups.put(id, new LotGroup(id, limit, lotPrice));
        } else {
            throw new Exception("\"" + id + "\" is not defined as a lot group.");
        }
    }

    public void defineLot(Integer worldId, String id, String groupid) throws Exception {
        LotGroup group = this.lotgroups.get(groupid);
        if (!lots.containsKey(id + worldId.toString())) {
            lots.put(id + worldId.toString(), new Lot(id, worldId, group));
        } else {
            throw new Exception("\"" + id + "\" is already defined as a lot.");
        }
    }

    public void defineLotGroup(String id, Integer limit, Double lotPrice) throws Exception {
        if (!lotgroups.containsKey(id)) {
            lotgroups.put(id, new LotGroup(id, limit, lotPrice));
        } else {
            throw new Exception("\"" + id + "\" is already defined as a lot group.");
        }
    }

    public boolean undefineLot(String id, Integer worldId) throws Exception {
        if (lots.containsKey(id + worldId.toString())) {
            lots.remove(id + worldId.toString());
        } else {
            throw new Exception("\"" + id + "\" is not defined as a lot.");
        }
        return true;
    }

    public boolean undefineLotGroup(String id) throws Exception {
        for (Lot lot: this.lots.values()) {
            if (lot.getGroup().getId() == null ? id == null : lot.getGroup().getId().equals(id)) {
                throw new Exception("\"" + id + "\" is not empty.");
            }
        }
        if (lotgroups.containsKey(id)) {
            lotgroups.remove(id);
        } else {
            throw new Exception("\"" + id + "\" is not defined as a lot group.");
        }
        return true;
    }

    public HashMap<String, Lot> getAllLots() {
        return this.lots;
    }

    public HashMap<String, LotGroup> getAllLotGroups() {
        return this.lotgroups;
    }

    public Class<?> sqlClass() throws ClassNotFoundException {
        return Class.forName("com.mysql.jdbc.Driver");
    }

    public boolean connect() throws SQLException {
        this.mConn = DriverManager.getConnection("jdbc:mysql://" + this.url + ":" + this.port + 
                "/" + this.database, this.username, this.password);
        return true;
    }

    public Connection getConnection() {
        return this.mConn;
    }

    // Creater statement
    public Statement createS() throws SQLException {
        Statement stmt = this.mConn.createStatement();
        return stmt;
    }

    // Get ResulSet for SELECT statment
    public ResultSet SELECT(String getQuery) throws SQLException {
        return createS().executeQuery(getQuery);
    }

    // Execute INSERT statement
    public void INSERT(String getQuery) throws SQLException {
        Statement stmt = this.mConn.createStatement();
        stmt.execute(getQuery);
    }

    // Execute DELETE statement
    public void DELETE(String getQuery) throws SQLException {
        Statement stmt = this.mConn.createStatement();
        stmt.execute(getQuery);
    }

    public void UPDATE(String getQuery) throws SQLException {
        Statement stmt = this.mConn.createStatement();
        stmt.execute(getQuery);
    }

    // Close MySQL Connection
    public void Disconnect() throws SQLException {
        this.mConn.close();
    }
}
