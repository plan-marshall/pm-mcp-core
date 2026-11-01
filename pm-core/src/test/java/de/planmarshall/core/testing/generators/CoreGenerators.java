/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import de.cuioss.test.generator.TypedGenerator;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.ProjectEnrolment;

/**
 * Registers the generator artifact of {@code pm-core} in one place: one factory method per
 * {@link TypedGenerator}, so a test names the factory rather than the generator class. The CUI framework selects a
 * generator for a parameterized test through {@code @TypeGeneratorFactorySource(factoryClass = CoreGenerators.class,
 * factoryMethod = "roleSkills")}, which is the library's factory registration; its {@code @GeneratorsSource} reads
 * only the built-in {@code GeneratorType} enum and cannot name an artifact generator.
 *
 * @since 0.1
 */
@UtilityClass
public class CoreGenerators {

    /** @return a generator of valid role skills */
    public static TypedGenerator<RoleSkill> roleSkills() {
        return new RoleSkillGenerator();
    }

    /** @return a generator of valid project enrolments */
    public static TypedGenerator<ProjectEnrolment> projectEnrolments() {
        return new ProjectEnrolmentGenerator();
    }

    /** @return a generator of valid endpoint profiles */
    public static TypedGenerator<EndpointProfile> endpointProfiles() {
        return new EndpointProfileGenerator();
    }

    /** @return a generator of valid lease records */
    public static TypedGenerator<LeaseRecord> leaseRecords() {
        return new LeaseRecordGenerator();
    }
}
