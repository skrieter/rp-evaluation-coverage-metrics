/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula-analysis-sat4j.
 *
 * formula-analysis-sat4j is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula-analysis-sat4j is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula-analysis-sat4j. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula-analysis-sat4j> for further information.
 */
package de.featjar.analysis.sat4j.computation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class CombinationSpecificationList implements ICombinationSpecification {

    private final List<ICombinationSpecification> specifications = new ArrayList<>();

    public void addSpecifications(ICombinationSpecification spec) {
        specifications.add(spec);
    }

    public Stream<int[]> stream() {
        return specifications.stream().flatMap(ICombinationSpecification::stream);
    }

    public int getTotalSteps() {
        return specifications.stream()
                .mapToInt(ICombinationSpecification::getTotalSteps)
                .sum();
    }

    @Override
    public void shuffle(Random random) {
        final long seed = random.nextLong();
        specifications.stream().forEach(s -> s.shuffle(new Random(seed)));
    }
}
