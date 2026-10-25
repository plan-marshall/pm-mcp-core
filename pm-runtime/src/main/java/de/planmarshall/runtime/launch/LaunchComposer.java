/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.security.JobTokens;

/**
 * Composes one worker launch from server-owned inputs (PM-WORK-1, Launch Contract; PM-CLIENT-2): the role's effective
 * harness, model and effort, the harness profile's template and per-class arguments, the working-directory rule, the
 * tool set, the launch prompt, the per-call MCP configuration and the minted job token.
 * <p>
 * A tier of the default assignment is rendered into the model the harness maps it to, and an effort the harness does
 * not accept is narrowed to the nearest accepted lower value. The ephemeral flag is withheld for a role that may resume
 * after a question, and a resumed generation is launched with the profile's {@code resume.argv}. The job record's
 * {@code worker} block is composed here, from the same values, so no contract field is validated by the model.
 *
 * @since 0.1
 */
public final class LaunchComposer {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final String PROMPT_FILE = "prompt.md";
    private static final String MEMBER_MODEL = "model";
    private static final String MEMBER_EFFORT = "effort";
    private static final String RESULT_SCHEMA_FILE = "result-schema.json";
    private static final String CWD = "cwd";
    private static final String EPHEMERAL_FLAG = "no-session-persistence";
    private static final String RELAY = "pm-job";

    /**
     * @param request what to compose
     * @return the composed launch
     * @throws CompositionException if the harness cannot run the role's class, the prompt cannot be composed or a file
     *                              of the job-private directory cannot be written
     */
    public WorkerLaunch compose(LaunchRequest request) {
        Objects.requireNonNull(request, "request");
        var role = request.role();
        var block = request.block();
        var roleClass = role.roleClass();
        requireClass(block, roleClass);
        var now = request.clock().instant();
        var jobPrivate = request.jobPrivateDirectory().toAbsolutePath().normalize();
        createDirectories(jobPrivate);
        var workdirKind = roleClass == RoleClass.ZERO_TOOL ? LaunchRequest.WorkdirKind.JOB_PRIVATE
                : request.workdirKind();
        var workingDirectory = workingDirectory(request, jobPrivate, workdirKind);
        var model = renderModel(role.model(), block);
        var effort = narrowEffort(role, model, block);
        var prompt = composePrompt(request, role, roleClass);
        var promptFile = writeFile(jobPrivate.resolve(PROMPT_FILE), prompt.text());
        var resultSchema = resultSchemaFile(request, roleClass, jobPrivate);
        var token = roleClass == RoleClass.ZERO_TOOL ? "" : JobTokens.mint();
        var tokenSha256 = token.isEmpty() ? "" : JobTokens.sha256Hex(token);
        var mcp = composeMcp(request, roleClass, workingDirectory, token);
        var tools = roleClass == RoleClass.ZERO_TOOL ? List.<String>of() : role.artifact().tools();
        var argv = composeArgv(request, block, roleClass, workingDirectory, model, effort.value(), promptFile,
                resultSchema, tools, mcp);
        var env = composeEnvironment(request, jobPrivate);
        var workerBlock = workerBlock(request, role, roleClass, workdirKind, model, effort, prompt, now, tokenSha256);
        return new WorkerLaunch(request.generationId(), role.artifact().name(), roleClass,
                request.fresh() ? RoleForm.FRESH : RoleForm.WARM,
                argv, env, workingDirectory, jobPrivate, promptFile, prompt, Optional.ofNullable(mcp.file()),
                Optional.ofNullable(mcp.content()), token, tokenSha256, role.harness(), request.enrolment().version(),
                model, effort.value(), effort.narrowedFrom(), workerBlock);
    }

    /**
     * The {@code worker} block a refused launch is recorded with: the values the runtime knows before the refusal, an
     * empty prompt digest and no connection. The job record requires the block for the kind {@code llm} on every fate.
     *
     * @param role         the effective role of the refused launch
     * @param generationId the generation the launch would have had
     * @param now          the instant of the refusal
     * @return the worker block
     */
    public static JsonNode refusalBlock(EffectiveRole role, String generationId, Instant now) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("role", JsonNode.string(role.artifact().name()));
        members.put("configuration_digest", JsonNode.string(role.configurationDigest()));
        members.put("class", JsonNode.string(role.roleClass().wireName()));
        members.put("form", JsonNode.string(role.form().wireName()));
        members.put("harness", JsonNode.string(role.harness()));
        members.put("harness_version", JsonNode.string("-"));
        members.put(MEMBER_MODEL, JsonNode.string(role.model()));
        members.put(MEMBER_EFFORT, JsonNode.nullValue());
        members.put("effort_narrowed_from", JsonNode.nullValue());
        members.put("generation_id", JsonNode.string(generationId));
        members.put("workdir", JsonNode.object(Map.of("kind", JsonNode.string("project_checkout"), "plan_id",
                JsonNode.nullValue())));
        members.put("prompt_digest", JsonNode.string("0".repeat(64)));
        members.put("job_token_sha256", JsonNode.nullValue());
        members.put("connected_at", JsonNode.nullValue());
        members.put("state", JsonNode.string("lost"));
        members.put("state_at", JsonNode.string(now.toString()));
        members.put("task", JsonNode.nullValue());
        members.put("served_tasks", JsonNode.array(List.of()));
        members.put("worktree_mark", JsonNode.nullValue());
        members.put("idle_wakeups", JsonNode.integer(0));
        members.put("context_tokens", JsonNode.string("unmeasured"));
        members.put("delivered_skills", JsonNode.array(List.of()));
        members.put("end_reason", JsonNode.nullValue());
        members.put("start_failure", JsonNode.bool(true));
        members.put("harness_error", JsonNode.nullValue());
        return JsonNode.object(members);
    }

    private static void requireClass(HeadlessBlock block, RoleClass roleClass) {
        if (!block.runs(roleClass)) {
            throw new CompositionException("the harness '%s' runs no %s worker".formatted(block.executable(),
                    roleClass.wireName()));
        }
        if (roleClass == RoleClass.ZERO_TOOL && !block.projectSettingsControl()) {
            throw new CompositionException("the harness cannot turn the project settings off for a zero_tool worker");
        }
        if (roleClass != RoleClass.ZERO_TOOL && !block.extensionIsolation()) {
            throw new CompositionException("the harness does not declare extension isolation for a %s worker".formatted(
                    roleClass.wireName()));
        }
    }

    private static Path workingDirectory(LaunchRequest request, Path jobPrivate, LaunchRequest.WorkdirKind workdirKind) {
        if (workdirKind == LaunchRequest.WorkdirKind.JOB_PRIVATE) {
            var cwd = jobPrivate.resolve(CWD);
            createDirectories(cwd);
            return cwd;
        }
        return request.workdir().toAbsolutePath().normalize();
    }

    /**
     * @param model the role's effective model, which may be a tier of the default assignment
     * @param block the harness's launch contract
     * @return the model the harness takes
     */
    public static String renderModel(String model, HeadlessBlock block) {
        return block.tierModel(model).orElse(model);
    }

    /**
     * @param role  the effective role
     * @param model the model the harness takes
     * @param block the harness's launch contract
     * @return the effort to pass, narrowed to the nearest accepted lower value
     */
    public static NarrowedEffort narrowEffort(EffectiveRole role, String model, HeadlessBlock block) {
        var declared = role.effort().value();
        var accepted = block.efforts(model);
        if (declared == null) {
            return new NarrowedEffort(null, null);
        }
        if (accepted.isEmpty()) {
            return new NarrowedEffort(null, declared);
        }
        if (accepted.contains(declared)) {
            return new NarrowedEffort(declared, null);
        }
        String lower = null;
        for (var value : accepted) {
            if (value.compareTo(declared) < 0) {
                lower = value;
            }
        }
        return new NarrowedEffort(lower, declared);
    }

    private static LaunchPrompt composePrompt(LaunchRequest request, EffectiveRole role, RoleClass roleClass) {
        try {
            return new PromptComposer().compose(new PromptComposer.PromptRequest(request.shimParagraph(),
                    roleClass == RoleClass.ZERO_TOOL, request.zeroToolPrompt(), request.skills(), role.skills(),
                    request.fresh(), request.writesFreeText(), request.stateSkills(), request.domainSkills(),
                    request.envelope(), request.inBandBoundChars()));
        } catch (PromptComposer.CompositionException failure) {
            throw new CompositionException(failure.getMessage(), failure);
        }
    }

    private static Path resultSchemaFile(LaunchRequest request, RoleClass roleClass, Path jobPrivate) {
        if (roleClass == RoleClass.ZERO_TOOL || !request.fresh() || request.envelope() == null) {
            return null;
        }
        return writeFile(jobPrivate.resolve(RESULT_SCHEMA_FILE),
                CanonicalJson.write(request.envelope().resultSchema()) + "\n");
    }

    private static McpComposition composeMcp(LaunchRequest request, RoleClass roleClass, Path workingDirectory,
            String token) {
        if (roleClass == RoleClass.ZERO_TOOL) {
            return new McpComposition(null, null);
        }
        var binary = request.runtimeHome().toAbsolutePath().normalize().resolve("bin").resolve("pm-mcp");
        var relay = new LinkedHashMap<String, JsonNode>();
        relay.put("command", JsonNode.string(binary.toString()));
        relay.put("args", JsonNode.array(List.of(JsonNode.string("serve"), JsonNode.string("--job"),
                JsonNode.string("--socket"), JsonNode.string(request.relaySocket().toString()))));
        relay.put("env", JsonNode.object(Map.of("PM_MCP_JOB_TOKEN", JsonNode.string(token), "PM_MCP_JOB_ID",
                JsonNode.string(request.generationId()))));
        var config = JsonNode.object(Map.of("mcpServers", JsonNode.object(Map.of(RELAY, JsonNode.object(relay)))));
        var json = CanonicalJson.write(config);
        if (request.block().mcpConfig().delivery() == HeadlessBlock.McpDelivery.FILE) {
            var path = workingDirectory.resolve(request.block().mcpConfig().path()).normalize();
            if (!path.startsWith(workingDirectory)) {
                throw new CompositionException("the MCP configuration path escapes the working directory: "
                        + request.block().mcpConfig().path());
            }
            writeFile(path, json + "\n");
            return new McpComposition(path, null);
        }
        return new McpComposition(null, json);
    }

    // java:S107 does not apply: the parameters are the slots a launch fills, one per input of the composition; a
    // holder would hide which slot a caller sets and where its value comes from.
    @SuppressWarnings("java:S107")
    private static List<String> composeArgv(LaunchRequest request, HeadlessBlock block, RoleClass roleClass,
            Path workingDirectory, String model, String effort, Path promptFile, Path resultSchema, List<String> tools,
            McpComposition mcp) {
        var template = request.resumeSessionId().isPresent() && !block.resumeArgv().isEmpty()
                ? block.resumeArgv()
                : block.argumentsFor(roleClass);
        var slots = new LinkedHashMap<String, String>();
        slots.put(MEMBER_MODEL, model);
        slots.put(MEMBER_EFFORT, effort);
        slots.put("prompt_file", promptFile.toString());
        slots.put("result_schema", resultSchema == null ? null : resultSchema.toString());
        slots.put("tools", tools.isEmpty() ? null : String.join(",", tools));
        slots.put("cwd", workingDirectory.toString());
        slots.put("mcp_config", mcp.slot());
        slots.put("agent", block.agentMode() == HeadlessBlock.AgentMode.INLINE ? inlineAgent(request, model, effort,
                tools) : null);
        request.resumeSessionId().ifPresent(session -> slots.put("session_id", session));
        var filled = fill(template, slots);
        if (filled.isEmpty()) {
            throw new CompositionException("the argument template of '%s' rendered empty".formatted(roleClass
                    .wireName()));
        }
        filled.set(0, request.enrolment().executablePath().toString());
        if (mayResume(request)) {
            filled.removeIf(argument -> argument.contains(EPHEMERAL_FLAG));
        }
        return List.copyOf(filled);
    }

    private static boolean mayResume(LaunchRequest request) {
        return request.role().questions().resumeWithinSeconds() > 0 && !request.block().resumeArgv().isEmpty();
    }

    private static String inlineAgent(LaunchRequest request, String model, String effort, List<String> tools) {
        var agent = new LinkedHashMap<String, JsonNode>();
        agent.put("description", JsonNode.string("PM-MCP worker '%s'".formatted(request.role().artifact().name())));
        agent.put("tools", JsonNode.array(tools.stream().map(JsonNode::string).toList()));
        agent.put(MEMBER_MODEL, JsonNode.string(model));
        if (effort != null) {
            agent.put(MEMBER_EFFORT, JsonNode.string(effort));
        }
        return CanonicalJson.write(JsonNode.object(Map.of(RELAY, JsonNode.object(agent))));
    }

    private static Map<String, String> composeEnvironment(LaunchRequest request, Path jobPrivate) {
        var env = new LinkedHashMap<>(request.block().env());
        var dataHome = request.block().dataHome();
        if (dataHome != null) {
            var data = jobPrivate.resolve("data");
            createDirectories(data);
            env.put(dataHome.variable(), data.toString());
        }
        return env;
    }

    // java:S107 does not apply: the parameters are the members of the worker block, one per value the record carries.
    @SuppressWarnings("java:S107")
    private static JsonNode workerBlock(LaunchRequest request, EffectiveRole role, RoleClass roleClass,
            LaunchRequest.WorkdirKind workdirKind, String model, NarrowedEffort effort, LaunchPrompt prompt,
            Instant now, String tokenSha256) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("role", JsonNode.string(role.artifact().name()));
        members.put("configuration_digest", JsonNode.string(role.configurationDigest()));
        members.put("class", JsonNode.string(roleClass.wireName()));
        members.put("form", JsonNode.string(request.fresh() ? "fresh" : "warm"));
        members.put("harness", JsonNode.string(role.harness()));
        members.put("harness_version", JsonNode.string(request.enrolment().version()));
        members.put(MEMBER_MODEL, JsonNode.string(model));
        members.put(MEMBER_EFFORT, effort.value() == null ? JsonNode.nullValue() : JsonNode.string(effort.value()));
        members.put("effort_narrowed_from", effort.narrowedFrom() == null ? JsonNode.nullValue()
                : JsonNode.string(effort.narrowedFrom()));
        members.put("generation_id", JsonNode.string(request.generationId()));
        members.put("workdir", JsonNode.object(Map.of("kind", JsonNode.string(workdirKind.wireName()),
                "plan_id", request.planId() == null ? JsonNode.nullValue() : JsonNode.string(request.planId()))));
        members.put("prompt_digest", JsonNode.string(prompt.digest()));
        members.put("job_token_sha256", roleClass == RoleClass.ZERO_TOOL || tokenSha256.isEmpty() ? JsonNode.nullValue()
                : JsonNode.string(tokenSha256));
        members.put("connected_at", JsonNode.nullValue());
        members.put("state", JsonNode.string("waiting"));
        members.put("state_at", JsonNode.string(now.toString()));
        members.put("task", JsonNode.nullValue());
        members.put("served_tasks", JsonNode.array(List.of()));
        members.put("worktree_mark", JsonNode.nullValue());
        members.put("idle_wakeups", JsonNode.integer(role.idleWakeups() == null ? 0 : role.idleWakeups()));
        members.put("context_tokens", JsonNode.string("unmeasured"));
        members.put("delivered_skills", JsonNode.array(prompt.delivered().stream().map(skill -> JsonNode.object(Map.of(
                "uri", JsonNode.string(skill.uri()), "digest", JsonNode.string(skill.digest()), "delivered_at",
                JsonNode.string(now.toString())))).toList()));
        members.put("end_reason", JsonNode.nullValue());
        members.put("start_failure", JsonNode.bool(false));
        members.put("harness_error", JsonNode.nullValue());
        return JsonNode.object(members);
    }

    private static List<String> fill(List<String> template, Map<String, String> slots) {
        var filled = new ArrayList<String>();
        for (var element : template) {
            var matcher = PLACEHOLDER.matcher(element);
            var skipped = false;
            var rendered = new StringBuilder();
            while (matcher.find()) {
                var value = slots.get(matcher.group(1));
                if (value == null) {
                    skipped = true;
                    break;
                }
                matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
            }
            if (!skipped) {
                matcher.appendTail(rendered);
                filled.add(rendered.toString());
            } else if (!filled.isEmpty() && isFlag(filled.getLast())) {
                filled.removeLast();
            }
        }
        return filled;
    }

    /** @return whether an argument is a flag whose value is the next element, for example {@code --model} */
    private static boolean isFlag(String argument) {
        return argument.startsWith("--") && argument.length() > 2 && !argument.contains("=");
    }

    private static Path writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            restrict(file, OWNER_ONLY);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("The launch file cannot be written: " + file, e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
            restrict(directory, OWNER_ONLY_DIRECTORY);
        } catch (IOException e) {
            throw new UncheckedIOException("The job-private directory cannot be created: " + directory, e);
        }
    }

    private static void restrict(Path path, Set<PosixFilePermission> permissions) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    /**
     * The effort a launch passes.
     *
     * @param value          the effort passed; {@code null} when none is passed
     * @param narrowedFrom   the role's effective effort when it was narrowed; {@code null} otherwise
     * @since 0.1
     */
    public record NarrowedEffort(String value, String narrowedFrom) {
    }

    private record McpComposition(Path file, String content) {

        private String slot() {
            return content;
        }
    }

    /** A launch that cannot be composed. */
    public static final class CompositionException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        CompositionException(String detail) {
            super(detail);
        }

        CompositionException(String detail, Throwable cause) {
            super(detail, cause);
        }

        /** @return the launch refusal reason of the failure */
        public LaunchRefusal refusal() {
            return LaunchRefusal.PROMPT_COMPOSITION_FAILED;
        }
    }
}
