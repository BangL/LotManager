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

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import java.util.HashMap;
import java.util.List;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class WorldGuardWrapper {

    private WorldGuardPlugin wg;
    private HashMap<String, RegionManager> rm;

    public WorldGuardWrapper(PluginManager pm, List<World> worlds) throws ClassNotFoundException {
        Plugin wgp = pm.getPlugin("WorldGuard");
        if (wgp == null || !(wgp instanceof WorldGuardPlugin)) {
            throw new ClassNotFoundException();
        }
        this.wg = ((WorldGuardPlugin)wgp);

        this.rm = new HashMap<String, RegionManager>();
        for (World world : worlds) {
            RegionManager r = this.wg.getRegionManager(world);
            this.rm.put(world.getName(), r);
        }
    }

    public void save() throws ProtectionDatabaseException {
        for (RegionManager trm : this.rm.values()) {
            trm.save();
        }
    }

    public WorldGuardPlugin getWG() {
        return this.wg;
    }

    public RegionManager getRegionManager(World world) {
        RegionManager result = null;
        result = (RegionManager)this.rm.get(world.getName());
        return result;
    }
}
