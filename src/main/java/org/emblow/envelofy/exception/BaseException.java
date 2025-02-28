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


import java.util.Map;

public abstract class BaseException extends RuntimeException {
    private final String code;
    private final String message;
    private final Map<String, Object> details;

    protected BaseException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public String getCode() { return code; }
    @Override
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
}
