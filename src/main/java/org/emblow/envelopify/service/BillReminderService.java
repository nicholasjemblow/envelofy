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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.emblow.envelopify.domain.BillReminder;
import org.emblow.envelopify.repositry.BillReminderRepository;
import java.time.LocalDate;
import java.util.List;

@Service
public class BillReminderService {
    private final BillReminderRepository repository;

    public BillReminderService(BillReminderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BillReminder create(BillReminder reminder) {
        return repository.save(reminder);
    }

    @Transactional
    public void update(Long id, BillReminder updated) {
        BillReminder existing = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Bill reminder not found"));
            
        existing.setDescription(updated.getDescription());
        existing.setAmount(updated.getAmount());
        existing.setDueDate(updated.getDueDate());
        existing.setReminderDays(updated.getReminderDays());
        existing.setPaid(updated.isPaid());
        existing.setEnvelope(updated.getEnvelope());
        
        repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public void markAsPaid(Long id) {
        BillReminder reminder = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Bill reminder not found"));
        reminder.setPaid(true);
        repository.save(reminder);
    }

    public List<BillReminder> getUpcoming() {
        return repository.findByPaidFalseOrderByDueDateAsc();
    }

    public List<BillReminder> getOverdue() {
        return repository.findByPaidFalseAndDueDateBefore(LocalDate.now());
    }

    public List<BillReminder> getAllForEnvelope(Long envelopeId) {
        return repository.findByEnvelopeId(envelopeId);
    }

    @Scheduled(cron = "0 0 9 * * *") // Run daily at 9 AM
    public void checkReminders() {
        List<BillReminder> reminders = repository.findByPaidFalseOrderByDueDateAsc();
        for (BillReminder reminder : reminders) {
            if (reminder.needsReminder()) {
                // TODO: Send notification
                System.out.println("Reminder needed for: " + reminder.getDescription());
            }
        }
    }
}
