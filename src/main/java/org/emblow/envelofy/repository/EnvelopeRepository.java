/*
 * Copyright (C) 2025 Nicholas J Emblow
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
package org.emblow.envelofy.repository;

/**
 *
 * @author Nicholas J Emblow
 */
import java.util.List;
import java.util.Optional;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnvelopeRepository extends JpaRepository<Envelope, Long> {
@Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.transactions WHERE e.id = :id")
Optional<Envelope> findByIdWithTransactions(@Param("id") Long id);
    List<Envelope> findByOwner(User owner);
    Optional<Envelope> findByIdAndOwner(Long id, User owner);
    
    @Query("SELECT e FROM Envelope e LEFT JOIN FETCH e.transactions WHERE e.id = :id AND e.owner = :owner")
    Optional<Envelope> findByIdAndOwnerWithTransactions(@Param("id") Long id, @Param("owner") User owner);
}
