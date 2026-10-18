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
 * {@link de.planmarshall.workflow.ast} and prints a tree back to text.
 * <ul>
 * <li><b>Frontmatter reader</b>: {@link de.planmarshall.workflow.dsl.UnitFrontmatterReader} reads the frontmatter a
 * unit opens with, the closed, line-oriented subset of YAML between two {@code ---} lines, with a parser of its own and
 * never a general YAML loader (PM-IMPL-5). It needs no body, so a unit's name, description, type and interface
 * can be read alone; a frontmatter outside its format or field rules is refused with the rule
 * {@link de.planmarshall.workflow.dsl.RefusalRule#FRONTMATTER}.</li>
 * <li><b>Lexer</b>: a package-private tokenizer cuts the text into lines of tokens with their line and column,
 * drops comments ({@code #} to the end of the line) and keeps the JSON of an {@code instance} line as one token.</li>
 * <li><b>Parser</b>: {@link de.planmarshall.workflow.dsl.WorkflowParser} is a recursive-descent parser over the
 * grammar of the language. It reads in this order: the frontmatter first, through the frontmatter reader, then the
 * body from the line after the closing {@code ---}. It reads the instance header of a plan instance with jackson-core
 * and builds the tree, in which the frontmatter, the definition, an entry binding, a state, a state migration, a
 * sub-workflow parameter, a link, a link parameter, an outcome transition and a step completion carry the source
 * location they were read at and every other node carries none.</li>
 * <li><b>Result</b>: {@link de.planmarshall.workflow.dsl.ParseResult} is sealed: the tree
 * ({@link de.planmarshall.workflow.dsl.ParseResult.Parsed}) or the first refusal
 * ({@link de.planmarshall.workflow.dsl.ParseResult.Refused}). A refusal is a value, never an exception.</li>
 * <li><b>Diagnostic</b>: {@link de.planmarshall.workflow.dsl.Diagnostic} names the
 * {@link de.planmarshall.workflow.dsl.RefusalRule} a refused unit violates, its resource, line and column, and what
 * is wrong.</li>
 * <li><b>Printer</b>: {@link de.planmarshall.workflow.dsl.WorkflowPrinter} prints a tree as canonical source in
 * grammar order, the frontmatter first in the order of its field table, every link with its explicit form and every guard with the parentheses its precedence needs, so
 * that parsing the printed source yields the same tree, source locations aside (the round trip).</li>
 * </ul>
 */
package de.planmarshall.workflow.dsl;
