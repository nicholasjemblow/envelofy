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
package org.emblow.envelofy.ui.components;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.emblow.envelofy.domain.ChatSession;
import org.emblow.envelofy.domain.ChatMessage;
import org.emblow.envelofy.service.llm.LLMService;
import org.emblow.envelofy.service.ChatHistoryService;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.emblow.envelofy.ui.components.DataTable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.emblow.envelofy.domain.ChartArtifact;
import org.emblow.envelofy.exception.BusinessException;
import org.emblow.envelofy.repository.ChartArtifactRepository;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

/**
 * A Vaadin component for handling chat interactions with an LLM service,
 * including streaming responses, rendering markdown, charts, and tables.
 */
@SpringComponent
@UIScope
public class ChatComponent extends VerticalLayout {
    private final LLMService llmService;
    private final ChatHistoryService chatHistoryService;
    private final VerticalLayout messagesLayout;
    private final VerticalLayout dataLayout;
    private final TextField inputField;
    private final Button sendButton;
    private final ComboBox<ChatSession> sessionSelector;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatSession currentSession;
    private Div currentAssistantMessage;
    private StringBuilder streamedResponse;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private int retryCount = 0;
    private static final int MAX_RETRIES = 2;
    private boolean isRetrying = false;
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_RESPONSE_SIZE = 1_000_000; // 1MB
    private ChartArtifactRepository chartArtifactRepository;

    public ChatComponent(LLMService llmService, ChatHistoryService chatHistoryService, ChartArtifactRepository chartArtifactRepository) {
        this.llmService = llmService;
        this.chatHistoryService = chatHistoryService;
        this.chartArtifactRepository = chartArtifactRepository;
        // Set up component styling (unchanged)
        setHeightFull();
        setWidth("100%");
        addClassName("chat-component");
        getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-m)");

        HorizontalLayout mainLayout = new HorizontalLayout();
        mainLayout.setHeightFull();
        mainLayout.setWidthFull();
        mainLayout.setSpacing(true);

        VerticalLayout chatContainer = new VerticalLayout();
        chatContainer.setHeightFull();
        chatContainer.setWidth("30%");

        HorizontalLayout sessionControls = new HorizontalLayout();
        sessionControls.setWidthFull();
        sessionControls.setJustifyContentMode(JustifyContentMode.BETWEEN);

        sessionSelector = new ComboBox<>("Chat Sessions");
        sessionSelector.setItems(chatHistoryService.getUserSessions());
        sessionSelector.setItemLabelGenerator(session ->
            session.getTitle() + " (" + session.getCreatedAt().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")) + ")");
        sessionSelector.addValueChangeListener(e -> loadSession(e.getValue()));

        Button newChatButton = new Button("New Chat", new Icon(VaadinIcon.PLUS),
            e -> createNewSession());
        newChatButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        sessionControls.add(sessionSelector, newChatButton);

        messagesLayout = new VerticalLayout();
        messagesLayout.setSpacing(true);
        messagesLayout.setPadding(true);
        messagesLayout.setHeightFull();
        messagesLayout.getStyle()
            .set("overflow-y", "auto")
            .set("flex-grow", "1");

        HorizontalLayout inputLayout = new HorizontalLayout();
        inputLayout.setWidthFull();
        inputLayout.setPadding(true);
        inputLayout.setSpacing(true);
        inputLayout.setAlignItems(Alignment.CENTER);
        inputLayout.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-l)");

        inputField = new TextField();
        inputField.setWidthFull();
        inputField.setPlaceholder("Ask about your finances...");
        inputField.addKeyPressListener(Key.ENTER, e -> handleSend());

        sendButton = new Button(new Icon(VaadinIcon.PAPERPLANE));
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> handleSend());

        inputLayout.add(inputField, sendButton);
        inputLayout.setFlexGrow(1, inputField);

        chatContainer.add(sessionControls, messagesLayout, inputLayout);
        chatContainer.setFlexGrow(1, messagesLayout);

        dataLayout = new VerticalLayout();
        dataLayout.setHeightFull();
        dataLayout.setWidth("70%");
        dataLayout.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("padding", "var(--lumo-space-m)")
            .set("overflow-y", "auto");

        mainLayout.add(dataLayout, chatContainer);
        add(mainLayout);

        List<ChatSession> sessions = chatHistoryService.getUserSessions();
        if (!sessions.isEmpty()) {
            sessionSelector.setValue(sessions.get(0));
        } else {
            createNewSession();
        }

        getUI().ifPresent(ui -> ui.getPage().executeJs(
            "if (typeof Chart === 'undefined') {" +
            "  var script = document.createElement('script');" +
            "  script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.js';" +
            "  script.onload = function() { console.log('Chart.js loaded'); };" +
            "  script.onerror = function() { console.error('Failed to load Chart.js'); };" +
            "  document.head.appendChild(script);" +
            "}"
        ));
    }

    private void createNewSession() {
        ChatSession newSession = chatHistoryService.createNewSession("Chat " +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")));
        sessionSelector.setItems(chatHistoryService.getUserSessions());
        sessionSelector.setValue(newSession);
    }

    private void loadSession(ChatSession session) {
        if (session == null) return;
        currentSession = session;
        messagesLayout.removeAll();
        dataLayout.removeAll();

        List<ChatMessage> messages = chatHistoryService.getSessionMessages(session.getId());
        for (ChatMessage msg : messages) {
            addMessage(msg.getContent(), msg.getRole().equals("user"));
        }

        loadSavedCharts(session);
    }

    private void handleSend() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        retryCount = 0;
        isRetrying = false;

        String rawMessage = inputField.getValue().trim();
        String message = sanitizeInput(rawMessage);
        if (message.isEmpty()) return;

        System.out.println("Starting handleSend with message: " + message);

        addMessage(message, true);
        chatHistoryService.addMessage(currentSession.getId(), "user", message);
        inputField.clear();
        setInputEnabled(false);

        Div thinkingIndicator = new Div();
        thinkingIndicator.addClassName("thinking-indicator");
        thinkingIndicator.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("color", "var(--lumo-body-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("max-width", "80%")
            .set("margin", "0 auto var(--lumo-space-m) 0")
            .set("display", "flex")
            .set("align-items", "center")
            .set("gap", "var(--lumo-space-s)");
        
        Icon spinner = VaadinIcon.SPINNER.create();
        spinner.getStyle().set("animation", "spin 2s infinite linear");
        thinkingIndicator.add(spinner, new Span("Thinking..."));
        messagesLayout.add(thinkingIndicator);

        UI.getCurrent().getPage().addStyleSheet(
            "data:text/css," + 
            "@keyframes spin {" +
            "  from { transform: rotate(0deg); }" +
            "  to { transform: rotate(360deg); }" +
            "}"
        );

        streamedResponse = new StringBuilder();
        currentAssistantMessage = new Div();
        currentAssistantMessage.addClassName("assistant-message");
        currentAssistantMessage.getStyle()
            .set("background-color", "var(--lumo-contrast-5pct)")
            .set("color", "var(--lumo-body-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("max-width", "80%")
            .set("margin", "0 auto var(--lumo-space-m) 0");

        Runnable task = () -> {
            try {
                System.out.println("Starting LLM query...");
                SecurityContextHolder.getContext().setAuthentication(authentication);

                llmService.streamUserQuery(message)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                    .subscribe(
                        chunk -> {
                            System.out.println("Received chunk: " + chunk);
                            getUI().ifPresent(ui -> ui.access(() -> {
                                streamedResponse.append(chunk);
                                processStreamedChunk(chunk);
                            }));
                        },
                        error -> {
                            System.out.println("Stream error: " + error.getMessage());
                            error.printStackTrace();
                            getUI().ifPresent(ui -> ui.access(() -> {
                                messagesLayout.remove(thinkingIndicator);
                                Notification.show("Error: " + error.getMessage(), 3000, Notification.Position.MIDDLE);
                                setInputEnabled(true);
                            }));
                        },
                        () -> {
                            System.out.println("Stream completed");
                            getUI().ifPresent(ui -> ui.access(() -> {
                                messagesLayout.remove(thinkingIndicator);
                                messagesLayout.add(currentAssistantMessage);
                                finalizeStreamedMessage(authentication);
                                setInputEnabled(true);
                            }));
                        }
                    );
            } catch (Exception e) {
                System.out.println("Task execution error: " + e.getMessage());
                e.printStackTrace();
                getUI().ifPresent(ui -> ui.access(() -> {
                    messagesLayout.remove(thinkingIndicator);
                    Notification.show("Unexpected error: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
                    setInputEnabled(true);
                }));
            }
        };

        CompletableFuture.runAsync(new DelegatingSecurityContextRunnable(task));
    }

  private void processStreamedChunk(String chunk) {
    if (streamedResponse.length() > MAX_RESPONSE_SIZE) {
        Notification.show("Response too large. Please try a different query.", 
            3000, Notification.Position.MIDDLE);
        return;
    }
    streamedResponse.append(chunk);
}

    private void processFullResponse() {
      if (streamedResponse.length() > MAX_RESPONSE_SIZE) {
          Notification.show("Response too large. Please try a different query.", 
              3000, Notification.Position.MIDDLE);
          return;
      }

      String responseText = streamedResponse.toString();
      currentAssistantMessage.removeAll();

      Matcher matcher = JSON_PATTERN.matcher(responseText);
      if (matcher.find()) {
          String jsonPart = matcher.group();
          String beforeJson = responseText.substring(0, matcher.start()).trim();
          String afterJson = responseText.substring(matcher.end()).trim();

          StringBuilder fullText = new StringBuilder();
          if (!beforeJson.isEmpty()) {
              fullText.append(beforeJson).append("\n\n");
          }

          try {
              JsonNode response = objectMapper.readTree(jsonPart);
              String innerText = response.has("text") ? response.get("text").asText() : "";
              if (!innerText.isEmpty()) {
                  fullText.append(innerText);
              }
              if (!afterJson.isEmpty()) {
                  fullText.append("\n\n").append(afterJson);
              }

              String html = htmlRenderer.render(markdownParser.parse(fullText.toString()));
              html = html.replaceAll("(?s)<think>.*?</think>", "");
              currentAssistantMessage.getElement().setProperty("innerHTML", html);

              List<String> chartDataList = new ArrayList<>();
              if (response.has("charts") && response.get("charts").isArray()) {
                  ArrayNode charts = (ArrayNode) response.get("charts");
                  for (JsonNode chartNode : charts) {
                      chartDataList.add(chartNode.toString());
                      dataLayout.add(renderChart(chartNode));
                  }
              }

              if (response.has("tables") && response.get("tables").isArray()) {
                  ArrayNode tables = (ArrayNode) response.get("tables");
                  for (JsonNode tableNode : tables) {
                      dataLayout.add(DataTable.createFromJson(tableNode));
                  }
              }

              chatHistoryService.addMessageWithCharts(
                  currentSession.getId(),
                  "assistant",
                  fullText.toString(),
                  chartDataList
              );

          } catch (JsonProcessingException e) {
              throw new BusinessException(
                  "CHART_PROCESSING_ERROR",
                  "Failed to process chart data: " + e.getMessage()
              );
          }
      } else {
          String html = htmlRenderer.render(markdownParser.parse(responseText));
          html = html.replaceAll("(?s)<think>.*?</think>", "");
          currentAssistantMessage.getElement().setProperty("innerHTML", html);
          chatHistoryService.addMessage(currentSession.getId(), "assistant", responseText);
      }
  }

    private void handleJsonError(Exception e, String invalidJson) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isRetrying || retryCount >= MAX_RETRIES) {
            Notification.show("Failed to fix JSON after retries. Please try again.", 3000, Notification.Position.MIDDLE);
            setInputEnabled(true);
            return;
        }

        isRetrying = true;
        streamedResponse = new StringBuilder();

        String errorMessage = "The previous response had invalid JSON: " + e.getMessage() + ". Please provide a corrected version:\n" + invalidJson;
        retryCount++;

        Notification.show("Correcting JSON response...", 1000, Notification.Position.BOTTOM_END);

        llmService.streamUserQuery(errorMessage)
            .subscribe(
                chunk -> getUI().ifPresent(ui -> ui.access(() -> {
                    streamedResponse.append(chunk);
                    processStreamedChunk(chunk);
                })),
                error -> getUI().ifPresent(ui -> ui.access(() -> {
                    System.out.println("Retry error: " + error.getMessage());
                    Notification.show("Retry failed: " + error.getMessage(), 3000, Notification.Position.MIDDLE);
                    setInputEnabled(true);
                    isRetrying = false;
                })),
                () -> getUI().ifPresent(ui -> ui.access(() -> {
                    System.out.println("Retry stream completed");
                    finalizeStreamedMessage(authentication);
                    setInputEnabled(true);
                    isRetrying = false;
                }))
            );
    }

 private void finalizeStreamedMessage(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
    if (streamedResponse.length() > 0) {
        processFullResponse();
    }
    retryCount = 0;
    currentAssistantMessage = null;
    streamedResponse = null;
}

    private void addMessage(String text, boolean isUser) {
        Div messageDiv = new Div();
        messageDiv.addClassName(isUser ? "user-message" : "assistant-message");
        messageDiv.getStyle()
            .set("background-color", isUser ? "var(--lumo-primary-color)" : "var(--lumo-contrast-5pct)")
            .set("color", isUser ? "var(--lumo-primary-contrast-color)" : "var(--lumo-body-text-color)")
            .set("padding", "var(--lumo-space-m)")
            .set("border-radius", "var(--lumo-border-radius-m)")
            .set("max-width", "80%")
            .set("margin", isUser ? "0 0 var(--lumo-space-m) auto" : "0 auto var(--lumo-space-m) 0");

        if (isUser) {
            messageDiv.setText(text);
        } else {
            Matcher matcher = JSON_PATTERN.matcher(text);
            if (matcher.find()) {
                String jsonPart = matcher.group();
                String beforeJson = text.substring(0, matcher.start()).trim();
                String afterJson = text.substring(matcher.end()).trim();

                StringBuilder fullText = new StringBuilder();
                if (!beforeJson.isEmpty()) {
                    fullText.append(beforeJson).append("\n\n");
                }

                try {
                    JsonNode response = objectMapper.readTree(jsonPart);
                    String innerText = response.has("text") ? response.get("text").asText() : "";
                    if (!innerText.isEmpty()) {
                        fullText.append(innerText);
                    }

                    if (!afterJson.isEmpty()) {
                        fullText.append("\n\n").append(afterJson);
                    }

                    String html = htmlRenderer.render(markdownParser.parse(fullText.toString()));
                    html = html.replaceAll("(?s)<think>.*?</think>", "");
                    messageDiv.getElement().setProperty("innerHTML", html);

                    if (response.has("charts") && response.get("charts").isArray()) {
                        for (JsonNode chartNode : response.get("charts")) {
                            dataLayout.add(renderChart(chartNode));
                        }
                    }

                    if (response.has("tables") && response.get("tables").isArray()) {
                        for (JsonNode tableNode : response.get("tables")) {
                            dataLayout.add(renderTable(tableNode));
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Failed to parse JSON in addMessage: " + e.getMessage());
                    String html = htmlRenderer.render(markdownParser.parse(fullText.toString()));
                    html = html.replaceAll("(?s)<think>.*?</think>", "");
                    messageDiv.getElement().setProperty("innerHTML", html);
                }
            } else {
                String html = htmlRenderer.render(markdownParser.parse(text));
                html = html.replaceAll("(?s)<think>.*?</think>", "");
                messageDiv.getElement().setProperty("innerHTML", html);
            }
        }

        messagesLayout.add(messageDiv);
        messagesLayout.getElement().executeJs("this.scrollTop = this.scrollHeight;");
    }

    private Div renderChart(JsonNode chartNode) {
        Div chartDiv = new Div();
        String chartId = "chart-" + UUID.randomUUID().toString();
        chartDiv.getElement().setAttribute("id", chartId);
        chartDiv.setHeight("300px");
        chartDiv.getStyle().set("width", "100%");

        chartDiv.getElement().setProperty("innerHTML", "<canvas></canvas>");

        String type = chartNode.has("type") ? chartNode.get("type").asText() : "bar";
        String title = chartNode.has("title") ? chartNode.get("title").asText() : "Chart";
        JsonNode dataNode = chartNode.has("data") ? chartNode.get("data") : null;

        StringBuilder jsCode = new StringBuilder();
        jsCode.append("if (typeof Chart === 'undefined') { console.error('Chart.js not loaded'); return; }");
        jsCode.append("var ctx = document.getElementById('").append(chartId).append("').querySelector('canvas');");
        jsCode.append("if (!ctx) { console.error('Canvas not found for ").append(chartId).append("'); return; }");
        jsCode.append("new Chart(ctx, {");
        jsCode.append("  type: '").append(type).append("',");
        jsCode.append("  data: ");
        try {
            jsCode.append(objectMapper.writeValueAsString(dataNode)).append(",");
        } catch (JsonProcessingException e) {
            jsCode.append("{ labels: [], datasets: [] },");
            System.out.println("Error serializing chart data: " + e.getMessage());
        }
        jsCode.append("  options: {");
        jsCode.append("    responsive: true,");
        jsCode.append("    maintainAspectRatio: false,");
        jsCode.append("    plugins: {");
        jsCode.append("      title: { display: true, text: '").append(title).append("' }");
        jsCode.append("    }");
        jsCode.append("  }");
        jsCode.append("});");

        chartDiv.getElement().executeJs(jsCode.toString());
        return chartDiv;
    }

    private Grid<Map<String, String>> renderTable(JsonNode tableNode) {
        Grid<Map<String, String>> grid = new Grid<>();
        List<Map<String, String>> rows = new ArrayList<>();

        JsonNode headersNode = tableNode.has("headers") ? tableNode.get("headers") : null;
        List<String> headers = new ArrayList<>();
        if (headersNode != null && headersNode.isArray()) {
            for (JsonNode header : headersNode) {
                headers.add(header.asText());
            }
            grid.setColumns(headers.toArray(String[]::new));
            grid.getColumns().forEach(column -> column.setKey(column.getKey()).setAutoWidth(true));
        }

        JsonNode rowsNode = tableNode.has("rows") ? tableNode.get("rows") : null;
        if (rowsNode != null && rowsNode.isArray()) {
            for (JsonNode row : rowsNode) {
                Map<String, String> rowData = new HashMap<>();
                if (row.isArray() && headers.size() == row.size()) {
                    for (int i = 0; i < headers.size(); i++) {
                        rowData.put(headers.get(i), row.get(i).asText());
                    }
                }
                rows.add(rowData);
            }
        }

        grid.setItems(rows);
        grid.setHeight("200px");
        grid.getStyle()
            .set("max-width", "100%")
            .set("overflow", "auto")
            .set("border", "1px solid var(--lumo-contrast-10pct)");
        return grid;
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private void loadSavedCharts(ChatSession session) {
        try {
            List<ChartArtifact> charts = chatHistoryService.getChartsForSession(session);
            for (ChartArtifact chart : charts) {
                try {
                    JsonNode chartNode = objectMapper.readTree(chart.getChartData());
                    dataLayout.add(renderChart(chartNode));
                } catch (JsonProcessingException e) {
                    throw new BusinessException(
                        "CHART_PARSE_ERROR",
                        "Failed to parse saved chart data: " + e.getMessage()
                    );
                }
            }
        } catch (BusinessException e) {
            e.printStackTrace();
            Notification.show("Could not load saved charts", 3000, Notification.Position.MIDDLE);
        }
    }

    private String sanitizeInput(String input) {
        return input.replaceAll("<[^>]*>", "");
    }
}