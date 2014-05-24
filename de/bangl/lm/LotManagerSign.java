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

import org.bukkit.block.Block;

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class LotManagerSign {
    
    private Block block;
    private Boolean isTpSign = false;
    
    public LotManagerSign(Block block, Boolean isTpSign) {
        this.block = block;
        this.isTpSign = isTpSign;
    }
    
    public Boolean isTpSign() {
        return this.isTpSign;
    }
    
    public Block getBlock() {
        return this.block;
    }
}
