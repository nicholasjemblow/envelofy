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
import org.emblow.envelofy.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import org.emblow.envelofy.domain.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByType(Account.AccountType type);
    List<Account> findByInstitution(String institution);
    List<Account> findByOwner(User owner);
    List<Account> findByOwnerAndType(User owner, Account.AccountType type);
    List<Account> findByOwnerAndInstitution(User owner, String institution);
    Optional<Account> findByIdAndOwner(Long id, User owner);
        @Query("SELECT a FROM Account a LEFT JOIN FETCH a.transactions " +
           "WHERE a.id = :id")
    Optional<Account> findByIdWithTransactions(@Param("id") Long id);
}

