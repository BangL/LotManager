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
public class LotGroup {
    private String groupId;
    private Integer limit;
    private Double lotPrice;

    /**
     *
     * @param id
     * @param limit
     * @param lotPrice
     */
    public LotGroup(String groupId, Integer limit, Double lotPrice) {
        this.groupId = groupId;
        this.limit = limit;
        this.lotPrice = lotPrice;
    }

    public String getId() {
        return this.groupId;
    }

    public Integer getLimit() {
        return this.limit;
    }

    public Double getLotPrice() {
        return this.lotPrice;
    }
}
