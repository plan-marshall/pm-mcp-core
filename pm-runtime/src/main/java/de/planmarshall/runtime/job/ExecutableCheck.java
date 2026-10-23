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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.HostToolBuild;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;

/**
 * Stage 2 of the pipeline, the executable and its digest (PM-EXEC-1, PM-EXEC-2, PM-EXEC-3): answers the verified
 * absolute start of the job's argument vector, the executable and, where one runs the target, its interpreter first.
 * <p>
 * Nothing is looked up through {@code PATH} and no shebang is read:
 * <ul>
 * <li>a host tool must be enrolled by its name ({@code executable_not_enrolled} otherwise), its recorded
 * {@code executable_path} must be absolute and its current SHA-256 must be the enrolled one
 * ({@code executable_digest_mismatch} otherwise); a host tool that is a script ({@code asciidoctor}) is verified
 * together with its enrolled interpreter ({@code ruby}) and starts as the interpreter followed by the script;</li>
 * <li>a build job of {@code npm} runs the enrolled host tool {@code npm};</li>
 * <li>a wrapper job is verified by {@link WrapperVerification}; {@code pw} runs through the enrolled host tool
 * {@code python3} with the wrapper as its first argument, never through its shebang;</li>
 * <li>a project script must be enrolled by its id, the job's use must match its {@code binding}
 * ({@code executable_not_enrolled} otherwise), and its {@code script_path}, inside the verified working-directory tree,
 * and its interpreter {@code python3} must match their digests ({@code executable_digest_mismatch} otherwise).</li>
 * </ul>
 *
 * @since 0.1
 */
@UtilityClass
class ExecutableCheck {

    /**
     * @param enrolment        the verified enrolment of the project
     * @param workingDirectory the canonical working directory of the job
     * @param target           what the job runs
     * @return the verified absolute start of the argument vector, or the refusal
     * @throws UncheckedIOException if a file exists and cannot be read
     */
    static VerifyNotResolvePipeline.Stage<List<String>> check(ProjectEnrolment enrolment,
            Path workingDirectory, JobRequest.Target target) {
        return switch (target) {
            case HostToolCall call -> hostTool(enrolment, call.tool());
            case HostToolBuild _ -> hostTool(enrolment, HostToolName.NPM);
            case WrapperBuild build -> wrapper(enrolment, workingDirectory, build.wrapper());
            case ProjectScriptCall call -> projectScript(enrolment, workingDirectory, call);
        };
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> wrapper(ProjectEnrolment enrolment,
            Path workingDirectory, WrapperName name) {
        var wrapper = WrapperVerification.verify(enrolment, workingDirectory, name);
        if (wrapper instanceof VerifyNotResolvePipeline.Stage.Failed<Path>(var refusal)) {
            return VerifyNotResolvePipeline.Stage.failed(refusal);
        }
        var path = ((VerifyNotResolvePipeline.Stage.Passed<Path>) wrapper).value().toString();
        if (name != WrapperName.PW) {
            return VerifyNotResolvePipeline.Stage.passed(List.of(path));
        }
        return prepend(interpreter(enrolment, Interpreter.PYTHON3), path);
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> projectScript(ProjectEnrolment enrolment,
            Path workingDirectory, ProjectScriptCall call) {
        var script = enrolment.projectScripts().get(call.scriptId());
        if (script == null) {
            return notEnrolled("the project script %s is not enrolled".formatted(call.scriptId()));
        }
        if (!script.binding().equals(call.binding())) {
            return notEnrolled("the project script %s is not enrolled for the use %s of %s".formatted(
                    call.scriptId(), call.binding().workerKind().wireName(), call.binding().target()));
        }
        if (!FileDigest.of(workingDirectory, script.scriptPath()).matches(script.sha256())) {
            return mismatch("the project script %s differs from its enrolled digest".formatted(call.scriptId()));
        }
        return prepend(interpreter(enrolment, script.interpreter()),
                workingDirectory.resolve(script.scriptPath()).toString());
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> interpreter(ProjectEnrolment enrolment,
            Interpreter interpreter) {
        var name = HostToolName.fromWireName(interpreter.wireName()).orElseThrow();
        return hostTool(enrolment, name);
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> hostTool(ProjectEnrolment enrolment,
            HostToolName name) {
        var enrolled = enrolment.hostTools().stream().filter(tool -> tool.name() == name).findFirst();
        if (enrolled.isEmpty()) {
            return notEnrolled("the host tool %s is not enrolled".formatted(name.wireName()));
        }
        var tool = enrolled.get();
        var path = absolutePath(tool);
        if (path == null) {
            return notEnrolled("the enrolled path of the host tool %s is not absolute".formatted(name.wireName()));
        }
        if (!FileDigest.of(path).matches(tool.sha256())) {
            return mismatch("the host tool %s differs from its enrolled digest".formatted(name.wireName()));
        }
        if (tool.interpreter() == null) {
            return VerifyNotResolvePipeline.Stage.passed(List.of(path.toString()));
        }
        return prepend(interpreter(enrolment, tool.interpreter()), path.toString());
    }

    /** @return the enrolled path of the tool if it is a normalized absolute path, else {@code null} */
    private static Path absolutePath(HostTool tool) {
        try {
            var path = Path.of(tool.executablePath());
            return path.isAbsolute() && path.normalize().equals(path) ? path : null;
        } catch (InvalidPathException _) {
            return null;
        }
    }

    /** Appends the target to the verified start of an interpreter, or passes its refusal on. */
    private static VerifyNotResolvePipeline.Stage<List<String>> prepend(
            VerifyNotResolvePipeline.Stage<List<String>> interpreter, String target) {
        if (interpreter instanceof VerifyNotResolvePipeline.Stage.Passed<List<String>>(var start)) {
            var argv = new ArrayList<>(start);
            argv.add(target);
            return VerifyNotResolvePipeline.Stage.passed(List.copyOf(argv));
        }
        return interpreter;
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> notEnrolled(String detail) {
        return VerifyNotResolvePipeline.Stage.failed(JobRefusal.of(Reason.EXECUTABLE_NOT_ENROLLED, detail));
    }

    private static VerifyNotResolvePipeline.Stage<List<String>> mismatch(String detail) {
        return VerifyNotResolvePipeline.Stage.failed(JobRefusal.of(Reason.EXECUTABLE_DIGEST_MISMATCH, detail));
    }
}
