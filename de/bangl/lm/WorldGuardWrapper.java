package de.bangl.lm;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import java.util.HashMap;
import java.util.List;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public class WorldGuardWrapper {

    private WorldGuardPlugin wg;
    private HashMap<String, RegionManager> rm;

    public WorldGuardWrapper(PluginManager pm, List<World> worlds) throws ClassNotFoundException {
        Plugin wgp = pm.getPlugin("WorldGuard");
        if (wgp == null || !(wgp instanceof WorldGuardPlugin)) {
            throw new ClassNotFoundException();
        }
        this.wg = ((WorldGuardPlugin)wgp);

        this.rm = new HashMap<>();
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

    public WorldGuardPlugin getWG()
    {
        return this.wg;
    }

    public RegionManager getRegionManager(World world) {
        RegionManager result = null;
        result = (RegionManager)this.rm.get(world.getName());
        return result;
    }
}
