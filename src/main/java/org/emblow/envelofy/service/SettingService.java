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
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import org.emblow.envelofy.domain.Setting;
import org.emblow.envelofy.repository.SettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.emblow.envelofy.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class SettingService {
    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    
    private final SettingRepository settingRepository;
    private final SecurityService securityService;
    private final Environment environment;
    private final ContextRefresher contextRefresher;

    public SettingService(
        SettingRepository settingRepository,
        SecurityService securityService,
        Environment environment,
        ContextRefresher contextRefresher
    ) {
        this.settingRepository = settingRepository;
        this.securityService = securityService;
        this.environment = environment;
        this.contextRefresher = contextRefresher;
    }

    @PostConstruct
    public void init() {
        try {
            initializeDefaultSettings();
        } catch (Exception e) {
            log.error("Error initializing settings", e);
        }
    }

    private void initializeDefaultSettings() {
        // Initialize LLM settings (system-wide)
        saveSettingIfNotExists(
            "llm.service.type",
            environment.getProperty("llm.service.type", "jllama"),
            Setting.SettingType.STRING,
            Setting.SettingCategory.LLM,
            "Type of LLM service to use"
        );
        
        saveSettingIfNotExists(
            "openai.api.key",
            environment.getProperty("openai.api.key", ""),
            Setting.SettingType.PASSWORD,
            Setting.SettingCategory.LLM,
            "OpenAI API Key"
        );
        
        // More system-wide settings...
        
        // Initialize default appearance settings (these will be overridden per user)
        saveSettingIfNotExists(
            "appearance.theme.default",
            "light",
            Setting.SettingType.STRING,
            Setting.SettingCategory.APPEARANCE,
            "Default UI Theme"
        );
    }

    @Transactional
    private void saveSettingIfNotExists(
        String key,
        String defaultValue,
        Setting.SettingType type,
        Setting.SettingCategory category,
        String description
    ) {
        try {
            if (!settingRepository.existsById(key)) {
                Setting setting = new Setting(key, defaultValue, type, category, description);
                settingRepository.save(setting);
                log.debug("Created setting: {}", key);
            }
        } catch (Exception e) {
            log.error("Error creating setting: {}", key, e);
        }
    }

    @Transactional
    public void saveSetting(String key, String value) {
        try {
            // Get current user if needed for user-specific setting
            final String settingKey;
            if (isUserSpecificSetting(key)) {
                User currentUser = securityService.getCurrentUser();
                settingKey = getUserSettingKey(currentUser.getId(), key);
            } else {
                settingKey = key;
            }

            // Lookup or create the setting
            Setting setting = settingRepository.findById(settingKey)
                .orElseGet(() -> createNewSetting(settingKey, key));

            setting.setValue(value);
            settingRepository.save(setting);

            // Refresh configuration if needed
            Set<String> refreshedKeys = contextRefresher.refresh();
            log.debug("Refreshed properties: {}", refreshedKeys);

        } catch (Exception e) {
            log.error("Error saving setting: {}", key, e);
            throw new RuntimeException("Could not save setting: " + e.getMessage());
        }
    }

    private Setting createNewSetting(String newKey, String originalKey) {
        Setting newSetting = new Setting();
        newSetting.setKey(newKey);

        // Try to get properties from default setting
        Setting defaultSetting = settingRepository.findById(originalKey).orElse(null);
        if (defaultSetting != null) {
            newSetting.setType(defaultSetting.getType());
            newSetting.setCategory(defaultSetting.getCategory());
            newSetting.setDescription(defaultSetting.getDescription());
        } else {
            // Set some reasonable defaults if no template exists
            newSetting.setType(Setting.SettingType.STRING);
            newSetting.setCategory(Setting.SettingCategory.GENERAL);
        }

        return newSetting;
    }
    public String getSetting(String key) {
        try {
            // For user-specific settings, try user's setting first
            if (isUserSpecificSetting(key)) {
                User currentUser = securityService.getCurrentUser();
                String userKey = getUserSettingKey(currentUser.getId(), key);
                Optional<Setting> userSetting = settingRepository.findById(userKey);
                if (userSetting.isPresent()) {
                    return userSetting.get().getValue();
                }
            }
            
            // Fall back to default setting
            return settingRepository.findById(key)
                .map(Setting::getValue)
                .orElse(null);
                
        } catch (Exception e) {
            log.error("Error retrieving setting: {}", key, e);
            throw new RuntimeException("Could not retrieve setting: " + e.getMessage());
        }
    }

    public String getSetting(String key, String defaultValue) {
        return Optional.ofNullable(getSetting(key)).orElse(defaultValue);
    }

    public Map<String, String> getSettingsByCategory(Setting.SettingCategory category) {
        try {
            Map<String, String> settings = new HashMap<>();
            List<Setting> categorySettings = settingRepository.findByCategory(category);
            
            // First add all non-user-specific settings
            categorySettings.stream()
                .filter(s -> !isUserSpecificSettingKey(s.getKey()))
                .forEach(s -> settings.put(s.getKey(), s.getValue()));
            
            // Then override with user-specific settings if they exist
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                User currentUser = securityService.getCurrentUser();
                String userPrefix = getUserPrefix(currentUser.getId());
                
                categorySettings.stream()
                    .filter(s -> s.getKey().startsWith(userPrefix))
                    .forEach(s -> {
                        String originalKey = s.getKey().substring(userPrefix.length());
                        settings.put(originalKey, s.getValue());
                    });
            }
            
            return settings;
            
        } catch (Exception e) {
            log.error("Error retrieving settings for category: {}", category, e);
            throw new RuntimeException("Could not retrieve settings: " + e.getMessage());
        }
    }

    @Transactional
    public void resetToDefaults() {
        try {
            User currentUser = securityService.getCurrentUser();
            String userPrefix = getUserPrefix(currentUser.getId());
            
            // Find and delete all settings with user's prefix
            settingRepository.findAll().stream()
                .filter(s -> s.getKey().startsWith(userPrefix))
                .forEach(s -> settingRepository.deleteById(s.getKey()));
                
            log.info("Reset user-specific settings for user ID: {}", currentUser.getId());
        } catch (Exception e) {
            log.error("Error resetting settings to defaults", e);
            throw new RuntimeException("Could not reset settings: " + e.getMessage());
        }
    }

    // Helper methods
    private boolean isUserSpecificSetting(String key) {
        return key.startsWith("appearance.") || 
               key.startsWith("preference.") ||
               key.startsWith("notification.");
    }

    private boolean isUserSpecificSettingKey(String key) {
        return key.matches("user\\.[0-9]+\\..*");
    }

    private String getUserPrefix(Long userId) {
        return String.format("user.%d.", userId);
    }

    private String getUserSettingKey(Long userId, String key) {
        return getUserPrefix(userId) + key;
    }
}