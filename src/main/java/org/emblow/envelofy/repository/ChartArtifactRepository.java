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
package org.emblow.envelofy.repository;

import org.emblow.envelofy.domain.ChartArtifact;
import org.emblow.envelofy.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChartArtifactRepository extends JpaRepository<ChartArtifact, Long> {
    List<ChartArtifact> findBySession(ChatSession session);
    List<ChartArtifact> findBySessionOrderByCreatedAtDesc(ChatSession session);
    List<ChartArtifact> findBySession_IdOrderByCreatedAtDesc(Long sessionId);
    void deleteBySession(ChatSession session);
}
