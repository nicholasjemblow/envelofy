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
package org.emblow.envelopify.config;

/**
 *
 * @author Nicholas J Emblow
 */
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
@Configuration
public class SecurityConfig extends VaadinWebSecurity {
     
    @Bean
    public SecurityFilterChain configureHttpSecurity(HttpSecurity http) throws Exception {
        // Configure your application's security
        http.authorizeHttpRequests((auth) -> auth
            .requestMatchers(
                new AntPathRequestMatcher("/images/**"),
                new AntPathRequestMatcher("/h2-console/**"),
                new AntPathRequestMatcher("/frontend/**"),
                new AntPathRequestMatcher("/VAADIN/**")
            ).permitAll()
        );
        
        // Configure H2 console
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        
        // Call parent configuration
        super.configure(http);
        
        // Return the built configuration
        return http.build();
    }
}


