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
package org.emblow.envelofy.service.llm;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 *
 * @author Nicholas J Emblow
 */
@Service
public interface LLMService {

    /**
     * Processes the user query by building financial context, assembling a prompt, and then generating
     * a response using the JLama inference engine.
     * @param userQuery
     * @return
     */
    String processUserQuery(String userQuery);
        Flux<String> streamUserQuery(String userQuery);
}
