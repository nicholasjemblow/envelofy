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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;

/**
 * LoginView is the entry point for unauthenticated users.
 * It displays a Vaadin LoginForm for user authentication and provides a registration link.
 *
 * <p>This view is accessible to anonymous users, and its form posts to the /login URL (handled by Spring Security).</p>
 *
 * @author Nicholas J Emblow
 * @version 1.0
 * @since 2025
 */
@Route("login") 
@PageTitle("Login | Envelofy")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {
    private final LoginForm login;

    public LoginView() {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().set("padding", "var(--lumo-space-l)");

        // Create a card-like container for the login form
        Div loginContainer = new Div();
        loginContainer.addClassName("login-container");
        loginContainer.getStyle()
            .set("background-color", "var(--lumo-base-color)")
            .set("border-radius", "var(--lumo-border-radius-l)")
            .set("box-shadow", "var(--lumo-box-shadow-m)")
            .set("padding", "var(--lumo-space-l)")
            .set("max-width", "400px")
            .set("width", "100%");

        // Initialize the login form
        login = new LoginForm();
        login.setAction("login");  // This matches the loginProcessingUrl in SecurityConfig
        login.setForgotPasswordButtonVisible(false);

        // Create header
        H2 header = new H2("Welcome to Envelofy");
        header.getStyle()
            .set("margin", "0")
            .set("text-align", "center")
            .set("color", "var(--lumo-primary-text-color)");

        // Add register button
        Button registerButton = new Button("Don't have an account? Register");
        registerButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        registerButton.getStyle().set("margin-top", "var(--lumo-space-m)");
        registerButton.addClickListener(e -> getUI().ifPresent(ui -> 
            ui.navigate("register")));

        // Add components to the container
        loginContainer.add(
            header,
            new Hr(),  // Add a divider
            login,
            registerButton
        );

        add(loginContainer);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Add error parameter handling
        if (event.getLocation()
                .getQueryParameters()
                .getParameters()
                .containsKey("error")) {
            login.setError(true);
        }
    }
}