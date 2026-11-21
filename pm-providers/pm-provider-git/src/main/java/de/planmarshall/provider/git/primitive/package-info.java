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
 * The commit primitives of the sub-workflow {@code git.commit_and_push} (PM-WF-16) over the port
 * {@link de.planmarshall.provider.git.primitive.CommitSite}, which the assembly implements per scope:
 * {@link de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive},
 * {@link de.planmarshall.provider.git.primitive.GitTemplateMessagePrimitive},
 * {@link de.planmarshall.provider.git.primitive.GitStageAndCommitPrimitive} and the return evaluation of the entity
 * kind {@code commit-message}, {@link de.planmarshall.provider.git.primitive.CommitMessageEvaluatePrimitive}.
 * <p>
 * The package holds the rules the primitives apply, each a pure function whose refusals are returned values:
 * {@link de.planmarshall.provider.git.primitive.CommitTrailer} resolves the trailer of a server commit from the
 * operator's stored values, {@link de.planmarshall.provider.git.primitive.CommitMessages} validates a commit message and
 * is the one writer of the trailer line, {@link de.planmarshall.provider.git.primitive.StagingSets} derives the staging
 * set from the scope and {@link de.planmarshall.provider.git.primitive.FootprintBoundary} judges the footprint check that
 * runs before anything is staged.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Six-Stage Execution Contract,
 * Stages 3 and 4).
 */
@NullMarked
package de.planmarshall.provider.git.primitive;

import org.jspecify.annotations.NullMarked;
