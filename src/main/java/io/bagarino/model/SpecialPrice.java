/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class SpecialPrice {

    public enum Status {
        WAITING, FREE, PENDING, TAKEN, CANCELLED
    }

    private final int id;
    private final String code;
    private final int priceInCents;
    private final int ticketCategoryId;
    private final Status status;

    public SpecialPrice(@Column("id") int id,
                        @Column("code") String code,
                        @Column("price_cts") int priceInCents,
                        @Column("ticket_category_id") int ticketCategoryId,
                        @Column("status") String status) {
        this.id = id;
        this.code = code;
        this.priceInCents = priceInCents;
        this.ticketCategoryId = ticketCategoryId;
        this.status = Status.valueOf(status);
    }
}
