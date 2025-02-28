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
package org.emblow.envelofy;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme(value = "envelopify")
@Push
@NpmPackage(value = "@vaadin/vaadin-lumo-styles", version = "24.2.5")
public class EnvelofyApplication implements AppShellConfigurator {
    
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addMetaTag("viewport", "width=device-width, initial-scale=1.0");
        settings.addFavIcon("icon", "icons/favicon.ico", "256x256");
    }

    public static void main(String[] args) {
        SpringApplication.run(EnvelofyApplication.class, args);
    }
}
