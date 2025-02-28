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

import org.emblow.envelofy.domain.Pattern;
import org.emblow.envelofy.domain.Category;
import org.emblow.envelofy.domain.Pattern.PatternType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatternRepository extends JpaRepository<Pattern, Long> {
    List<Pattern> findByCategory(Category category);
    List<Pattern> findByType(PatternType type);
    Optional<Pattern> findByPatternAndType(String pattern, PatternType type);
    
    @Query("SELECT p FROM Pattern p WHERE p.category.owner.id = :userId")
    List<Pattern> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT p FROM Pattern p WHERE p.category.owner.id = :userId AND p.confidence >= :minConfidence")
    List<Pattern> findConfidentPatterns(@Param("userId") Long userId, @Param("minConfidence") Double minConfidence);
    
    @Query("SELECT p FROM Pattern p WHERE p.category.id = :categoryId AND p.type = :type ORDER BY p.confidence DESC")
    List<Pattern> findByCategoryAndType(@Param("categoryId") Long categoryId, @Param("type") PatternType type);
}