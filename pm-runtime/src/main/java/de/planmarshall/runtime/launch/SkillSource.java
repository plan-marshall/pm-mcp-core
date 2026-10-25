/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.Optional;

/**
 * Where the composer reads a skill's text and digest from (PM-SKILL-4): the compiled skills of the release and the
 * project's extension skills under {@code .marshall/skills/}. A skill that does not resolve, or that renders empty, is
 * a failed composition; the launch then never runs with a required skill missing.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface SkillSource {

    /**
     * @param uri a skill URI, {@code skill://pm/.../SKILL.md}
     * @return the skill, or empty when the source does not hold it
     */
    Optional<ResolvedSkill> resolve(String uri);
}
