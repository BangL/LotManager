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

/**
 *
 * @author BangL <henno.rickowski@googlemail.com>
 */
public class Lot {

    private String lotId;
    private Integer worldId;
    private LotGroup lotGroup;

    /**
     *
     * @param lotId
     * @param worldId
     * @param lotGroup
     */
    public Lot(final String lotId, final Integer worldId, final LotGroup lotGroup) {
        this.lotId = lotId;
        this.worldId = worldId;
        this.lotGroup = lotGroup;
    }

    public String getId() {
        return this.lotId;
    }

    public Integer getWorldId() {
        return this.worldId;
    }

    public LotGroup getGroup() {
        return this.lotGroup;
    }

}
