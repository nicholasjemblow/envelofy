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
package org.emblow.envelofy.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.emblow.envelofy.service.SecurityService;
import org.emblow.envelofy.ui.views.ChatView;
import org.emblow.envelofy.ui.views.ConfigurationView;
import org.emblow.envelofy.ui.views.DashboardView;
import org.emblow.envelofy.ui.views.InsightsView;
import org.springframework.beans.factory.annotation.Autowired;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.emblow.envelofy.service.InitializationService;

/**
 * MainLayout is the primary application layout used after authentication.
 * It contains the header with navigation controls, a personalized greeting, and the side drawer.
 * All views for logged‑in users are displayed within this layout.
 *
 * <p>This class is a Spring-managed component (per UI) and obtains the current user via SecurityService.</p>
 *
 * @author Nicholas J Emblow
 * @version 1.0
 * @since 2025
 */
@SpringComponent
@UIScope
@AnonymousAllowed
@PageTitle("Envelofy")
public class MainLayout extends AppLayout {

    private final SecurityService securityService;
    private Button toggleDarkMode;
    private final InitializationService initializationService;
    @Autowired
    public MainLayout(SecurityService securityService, InitializationService initializationService) {
        this.securityService = securityService;
        // Set initial theme based on local storage value.
        UI.getCurrent().getPage().executeJs(
            "if(window.localStorage.getItem('theme') === 'dark') {" +
            "   document.documentElement.setAttribute('theme', 'dark');" +
            "} else {" +
            "   document.documentElement.setAttribute('theme', 'light');" +
            "}"
        );
                this.initializationService = initializationService;
        createHeader();
        createDrawer();
        
    }

    private void createHeader() {
        // Create logo label.
        H1 logo = new H1("Envelofy");
        logo.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.MEDIUM);

        // Create the drawer toggle button.
        DrawerToggle drawerToggle = new DrawerToggle();

        // Create a donation button that opens a GitHub Sponsors page.
        Button donateButton = new Button("Support the Project", e ->
            UI.getCurrent().getPage().open("https://github.com/sponsors/nicholasjemblow", "_blank")
        );

        // Create a button to toggle dark/light mode.
        toggleDarkMode = new Button("Toggle Dark Mode", e -> {
            UI.getCurrent().getPage().executeJs(
                "var theme = document.documentElement.getAttribute('theme') || 'light';" +
                "var newTheme = (theme === 'dark') ? 'light' : 'dark';" +
                "document.documentElement.setAttribute('theme', newTheme);" +
                "window.localStorage.setItem('theme', newTheme);"
            );
        });

        
                // Create a button to trigger test data initialization.
        Button initTestDataButton = new Button("Initialize Test Data", e -> {
            initializationService.initializeTestData();
            // Optionally, you can show a notification to the user here.
        });

        
        // --- Add a logout form (hidden) ---
        // Create a Div to hold our logout form.
        Div logoutFormDiv = new Div();
        // Insert the form HTML: it's hidden via inline CSS.
        logoutFormDiv.getElement().setProperty("innerHTML",
            "<form id='logoutForm' action='/logout' method='POST' style='display:none;'></form>"
        );

        // Create a sign out button that triggers the logout form submission.
        Button signOutButton = new Button("Sign Out", e ->
            UI.getCurrent().getPage().executeJs("document.getElementById('logoutForm').submit()")
        );

        // Retrieve current user's username from the SecurityService.
        String currentUsername = securityService.getCurrentUser().getUsername();
        // Create a greeting header.
        H1 greeting = new H1("Welcome, " + currentUsername + "!");
        greeting.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.Margin.NONE);

        // Create the header layout and add all components.
        HorizontalLayout headerLayout = new HorizontalLayout(
            drawerToggle, logo, initTestDataButton,donateButton, toggleDarkMode, signOutButton, greeting
        );
        headerLayout.setWidthFull();
        headerLayout.setDefaultVerticalComponentAlignment(
            com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER
        );
        headerLayout.expand(logo);
        headerLayout.addClassNames(
            LumoUtility.Background.BASE,
            LumoUtility.BoxSizing.BORDER,
            LumoUtility.Display.FLEX,
            LumoUtility.AlignItems.CENTER,
            LumoUtility.Padding.MEDIUM,
            LumoUtility.Width.FULL
        );

        // Add both the header layout and the hidden logout form.
        addToNavbar(headerLayout);
        addToNavbar(logoutFormDiv); // This adds the form to the DOM so it can be submitted.
    }

    private void createDrawer() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, 
                com.vaadin.flow.component.icon.VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Smart Insights", InsightsView.class, 
                com.vaadin.flow.component.icon.VaadinIcon.CHART.create()));
        nav.addItem(new SideNavItem("Chat Assistant", ChatView.class, 
                com.vaadin.flow.component.icon.VaadinIcon.CHAT.create()));
        nav.addItem(new SideNavItem("Configuration", ConfigurationView.class, 
                com.vaadin.flow.component.icon.VaadinIcon.COG.create()));

        Scroller scroller = new Scroller(nav);
        addToDrawer(scroller);
    }
}