/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The content validators: the checks a text passes before the server stores it (PM-WF-6).
 * <p>
 * A model or an operator submits prose as a parameter of a link: a commit message, an outline, the body of an
 * architecture decision record. The primitive behind the link runs the
 * {@link de.planmarshall.core.spi.validation.ContentValidator} of that text in its own process, on the submitted
 * parameter, before it writes a store, and refuses the submission when the validator reports a
 * {@link de.planmarshall.core.spi.validation.ContentViolation}. A validator reads nothing but the text, so it
 * decides the same for the same text wherever it runs.
 * <p>
 * The validators of this package:
 * <ul>
 * <li>{@link de.planmarshall.core.spi.validation.ConventionalCommitValidator} for a commit message,</li>
 * <li>{@link de.planmarshall.core.spi.validation.AsciiDocValidator} for AsciiDoc text,</li>
 * <li>{@link de.planmarshall.core.spi.validation.OutlineValidator} for a solution outline.</li>
 * </ul>
 * <p>
 * What a text alone does not decide is not checked here. For an outline that is whether its tasks can be derived,
 * whether their dependencies form a graph without a cycle, and whether each deliverable carries the profiles its
 * files require; these checks need the task model and the build extensions of a project and belong to
 * {@code pm-workflow}.
 */
package de.planmarshall.core.spi.validation;
