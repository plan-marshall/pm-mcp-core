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

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


import de.planmarshall.core.store.ProjectEnrolment.Binding;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;

/**
 * A job as its caller asks for it, before anything in it is trusted (PM-EXEC-2): the
 * {@link VerifyNotResolvePipeline} checks every part of it against the project's verified enrolment and either
 * verifies it or refuses it.
 * <p>
 * The request carries what the caller has determined and this package does not: the live linked worktrees of the
 * project, which the caller has verified (this package has no git interface), and the module set of the project
 * discovered for the {@code {module}} placeholder. A worker job of kind {@code llm} has no target here.
 *
 * @param projectId        the project; a malformed id is refused by the enrolment store as an
 *                         {@link IllegalArgumentException}
 * @param workingDirectory the working directory as the caller gave it, absolute or relative to the enrolled root;
 *                         never trusted as text, the verified job runs in its canonical form
 * @param liveWorktrees    the roots of the live linked worktrees of the project the caller verified; may be empty
 * @param modules          the module set of the project for the {@code {module}} placeholder; may be empty
 * @param target           what the job runs
 * @since 0.1
 */
public record JobRequest(String projectId, String workingDirectory, Set<Path> liveWorktrees, Set<String> modules,
Target target) {

    /**
     * @throws NullPointerException     if a component or an element is {@code null}
     * @throws IllegalArgumentException if the project id is empty
     */
    public JobRequest {
        nonEmpty(projectId, "projectId");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        liveWorktrees = Set.copyOf(Objects.requireNonNull(liveWorktrees, "liveWorktrees"));
        modules = Set.copyOf(Objects.requireNonNull(modules, "modules"));
        Objects.requireNonNull(target, "target");
    }

    /**
     * What a job runs. The set is closed.
     *
     * @since 0.1
     */
    public sealed interface Target permits WrapperBuild, HostToolBuild, HostToolCall, ProjectScriptCall {
    }

    /**
     * A build job through an enrolled build wrapper of the repository ({@code mvnw}, {@code gradlew}, {@code pw}).
     *
     * @param wrapper the wrapper
     * @param verb    the canonical verb, as the caller named it
     * @param module  the module of a module-scoped job; empty for a whole-tree job
     * @since 0.1
     */
    public record WrapperBuild(WrapperName wrapper, String verb, Optional<String> module) implements Target {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the verb is empty
         */
        public WrapperBuild {
            Objects.requireNonNull(wrapper, "wrapper");
            nonEmpty(verb, "verb");
            Objects.requireNonNull(module, "module");
        }
    }

    /**
     * A build job of the build system {@code npm}, run by the enrolled host tool {@code npm}.
     *
     * @param verb   the canonical verb, as the caller named it
     * @param module the module of a module-scoped job; empty for a whole-tree job
     * @since 0.1
     */
    public record HostToolBuild(String verb, Optional<String> module) implements Target {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the verb is empty
         */
        public HostToolBuild {
            nonEmpty(verb, "verb");
            Objects.requireNonNull(module, "module");
        }
    }

    /**
     * A call of an enrolled host tool with an argument vector.
     *
     * @param tool      the host tool
     * @param arguments the arguments after the executable, as the caller gave them
     * @since 0.1
     */
    public record HostToolCall(HostToolName tool, List<String> arguments) implements Target {

        /** @throws NullPointerException if a component or an argument is {@code null} */
        public HostToolCall {
            Objects.requireNonNull(tool, "tool");
            arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        }
    }

    /**
     * A run of an approved project script (PM-EXT-5).
     *
     * @param scriptId  the id of the script in the enrolment
     * @param binding   the use the job makes of the script; must match the script's enrolled binding
     * @param arguments the arguments of the run, validated against the script's enrolled argument schema
     * @since 0.1
     */
    public record ProjectScriptCall(String scriptId, Binding binding, Value arguments) implements Target {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the script id is empty
         */
        public ProjectScriptCall {
            nonEmpty(scriptId, "scriptId");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(arguments, "arguments");
        }
    }

    /**
     * A JSON value of the arguments of a project script. The set is closed.
     *
     * @since 0.1
     */
    public sealed interface Value permits Text, Numeric, Bool, JsonNull, Items, Fields {
    }

    /**
     * A JSON string.
     *
     * @param value the string
     * @since 0.1
     */
    public record Text(String value) implements Value {

        /** @throws NullPointerException if the value is {@code null} */
        public Text {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number.
     *
     * @param value the number
     * @since 0.1
     */
    public record Numeric(BigDecimal value) implements Value {

        /** @throws NullPointerException if the value is {@code null} */
        public Numeric {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON boolean.
     *
     * @param value the boolean
     * @since 0.1
     */
    public record Bool(boolean value) implements Value {
    }

    /**
     * The JSON null.
     *
     * @since 0.1
     */
    public record JsonNull() implements Value {
    }

    /**
     * A JSON array.
     *
     * @param items the elements, in order
     * @since 0.1
     */
    public record Items(List<Value> items) implements Value {

        /** @throws NullPointerException if the list or an element is {@code null} */
        public Items {
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    /**
     * A JSON object.
     *
     * @param fields the members by their names, in the natural order of the names
     * @since 0.1
     */
    public record Fields(SortedMap<String, Value> fields) implements Value {

        /** @throws NullPointerException if the map, a name or a value is {@code null} */
        public Fields {
            fields = copy(Objects.requireNonNull(fields, "fields"));
        }

        /**
         * @param fields the members by their names
         * @return the object of these members
         */
        public static Fields of(Map<String, Value> fields) {
            return new Fields(new TreeMap<>(fields));
        }

        private static SortedMap<String, Value> copy(Map<String, Value> fields) {
            var copy = new TreeMap<String, Value>();
            for (var entry : fields.entrySet()) {
                copy.put(Objects.requireNonNull(entry.getKey(), "name"), Objects.requireNonNull(entry.getValue(), "value"));
            }
            return Collections.unmodifiableSortedMap(copy);
        }
    }

    private static void nonEmpty(String value, String name) {
        if (Objects.requireNonNull(value, name).isEmpty()) {
            throw new IllegalArgumentException(name + " is not empty");
        }
    }
}
