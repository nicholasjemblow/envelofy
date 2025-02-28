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
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.emblow.envelofy.service.UserService;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;

/**
 * RegistrationView is responsible for allowing new users to register an account.
 * 
 * <p>This view collects the username, email, password, and confirmation of password.
 * On successful registration (handled via UserService), it redirects the user to the login page.</p>
 *
 * @author Nicholas J Emblow
 * @version 1.0
 * @since 2025
 */
@Route("register")
@PageTitle("Register | Envelofy")
@AnonymousAllowed
public class RegistrationView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;

    /**
     * Constructs a new RegistrationView.
     *
     * @param userService The service used to create a new user.
     */
    public RegistrationView(UserService userService) {
        this.userService = userService;
        configureLayout();
    }

    /**
     * Configures the layout and adds all UI components required for registration.
     */
    private void configureLayout() {
        // Set full size and center components
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        addClassName("register-view");

        // Header for the registration page
        H2 header = new H2("Create Account");

        // Username field: required and full width.
        TextField usernameField = new TextField("Username");
        usernameField.setRequired(true);
        usernameField.setWidthFull();

        // Email field: required and validated.
        EmailField emailField = new EmailField("Email");
        emailField.setRequired(true);
        emailField.setWidthFull();

        // Password field: required.
        PasswordField passwordField = new PasswordField("Password");
        passwordField.setRequired(true);
        passwordField.setWidthFull();

        // Confirm password field: required.
        PasswordField confirmPasswordField = new PasswordField("Confirm Password");
        confirmPasswordField.setRequired(true);
        confirmPasswordField.setWidthFull();

        // Registration button with click listener.
        Button registerButton = new Button("Register", (ComponentEventListener<ClickEvent<Button>>) event -> {
            handleRegistration(usernameField.getValue(), emailField.getValue(), 
                               passwordField.getValue(), confirmPasswordField.getValue());
        });
        registerButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Button that navigates to the login view if the user already has an account.
        Button loginButton = new Button("Already have an account? Login", 
                e -> getUI().ifPresent(ui -> ui.navigate("login")));
        loginButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Add all components to the layout.
        add(header, usernameField, emailField, passwordField, confirmPasswordField, registerButton, loginButton);
    }

    /**
     * Handles the registration logic. Validates that the passwords match and calls UserService.
     * On success, navigates to the login page; on failure, shows an error notification.
     *
     * @param username         The username entered by the user.
     * @param email            The email address entered by the user.
     * @param password         The password entered by the user.
     * @param confirmPassword  The confirmation password entered by the user.
     */
    private void handleRegistration(String username, String email, String password, String confirmPassword) {
        try {
            if (!password.equals(confirmPassword)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            // Create the user using the UserService.
            userService.createUser(username, password, email);
            // On successful registration, navigate to the login view.
            getUI().ifPresent(ui -> ui.navigate("login"));
            Notification.show("Registration successful! Please login.");
        } catch (IllegalArgumentException e) {
            Notification.show("Registration failed: " + e.getMessage(), 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /**
     * Implements BeforeEnterObserver to perform actions before the view is entered.
     * This method can be used to reset fields or perform cleanup if needed.
     *
     * @param event The before-enter event.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Optionally, perform any cleanup or reset operations here.
    }
}

