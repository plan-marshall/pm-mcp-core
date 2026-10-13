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
 * The primitive SPI and the engine ports: the interface every deterministic server operation implements, and the
 * interfaces through which the workflow engine causes and observes what lies outside the status store. The
 * plan-domain primitives of {@code pm-workflow} and the {@code git.*} and {@code ci.*} primitives of the provider
 * modules build against the primitive SPI; the engine of {@code pm-workflow} consumes the engine ports.
 * <p>
 * The contract: a {@link de.planmarshall.core.spi.Primitive} has a canonical name, a parameter record whose free
 * components carry {@link de.planmarshall.core.spi.FreeParam}, and a closed enum of
 * {@link de.planmarshall.core.spi.PrimitiveOutcome outcomes}, each of one
 * {@link de.planmarshall.core.spi.OutcomeClass}. It runs for a
 * {@link de.planmarshall.core.spi.WorkflowExecutionContext} and returns a
 * {@link de.planmarshall.core.spi.PrimitiveResult}. It declares the event it waits for
 * ({@link de.planmarshall.core.spi.AwaitedEvent}) and the measure that ends a cycle through its state
 * ({@link de.planmarshall.core.spi.CycleMeasure}).
 * <p>
 * The registry: {@link de.planmarshall.core.spi.PrimitiveRegistry} holds the primitives the assembly passes to its
 * constructor and refuses a primitive whose declarations are malformed or contradict each other.
 * <p>
 * The engine ports: the workflow engine starts the job of a {@code job} state through
 * {@link de.planmarshall.core.spi.JobStarter}, creates the queue tasks of a {@code job llm} state through
 * {@link de.planmarshall.core.spi.QueueTaskCreator}, reads the question record a {@code question} state rests on
 * through {@link de.planmarshall.core.spi.QuestionRecordReader}, reads the pending entry record of a plan or epic
 * bootstrap addressed by its {@code entry_id} through {@link de.planmarshall.core.spi.PendingEntryReader}, and reads
 * every instant it persists or compares from {@link de.planmarshall.core.spi.WorkflowClock}. Each start and each
 * creation is an explicit side effect of the state that owns it, and what it returns is bound in the status store in
 * the same transaction, so a run depends on persisted state only. Their values are records that check the identifier
 * grammars ({@code job_id}, {@code queue_task_id}, {@code question_id}); the {@code entry_id} arrives as the typed
 * {@link de.planmarshall.core.store.EntryId}, which owns its grammar. A refused start, an unreadable question
 * record and an unreadable or deleted entry record are explicit results, never exceptions or a default.
 * {@code pm-runtime} implements the ports; no class of this module does.
 * <p>
 * The sub-package {@link de.planmarshall.core.spi.validation} holds the content validators a primitive runs over
 * submitted text.
 * <p>
 * Requirements: PM-IMPL-5, PM-ARCH-4.
 */
package de.planmarshall.core.spi;
