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
 * The line-oriented workflow language (PM-IMPL-5): it reads the text of a unit into the immutable tree of
 * {@link de.planmarshall.workflow.ast}.
 * <ul>
 * <li><b>Lexer</b>: a package-private tokenizer cuts the text into lines of tokens with their line and column,
 * drops comments ({@code #} to the end of the line) and keeps the JSON of an {@code instance} line as one token.</li>
 * <li><b>Parser</b>: {@link de.planmarshall.workflow.dsl.WorkflowParser} is a recursive-descent parser over the
 * grammar of the language; it reads the instance header of a plan instance with jackson-core and builds the tree with
 * a source location on every node.</li>
 * <li><b>Result</b>: {@link de.planmarshall.workflow.dsl.ParseResult} is sealed: the tree
 * ({@link de.planmarshall.workflow.dsl.ParseResult.Parsed}) or the first refusal
 * ({@link de.planmarshall.workflow.dsl.ParseResult.Refused}). A refusal is a value, never an exception.</li>
 * <li><b>Diagnostic</b>: {@link de.planmarshall.workflow.dsl.Diagnostic} names the
 * {@link de.planmarshall.workflow.dsl.RefusalRule} a refused unit violates, its resource, line and column, and what
 * is wrong.</li>
 * </ul>
 */
package de.planmarshall.workflow.dsl;
