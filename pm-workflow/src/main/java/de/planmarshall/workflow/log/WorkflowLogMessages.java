/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.log;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;

/**
 * Log messages of the module {@code pm-workflow}.
 * <p>
 * Identifier ranges: INFO 001-099, WARN 100-199, ERROR 200-299. A new message takes the next free identifier of
 * its level.
 *
 * @since 0.1
 */
public final class WorkflowLogMessages {

    /** Prefix of all log messages of {@code pm-workflow}. */
    public static final String PREFIX = "PM_WORKFLOW";

    private WorkflowLogMessages() {
    }

    /** INFO level messages. */
    public static final class INFO {

        /**
         * Logged when every unit of a project has been loaded and verified (PM-IMPL-5), with the project id and the
         * number of verified units.
         */
        public static final LogRecord PROJECT_UNITS_VERIFIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Units of project '%s' verified: %s units")
                .build();

        private INFO() {
        }
    }

    /** WARN level messages. */
    public static final class WARN {

        /**
         * Logged for every finding that refuses a unit of a project (PM-IMPL-5), with the unit file, the line and the
         * rule or invariant id; the plan lifecycle is refused with {@code config_invalid}, and no default unit stands
         * in for the refused one.
         */
        public static final LogRecord PROJECT_UNIT_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Project unit '%s' refused at line %s by '%s', the plan lifecycle is refused with 'config_invalid'")
                .build();

        private WARN() {
        }
    }

    /** ERROR level messages. */
    public static final class ERROR {

        /**
         * Logged when a default unit read from the classpath fails to parse or verify (PM-IMPL-5), with the resource,
         * the line and the rule; a default unit that does not verify is a defect of the build.
         */
        public static final LogRecord DEFAULT_UNIT_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Default unit '%s' refused at line %s by '%s'")
                .build();

        private ERROR() {
        }
    }
}
