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
package org.emblow.envelofy.domain;

/**
 *
 * @author Nicholas J Emblow
 */
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "app_setting")
public class Setting {
    @Id
    @Column(name = "setting_key")  // Avoid using reserved word 'key'
    private String key;
    
    @NotNull
    @Column(name = "setting_value", length = 1000)
    private String value;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_type")  // More explicit column naming
    private SettingType type;
    
    @Column(length = 500)  // Set appropriate length for description
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_category")  // More explicit column naming
    private SettingCategory category = SettingCategory.GENERAL;

    
    public enum SettingType {
        STRING,
        PASSWORD,
        NUMBER,
        BOOLEAN,
        URL
    }
    
    public enum SettingCategory {
        GENERAL,
        LLM,
        APPEARANCE,
        SECURITY,
        INTEGRATION
    }
    
    // Constructors
    public Setting() {}
    
    public Setting(String key, String value, SettingType type, SettingCategory category) {
        this.key = key;
        this.value = value;
        this.type = type;
        this.category = category;
    }
    
    public Setting(String key, String value, SettingType type, SettingCategory category, String description) {
        this(key, value, type, category);
        this.description = description;
    }
    
    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public SettingType getType() { return type; }
    public void setType(SettingType type) { this.type = type; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public SettingCategory getCategory() { return category; }
    public void setCategory(SettingCategory category) { this.category = category; }
}