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

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.emblow.envelofy.repository.ChartArtifactRepository;
import org.emblow.envelofy.service.llm.LLMService;
import org.emblow.envelofy.service.ChatHistoryService;
import org.emblow.envelofy.ui.MainLayout;
import org.emblow.envelofy.ui.components.ChatComponent;

@Route(value = "chat", layout = MainLayout.class)
@PageTitle("Financial Assistant | Envelofy")
public class ChatView extends VerticalLayout {

    public ChatView(LLMService llmService, 
                   ChatHistoryService chatHistoryService,
                   ChartArtifactRepository chartArtifactRepository) { // Add this parameter
        addClassName("chat-view");
        setSizeFull(); 
        setAlignItems(Alignment.CENTER);
        
        H2 header = new H2("Financial Assistant");
        header.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        ChatComponent chat = new ChatComponent(llmService, chatHistoryService, chartArtifactRepository);
        
        add(header, chat);
        setFlexGrow(1, chat);
    }
}