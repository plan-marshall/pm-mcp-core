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
import java.util.HashSet;
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
 * template element are required exactly, and the text a slot carries is read back by one of three bounds, none of them
 * a search: the only slot of its element is bounded by the literal before it and the literal after it, from both ends of
 * the element; a slot whose value the check already holds ({@code {{model}}}, {@code {{cwd}}}) is matched with that
 * value at the cursor; the last slot of an element carries the rest of it. The decision is recorded in ADR
 * {@code runtime-launch/010}, the bounds in ADR {@code runtime-launch/014}.
 * <p>
 * The residual: in an element of two or more slots, a slot of unknown value followed by a literal is still bounded by
 * the earliest occurrence of that literal. A value that contains the literal is cut short, the element then does not
 * correspond, and the vector is refused — fail-closed, never accepted with a wrong value. The profile validator refuses
 * two slots that touch, so every slot of a validated profile has a literal or an end of the element to bound it.
 * <p>
 * The rules; the correspondence pass decides (b) and (e) together, so the answer reports (a), then (b) and (e), then
 * (c), then (d):
 * <ol>
 * <li>(a) element 0 is the enrolled executable path.</li>
 * <li>(b) every element of the vector corresponds to one element of the template; the elements the template skips (a
 * slot with no value removes its flag with it) are the only gaps, and the literal parts of a template element are
 * reproduced exactly.</li>
 * <li>(c) every typed slot value matches the closed grammar of its slot: {@code {{model}}} is the model the composer
 * resolved, {@code {{effort}}} one of the efforts the profile accepts for that model, {@code {{prompt_file}}} and
 * {@code {{result_schema}}} absolute normalized paths below the job-private directory, {@code {{cwd}}} the working
 * directory the composer fixed, {@code {{session_id}}} a harness session id, {@code {{tools}}} a comma list in which
 * every member is a tool of the role's set, no member is empty and no member occurs twice, in any order (the composer
 * collects the set in insertion order without duplicates, so a vector it rendered never repeats a tool), and
 * {@code {{mcp_config}}} and {@code {{agent}}} a JSON object of the composed relay shape.</li>
 * <li>(d) no element carries a shell metacharacter outside the value of a JSON slot: the value of {@code {{mcp_config}}}
 * and {@code {{agent}}} is exempt and checked as JSON by rule (c), the literals of its element are not. It is the rule
 * the job runtime's {@code ArgumentCheck} applies to every caller-supplied argument of every other job kind, applied here
 * to the vector the composition made of the profile's data (the runtime spawns without a shell, so this is defence in
 * depth, not a restriction the harness sees).</li>
 * <li>(e) the vector carries no element that is neither a template element nor a slot value.</li>
 * </ol>
 * <p>
 * The check runs on the vector as it is filled and prefixed with the executable, against the very template that was
 * filled: the ephemeral flag a role that may resume must not receive is withheld from the template before it is
 * rendered, so the vector and the template correspond element for element and the only gap rule (b) admits is the one a
 * skipped slot makes.
 *
 * @since 0.1
 */
@UtilityClass
class WorkerVectorCheck {

    /** The placeholder of a typed slot in a template element. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    /** The shell metacharacters, refused in every element outside the value of a JSON slot. */
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
        if (!starts(input)) {
            return Optional.of(new Violation(Rule.EXECUTABLE, 0));
        }
        var parsed = new ArrayList<PlacedSlot>();
        var structure = correspondence(input, parsed);
        if (structure.isPresent()) {
            return structure;
        }
        var grammar = grammar(parsed, input);
        if (grammar.isPresent()) {
            return grammar;
        }
        return metacharacter(input, parsed);
    }

    /** @return whether element 0 of the vector is the enrolled executable, and the vector is not empty */
    private static boolean starts(Input input) {
        var vector = input.vector();
        return !vector.isEmpty() && vector.getFirst().equals(input.executablePath().toString());
    }

    /**
     * Rules (b) and (e): every kept template element is matched against the next vector element in order, and nothing may
     * be left over.
     *
     * @param input  the check's input
     * @param parsed receives the slot values the vector carries, with the position of their element and their span in it
     * @return the violation of rule (b) or (e), or empty
     */
    private static Optional<Violation> correspondence(Input input, List<PlacedSlot> parsed) {
        var rendered = input.vector().subList(1, input.vector().size());
        var cursor = 0;
        for (var index : keptIndices(input.template(), input.absentSlots())) {
            if (cursor >= rendered.size()) {
                return Optional.of(new Violation(Rule.CORRESPONDENCE, cursor + 1));
            }
            var matched = match(segments(input.template().get(index)), rendered.get(cursor), input);
            if (matched.isEmpty()) {
                return Optional.of(new Violation(Rule.CORRESPONDENCE, cursor + 1));
            }
            for (var value : matched.get()) {
                parsed.add(new PlacedSlot(value, cursor + 1));
            }
            cursor++;
        }
        return cursor < rendered.size() ? Optional.of(new Violation(Rule.SURPLUS, cursor + 1)) : Optional.empty();
    }

    /** Rule (c): each slot value the vector carries matches the closed grammar of the slot the template names. */
    private static Optional<Violation> grammar(List<PlacedSlot> parsed, Input input) {
        for (var placed : parsed) {
            if (!conforms(placed.value(), input)) {
                return Optional.of(new Violation(Rule.SLOT_GRAMMAR, placed.position()));
            }
        }
        return Optional.empty();
    }

    /** Rule (d): no element carries a shell metacharacter outside the value of a JSON slot. */
    private static Optional<Violation> metacharacter(Input input, List<PlacedSlot> parsed) {
        var vector = input.vector();
        // element 0 is the enrolled executable, not a caller-supplied argument: a path with a metacharacter must not
        // refuse every launch of that harness (the specification's standing constraint), and the launcher spawns it by
        // absolute path without a shell
        for (var position = 1; position < vector.size(); position++) {
            if (containsMetacharacter(vector.get(position), jsonSpans(parsed, position))) {
                return Optional.of(new Violation(Rule.METACHARACTER, position));
            }
        }
        return Optional.empty();
    }

    /**
     * @param parsed   the slot values the vector carries
     * @param position the position of an element
     * @return the spans of the element that hold the value of a JSON slot, which rule (d) exempts and rule (c) checks as
     *         JSON instead
     */
    private static List<Span> jsonSpans(List<PlacedSlot> parsed, int position) {
        var spans = new ArrayList<Span>();
        for (var placed : parsed) {
            var slot = placed.value().slot();
            if (placed.position() == position && (SLOT_MCP_CONFIG.equals(slot) || SLOT_AGENT.equals(slot))) {
                spans.add(placed.value().span());
            }
        }
        return spans;
    }

    /**
     * The template elements the composer keeps, in order: an element that names a slot with no value is skipped, and the
     * flag element before it is skipped with it. Only presence is derived here; the value of a slot is read back out of
     * the vector, never rendered.
     * <p>
     * This is a copy of the presence rule of {@code LaunchComposer.fill}, kept beside the parse it serves rather than
     * shared with the code it checks: the tests that place an absent slot first, in the middle, last and after a flag
     * bind the two, so a change of one that the other does not follow refuses every launch of that shape.
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
     * the previous fragment ended, and the text a slot carries is read by the bound its place in the element allows.
     *
     * @param segments the template element
     * @param rendered the vector element
     * @param input    the check's input, which holds the known values
     * @return the slot values the element carries, or empty when the element does not correspond to the template
     */
    private static Optional<List<SlotValue>> match(List<Segment> segments, String rendered, Input input) {
        var parsed = new ArrayList<SlotValue>();
        var onlySlot = segments.stream().filter(Segment::isSlot).count() == 1;
        var cursor = 0;
        for (var index = 0; index < segments.size(); index++) {
            var segment = segments.get(index);
            var name = segment.slot();
            if (name != null) {
                var span = onlySlot ? bounded(segments, index, rendered, cursor)
                        : carried(segments, index, name, rendered, cursor, input);
                if (span.isEmpty()) {
                    return Optional.empty();
                }
                parsed.add(new SlotValue(name, rendered.substring(span.get().start(), span.get().end()), span.get()));
                cursor = span.get().end();
            } else if (!rendered.startsWith(segment.text(), cursor)) {
                return Optional.empty();
            } else {
                cursor += segment.text().length();
            }
        }
        return cursor == rendered.length() ? Optional.of(parsed) : Optional.empty();
    }

    /**
     * The only slot of its element: the literal before it is matched up to the cursor, the literal after it is the end of
     * the element, so the value is what lies between, whatever it contains.
     *
     * @param segments the template element of the slot
     * @param index    the position of the slot marker in it
     * @param rendered the vector element
     * @param cursor   the position the literal before the slot ended at
     * @return the span of the value, or empty when the element does not end with the literal after the slot
     */
    private static Optional<Span> bounded(List<Segment> segments, int index, String rendered, int cursor) {
        var suffix = index == segments.size() - 1 ? "" : segments.get(index + 1).text();
        var end = rendered.length() - suffix.length();
        return end >= cursor && rendered.endsWith(suffix) ? Optional.of(new Span(cursor, end)) : Optional.empty();
    }

    /**
     * A slot of an element of two or more slots: a slot directly followed by another has no bound and is refused; a known
     * value is matched at the cursor; the last slot carries the rest; any other slot ends at the earliest occurrence of
     * the literal after it — the residual, which refuses a value containing that literal rather than accept a wrong one.
     *
     * @param segments the template element of the slot
     * @param index    the position of the slot marker in it
     * @param name     the name of the slot
     * @param rendered the vector element
     * @param cursor   the position the previous fragment ended at
     * @param input    the check's input, which holds the known values
     * @return the span of the value, or empty when the element does not carry it
     */
    private static Optional<Span> carried(List<Segment> segments, int index, String name, String rendered, int cursor,
            Input input) {
        var last = index == segments.size() - 1;
        if (!last && segments.get(index + 1).isSlot()) {
            return Optional.empty();
        }
        var known = knownValue(name, input);
        if (known.isPresent()) {
            var end = cursor + known.get().length();
            return rendered.startsWith(known.get(), cursor) ? Optional.of(new Span(cursor, end)) : Optional.empty();
        }
        if (last) {
            return Optional.of(new Span(cursor, rendered.length()));
        }
        var boundary = rendered.indexOf(segments.get(index + 1).text(), cursor);
        return boundary < 0 ? Optional.empty() : Optional.of(new Span(cursor, boundary));
    }

    /** @return the value the check already holds for a slot, {@code {{model}}} and {@code {{cwd}}}, or empty */
    private static Optional<String> knownValue(String slot, Input input) {
        return switch (slot) {
            case SLOT_MODEL -> Optional.of(input.model());
            case SLOT_CWD -> Optional.of(input.workingDirectory().toString());
            default -> Optional.empty();
        };
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
     * @return whether every member of the comma list is one of the role's tools, none is empty and none occurs twice; the
     *         order is not checked
     */
    private static boolean toolSet(String value, List<String> tools) {
        var named = new HashSet<String>();
        for (var tool : value.split(SEPARATOR, -1)) {
            if (tool.isEmpty() || !tools.contains(tool) || !named.add(tool)) {
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

    /**
     * @param element an element of the vector
     * @param exempt  the spans of the element that hold the value of a JSON slot
     * @return whether a character outside the exempt spans is a shell metacharacter
     */
    private static boolean containsMetacharacter(String element, List<Span> exempt) {
        for (var index = 0; index < element.length(); index++) {
            if (SHELL_METACHARACTERS.indexOf(element.charAt(index)) >= 0 && !within(exempt, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean within(List<Span> spans, int index) {
        for (var span : spans) {
            if (index >= span.start() && index < span.end()) {
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
            Objects.requireNonNull(model);
            Objects.requireNonNull(acceptedEfforts, "acceptedEfforts");
            acceptedEfforts = List.copyOf(acceptedEfforts);
            Objects.requireNonNull(jobPrivateDirectory, "jobPrivateDirectory");
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(tools);
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

        /** (d) an element carries a shell metacharacter outside the value of a JSON slot. */
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

    /**
     * The characters of an element a slot value occupies.
     *
     * @param start the index of the first character of the value in the element
     * @param end   the index after the last character of the value
     */
    private record Span(int start, int end) {
    }

    /** One slot value read back out of an element, with the span it occupies there. */
    private record SlotValue(String slot, String value, Span span) {
    }

    /** One slot value read back out of the vector, at the position of the element that carries it. */
    private record PlacedSlot(SlotValue value, int position) {
    }
}
