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

import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;

/**
 * The check of the composed vector of a worker launch (PM-EXEC-1, PM-EXEC-5; PM-CLIENT-2, Launch Contract): the last
 * step of the composition, run on the vector {@link LaunchComposer} rendered and before the {@link WorkerLaunch} is
 * built. It answers the first violated rule together with the position of the element, never the value, so a refusal
 * can be recorded and shown without the vector in it.
 * <p>
 * The check <em>parses the rendered vector back</em> against the template it was rendered from and against the closed
 * grammar of each typed slot. It does not render again: re-running the composer's own replacement would repeat the code
 * under test and prove nothing, so the vector is matched element against template element, the literal parts of a
 * template element are required exactly, and the value between two literals is the slot value that element carries.
 * The decision is recorded in ADR {@code runtime-launch/010}.
 * <p>
 * The rules, in the order the answer reports them:
 * <ol>
 * <li>(a) element 0 is the enrolled executable path.</li>
 * <li>(b) every element of the vector corresponds to one element of the template; the elements the template skips (a
 * slot with no value removes its flag with it) are the only gaps, and the literal parts of a template element are
 * reproduced exactly.</li>
 * <li>(c) every typed slot value matches the closed grammar of its slot: {@code {{model}}} is the model the composer
 * resolved, {@code {{effort}}} one of the efforts the profile accepts for that model, {@code {{prompt_file}}} and
 * {@code {{result_schema}}} absolute normalized paths below the job-private directory, {@code {{cwd}}} the working
 * directory the composer fixed, {@code {{session_id}}} a harness session id, {@code {{tools}}} a comma list of the
 * role's tool set, and {@code {{mcp_config}}} and {@code {{agent}}} a JSON object of the composed relay shape.</li>
 * <li>(d) no element outside the two JSON slots contains a shell metacharacter — the rule the job runtime's
 * {@code ArgumentCheck} applies to every caller-supplied argument of every other job kind, applied here to the vector
 * the composition made of the profile's data (the runtime spawns without a shell, so this is defence in depth, not a
 * restriction the harness sees).</li>
 * <li>(e) the vector carries no element that is neither a template element nor a slot value.</li>
 * </ol>
 * <p>
 * The check runs on the vector as it is filled and prefixed with the executable, before the ephemeral flag is withheld;
 * a template element that a skipped slot removes is therefore the only gap rule (b) has to admit, and the vector that is
 * spawned differs from the checked one only in template literals the composer never alters.
 *
 * @since 0.1
 */
@UtilityClass
class WorkerVectorCheck {

    /** The placeholder of a typed slot in a template element. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    /** The shell metacharacters, refused in every element outside the two JSON slots. */
    static final String SHELL_METACHARACTERS = "|;&$`><";

    /** The grammar of a harness session id. */
    static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    /** The server name of the job relay, the only member of the composed agent object. */
    static final String RELAY = "pm-job";

    /** The member of the composed MCP configuration that holds the relay. */
    static final String MCP_SERVERS = "mcpServers";

    static final String SLOT_MODEL = "model";
    static final String SLOT_EFFORT = "effort";
    static final String SLOT_PROMPT_FILE = "prompt_file";
    static final String SLOT_RESULT_SCHEMA = "result_schema";
    static final String SLOT_TOOLS = "tools";
    static final String SLOT_CWD = "cwd";
    static final String SLOT_MCP_CONFIG = "mcp_config";
    static final String SLOT_AGENT = "agent";
    static final String SLOT_SESSION_ID = "session_id";

    private static final String SEPARATOR = ",";

    /**
     * @param input the rendered vector and the values the composition resolved
     * @return the first violation, or empty when the vector conforms
     */
    static Optional<Violation> check(Input input) {
        Objects.requireNonNull(input, "input");
        var vector = input.vector();
        if (vector.isEmpty() || !vector.getFirst().equals(input.executablePath().toString())) {
            return Optional.of(new Violation(Rule.EXECUTABLE, 0));
        }
        var rendered = vector.subList(1, vector.size());
        var parsed = new ArrayList<SlotValue>();
        var cursor = 0;
        for (var index : keptIndices(input.template(), input.absentSlots())) {
            if (cursor >= rendered.size()) {
                return Optional.of(new Violation(Rule.CORRESPONDENCE, cursor + 1));
            }
            var matched = match(segments(input.template().get(index)), rendered.get(cursor));
            if (matched.isEmpty()) {
                return Optional.of(new Violation(Rule.CORRESPONDENCE, cursor + 1));
            }
            for (var value : matched.get()) {
                parsed.add(new SlotValue(value.slot(), value.value(), cursor + 1));
            }
            cursor++;
        }
        if (cursor < rendered.size()) {
            return Optional.of(new Violation(Rule.SURPLUS, cursor + 1));
        }
        for (var value : parsed) {
            if (!conforms(value, input)) {
                return Optional.of(new Violation(Rule.SLOT_GRAMMAR, value.position()));
            }
        }
        var jsonSlots = jsonSlots(parsed);
        for (var position = 0; position < vector.size(); position++) {
            if (!jsonSlots.contains(position) && containsMetacharacter(vector.get(position))) {
                return Optional.of(new Violation(Rule.METACHARACTER, position));
            }
        }
        return Optional.empty();
    }

    /**
     * The positions of the elements that hold the value of a JSON slot, which rule (d) exempts and rule (c) checks as
     * JSON instead.
     */
    private static Set<Integer> jsonSlots(List<SlotValue> parsed) {
        var positions = new LinkedHashSet<Integer>();
        for (var value : parsed) {
            if (SLOT_MCP_CONFIG.equals(value.slot()) || SLOT_AGENT.equals(value.slot())) {
                positions.add(value.position());
            }
        }
        return positions;
    }

    /**
     * The template elements the composer keeps, in order: an element that names a slot with no value is skipped, and the
     * flag element before it is skipped with it. Only presence is derived here; the value of a slot is read back out of
     * the vector, never rendered.
     */
    private static List<Integer> keptIndices(List<String> template, Set<String> absentSlots) {
        var kept = new ArrayList<Integer>();
        for (var index = 0; index < template.size(); index++) {
            var element = template.get(index);
            if (namesAbsentSlot(element, absentSlots)) {
                if (!kept.isEmpty() && isFlag(template.get(kept.getLast()))) {
                    kept.removeLast();
                }
            } else {
                kept.add(index);
            }
        }
        return kept;
    }

    private static boolean namesAbsentSlot(String element, Set<String> absentSlots) {
        var matcher = PLACEHOLDER.matcher(element);
        while (matcher.find()) {
            if (absentSlots.contains(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    /** @return whether an element is a flag whose value is the next element, for example {@code --model} */
    private static boolean isFlag(String element) {
        return element.startsWith("--") && element.length() > 2 && !element.contains("=");
    }

    /**
     * @param element a template element
     * @return its literal fragments and its slot markers, in order
     */
    private static List<Segment> segments(String element) {
        var segments = new ArrayList<Segment>();
        var matcher = PLACEHOLDER.matcher(element);
        var cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                segments.add(Segment.literal(element.substring(cursor, matcher.start())));
            }
            segments.add(Segment.slot(matcher.group(1)));
            cursor = matcher.end();
        }
        if (cursor < element.length()) {
            segments.add(Segment.literal(element.substring(cursor)));
        }
        return segments;
    }

    /**
     * Parses one rendered element back against its template element: every literal fragment is required at the position
     * the previous fragment ended, and the text between two fragments is the value of a slot.
     *
     * @param segments the template element
     * @param rendered the vector element
     * @return the slot values the element carries, or empty when the element does not correspond to the template
     */
    private static Optional<List<SlotValue>> match(List<Segment> segments, String rendered) {
        var parsed = new ArrayList<SlotValue>();
        var cursor = 0;
        for (var index = 0; index < segments.size(); index++) {
            var segment = segments.get(index);
            if (!segment.isSlot()) {
                if (!rendered.startsWith(segment.text(), cursor)) {
                    return Optional.empty();
                }
                cursor += segment.text().length();
                continue;
            }
            if (index == segments.size() - 1) {
                parsed.add(new SlotValue(segment.slot(), rendered.substring(cursor), SlotValue.UNPLACED));
                cursor = rendered.length();
                continue;
            }
            var next = segments.get(index + 1);
            if (next.isSlot()) {
                return Optional.empty();
            }
            var boundary = rendered.indexOf(next.text(), cursor);
            if (boundary < 0) {
                return Optional.empty();
            }
            parsed.add(new SlotValue(segment.slot(), rendered.substring(cursor, boundary), SlotValue.UNPLACED));
            cursor = boundary;
        }
        return cursor == rendered.length() ? Optional.of(parsed) : Optional.empty();
    }

    private static boolean conforms(SlotValue value, Input input) {
        return switch (value.slot()) {
            case SLOT_MODEL -> value.value().equals(input.model());
            case SLOT_EFFORT -> input.acceptedEfforts().contains(value.value());
            case SLOT_PROMPT_FILE, SLOT_RESULT_SCHEMA -> below(input.jobPrivateDirectory(), value.value());
            case SLOT_CWD -> value.value().equals(input.workingDirectory().toString());
            case SLOT_SESSION_ID -> SESSION_ID.matcher(value.value()).matches();
            case SLOT_TOOLS -> toolSet(value.value(), input.tools());
            case SLOT_MCP_CONFIG -> relayObject(value.value(), MCP_SERVERS);
            case SLOT_AGENT -> relayObject(value.value(), RELAY);
            default -> false;
        };
    }

    /**
     * @param directory the job-private directory
     * @param value     a rendered slot value
     * @return whether the value is an absolute normalized path below the directory
     */
    private static boolean below(Path directory, String value) {
        return pathOf(value).filter(path -> path.isAbsolute() && path.normalize().equals(path))
                .filter(path -> path.startsWith(directory)).isPresent();
    }

    private static Optional<Path> pathOf(String value) {
        try {
            return Optional.of(Path.of(value));
        } catch (InvalidPathException _) {
            return Optional.empty();
        }
    }

    /**
     * @param value a rendered {@code {{tools}}} slot
     * @param tools the tool set the composer resolved for the role
     * @return whether every element of the comma list is one of the role's tools
     */
    private static boolean toolSet(String value, List<String> tools) {
        var named = value.split(SEPARATOR, -1);
        if (named.length == 0) {
            return false;
        }
        for (var tool : named) {
            if (tool.isEmpty() || !tools.contains(tool)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param value the rendered JSON slot
     * @param outer the only member of the composed object
     * @return whether the value is a JSON object of the composed relay shape
     */
    private static boolean relayObject(String value, String outer) {
        var parsed = parse(value);
        if (parsed.isEmpty() || !parsed.get().isObject() || !parsed.get().members().keySet().equals(Set.of(outer))) {
            return false;
        }
        var relay = parsed.get().member(outer).orElseThrow();
        if (RELAY.equals(outer)) {
            return relay.isObject();
        }
        return relay.isObject() && relay.members().keySet().equals(Set.of(RELAY))
                && relay.member(RELAY).orElseThrow().isObject();
    }

    private static Optional<JsonNode> parse(String value) {
        try {
            return Optional.of(JsonDocument.parse(value.getBytes(StandardCharsets.UTF_8), "composed slot"));
        } catch (ProfileRefusal _) {
            return Optional.empty();
        }
    }

    private static boolean containsMetacharacter(String element) {
        for (var index = 0; index < element.length(); index++) {
            if (SHELL_METACHARACTERS.indexOf(element.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rendered vector of one composition and the values the composition resolved, which the check reads the vector
     * back against. The template is the one that was rendered — the resume template for a resumed generation, the class
     * template otherwise.
     *
     * @param template            the argument template the composer rendered
     * @param vector              the rendered vector, element 0 being the enrolled executable
     * @param executablePath      the absolute path of the enrolled harness executable
     * @param model               the model the composer resolved and rendered into {@code {{model}}}
     * @param acceptedEfforts     the effort values the profile accepts for that model
     * @param jobPrivateDirectory the job-private directory of the launch
     * @param workingDirectory    the working directory the composer fixed
     * @param tools               the tool set the composer resolved for the role, resource tools included
     * @param absentSlots         the slots the composition left empty, which removes their element and its flag
     * @since 0.1
     */
    @Builder
    record Input(List<String> template, List<String> vector, Path executablePath, String model,
    List<String> acceptedEfforts, Path jobPrivateDirectory, Path workingDirectory, List<String> tools,
    Set<String> absentSlots) {

        /** Copies the collections. */
        Input {
            Objects.requireNonNull(template, "template");
            template = List.copyOf(template);
            Objects.requireNonNull(vector, "vector");
            vector = List.copyOf(vector);
            Objects.requireNonNull(executablePath, "executablePath");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(acceptedEfforts, "acceptedEfforts");
            acceptedEfforts = List.copyOf(acceptedEfforts);
            Objects.requireNonNull(jobPrivateDirectory, "jobPrivateDirectory");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(tools, "tools");
            tools = List.copyOf(tools);
            Objects.requireNonNull(absentSlots, "absentSlots");
            absentSlots = Collections.unmodifiableSet(new LinkedHashSet<>(absentSlots));
        }
    }

    /** One violated rule of the check. */
    enum Rule {

        /** (a) element 0 is not the enrolled executable path. */
        EXECUTABLE('a', "the vector does not start with the enrolled executable"),

        /** (b) an element does not correspond to its template element. */
        CORRESPONDENCE('b', "the element does not correspond to the template it was rendered from"),

        /** (c) a slot value violates the closed grammar of its slot. */
        SLOT_GRAMMAR('c', "the slot value violates the closed grammar of its slot"),

        /** (d) an element outside the two JSON slots contains a shell metacharacter. */
        METACHARACTER('d', "the element contains a shell metacharacter"),

        /** (e) the vector carries an element the template does not explain. */
        SURPLUS('e', "the vector carries an element that is neither a template element nor a slot value");

        private final char letter;
        private final String detail;

        Rule(char letter, String detail) {
            this.letter = letter;
            this.detail = detail;
        }

        /** @return the letter the specification's rule list gives the rule */
        char letter() {
            return letter;
        }

        /** @return what the rule requires, without the value that broke it */
        String detail() {
            return detail;
        }
    }

    /**
     * One violated rule and the position of the element that broke it, never the value.
     *
     * @param rule     the violated rule
     * @param position the index of the element in the composed vector, element 0 being the executable
     * @since 0.1
     */
    record Violation(Rule rule, int position) {

        /**
         * @return the detail a composition refusal carries: the rule and the position, never the element
         */
        String detail() {
            return "the composed worker vector violates rule (%s) at element %d: %s".formatted(rule.letter(),
                    position, rule.detail());
        }
    }

    /** One literal fragment or one slot marker of a template element. */
    private record Segment(@Nullable String slot, String text) {

        static Segment literal(String text) {
            return new Segment(null, text);
        }

        static Segment slot(String name) {
            return new Segment(name, "");
        }

        boolean isSlot() {
            return slot != null;
        }
    }

    /** One slot value read back out of the vector, at the position of the element that carries it. */
    private record SlotValue(String slot, String value, int position) {

        /** The position of a value read out of the vector before the element that carries it is known. */
        static final int UNPLACED = -1;
    }
}
