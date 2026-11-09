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
         * Logged when a load of a project's units from {@code .marshall/config/} found no finding ({@code PM-IMPL-5});
         * names the project id and the number of verified units, the plan unit and each phase sub-workflow counting as
         * one.
         * <p>
         * The component is {@code ProjectUnitLoader}.
         */
        public static final LogRecord PROJECT_UNITS_VERIFIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Units of project '%s' verified: %s units")
                .build();

        /**
         * Logged when a composed version of a plan instance is staged in the caller's scope transaction
         * ({@code PM-IMPL-5}), together with its line of {@code instance/composition.jsonl} and the texts of the
         * sub-units it is the first version to name; names the version, the plan id and the composition event. The
         * files are written when the caller commits the transaction.
         * <p>
         * The component is {@code PlanInstanceStore}.
         */
        public static final LogRecord PLAN_INSTANCE_VERSION_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(2)
                .template("Plan instance version %s of plan '%s' recorded for the event '%s'")
                .build();

        private INFO() {
        }
    }

    /** WARN level messages. */
    public static final class WARN {

        /**
         * Logged once for every finding of a load of a project's units ({@code PM-IMPL-5}); names the file below the
         * project (for example {@code .marshall/config/finalize.pmwf}), the line ({@code 0} for a file that could not
         * be read) and the code of the finding (the parser refusal or verification rule, the wire id of an invariant
         * such as {@code INV-GEN-1}, {@code format_newer}, {@code declaration_invalid}, a pipeline rule,
         * {@code project_step_unbound}, {@code unit_missing} or {@code path_traversal_rejected}). The load returns no
         * unit; no default unit stands in for the refused one.
         * <p>
         * The component is {@code ProjectUnitLoader}.
         */
        public static final LogRecord PROJECT_UNIT_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Project unit '%s' refused at line %s by '%s', the plan lifecycle is refused with 'config_invalid'")
                .build();

        /**
         * Logged when a refused composition attempt is recorded in {@code instance/composition.jsonl}
         * ({@code PM-IMPL-5}); names the composition event, the plan id and the refusal
         * ({@code position_not_contained} or {@code invariant_failed}). No version file is written; the version in force
         * stays.
         * <p>
         * The component is {@code PlanInstanceStore}.
         */
        public static final LogRecord PLAN_INSTANCE_COMPOSITION_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Composition '%s' of plan '%s' refused with '%s', the version in force is kept")
                .build();

        private WARN() {
        }
    }

    /** ERROR level messages. */
    public static final class ERROR {

        /**
         * Logged once for every finding of a load of the default units from the classpath ({@code PM-IMPL-5}); names
         * the resource (for example {@code workflows/init.pmwf}), the line ({@code 0} for a resource that is missing or
         * cannot be read) and the code of the finding ({@code unit_missing}, {@code unit_unreadable}, the parser
         * refusal or the verification rule). A default unit that does not verify is a defect of the build.
         * <p>
         * The component is {@code DefaultUnitLoader}.
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
