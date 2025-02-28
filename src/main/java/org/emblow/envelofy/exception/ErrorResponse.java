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
package org.emblow.envelofy.exception;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

public class ErrorResponse {
    private final String code;
    private final String message;
    private final LocalDateTime timestamp;
    private final Map<String, Object> details;

    public ErrorResponse(String code, String message) {
        this(code, message, Collections.emptyMap());
    }

    public ErrorResponse(String code, String message, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.details = details;
    }

    // Getters
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }
}