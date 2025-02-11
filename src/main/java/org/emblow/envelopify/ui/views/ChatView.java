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
package org.emblow.envelopify.ui.views;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelopify.service.llm.LLMService;
import org.emblow.envelopify.ui.MainLayout;
import org.emblow.envelopify.ui.components.ChatComponent;


@Route(value = "chat", layout = MainLayout.class)
@PageTitle("Financial Assistant | Envelopify")
@AnonymousAllowed
public class ChatView extends VerticalLayout {
    
    public ChatView(LLMService llmService) {
        addClassName("chat-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        
        H2 header = new H2("Financial Assistant");
        header.getStyle().set("margin-top", "var(--lumo-space-m)");
        
        ChatComponent chat = new ChatComponent(llmService);
        
        add(header, chat);
        setFlexGrow(1, chat);
    }
}
