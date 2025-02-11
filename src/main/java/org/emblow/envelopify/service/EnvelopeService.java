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
package org.emblow.envelopify.service;

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelopify.domain.Envelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.emblow.envelopify.domain.InsufficientFundsException;
import org.emblow.envelopify.domain.Transaction;
import org.emblow.envelopify.repositry.EnvelopeRepository;

@Service
public class EnvelopeService {
    private final EnvelopeRepository envelopeRepository;

    public EnvelopeService(EnvelopeRepository envelopeRepository) {
        this.envelopeRepository = envelopeRepository;
    }

    public List<Envelope> getAllEnvelopes() {
        return envelopeRepository.findAll();
    }

    @Transactional
    public Envelope getEnvelope(Long id) {
        return envelopeRepository.findByIdWithTransactions(id)
            .orElseThrow(() -> new RuntimeException("Envelope not found"));
    }


    @Transactional
    public Envelope createEnvelope(String name, BigDecimal initialAllocation) {
        Envelope envelope = new Envelope(name, initialAllocation);
        return envelopeRepository.save(envelope);
    }

    @Transactional
    public void reallocate(Long sourceId, Long targetId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Envelope source = getEnvelope(sourceId);
        Envelope target = getEnvelope(targetId);
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot move money to the same envelope");
        }
        // Check if source has enough funds
        if (!source.canSpend(amount)) {
            throw new InsufficientFundsException(
                source.getName(),
                amount
            );
        }
        source.withdraw(amount);
        target.allocate(amount);
        envelopeRepository.save(source);
        envelopeRepository.save(target);
    }

    @Transactional
    public Envelope save(Envelope envelope) {
        return envelopeRepository.save(envelope);
    }

    @Transactional
    public Envelope updateEnvelope(Long id, String name) {
        Envelope envelope = getEnvelope(id);
        envelope.setName(name);
        return envelopeRepository.save(envelope);
    }
    @Transactional
public void flush() {
    envelopeRepository.flush();
}

    @Transactional
    public void deleteEnvelope(Long sourceId, Long targetId) {
        Envelope source = getEnvelope(sourceId);
        Envelope target = getEnvelope(targetId);

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot transfer to the same envelope");
        }

        // Transfer the total allocation to the target envelope
        target.allocate(source.getAllocated());

        // Add the spent amount to the target envelope via proper business logic
        // We must add the spent amount AFTER allocating or we might trigger insufficient funds checks
        if (source.getSpent().compareTo(BigDecimal.ZERO) > 0) {
            target.spend(source.getSpent());
        }

        // Update all transactions to point to the target envelope
        List<Transaction> transactions = source.getTransactions();
        for (Transaction transaction : transactions) {
            transaction.setEnvelope(target);
        }

        // Save the target envelope with updated allocations and transactions
        envelopeRepository.save(target);

        // Now we can safely delete the source envelope
        envelopeRepository.deleteById(sourceId);
    }
}