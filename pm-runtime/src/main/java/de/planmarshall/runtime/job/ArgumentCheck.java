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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.Fields;
import de.planmarshall.runtime.job.JobRequest.HostToolBuild;
import de.planmarshall.runtime.job.JobRequest.HostToolCall;
import de.planmarshall.runtime.job.JobRequest.Items;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.job.JobRequest.Text;
import de.planmarshall.runtime.job.JobRequest.Value;
import de.planmarshall.runtime.job.JobRequest.WrapperBuild;
import de.planmarshall.runtime.job.VerifiedJob.TemplateSource;
import lombok.experimental.UtilityClass;

/**
 * Stage 3 of the pipeline, the argument check (PM-EXEC-1, PM-EXEC-2): the last stage, run only after the enrolment,
 * the working directory and the executable passed, so nothing a caller supplies is looked at for a job whose
 * executable is not verified. It answers the arguments that follow the verified executable.
 * <ul>
 * <li>A build job takes the template enrolled for its build system and verb
 * ({@code build.canonical_templates.<build_system>.<verb>}, source {@code enrolled}), else the template the caller's
 * {@link DefaultTemplates} supplies (source {@code compiled_default}); without either the job is refused as
 * {@code verb_not_supported}. A module-scoped job renders the module-argument group with {@code {module}} substituted,
 * the module must be one of the caller's discovered modules ({@code argument_rejected} otherwise), and a template
 * without a group refuses it as {@code verb_not_supported}; a whole-tree job omits the group.</li>
 * <li>A host tool call that names an enrolled interpreter is refused as {@code argument_rejected}, whatever its
 * arguments: a host tool that is the interpreter of a wrapper ({@code python3} of {@code pw}), of a host tool that is a
 * script ({@code ruby} of {@code asciidoctor}) or of a project script ({@code python3}), as the enrolment records it
 * ({@link #enrolledInterpreters(ProjectEnrolment)}). Such an interpreter would run whatever code the caller passes
 * ({@code python3 -c <code>}); it runs only through what it interprets, a project script bound by its binding and its
 * argument schema, a script host tool or a wrapper, all of which stage 2 starts with the verified interpreter first.
 * The refusal names the host tool, not the arguments. Any other host tool call passes its arguments on after they are
 * checked.</li>
 * <li>A project script job's arguments must validate against the script's enrolled argument schema
 * ({@link ArgumentSchemaValidator}, {@code argument_rejected} otherwise); they add nothing to the argument vector,
 * the verified job carries them as {@link VerifiedJob#projectScriptArguments()}.</li>
 * </ul>
 * <p>
 * Every argument the caller supplies (a module, an argument of a host tool call, a string of a project script's
 * arguments, a member name included) is refused as {@code argument_rejected} if it contains a shell metacharacter
 * ({@code |}, {@code ;}, {@code &}, {@code $}, {@code `}, {@code >}, {@code <}), whatever the kind of the job.
 * <p>
 * The forbidden free flags {@code -D}, {@code -f}, {@code -s} (also with an attached value, so they are matched as
 * prefixes), {@code --file}, {@code --settings}, {@code --init-script} (also with {@code =value}) are refused as
 * {@code argument_rejected} in canonical build jobs only (a {@link WrapperBuild} or a {@link HostToolBuild}), where
 * they would redirect the build to other settings, another project file or injected properties. A host tool call and
 * a project script are not checked against them: their arguments stay bound by the enrolment of the tool, and those of
 * a project script by its argument schema, so a call such as {@code git status -sb} is verified.
 * <p>
 * The enrolled templates are the operator's and are not filtered. Flags that repository files inject
 * ({@code .mvn/maven.config} and the like) are governed by the pinned files of stage 2, not here. A refusal names
 * which rule the argument broke, never the argument.
 *
 * @since 0.1
 */
@UtilityClass
class ArgumentCheck {

    /** The placeholder of the module-argument group. */
    static final String MODULE_PLACEHOLDER = "{module}";

    /** The forbidden short flags of a build job, refused alone and with an attached value. */
    static final List<String> FORBIDDEN_SHORT_FLAGS = List.of("-D", "-f", "-s");

    /** The forbidden long flags of a build job, refused alone and with {@code =value}. */
    static final List<String> FORBIDDEN_LONG_FLAGS = List.of("--file", "--settings", "--init-script");

    /** The shell metacharacters, refused anywhere in an argument of every job kind. */
    static final String SHELL_METACHARACTERS = "|;&$`><";

    /**
     * The arguments after the verified executable, and where the template of a build job came from.
     *
     * @param arguments the arguments, in order
     * @param source    the source of the template of a build job; empty for any other job
     */
    record Arguments(List<String> arguments, Optional<TemplateSource> source) {

        Arguments {
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * @param enrolment the verified enrolment of the project
     * @param defaults  the default templates the caller supplies
     * @param request   the job as the caller asks for it
     * @return the checked arguments, or the refusal
     */
    static VerifyNotResolvePipeline.Stage<Arguments> check(ProjectEnrolment enrolment, DefaultTemplates defaults,
            JobRequest request) {
        return switch (request.target()) {
            case WrapperBuild(var wrapper, var verb, var module) -> build(enrolment, defaults, buildSystem(wrapper),
                    verb, module, request.modules());
            case HostToolBuild(var verb, var module) -> build(enrolment, defaults, BuildSystem.NPM, verb, module,
                    request.modules());
            case HostToolCall call -> hostToolCall(enrolment, call);
            case ProjectScriptCall call -> projectScript(enrolment, call);
        };
    }

    /**
     * The host tools that are an enrolled interpreter: the interpreter of a wrapper ({@code pw}), of a host tool that
     * is a script ({@code asciidoctor}) or of a project script, as the enrolment records them.
     *
     * @param enrolment the verified enrolment of the project
     * @return the host tools that run only through what they interpret, never as a host tool call of their own
     */
    static Set<HostToolName> enrolledInterpreters(ProjectEnrolment enrolment) {
        var interpreters = EnumSet.noneOf(HostToolName.class);
        enrolment.wrappers().stream().map(ProjectEnrolment.Wrapper::interpreter).filter(Objects::nonNull)
                .forEach(interpreter -> interpreters.add(hostToolName(interpreter)));
        enrolment.hostTools().stream().map(ProjectEnrolment.HostTool::interpreter).filter(Objects::nonNull)
                .forEach(interpreter -> interpreters.add(hostToolName(interpreter)));
        enrolment.projectScripts().values().stream().map(ProjectEnrolment.ProjectScript::interpreter)
                .forEach(interpreter -> interpreters.add(hostToolName(interpreter)));
        return interpreters;
    }

    private static HostToolName hostToolName(Interpreter interpreter) {
        return HostToolName.fromWireName(interpreter.wireName()).orElseThrow();
    }

    private static BuildSystem buildSystem(WrapperName wrapper) {
        return switch (wrapper) {
            case MVNW -> BuildSystem.MAVEN;
            case GRADLEW -> BuildSystem.GRADLE;
            case PW -> BuildSystem.PYPROJECT;
        };
    }

    private static VerifyNotResolvePipeline.Stage<Arguments> build(ProjectEnrolment enrolment,
            DefaultTemplates defaults, BuildSystem buildSystem, String verb, Optional<String> module,
            Set<String> modules) {
        var enrolled = Optional.ofNullable(enrolment.build().canonicalTemplates().get(buildSystem))
                .map(templates -> templates.get(verb));
        TemplateSource source;
        Argv template;
        if (enrolled.isPresent()) {
            source = TemplateSource.ENROLLED;
            template = enrolled.get();
        } else {
            var fallback = Objects.requireNonNull(defaults.template(buildSystem, verb), "default template");
            if (fallback.isEmpty()) {
                return refused(Reason.VERB_NOT_SUPPORTED, "the verb %s of %s has neither an enrolled nor a default template"
                        .formatted(verb, buildSystem.wireName()));
            }
            source = TemplateSource.COMPILED_DEFAULT;
            template = fallback.get();
        }
        if (module.isPresent()) {
            var rejected = rejectedBuildArgument(module.get());
            if (rejected.isPresent()) {
                return refused(Reason.ARGUMENT_REJECTED, "the module " + rejected.get());
            }
            if (!modules.contains(module.get())) {
                return refused(Reason.ARGUMENT_REJECTED, "the module is not a discovered module of the project");
            }
            if (template.elements().stream().noneMatch(Argv.ModuleGroup.class::isInstance)) {
                return refused(Reason.VERB_NOT_SUPPORTED,
                        "the template of the verb %s of %s has no module-argument group, so it runs whole-tree only"
                                .formatted(verb, buildSystem.wireName()));
            }
        }
        var arguments = new ArrayList<String>();
        for (var element : template.elements()) {
            switch (element) {
                case Argv.Literal(var value) -> arguments.add(value);
                case Argv.ModuleGroup(var group) -> module.ifPresent(name -> group
                        .forEach(argument -> arguments.add(argument.replace(MODULE_PLACEHOLDER, name))));
            }
        }
        return VerifyNotResolvePipeline.Stage.passed(new Arguments(arguments, Optional.of(source)));
    }

    private static VerifyNotResolvePipeline.Stage<Arguments> hostToolCall(ProjectEnrolment enrolment,
            HostToolCall call) {
        if (enrolledInterpreters(enrolment).contains(call.tool())) {
            return refused(Reason.ARGUMENT_REJECTED, ("the host tool %s is an enrolled interpreter and runs only through"
                    + " a project script, a script host tool or a wrapper").formatted(call.tool().wireName()));
        }
        var arguments = call.arguments();
        for (var index = 0; index < arguments.size(); index++) {
            var rejected = rejectedArgument(arguments.get(index));
            if (rejected.isPresent()) {
                return refused(Reason.ARGUMENT_REJECTED, "the argument %d %s".formatted(index + 1, rejected.get()));
            }
        }
        return VerifyNotResolvePipeline.Stage.passed(new Arguments(arguments, Optional.empty()));
    }

    private static VerifyNotResolvePipeline.Stage<Arguments> projectScript(ProjectEnrolment enrolment,
            ProjectScriptCall call) {
        var rejected = rejectedValue(call.arguments(), ArgumentSchemaValidator.ROOT);
        if (rejected.isPresent()) {
            return refused(Reason.ARGUMENT_REJECTED, rejected.get());
        }
        var script = enrolment.projectScripts().get(call.scriptId());
        if (script == null) {
            return refused(Reason.EXECUTABLE_NOT_ENROLLED,
                    "the project script %s is not enrolled".formatted(call.scriptId()));
        }
        var violation = ArgumentSchemaValidator.validate(script.argumentSchema(), call.arguments());
        if (violation.isPresent()) {
            return refused(Reason.ARGUMENT_REJECTED,
                    "the arguments violate the argument schema: " + violation.get());
        }
        return VerifyNotResolvePipeline.Stage.passed(new Arguments(List.of(), Optional.empty()));
    }

    /** Checks every string of the arguments of a project script, the names of the members included. */
    private static Optional<String> rejectedValue(Value value, String path) {
        return switch (value) {
            case Text(var text) -> rejectedArgument(text).map(rule -> "the value at %s %s".formatted(path, rule));
            case Items(var items) -> {
                for (var element : items) {
                    var rejected = rejectedValue(element, path + "[]");
                    if (rejected.isPresent()) {
                        yield rejected;
                    }
                }
                yield Optional.empty();
            }
            case Fields(var fields) -> {
                for (var member : fields.entrySet()) {
                    var name = rejectedArgument(member.getKey());
                    if (name.isPresent()) {
                        yield Optional.of("a member name at %s %s".formatted(path, name.get()));
                    }
                    var rejected = rejectedValue(member.getValue(), path + "." + member.getKey());
                    if (rejected.isPresent()) {
                        yield rejected;
                    }
                }
                yield Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    /**
     * The rule for an argument of every job kind: no shell metacharacter.
     *
     * @param argument an argument the caller supplied
     * @return which rule the argument breaks, without the argument; empty if it breaks none
     */
    static Optional<String> rejectedArgument(String argument) {
        for (var index = 0; index < argument.length(); index++) {
            if (SHELL_METACHARACTERS.indexOf(argument.charAt(index)) >= 0) {
                return Optional.of("contains a shell metacharacter");
            }
        }
        return Optional.empty();
    }

    /**
     * The rules for an argument of a canonical build job: no shell metacharacter and none of the forbidden free flags.
     *
     * @param argument an argument the caller supplied to a build job
     * @return which rule the argument breaks, without the argument; empty if it breaks none
     */
    static Optional<String> rejectedBuildArgument(String argument) {
        var metacharacter = rejectedArgument(argument);
        if (metacharacter.isPresent()) {
            return metacharacter;
        }
        for (var flag : FORBIDDEN_SHORT_FLAGS) {
            if (argument.startsWith(flag)) {
                return Optional.of("is the forbidden flag " + flag);
            }
        }
        for (var flag : FORBIDDEN_LONG_FLAGS) {
            if (argument.equals(flag) || argument.startsWith(flag + "=")) {
                return Optional.of("is the forbidden flag " + flag);
            }
        }
        return Optional.empty();
    }

    private static VerifyNotResolvePipeline.Stage<Arguments> refused(Reason reason, String detail) {
        return VerifyNotResolvePipeline.Stage.failed(JobRefusal.of(reason, detail));
    }
}
