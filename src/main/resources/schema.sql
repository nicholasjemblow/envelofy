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
/**
 * Author:  Nicholas J Emblow
 * Created: 12 Feb 2025
 */

CREATE TABLE IF NOT EXISTS app_setting (
    setting_key VARCHAR(255) PRIMARY KEY,
    setting_value VARCHAR(1000),
    setting_type VARCHAR(50),
    setting_category VARCHAR(50),
    description VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES app_user(id)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(50) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    session_id BIGINT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);