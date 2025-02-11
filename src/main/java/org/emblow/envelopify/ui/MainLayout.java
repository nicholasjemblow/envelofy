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
package org.emblow.envelopify.ui;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.emblow.envelopify.ui.views.DashboardView;
import com.vaadin.flow.server.auth.AnonymousAllowed;  
import org.emblow.envelopify.ui.views.ChatView;
import org.emblow.envelopify.ui.views.InsightsView;

@PageTitle("Envelopify")
@AnonymousAllowed 
public class MainLayout extends AppLayout {

    private Button toggleDarkMode;


    public MainLayout() {
        createHeader();
        createDrawer();




    
    }

    private void createHeader() {
        // Create a logo
        H1 logo = new H1("Envelopify");
        logo.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.MEDIUM
        );

        // Create a drawer toggle button
        DrawerToggle drawerToggle = new DrawerToggle();

        // Create a donation button that links to GitHub Sponsors page
        Button donateButton = new Button("Support the Project", e -> 
            UI.getCurrent().getPage().open("https://github.com/sponsors/nicholasjemblow", "_blank")
        );

        
        // Create the dark mode toggle button
        toggleDarkMode = new Button("Toggle Dark Mode", e -> {
            String currentTheme = UI.getCurrent().getElement().getAttribute("theme");
            if (currentTheme != null && currentTheme.contains("dark")) {
                UI.getCurrent().getElement().setAttribute("theme", "light");
            } else {
                UI.getCurrent().getElement().setAttribute("theme", "dark");
            }
        });

        // Create a horizontal layout for header components
        HorizontalLayout headerLayout = new HorizontalLayout(drawerToggle, logo,donateButton, toggleDarkMode);
        headerLayout.setWidthFull();
        headerLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        headerLayout.expand(logo);

        // Optionally add some spacing or styling to headerLayout as needed.
        headerLayout.addClassNames(
            LumoUtility.Background.BASE,
            LumoUtility.BoxSizing.BORDER,
            LumoUtility.Display.FLEX,
            LumoUtility.AlignItems.CENTER,
            LumoUtility.Padding.MEDIUM,
            LumoUtility.Width.FULL
        );

        // Add the header layout to the navbar
        addToNavbar(headerLayout);
        
    }


    private void createDrawer() {
        var nav = new SideNav();
        
        nav.addItem(new SideNavItem("Dashboard", DashboardView.class, VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Smart Insights", InsightsView.class, VaadinIcon.CHART.create()));
        nav.addItem(new SideNavItem("Chat Assistant", ChatView.class, VaadinIcon.CHAT.create()));
 
        
        Scroller scroller = new Scroller(nav);
   
        addToDrawer(scroller);
    }
}
