/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.util.Objects;
import java.util.Set;

/**
 * The harness-specific tool names the navigation figures read from a task's activity (Model Work § Navigation Figures):
 * which tools read a file, which of them read a whole file, which are the harness's own search tools, and the two
 * server tools the figures watch.
 *
 * @param fileReadTools       the tools whose call reads a file of the worktree
 * @param wholeFileReadTools  the subset of them that reads the whole file; a partial read is not one
 * @param searchTools         the harness's own code-search tools
 * @param languageServerTool  the server's language-server tool
 * @param skillTool           the server's skill tool
 * @since 0.1
 */
public record NavigationVocabulary(Set<String> fileReadTools, Set<String> wholeFileReadTools,
Set<String> searchTools, String languageServerTool, String skillTool) {

    /** The compiled server tool names of the language-server and the skill reads. */
    public static final String DEFAULT_LANGUAGE_SERVER_TOOL = "pm_lsp";

    /** The compiled server tool name of a skill read. */
    public static final String DEFAULT_SKILL_TOOL = "pm_skill";

    /** Copies the sets and refuses a whole-file set that is not a subset of the read tools. */
    public NavigationVocabulary {
        fileReadTools = Set.copyOf(Objects.requireNonNull(fileReadTools, "fileReadTools"));
        wholeFileReadTools = Set.copyOf(Objects.requireNonNull(wholeFileReadTools, "wholeFileReadTools"));
        searchTools = Set.copyOf(Objects.requireNonNull(searchTools, "searchTools"));
        Objects.requireNonNull(languageServerTool, "languageServerTool");
        Objects.requireNonNull(skillTool, "skillTool");
        if (!fileReadTools.containsAll(wholeFileReadTools)) {
            throw new IllegalArgumentException("A whole-file read tool is a file-reading tool");
        }
    }
}
