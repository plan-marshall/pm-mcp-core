/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;


import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import lombok.experimental.UtilityClass;

/**
 * The check of a build wrapper in stage 2 of the pipeline (PM-EXEC-1, PM-EXEC-2): the wrapper is verified by its
 * location, its enrolled name and its digest, together with every file pinned to it.
 * <p>
 * The wrapper must sit at the root of the verified working-directory tree: its enrolled {@code relative_path} is a
 * single name, resolved against the canonical working directory and read without following a symbolic link. Its
 * name must be enrolled ({@code executable_not_enrolled} otherwise), the file name of its enrolled
 * {@code relative_path} must be that name ({@code mvnw}, {@code gradlew}, {@code pw}), and its SHA-256 must be the
 * enrolled one; a wrong name or digest counts as a changed path. Every
 * enrolled pinned file must match its digest: a file recorded with a digest that is missing or differs, and a file
 * recorded as must-stay-absent that now exists, each count as changed. Any change refuses the job as
 * {@code build_config_changed}, naming every changed repository-relative path and the {@code pm-operator enrol}
 * command that records the changed build configuration once the operator reviewed it.
 * <p>
 * Whether the wrapper is tracked or committed in git is not checked, and there is no git interface here: a wrapper
 * that matches its enrolment is the enrolled wrapper wherever it came from.
 *
 * @since 0.1
 */
@UtilityClass
class WrapperVerification {

    /**
     * @param projectId        the project
     * @param enrolment        the verified enrolment of the project
     * @param workingDirectory the canonical working directory of the job, the root of the verified tree
     * @param name             the wrapper the job runs
     * @return the absolute path of the verified wrapper, or the refusal
     * @throws UncheckedIOException if a file exists and cannot be read
     */
    static VerifyNotResolvePipeline.Stage<Path> verify(String projectId, ProjectEnrolment enrolment,
            Path workingDirectory, WrapperName name) {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        var enrolled = enrolment.wrappers().stream().filter(wrapper -> wrapper.name() == name).findFirst();
        if (enrolled.isEmpty()) {
            return VerifyNotResolvePipeline.Stage.failed(JobRefusal.of(Reason.EXECUTABLE_NOT_ENROLLED,
                    "the build wrapper %s is not enrolled".formatted(name.wireName())));
        }
        var wrapper = enrolled.get();
        var changed = new ArrayList<String>();
        var relativePath = wrapper.relativePath();
        if (!FileDigest.of(workingDirectory, relativePath).matches(wrapper.sha256())
                || !isRootFileNamed(Path.of(relativePath), name)) {
            changed.add(relativePath);
        }
        for (var pinned : wrapper.pinnedFiles()) {
            var found = FileDigest.of(workingDirectory, pinned.relativePath());
            var unchanged = pinned.mustBeAbsent() ? found instanceof FileDigest.Absent : found.matches(pinned.sha256());
            if (!unchanged) {
                changed.add(pinned.relativePath());
            }
        }
        if (!changed.isEmpty()) {
            return VerifyNotResolvePipeline.Stage.failed(JobRefusal.buildConfigChanged(projectId, changed,
                    "the build wrapper %s or a file pinned to it differs from the enrolment"
                            .formatted(name.wireName())));
        }
        return VerifyNotResolvePipeline.Stage.passed(workingDirectory.resolve(relativePath));
    }

    /** @return whether the path is a single name, and that name is the file name of the wrapper */
    private static boolean isRootFileNamed(Path relativePath, WrapperName name) {
        return relativePath.getNameCount() == 1 && relativePath.getFileName().toString().equals(name.wireName());
    }
}
