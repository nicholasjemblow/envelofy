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
package org.emblow.envelopify.ui.components;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.notification.Notification;
import org.emblow.envelopify.service.llm.OllamaService;
import java.util.concurrent.CompletableFuture;
import com.vaadin.flow.component.Key;
import org.emblow.envelopify.service.llm.LLMService;

public class ChatComponent extends VerticalLayout {
    private final LLMService llmService;
    private final VerticalLayout messagesLayout;
    private final TextField inputField;
    private final Button sendButton;

    public ChatComponent(LLMService llmService) {

          this.llmService= llmService;

        // Configure main layout
        setHeightFull();
        setWidth("800px");
        addClassName("chat-component");
        getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-m)");

        // Messages area
        messagesLayout = new VerticalLayout();
        messagesLayout.setSpacing(true);
        messagesLayout.setPadding(true);
        messagesLayout.setHeightFull();
        messagesLayout.getStyle()
            .set("overflow-y", "auto")
            .set("flex-grow", "1");

        // Input area
        HorizontalLayout inputLayout = new HorizontalLayout();
        inputLayout.setWidthFull();
        inputLayout.setPadding(true);
        inputLayout.setSpacing(true);
        inputLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        inputLayout.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "0 0 var(--lumo-border-radius-l) var(--lumo-border-radius-l)");

        inputField = new TextField();
        inputField.setWidthFull();
        inputField.setPlaceholder("Ask about your finances...");
        inputField.addKeyPressListener(Key.ENTER, e -> handleSend());

        sendButton = new Button(new Icon(VaadinIcon.PAPERPLANE));
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> handleSend());

        inputLayout.add(inputField, sendButton);
        inputLayout.setFlexGrow(1, inputField);

        add(messagesLayout, inputLayout);
        setFlexGrow(1, messagesLayout);
    }

    private void handleSend() {
        String message = inputField.getValue().trim();
        if (message.isEmpty()) return;

        // Add user message
        addMessage(message, true);
        inputField.clear();

        // Disable input while processing
        setInputEnabled(false);

        // Process message asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                String response = llmService.processUserQuery(message);
                getUI().ifPresent(ui -> ui.access(() -> {
                    addMessage(response, false);
                    setInputEnabled(true);
                }));
            } catch (Exception e) {
                getUI().ifPresent(ui -> ui.access(() -> {
                    Notification.show("Error processing message: " + e.getMessage(),
                        3000, Notification.Position.MIDDLE);
                    setInputEnabled(true);
                }));
            }
        });
    }

    private void addMessage(String text, boolean isUser) {
        Div messageDiv = new Div();
        messageDiv.addClassName(isUser ? "user-message" : "assistant-message");
        messageDiv.getStyle()
            .set("background-color", isUser ? 
                "var(--lumo-primary-color)" : "var(--lumo-contrast-5pct)")
            .set("color", isUser ? 
                "var(--lumo-primary-contrast-color)" : "var(--lumo-body-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("max-width", "80%")
            .set("margin", isUser ? "0 0 var(--lumo-space-m) auto" : "0 auto var(--lumo-space-m) 0");

        Span content = new Span(text);
        messageDiv.add(content);
        messagesLayout.add(messageDiv);
        
        // Scroll to bottom
        messagesLayout.getElement().executeJs(
            "this.scrollTop = this.scrollHeight;"
        );
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }
}
