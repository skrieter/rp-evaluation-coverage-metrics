/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-formula.
 *
 * formula is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * formula is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with formula. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-formula> for further information.
 */
package de.featjar.formula.io;

import de.featjar.base.FeatJAR;
import de.featjar.base.io.format.AFormats;
import de.featjar.formula.assignment.BooleanAssignmentGroups;

/**
 * Extension point for {@link AFormats formats} for {@link BooleanAssignmentGroups}.
 *
 * @author anonymous
 */
public class BooleanAssignmentGroupsFormats extends AFormats<BooleanAssignmentGroups> {
    public static BooleanAssignmentGroupsFormats getInstance() {
        return FeatJAR.extensionPoint(BooleanAssignmentGroupsFormats.class);
    }
}
