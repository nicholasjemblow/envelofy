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
import org.emblow.envelofy.domain.ChatSession;
import org.emblow.envelofy.domain.ChatMessage;
import org.emblow.envelofy.domain.User;
import org.emblow.envelofy.repository.ChatSessionRepository;
import org.emblow.envelofy.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import org.emblow.envelofy.domain.ChartArtifact;
import org.emblow.envelofy.repository.ChartArtifactRepository;

@Service
public class ChatHistoryService {
    private static final int MAX_TOKENS = 4000; // Adjust based on LLM limits

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;
    
    private final ChartArtifactRepository chartArtifactRepository;

    @Autowired
    public ChatHistoryService(SecurityService securityService,
                            ChatSessionRepository chatSessionRepository,
                            ChatMessageRepository chatMessageRepository,
                            ChartArtifactRepository chartArtifactRepository) {
        this.securityService = securityService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chartArtifactRepository = chartArtifactRepository;
    }

    @Transactional
    public ChatSession createNewSession(String title) {
        User currentUser = securityService.getCurrentUser();
        ChatSession session = new ChatSession(title, currentUser);
        return chatSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChartArtifact> getChartsForSession(ChatSession session) {
        // Verify the user owns this session
        if (!securityService.isOwner(session.getOwner())) {
            throw new RuntimeException("Access denied");
        }
        return chartArtifactRepository.findBySession(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> getUserSessions() {
        User currentUser = securityService.getCurrentUser();
        return chatSessionRepository.findByOwnerOrderByCreatedAtDesc(currentUser);
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> getSession(Long sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
            .orElse(null);
        if (session != null && !securityService.isOwner(session.getOwner())) {
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    @Transactional
    public void addMessage(Long sessionId, String role, String content) {
        ChatSession session = getSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Chat session not found or access denied"));
        
        ChatMessage message = new ChatMessage(role, content);
        session.addMessage(message);
        trimHistory(session);
        chatSessionRepository.save(session);
    }

    @Transactional
    public void addMessageWithCharts(Long sessionId, String role, String content, List<String> chartDataList) {
        ChatSession session = getSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Chat session not found or access denied"));

        ChatMessage message = new ChatMessage(role, content);
        session.addMessage(message);

        // Save charts if present
        if (chartDataList != null && !chartDataList.isEmpty()) {
            for (String chartData : chartDataList) {
                ChartArtifact chart = new ChartArtifact(chartData, session);
                chartArtifactRepository.save(chart);
            }
        }

        trimHistory(session);
        chatSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        ChatSession session = getSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Chat session not found or access denied"));
        return chatMessageRepository.findBySessionOrderByTimestampAsc(session);
    }

    private void trimHistory(ChatSession session) {
        List<ChatMessage> messages = session.getMessages();
        int totalTokens = messages.stream().mapToInt(ChatMessage::estimateTokens).sum();
        
        while (totalTokens > MAX_TOKENS && !messages.isEmpty()) {
            ChatMessage oldest = messages.remove(0);
            chatMessageRepository.delete(oldest);
            totalTokens -= oldest.estimateTokens();
        }
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        ChatSession session = getSession(sessionId)
            .orElseThrow(() -> new RuntimeException("Chat session not found or access denied"));
        chatSessionRepository.delete(session);
    }
}