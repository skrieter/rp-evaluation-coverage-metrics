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
package de.featjar.analysis.sat4j.twise;

/**
 * Holds statistics regarding coverage of a configuration sample.
 *
 * @author anonymous
 */
public class CoverageStatistic {
    long numberOfInvalidConditions;
    long numberOfCoveredConditions;
    long numberOfUncoveredConditions;

    public void setNumberOfInvalidConditions(long numberOfInvalidConditions) {
        this.numberOfInvalidConditions = numberOfInvalidConditions;
    }

    public void setNumberOfCoveredConditions(long numberOfCoveredConditions) {
        this.numberOfCoveredConditions = numberOfCoveredConditions;
    }

    public void setNumberOfUncoveredConditions(long numberOfUncoveredConditions) {
        this.numberOfUncoveredConditions = numberOfUncoveredConditions;
    }

    public void incNumberOfInvalidConditions() {
        numberOfInvalidConditions++;
    }

    public void incNumberOfCoveredConditions() {
        numberOfCoveredConditions++;
    }

    public void incNumberOfUncoveredConditions() {
        numberOfUncoveredConditions++;
    }

    public long total() {
        return numberOfInvalidConditions + numberOfCoveredConditions + numberOfUncoveredConditions;
    }

    public long valid() {
        return numberOfCoveredConditions + numberOfUncoveredConditions;
    }

    public long invalid() {
        return numberOfInvalidConditions;
    }

    public long covered() {
        return numberOfCoveredConditions;
    }

    public long uncovered() {
        return numberOfUncoveredConditions;
    }

    public double coverage() {
        return (numberOfCoveredConditions + numberOfUncoveredConditions != 0)
                ? (double) numberOfCoveredConditions / (numberOfCoveredConditions + numberOfUncoveredConditions)
                : 1.0;
    }

    CoverageStatistic merge(CoverageStatistic other) {
        numberOfInvalidConditions += other.numberOfInvalidConditions;
        numberOfCoveredConditions += other.numberOfCoveredConditions;
        numberOfUncoveredConditions += other.numberOfUncoveredConditions;
        return this;
    }
}
