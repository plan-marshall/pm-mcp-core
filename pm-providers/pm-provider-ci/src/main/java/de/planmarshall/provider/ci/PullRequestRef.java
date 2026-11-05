/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import java.util.Objects;

/**
 * One pull request (a merge request on GitLab) of one repository: the repository and the number the forge assigns.
 *
 * @param repository the repository the pull request belongs to
 * @param number     the pull-request number, at least 1
 * @since 0.1
 */
public record PullRequestRef(RepositoryRef repository, int number) {

    /**
     * @param repository the repository
     * @param number     the pull-request number
     * @throws NullPointerException     if the repository is {@code null}
     * @throws IllegalArgumentException if the number is below 1
     */
    public PullRequestRef {
        Objects.requireNonNull(repository, "repository");
        if (number < 1) {
            throw new IllegalArgumentException("a pull-request number is at least 1, was " + number);
        }
    }
}
