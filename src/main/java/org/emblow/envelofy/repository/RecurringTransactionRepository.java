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

import org.emblow.envelofy.domain.RecurringTransaction;
import org.emblow.envelofy.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import org.emblow.envelofy.domain.TransactionType;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    List<RecurringTransaction> findByNextDueDateBefore(LocalDateTime date);
    List<RecurringTransaction> findByEnvelopeId(Long envelopeId);
    List<RecurringTransaction> findByAccountId(Long accountId);
    List<RecurringTransaction> findByType(TransactionType type);
    List<RecurringTransaction> findByAccountIdAndType(Long accountId, TransactionType type);
}
