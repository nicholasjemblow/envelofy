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
package org.emblow.envelofy.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.ui.MainLayout;
import org.emblow.envelofy.service.SettingService;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ConfigurationView displays application configuration settings.
 * <p>
 * Currently, this view contains a single tab ("LLM Settings") where the user can select a language model service 
 * and configure its settings. The view uses the {@code SettingService} to load and save settings.
 * Settings are stored using key/value pairs.
 * </p>
 * 
 * <p>This view is accessible to anonymous users, so ensure your security configuration permits this.</p>
 * 
 * @author 
 * @version 1.0
 * @since 2025
 */
@Route(value = "config", layout = MainLayout.class)
@PageTitle("Configuration | Envelofy")
@AnonymousAllowed
public class ConfigurationView extends VerticalLayout {

    /**
     * The SettingService used to retrieve and update configuration settings.
     */
    private final SettingService settingService;
    
    @Value("${llm.service.type:jllama}")
    private String currentLLMType;
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    @Value("${openai.chat.endpoint:}")
    private String openaiEndpoint;
    
    @Value("${ollama.endpoint:}")
    private String ollamaEndpoint;
    
    @Value("${ollama.model:}")
    private String ollamaModel;
    
    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.base.url:}")
    private String groqBaseUrl;

    @Value("${groq.chat.model:}")
    private String groqModel;

    /**
     * Constructs a new ConfigurationView.
     *
     * @param settingService the service used to retrieve and update configuration settings
     */
    public ConfigurationView(SettingService settingService) {
        this.settingService = settingService;
        
        addClassName("config-view");
        setSpacing(true);
        setPadding(true);
        
        // Header section with title.
        H2 title = new H2("Configuration");
        title.getStyle().set("margin-top", "0");
        
        // Create a tab for LLM Settings.
        Tab llmTab = new Tab("LLM Settings");
        Tabs tabs = new Tabs(llmTab);
        
        // Create the content area that will hold the configuration components.
        VerticalLayout content = new VerticalLayout();
        content.setSpacing(true);
        content.setPadding(true);
        
        // Initialize content with LLM Settings.
        updateContent(content, llmTab);
        
        // Add a listener to update content when the tab selection changes.
        tabs.addSelectedChangeListener(event -> 
            updateContent(content, event.getSelectedTab())
        );
        
        add(title, tabs, content);
    }
    
    /**
     * Updates the content layout based on the selected tab.
     *
     * @param content     the VerticalLayout where content is displayed
     * @param selectedTab the currently selected tab
     */
    private void updateContent(VerticalLayout content, Tab selectedTab) {
        content.removeAll();
        
        switch (selectedTab.getLabel()) {
            case "LLM Settings" -> createLLMSettings(content);
            // Future configuration sections can be added here.
            default -> { }
        }
    }
    
    /**
     * Creates and adds the LLM Settings configuration components to the provided layout.
     *
     * @param content the layout to which LLM settings components are added
     */
    private void createLLMSettings(VerticalLayout content) {
        H3 llmTitle = new H3("Language Model Configuration");
        
        // ComboBox for selecting the LLM service type.
        ComboBox<String> llmTypeSelect = new ComboBox<>("LLM Service Type");
        llmTypeSelect.setItems("jllama", "chatgpt", "ollama", "groq");
        llmTypeSelect.setValue(currentLLMType != null ? currentLLMType : "jllama");
        
        // Create separate layouts for each LLM configuration.
        VerticalLayout chatgptSettings = createChatGPTSettings();
        VerticalLayout ollamaSettings = createOllamaSettings();
        VerticalLayout groqSettings = createGroqSettings();
        VerticalLayout jllamaSettings = createJLlamaSettings();
        
        // Set initial visibility based on the selected type.
        updateLLMSettingsVisibility(llmTypeSelect.getValue(), chatgptSettings, ollamaSettings, groqSettings, jllamaSettings);
        
        // Add listener to update visibility when the selected type changes.
        llmTypeSelect.addValueChangeListener(event ->
            updateLLMSettingsVisibility(event.getValue(), chatgptSettings, ollamaSettings, groqSettings, jllamaSettings)
        );
        
        // Save button that collects all settings and saves them.
        Button saveButton = new Button("Save Changes", new Icon(VaadinIcon.CHECK), e ->
            saveLLMSettings(llmTypeSelect.getValue(), chatgptSettings, ollamaSettings, groqSettings, jllamaSettings)
        );
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        // Add components to the content layout.
        content.add(
            llmTitle,
            new Paragraph("Choose your preferred language model service and configure its settings."),
            llmTypeSelect,
            chatgptSettings,
            ollamaSettings,
            groqSettings,
            jllamaSettings,
            saveButton
        );
    }
    
    /**
     * Creates the ChatGPT configuration layout.
     *
     * @return a VerticalLayout containing ChatGPT settings fields
     */
    private VerticalLayout createChatGPTSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setVisible(false);
        layout.setSpacing(true);
        
        H3 title = new H3("ChatGPT Settings");
        
        PasswordField apiKeyField = new PasswordField("API Key");
        apiKeyField.setValue(openaiApiKey != null ? openaiApiKey : "");
        apiKeyField.setWidthFull();
        apiKeyField.setHelperText("Your OpenAI API key");
        
        TextField endpointField = new TextField("API Endpoint");
        endpointField.setValue(openaiEndpoint != null ? openaiEndpoint : "https://api.openai.com/v1/chat/completions");
        endpointField.setWidthFull();
        endpointField.setHelperText("ChatGPT API endpoint URL");
        
        layout.add(title, apiKeyField, endpointField);
        return layout;
    }
    
    /**
     * Creates the Ollama configuration layout.
     *
     * @return a VerticalLayout containing Ollama settings fields
     */
    private VerticalLayout createOllamaSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setVisible(false);
        layout.setSpacing(true);
        
        H3 title = new H3("Ollama Settings");
        
        TextField endpointField = new TextField("Endpoint URL");
        endpointField.setValue(ollamaEndpoint != null ? ollamaEndpoint : "http://localhost:11434/api/generate");
        endpointField.setWidthFull();
        endpointField.setHelperText("Local Ollama endpoint (e.g., http://localhost:11434)");
        
        TextField modelField = new TextField("Model Name");
        modelField.setValue(ollamaModel != null ? ollamaModel : "llama2:latest");
        modelField.setWidthFull();
        modelField.setHelperText("Ollama model name (e.g., llama2:latest)");
        
        layout.add(title, endpointField, modelField);
        return layout;
    }
    
    /**
     * Creates the Groq configuration layout.
     *
     * @return a VerticalLayout containing Groq settings fields
     */
    private VerticalLayout createGroqSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setVisible(false);
        layout.setSpacing(true);
        
        H3 title = new H3("Groq Settings");
        
        PasswordField apiKeyField = new PasswordField("API Key");
        apiKeyField.setValue(groqApiKey != null ? groqApiKey : "");
        apiKeyField.setWidthFull();
        apiKeyField.setHelperText("Your Groq API key");
        
        TextField baseUrlField = new TextField("Base URL");
        baseUrlField.setValue(groqBaseUrl != null ? groqBaseUrl : "https://api.groq.com");
        baseUrlField.setWidthFull();
        baseUrlField.setHelperText("Groq API base URL");
        
        TextField modelField = new TextField("Model Name");
        modelField.setValue(groqModel != null ? groqModel : "mixtral-8x7b-32768");
        modelField.setWidthFull();
        modelField.setHelperText("Groq model name");
        
        layout.add(title, apiKeyField, baseUrlField, modelField);
        return layout;
    }
    
    /**
     * Creates the JLlama configuration layout.
     *
     * @return a VerticalLayout containing JLlama configuration information
     */
    private VerticalLayout createJLlamaSettings() {
        VerticalLayout layout = new VerticalLayout();
        layout.setVisible(false);
        layout.setSpacing(true);
        
        H3 title = new H3("JLlama Settings");
        
        Paragraph info = new Paragraph(
                "JLlama runs locally and requires no additional configuration. " +
                "Models will be automatically downloaded when first used."
        );
        
        layout.add(title, info);
        return layout;
    }
    
    /**
     * Updates the visibility of the various LLM settings layouts based on the selected LLM service type.
     *
     * @param selectedType      the selected LLM service type ("jllama", "chatgpt", "ollama", or "groq")
     * @param chatgptSettings   the ChatGPT settings layout
     * @param ollamaSettings    the Ollama settings layout
     * @param groqSettings      the Groq settings layout
     * @param jllamaSettings    the JLlama settings layout
     */
    private void updateLLMSettingsVisibility(
        String selectedType,
        VerticalLayout chatgptSettings,
        VerticalLayout ollamaSettings,
        VerticalLayout groqSettings,
        VerticalLayout jllamaSettings
    ) {
        chatgptSettings.setVisible("chatgpt".equals(selectedType));
        ollamaSettings.setVisible("ollama".equals(selectedType));
        groqSettings.setVisible("groq".equals(selectedType));
        jllamaSettings.setVisible("jllama".equals(selectedType));
    }
    
    /**
     * Gathers the configuration values from the different LLM settings layouts and saves them using the SettingService.
     * Since the SettingService's saveSetting method takes two parameters (key and value),
     * we iterate over the settings map and call saveSetting for each entry.
     *
     * @param selectedType      the selected LLM service type
     * @param chatgptSettings   the ChatGPT settings layout
     * @param ollamaSettings    the Ollama settings layout
     * @param groqSettings      the Groq settings layout
     * @param jllamaSettings    the JLlama settings layout
     */
    private void saveLLMSettings(
        String selectedType,
        VerticalLayout chatgptSettings,
        VerticalLayout ollamaSettings,
        VerticalLayout groqSettings,
        VerticalLayout jllamaSettings
    ) {
        try {
            Map<String, String> newSettings = new HashMap<>();
            
            // Save the LLM service type.
            newSettings.put("llm.service.type", selectedType);
            
            // Save ChatGPT settings.
            if ("chatgpt".equals(selectedType)) {
                PasswordField apiKeyField = (PasswordField) chatgptSettings.getChildren()
                        .filter(c -> c instanceof PasswordField)
                        .findFirst()
                        .orElseThrow();
                TextField endpointField = (TextField) chatgptSettings.getChildren()
                        .filter(c -> c instanceof TextField)
                        .findFirst()
                        .orElseThrow();
                newSettings.put("openai.api.key", apiKeyField.getValue());
                newSettings.put("openai.chat.endpoint", endpointField.getValue());
            }
            
            // Save Ollama settings.
            if ("ollama".equals(selectedType)) {
                // Expect two TextField components.
                var ollamaFields = ollamaSettings.getChildren()
                        .filter(c -> c instanceof TextField)
                        .map(c -> (TextField) c)
                        .collect(Collectors.toList());
                newSettings.put("ollama.endpoint", ollamaFields.get(0).getValue());
                newSettings.put("ollama.model", ollamaFields.get(1).getValue());
            }
            
            // Save Groq settings.
            if ("groq".equals(selectedType)) {
                PasswordField apiKeyField = (PasswordField) groqSettings.getChildren()
                        .filter(c -> c instanceof PasswordField)
                        .findFirst()
                        .orElseThrow();
                var groqFields = groqSettings.getChildren()
                        .filter(c -> c instanceof TextField)
                        .map(c -> (TextField) c)
                        .collect(Collectors.toList());
                newSettings.put("groq.api.key", apiKeyField.getValue());
                newSettings.put("groq.base.url", groqFields.get(0).getValue());
                newSettings.put("groq.chat.model", groqFields.get(1).getValue());
            }
            
            // Instead of calling a saveSettings(Map) method, iterate over each entry and call saveSetting(key, value)
            for (Map.Entry<String, String> entry : newSettings.entrySet()) {
                settingService.saveSetting(entry.getKey(), entry.getValue());
            }
            
            Notification notification = Notification.show(
                    "Settings saved successfully. Please restart the application for changes to take effect.",
                    5000,
                    Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception e) {
            Notification notification = Notification.show(
                    "Error saving settings: " + e.getMessage(),
                    5000,
                    Notification.Position.MIDDLE
            );
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
