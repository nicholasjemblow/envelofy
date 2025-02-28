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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.emblow.envelofy.domain.BillReminder;
import org.emblow.envelofy.repository.BillReminderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.Account;
import org.emblow.envelofy.domain.Envelope;
import org.emblow.envelofy.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BillReminderService {
    private static final Logger log = LoggerFactory.getLogger(BillReminderService.class);
    
    private final BillReminderRepository repository;
    private final SecurityService securityService;
    private final EnvelopeService envelopeService;
    private final AccountService accountService;

    public BillReminderService(
        BillReminderRepository repository,
        SecurityService securityService,
        EnvelopeService envelopeService,
        AccountService accountService
    ) {
        this.repository = repository;
        this.securityService = securityService;
        this.envelopeService = envelopeService;
        this.accountService = accountService;
    }

    @Transactional
    public BillReminder create(BillReminder reminder) {
        try {
            // Validate envelope and account ownership
            Envelope envelope = envelopeService.getEnvelope(reminder.getEnvelope().getId());
            Account account = accountService.getAccount(reminder.getAccount().getId());
            
            reminder.setEnvelope(envelope);
            reminder.setAccount(account);
            return repository.save(reminder);
            
        } catch (Exception e) {
            log.error("Error creating bill reminder", e);
            throw new RuntimeException("Could not create bill reminder: " + e.getMessage());
        }
    }

    @Transactional
    public void update(Long id, BillReminder updated) {
        try {
            BillReminder existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill reminder not found"));
                
            // Validate envelope and account ownership
            Envelope envelope = envelopeService.getEnvelope(updated.getEnvelope().getId());
            Account account = accountService.getAccount(updated.getAccount().getId());
            
            // Update fields
            existing.setDescription(updated.getDescription());
            existing.setAmount(updated.getAmount());
            existing.setDueDate(updated.getDueDate());
            existing.setReminderDays(updated.getReminderDays());
            existing.setPaid(updated.isPaid());
            existing.setEnvelope(envelope);
            existing.setAccount(account);
            
            repository.save(existing);
            
        } catch (Exception e) {
            log.error("Error updating bill reminder", e);
            throw new RuntimeException("Could not update bill reminder: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long id) {
        try {
            BillReminder reminder = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill reminder not found"));
                
            // Validate ownership through envelope
            envelopeService.getEnvelope(reminder.getEnvelope().getId());
            repository.deleteById(id);
            
        } catch (Exception e) {
            log.error("Error deleting bill reminder", e);
            throw new RuntimeException("Could not delete bill reminder: " + e.getMessage());
        }
    }

    @Transactional
    public void markAsPaid(Long id) {
        try {
            BillReminder reminder = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bill reminder not found"));
                
            // Validate ownership through envelope
            envelopeService.getEnvelope(reminder.getEnvelope().getId());
            reminder.setPaid(true);
            repository.save(reminder);
            
        } catch (Exception e) {
            log.error("Error marking bill reminder as paid", e);
            throw new RuntimeException("Could not mark bill reminder as paid: " + e.getMessage());
        }
    }

    public List<BillReminder> getUpcoming() {
        try {
            User currentUser = securityService.getCurrentUser();
            return repository.findByPaidFalseOrderByDueDateAsc().stream()
                .filter(reminder -> reminder.getEnvelope().getOwner().getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving upcoming bill reminders", e);
            throw new RuntimeException("Could not retrieve upcoming bill reminders: " + e.getMessage());
        }
    }

    public List<BillReminder> getOverdue() {
        try {
            User currentUser = securityService.getCurrentUser();
            return repository.findByPaidFalseAndDueDateBefore(LocalDate.now()).stream()
                .filter(reminder -> reminder.getEnvelope().getOwner().getId().equals(currentUser.getId()))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error retrieving overdue bill reminders", e);
            throw new RuntimeException("Could not retrieve overdue bill reminders: " + e.getMessage());
        }
    }

    public List<BillReminder> getAllForEnvelope(Long envelopeId) {
        try {
            // This validates ownership
            envelopeService.getEnvelope(envelopeId);
            return repository.findByEnvelopeId(envelopeId);
            
        } catch (Exception e) {
            log.error("Error retrieving bill reminders for envelope", e);
            throw new RuntimeException("Could not retrieve bill reminders: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 9 * * *") // Run daily at 9 AM
    public void checkReminders() {
        try {
            User currentUser = securityService.getCurrentUser();
            List<BillReminder> reminders = repository.findByPaidFalseOrderByDueDateAsc();
            
            for (BillReminder reminder : reminders) {
                // Only process reminders for the current user's envelopes
                if (reminder.getEnvelope().getOwner().getId().equals(currentUser.getId()) 
                    && reminder.needsReminder()) {
                    
                    // TODO: Implement notification system
                    log.info("Reminder needed for: {} (Due: {})", 
                        reminder.getDescription(), 
                        reminder.getDueDate());
                }
            }
        } catch (Exception e) {
            log.error("Error checking bill reminders", e);
        }
    }
}
