/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One open issue of Sonar as {@link SonarClient#openIssues(String, int)} reads it from {@code api/issues/search}: the
 * values of the fields of the same names, unchanged. Every text but the key is remote text, to be passed through the
 * secret redactor where it is emitted ({@code PM-CRED-5}).
 *
 * @param key       the key of the issue, the address a finding of Sonar is answered on
 * @param rule      the rule that raised the issue, e.g. {@code java:S1234}
 * @param severity  the severity Sonar reports, e.g. {@code MAJOR}
 * @param type      the type Sonar reports, e.g. {@code CODE_SMELL}
 * @param component the component the issue is raised on: the project key and the file path
 * @param line      the line of the issue; {@code null} for an issue on the file as a whole
 * @param message   the message of the issue
 * @since 0.1
 */
@Builder
public record SonarIssue(SonarIssueKey key, String rule, String severity, String type, String component,
@Nullable Integer line, String message) {

    /**
     * @throws NullPointerException for a missing value other than the line
     */
    public SonarIssue {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(message, "message");
    }
}
