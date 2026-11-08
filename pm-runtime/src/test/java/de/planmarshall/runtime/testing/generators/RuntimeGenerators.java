/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import de.cuioss.test.generator.TypedGenerator;
import javax.crypto.SecretKey;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.launch.ResolvedSkill;

/**
 * Registers the generator artifact of {@code pm-runtime} in one place: one factory method per
 * {@link TypedGenerator}, for {@code @TypeGeneratorFactorySource(factoryClass = RuntimeGenerators.class,
 * factoryMethod = "resolvedSkills")}.
 *
 * @since 0.1
 */
@UtilityClass
public class RuntimeGenerators {

    /** @return a generator of valid resolved skills */
    public static TypedGenerator<ResolvedSkill> resolvedSkills() {
        return new ResolvedSkillGenerator();
    }

    /** @return a generator of SHA-256 digests as an enrolment records them, 64 lower-case hex characters */
    public static TypedGenerator<String> sha256Digests() {
        return new Sha256DigestGenerator();
    }

    /** @return a generator of lower-case words of one to sixteen letters, for names and path elements */
    public static TypedGenerator<String> words() {
        return new WordGenerator(1, 16);
    }

    /** @return a generator of lower-case words of two to ten letters, for short names and keys */
    public static TypedGenerator<String> shortWords() {
        return new WordGenerator(2, 10);
    }

    /**
     * @param kind the kind whose prefix every generated id carries
     * @return a generator of job ids of that kind in their written form, the prefix, {@code -} and 16 hex digits
     */
    public static TypedGenerator<String> jobIds(JobId.Kind kind) {
        return new JobIdGenerator(kind);
    }

    /** @return a generator of plan ids, a number and a short word joined by a hyphen */
    public static TypedGenerator<String> planIds() {
        return new PlanIdGenerator();
    }

    /** @return a generator of project ids, taken from the generator artifact of {@code pm-core} */
    public static TypedGenerator<String> projectIds() {
        return new ProjectIdGenerator();
    }

    /** @return a generator of queue task ids in their written form, the prefix {@code qt-} and 16 hex digits */
    public static TypedGenerator<String> queueTaskIds() {
        return new QueueTaskIdGenerator();
    }

    /** @return a generator of question ids in their written form, the prefix {@code Q-} and three to six digits */
    public static TypedGenerator<String> questionIds() {
        return new QuestionIdGenerator();
    }

    /** @return a generator of keys of the enrolment integrity algorithm that are no enrolment's integrity key */
    public static TypedGenerator<SecretKey> hmacKeys() {
        return new HmacKeyGenerator();
    }

    /** @return a generator of the exit codes of a failed process, 1 to 255 */
    public static TypedGenerator<Integer> failures() {
        return new FailingExitCodeGenerator();
    }
}
