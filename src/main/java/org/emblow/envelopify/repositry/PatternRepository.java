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
package org.emblow.envelopify.repositry;

import java.util.List;
import java.util.Optional;
import org.emblow.envelopify.domain.Category;
import org.emblow.envelopify.domain.Pattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Nicholas J Emblow
 */
@Repository
public interface PatternRepository extends JpaRepository<Pattern, Long> {
    List<Pattern> findByCategory(Category category);
    List<Pattern> findByType(Pattern.PatternType type);
    Optional<Pattern> findByPatternAndType(String pattern, Pattern.PatternType type);
}
