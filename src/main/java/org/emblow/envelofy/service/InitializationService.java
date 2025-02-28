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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.emblow.envelofy.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InitializationService {
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    
    private final AccountRepository accountRepository;
    private final TestDataService testDataService;
    
    public InitializationService(
        AccountRepository accountRepository,
        TestDataService testDataService
    ) {
        this.accountRepository = accountRepository;
        this.testDataService = testDataService;
    }
    /* dont init yet!
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initializeTestData();
    }
    */
    public void initializeTestData() {
        // Only initialize if no accounts exist
        if (accountRepository.count() == 0) {
            log.info("No existing data found. Initializing test data...");
            try {
                testDataService.createTestData();
                log.info("Test data initialization complete.");
            } catch (Exception e) {
                log.error("Error initializing test data", e);
            }
        } else {
            log.info("Existing data found. Skipping test data initialization.");
        }
    }
}
