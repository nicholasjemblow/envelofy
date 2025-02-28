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
package org.emblow.envelofy.service;

/**
 *
 * @author Nicholas J Emblow
 */
import org.emblow.envelofy.domain.Envelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.emblow.envelofy.domain.InsufficientFundsException;
import org.emblow.envelofy.domain.Transaction;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.exception.BusinessException;
import org.emblow.envelofy.exception.EnvelopeException;
import org.emblow.envelofy.exception.ValidationException;
import org.emblow.envelofy.repository.EnvelopeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class EnvelopeService {
        private static final Logger log = LoggerFactory.getLogger(EnvelopeService.class);
    private final EnvelopeRepository envelopeRepository;
    private final SecurityService securityService;
    public EnvelopeService(
        EnvelopeRepository envelopeRepository,
        SecurityService securityService
    ) {
        this.envelopeRepository = envelopeRepository;
        this.securityService = securityService;
    }


    public List<Envelope> getAllEnvelopes() {
        User currentUser = securityService.getCurrentUser();
        return envelopeRepository.findByOwner(currentUser);
    }

    @Transactional
    public Envelope getEnvelope(Long id) {
        User currentUser = securityService.getCurrentUser();
        return envelopeRepository.findByIdAndOwnerWithTransactions(id, currentUser)
            .orElseThrow(() -> new RuntimeException("Envelope not found"));
    }


    @Transactional
    public Envelope createEnvelope(String name, BigDecimal initialAllocation) {
        validateEnvelopeCreation(name, initialAllocation);
        
        User currentUser = securityService.getCurrentUser();
        Envelope envelope = new Envelope(name, initialAllocation, currentUser);
        
        try {
            return envelopeRepository.save(envelope);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("DUPLICATE_ENVELOPE", 
                "An envelope with this name already exists");
        }
    }
    private void validateEnvelopeCreation(String name, BigDecimal initialAllocation) {
        Map<String, String> violations = new HashMap<>();
        
        if (name == null || name.trim().isEmpty()) {
            violations.put("name", "Name is required");
        } else if (name.length() > 100) {
            violations.put("name", "Name must be less than 100 characters");
        }
        
        if (initialAllocation == null) {
            violations.put("initialAllocation", "Initial allocation is required");
        } else if (initialAllocation.compareTo(BigDecimal.ZERO) < 0) {
            violations.put("initialAllocation", 
                "Initial allocation cannot be negative");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
     @Transactional
    public void reallocate(Long sourceId, Long targetId, BigDecimal amount) {
        validateReallocation(sourceId, targetId, amount);
        
        User currentUser = securityService.getCurrentUser();
        
        Envelope source = envelopeRepository.findByIdAndOwner(sourceId, currentUser)
            .orElseThrow(() -> new EnvelopeException(
                EnvelopeException.ENVELOPE_NOT_FOUND, 
                "Source envelope not found"
            ));
            
        Envelope target = envelopeRepository.findByIdAndOwner(targetId, currentUser)
            .orElseThrow(() -> new EnvelopeException(
                EnvelopeException.ENVELOPE_NOT_FOUND, 
                "Target envelope not found"
            ));

        if (sourceId.equals(targetId)) {
            throw new BusinessException("INVALID_OPERATION", 
                "Cannot move money to the same envelope");
        }

        if (!source.canSpend(amount)) {
            throw new EnvelopeException(
                EnvelopeException.INSUFFICIENT_FUNDS,
                String.format("Insufficient funds in %s: required %.2f, available %.2f",
                    source.getName(), amount, source.getAvailable())
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
    
    private void validateReallocation(Long sourceId, Long targetId, BigDecimal amount) {
        Map<String, String> violations = new HashMap<>();
        
        if (sourceId == null) {
            violations.put("sourceId", "Source envelope ID is required");
        }
        
        if (targetId == null) {
            violations.put("targetId", "Target envelope ID is required");
        }
        
        if (amount == null) {
            violations.put("amount", "Amount is required");
        } else if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            violations.put("amount", "Amount must be positive");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
    
    @Transactional
    public void flush() {
        envelopeRepository.flush();
    }


    @Transactional
    public void deleteEnvelope(Long sourceId, Long targetId) {
        validateEnvelopeDeletion(sourceId, targetId);
        
        User currentUser = securityService.getCurrentUser();

        Envelope source = envelopeRepository.findByIdAndOwner(sourceId, currentUser)
            .orElseThrow(() -> new EnvelopeException(
                EnvelopeException.ENVELOPE_NOT_FOUND, 
                "Source envelope not found"
            ));
            
        Envelope target = envelopeRepository.findByIdAndOwner(targetId, currentUser)
            .orElseThrow(() -> new EnvelopeException(
                EnvelopeException.ENVELOPE_NOT_FOUND, 
                "Target envelope not found"
            ));

        if (sourceId.equals(targetId)) {
            throw new BusinessException("INVALID_OPERATION", 
                "Cannot transfer to the same envelope");
        }

        // Transfer allocation and spent amounts
        target.allocate(source.getAllocated());
        if (source.getSpent().compareTo(BigDecimal.ZERO) > 0) {
            target.spend(source.getSpent());
        }

        // Update transactions to point to target envelope
        source.getTransactions().forEach(tx -> tx.setEnvelope(target));

        try {
            envelopeRepository.save(target);
            envelopeRepository.deleteById(sourceId);
        } catch (Exception e) {
            throw new BusinessException("DELETE_FAILED", 
                "Failed to delete envelope: " + e.getMessage());
        }
    }

    private void validateEnvelopeDeletion(Long sourceId, Long targetId) {
        Map<String, String> violations = new HashMap<>();
        
        if (sourceId == null) {
            violations.put("sourceId", "Source envelope ID is required");
        }
        
        if (targetId == null) {
            violations.put("targetId", "Target envelope ID is required");
        }
        
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }
}