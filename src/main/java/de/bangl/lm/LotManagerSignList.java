/*
 * Copyright (C) 2013 BangL <henno.rickowski@googlemail.com>
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.bukkit.block.Block;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class LotManagerSignList {
    
    private HashMap<String, ArrayList<LotManagerSign>> signs = new HashMap<String, ArrayList<LotManagerSign>>();
    
    public void LotManagerSignList() {
        
    }
    
    public Boolean containsKey(String key) {
        return signs.containsKey(key);
    }
    
    public Set<String> getKeys() {
        return signs.keySet();
    }
    
    public ArrayList<Block> getBlocks(String key) {
        ArrayList<Block> result = new ArrayList<Block>();
        for (LotManagerSign sign: signs.get(key)) {
            result.add(sign.getBlock());
        }
        return result;
    }
    
    public ArrayList<LotManagerSign> getSigns(String key) {
        return signs.get(key);
    }

    public void put(String key, ArrayList<LotManagerSign> signs) {
        this.signs.put(key, signs);
    }

    public void remove(String key) {
        this.signs.remove(key);
    }

    public void clear() {
        this.signs.clear();
    }
}
