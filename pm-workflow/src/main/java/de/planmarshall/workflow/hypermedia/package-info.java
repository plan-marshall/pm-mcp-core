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
 * The typed values of the representation forms the specification fixes: currently the items of {@code facts.answers}
 * ({@link de.planmarshall.workflow.hypermedia.AnswerFact}, {@link de.planmarshall.workflow.hypermedia.Answers}). The
 * representation itself, its link forms and link grammar, and the composite wait are composed by the assembly.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/hypermedia-format/01-representation-and-link-forms.adoc}</li>
 * <li>{@code doc/specification/hypermedia-format/02-link-grammar.adoc}</li>
 * <li>{@code doc/specification/hypermedia-format/03-composite-wait-and-encoder.adoc} (Composite Wait)</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.workflow.hypermedia;

import org.jspecify.annotations.NullMarked;
