/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.scanner.ScannerImpl;
import org.yaml.snakeyaml.tokens.AliasToken;
import org.yaml.snakeyaml.tokens.AnchorToken;
import org.yaml.snakeyaml.tokens.DocumentStartToken;
import org.yaml.snakeyaml.tokens.TagToken;
import org.yaml.snakeyaml.tokens.Token;

import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Claim;
import de.planmarshall.core.store.LessonFrontMatter.LastFilingFailure;
import de.planmarshall.core.store.LessonFrontMatter.MergedFrom;
import de.planmarshall.core.store.LessonFrontMatter.Promotion;
import de.planmarshall.core.store.LessonFrontMatter.Provenance;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * Reads and writes one lesson file {@code <lesson_id>.md} of the project lesson store (Store Schemas § Lesson Record;
 * PM-IMPL-2).
 * <p>
 * A lesson file is the line {@code ---}, the front matter, the line {@code ---} and the Markdown body. The front
 * matter is YAML restricted to the JSON data model (YAML 1.2 core schema; its {@code .inf} and {@code .nan} spellings
 * are not part of that data model and stay strings): anchors, aliases, tags and multiple documents are refused, and
 * every member is validated against the {@code LessonFrontMatter} schema on every read and write. The writer emits the
 * front matter as one JSON object in the order of the schema — a JSON object is a YAML 1.2 flow mapping — and the
 * reader accepts any YAML restricted to that data model, so a hand-edited block-style file is readable as well (ADR
 * core-store/061).
 * <p>
 * A file that does not decode is not an exception for the reader: {@link #decode(byte[])} returns a {@link Reading},
 * either {@link Readable} or {@link Unreadable} with its reason and detail, because a file without parseable front
 * matter lists as {@code present_unreadable} and is never {@code absent}. A write with missing or invalid front matter
 * is rejected: a {@link Lesson} cannot be constructed without a front matter, and every rule of the record is refused
 * by its constructor before the codec sees it.
 * <p>
 * The member lists are published as package-private constants in the order of the schema, so the parity test of the
 * schema block of the specification pins them (the pattern of {@link JobRecordCodec#MEMBERS}).
 *
 * @since 0.1
 */
@UtilityClass
public class LessonCodec {

    /** The format version of a lesson file that is read and written. */
    public static final int FORMAT_VERSION = LessonFrontMatter.FORMAT_VERSION;

    private static final String FIELD_FORMAT_VERSION = "format_version";
    private static final String FIELD_LESSON_ID = "lesson_id";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_COMPONENT = "component";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_BUNDLE = "bundle";
    private static final String FIELD_RULE = "rule";
    private static final String FIELD_REMEDY = "remedy";
    private static final String FIELD_RECURRENCE_COUNT = "recurrence_count";
    private static final String FIELD_LAST_SEEN = "last_seen";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_PROVENANCE = "provenance";
    private static final String FIELD_DERIVED_FROM_UNTRUSTED = "derived_from_untrusted";
    private static final String FIELD_AUDIENCE = "audience";
    private static final String FIELD_LAST_FILING_FAILURE = "last_filing_failure";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_TOOL_VERSION = "tool_version";
    private static final String FIELD_PROMOTIONS = "promotions";
    private static final String FIELD_REVISION = "revision";
    private static final String FIELD_SOURCES = "sources";
    private static final String FIELD_REVISIONS = "revisions";
    private static final String FIELD_MERGED_FROM = "merged_from";
    private static final String FIELD_CLAIM = "claim";
    private static final String FIELD_RETIRED = "retired";

    private static final String FIELD_KIND = "kind";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_STATEMENT = "statement";
    private static final String FIELD_CHECK = "check";
    private static final String FIELD_AT = "at";
    private static final String FIELD_N = "n";
    private static final String FIELD_BODY_SHA256 = "body_sha256";
    private static final String FIELD_BY = "by";
    private static final String FIELD_PLAN_ID = "plan_id";
    private static final String FIELD_CLAIMED_AT = "claimed_at";
    private static final String FIELD_VERDICT = "verdict";
    private static final String FIELD_INTO = "into";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_COVERING_CLAUSE = "covering_clause";
    private static final String FIELD_COVERING_INPUT = "covering_input";
    private static final String FIELD_LESSON_STATE = "lesson_state";

    /** The members of the front matter, in the order of the schema. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, FIELD_LESSON_ID, FIELD_TITLE, FIELD_COMPONENT,
            FIELD_CATEGORY, FIELD_BUNDLE, FIELD_RULE, FIELD_REMEDY, FIELD_RECURRENCE_COUNT, FIELD_LAST_SEEN,
            FIELD_STATUS, FIELD_PROVENANCE, FIELD_DERIVED_FROM_UNTRUSTED, FIELD_AUDIENCE, FIELD_LAST_FILING_FAILURE,
            FIELD_CREATED_AT, FIELD_TOOL_VERSION, FIELD_PROMOTIONS, FIELD_REVISION, FIELD_SOURCES, FIELD_REVISIONS,
            FIELD_MERGED_FROM, FIELD_CLAIM, FIELD_RETIRED);

    /** The required members of the front matter, in the order of the schema. */
    static final List<String> REQUIRED_MEMBERS = List.of(FIELD_FORMAT_VERSION, FIELD_LESSON_ID, FIELD_TITLE,
            FIELD_COMPONENT, FIELD_CATEGORY, FIELD_REMEDY, FIELD_STATUS, FIELD_CREATED_AT, FIELD_TOOL_VERSION,
            FIELD_PROVENANCE, FIELD_DERIVED_FROM_UNTRUSTED, FIELD_AUDIENCE, FIELD_REVISION, FIELD_SOURCES,
            FIELD_REVISIONS, FIELD_CLAIM, FIELD_RETIRED);

    /** The members of a source, in the order of the schema. */
    static final List<String> SOURCE_MEMBERS = List.of(FIELD_KIND, FIELD_REF, FIELD_DIGEST);

    /** The required members of a source, in the order of the schema. */
    static final List<String> SOURCE_REQUIRED_MEMBERS = List.of(FIELD_KIND, FIELD_REF);

    /** The members of a remedy, in the order of the schema. */
    static final List<String> REMEDY_MEMBERS = List.of(FIELD_KIND, FIELD_STATEMENT, FIELD_CHECK);

    /** The required members of a remedy, in the order of the schema. */
    static final List<String> REMEDY_REQUIRED_MEMBERS = REMEDY_MEMBERS;

    /** The members of a promotion, in the order of the schema. */
    static final List<String> PROMOTION_MEMBERS = List.of(FIELD_KIND, FIELD_REF, FIELD_AT);

    /** The required members of a promotion, in the order of the schema. */
    static final List<String> PROMOTION_REQUIRED_MEMBERS = PROMOTION_MEMBERS;

    /** The members of a revision, in the order of the schema. */
    static final List<String> REVISION_MEMBERS = List.of(FIELD_N, FIELD_AT, FIELD_SOURCES, FIELD_BODY_SHA256);

    /** The required members of a revision, in the order of the schema. */
    static final List<String> REVISION_REQUIRED_MEMBERS = REVISION_MEMBERS;

    /** The members of a merged-from entry, in the order of the schema. */
    static final List<String> MERGED_FROM_MEMBERS = List.of(FIELD_LESSON_ID, FIELD_AT, FIELD_BY);

    /** The required members of a merged-from entry, in the order of the schema. */
    static final List<String> MERGED_FROM_REQUIRED_MEMBERS = MERGED_FROM_MEMBERS;

    /** The members of a claim, in the order of the schema. */
    static final List<String> CLAIM_MEMBERS = List.of(FIELD_PLAN_ID, FIELD_CLAIMED_AT);

    /** The required members of a claim, in the order of the schema. */
    static final List<String> CLAIM_REQUIRED_MEMBERS = CLAIM_MEMBERS;

    /** The members of a retirement, in the order of the schema. */
    static final List<String> RETIRED_MEMBERS = List.of(FIELD_AT, FIELD_BY, FIELD_VERDICT, FIELD_INTO, FIELD_REASON,
            FIELD_COVERING_CLAUSE, FIELD_COVERING_INPUT, FIELD_LESSON_STATE);

    /** The required members of a retirement, in the order of the schema; {@code into} is optional. */
    static final List<String> RETIRED_REQUIRED_MEMBERS = List.of(FIELD_AT, FIELD_BY, FIELD_VERDICT, FIELD_REASON,
            FIELD_COVERING_CLAUSE, FIELD_COVERING_INPUT, FIELD_LESSON_STATE);

    private static final String FENCE = "---";
    private static final byte[] OPENING_FENCE = (FENCE + "\n").getBytes(UTF_8);
    private static final byte[] CLOSING_FENCE = ("\n" + FENCE + "\n").getBytes(UTF_8);
    private static final int CODE_POINT_LIMIT = 1_000_000;

    /**
     * The result of reading one lesson file: the lesson, or the reason the file is unreadable.
     *
     * @since 0.1
     */
    public sealed interface Reading permits Readable, Unreadable {
    }

    /**
     * A lesson file that decoded.
     *
     * @param lesson the lesson
     * @since 0.1
     */
    public record Readable(Lesson lesson) implements Reading {

        /**
         * @param lesson the lesson
         */
        public Readable {
            Objects.requireNonNull(lesson, "lesson");
        }
    }

    /**
     * A lesson file that did not decode. It is the state {@code present_unreadable} of the resolver, never
     * {@code absent}.
     *
     * @param reason why the file is unreadable
     * @param detail what exactly could not be read
     * @since 0.1
     */
    public record Unreadable(UnreadableReason reason, String detail) implements Reading {

        /**
         * @param reason why the file is unreadable
         * @param detail what exactly could not be read
         */
        public Unreadable {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * Why a lesson file is unreadable.
     *
     * @since 0.1
     */
    public enum UnreadableReason {
        /** The file does not start with the line {@code ---}. */
        NO_OPENING_FENCE("no_opening_fence"),
        /** The file has no second line {@code ---}. */
        NO_CLOSING_FENCE("no_closing_fence"),
        /** The front matter is not an object. */
        NOT_AN_OBJECT("not_an_object"),
        /** The strict reader refused the front matter. */
        STRICT_READER("strict_reader"),
        /** A rule of the record or of its schema refused the front matter. */
        RULE_VIOLATION("rule_violation");

        private final String wireName;

        UnreadableReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a report */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Writes one lesson file.
     *
     * @param lesson the lesson
     * @return the file, UTF-8 encoded
     */
    public static byte[] encode(Lesson lesson) {
        Objects.requireNonNull(lesson, "lesson");
        var out = new ByteArrayOutputStream();
        out.writeBytes(OPENING_FENCE);
        out.writeBytes(writeFrontMatter(lesson.frontMatter()));
        out.writeBytes(CLOSING_FENCE);
        out.writeBytes(lesson.body().getBytes(UTF_8));
        return out.toByteArray();
    }

    /**
     * Reads one lesson file. A file that does not decode is a value, never an exception.
     *
     * @param file the file, UTF-8 encoded
     * @return the reading
     */
    public static Reading decode(byte[] file) {
        Objects.requireNonNull(file, "file");
        String text;
        try {
            text = UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file))
                    .toString();
        } catch (CharacterCodingException e) {
            return new Unreadable(UnreadableReason.STRICT_READER,
                    "the file is not UTF-8: %s".formatted(e.getMessage()));
        }
        var opening = lineEnd(text, 0);
        if (!isFence(text, 0, opening)) {
            return new Unreadable(UnreadableReason.NO_OPENING_FENCE, "the file does not start with the line '---'");
        }
        var frontMatterStart = opening < text.length() ? opening + 1 : text.length();
        var closing = fenceLineAfter(text, frontMatterStart);
        if (closing < 0) {
            return new Unreadable(UnreadableReason.NO_CLOSING_FENCE, "no second line '---' closes the front matter");
        }
        var frontMatterEnd = closing;
        var closingEnd = lineEnd(text, closing);
        var body = text.substring(closingEnd < text.length() ? closingEnd + 1 : text.length());
        Object loaded;
        try {
            loaded = load(text.substring(frontMatterStart, frontMatterEnd));
        } catch (StrictJson.Malformed e) {
            return new Unreadable(UnreadableReason.STRICT_READER, e.getMessage());
        } catch (YAMLException e) {
            return new Unreadable(UnreadableReason.STRICT_READER, String.valueOf(e.getMessage()));
        }
        if (!(loaded instanceof Map<?, ?>)) {
            return new Unreadable(UnreadableReason.NOT_AN_OBJECT, "the front matter is not an object");
        }
        try {
            return new Readable(new Lesson(readFrontMatter(mapping(loaded, "the front matter")), body));
        } catch (StrictJson.Malformed e) {
            return new Unreadable(UnreadableReason.STRICT_READER, e.getMessage());
        } catch (IllegalArgumentException e) {
            return new Unreadable(UnreadableReason.RULE_VIOLATION, String.valueOf(e.getMessage()));
        }
    }

    private static Object load(String frontMatter) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false);
        options.setCodePointLimit(CODE_POINT_LIMIT);
        refuseYamlBeyondTheJsonModel(frontMatter, options);
        var dumper = new DumperOptions();
        var yaml = new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options,
                new JsonModelResolver());
        return yaml.load(frontMatter);
    }

    private static void refuseYamlBeyondTheJsonModel(String frontMatter, LoaderOptions options) {
        var scanner = new ScannerImpl(new StreamReader(frontMatter), options);
        var documents = 0;
        while (!scanner.checkToken(Token.ID.StreamEnd)) {
            var token = scanner.getToken();
            if (token instanceof AnchorToken) {
                throw StrictJson.unreadable("an anchor is not part of the JSON data model");
            }
            if (token instanceof AliasToken) {
                throw StrictJson.unreadable("an alias is not part of the JSON data model");
            }
            if (token instanceof TagToken) {
                throw StrictJson.unreadable("a tag is not part of the JSON data model");
            }
            if (token instanceof DocumentStartToken) {
                documents++;
                if (documents > 1) {
                    throw StrictJson.unreadable("the front matter is more than one document");
                }
            }
        }
    }

    private static LessonFrontMatter readFrontMatter(Map<String, Object> object) {
        for (var member : object.keySet()) {
            if (!MEMBERS.contains(member)) {
                throw StrictJson.unreadable("undeclared member '%s'".formatted(member));
            }
        }
        for (var required : REQUIRED_MEMBERS) {
            if (!object.containsKey(required)) {
                throw StrictJson.unreadable("member '%s' is missing".formatted(required));
            }
        }
        var formatVersion = integer(object.get(FIELD_FORMAT_VERSION), FIELD_FORMAT_VERSION);
        if (formatVersion != FORMAT_VERSION) {
            throw StrictJson.unreadable(
                    "the front matter is format version %d, not %d".formatted(formatVersion, FORMAT_VERSION));
        }
        return LessonFrontMatter.builder()
                .formatVersion(formatVersion)
                .lessonId(new LessonId(text(object.get(FIELD_LESSON_ID), FIELD_LESSON_ID)))
                .title(text(object.get(FIELD_TITLE), FIELD_TITLE))
                .component(text(object.get(FIELD_COMPONENT), FIELD_COMPONENT))
                .category(closed(object.get(FIELD_CATEGORY), FIELD_CATEGORY, Category::fromWireName))
                .bundle(nullableText(object.get(FIELD_BUNDLE), FIELD_BUNDLE).orElse(null))
                .rule(nullableText(object.get(FIELD_RULE), FIELD_RULE).orElse(null))
                .remedy(remedy(mapping(object.get(FIELD_REMEDY), FIELD_REMEDY)))
                .recurrenceCount(nullableInteger(object.get(FIELD_RECURRENCE_COUNT), FIELD_RECURRENCE_COUNT)
                        .orElse(null))
                .lastSeen(nullableDate(object.get(FIELD_LAST_SEEN), FIELD_LAST_SEEN).orElse(null))
                .status(closed(object.get(FIELD_STATUS), FIELD_STATUS, Status::fromWireName))
                .provenance(closed(object.get(FIELD_PROVENANCE), FIELD_PROVENANCE, Provenance::fromWireName))
                .derivedFromUntrusted(bool(object.get(FIELD_DERIVED_FROM_UNTRUSTED), FIELD_DERIVED_FROM_UNTRUSTED))
                .audience(closed(object.get(FIELD_AUDIENCE), FIELD_AUDIENCE, Audience::fromWireName))
                .lastFilingFailure(nullableClosed(object.get(FIELD_LAST_FILING_FAILURE), FIELD_LAST_FILING_FAILURE,
                        LastFilingFailure::fromWireName).orElse(null))
                .createdAt(instant(object.get(FIELD_CREATED_AT), FIELD_CREATED_AT))
                .toolVersion(text(object.get(FIELD_TOOL_VERSION), FIELD_TOOL_VERSION))
                .promotions(sequence(object, FIELD_PROMOTIONS, LessonCodec::promotion))
                .revision(integer(object.get(FIELD_REVISION), FIELD_REVISION))
                .sources(requiredSequence(object, FIELD_SOURCES, LessonCodec::source))
                .revisions(requiredSequence(object, FIELD_REVISIONS, LessonCodec::revision))
                .mergedFrom(sequence(object, FIELD_MERGED_FROM, LessonCodec::mergedFrom))
                .claim(nullable(object, FIELD_CLAIM, LessonCodec::claim).orElse(null))
                .retired(nullable(object, FIELD_RETIRED, LessonCodec::retired).orElse(null))
                .build();
    }

    private static Source source(Object value, String name) {
        var object = mapping(value, name);
        requireMembers(object, name, SOURCE_MEMBERS, SOURCE_REQUIRED_MEMBERS);
        return new Source(closed(object.get(FIELD_KIND), name + "." + FIELD_KIND, Source.Kind::fromWireName),
                text(object.get(FIELD_REF), name + "." + FIELD_REF),
                optionalText(object, FIELD_DIGEST, name + "." + FIELD_DIGEST).orElse(null));
    }

    private static Remedy remedy(Map<String, Object> object) {
        requireMembers(object, FIELD_REMEDY, REMEDY_MEMBERS, REMEDY_REQUIRED_MEMBERS);
        return new Remedy(closed(object.get(FIELD_KIND), FIELD_REMEDY + "." + FIELD_KIND, Remedy.Kind::fromWireName),
                text(object.get(FIELD_STATEMENT), FIELD_REMEDY + "." + FIELD_STATEMENT),
                nullableText(object.get(FIELD_CHECK), FIELD_REMEDY + "." + FIELD_CHECK).orElse(null));
    }

    private static Promotion promotion(Object value, String name) {
        var object = mapping(value, name);
        requireMembers(object, name, PROMOTION_MEMBERS, PROMOTION_REQUIRED_MEMBERS);
        return new Promotion(closed(object.get(FIELD_KIND), name + "." + FIELD_KIND, Promotion.Kind::fromWireName),
                text(object.get(FIELD_REF), name + "." + FIELD_REF), instant(object.get(FIELD_AT), name + "." + FIELD_AT));
    }

    private static Revision revision(Object value, String name) {
        var object = mapping(value, name);
        requireMembers(object, name, REVISION_MEMBERS, REVISION_REQUIRED_MEMBERS);
        return new Revision(integer(object.get(FIELD_N), name + "." + FIELD_N),
                instant(object.get(FIELD_AT), name + "." + FIELD_AT),
                requiredSequence(object, FIELD_SOURCES, LessonCodec::source),
                text(object.get(FIELD_BODY_SHA256), name + "." + FIELD_BODY_SHA256));
    }

    private static MergedFrom mergedFrom(Object value, String name) {
        var object = mapping(value, name);
        requireMembers(object, name, MERGED_FROM_MEMBERS, MERGED_FROM_REQUIRED_MEMBERS);
        return new MergedFrom(new LessonId(text(object.get(FIELD_LESSON_ID), name + "." + FIELD_LESSON_ID)),
                instant(object.get(FIELD_AT), name + "." + FIELD_AT), text(object.get(FIELD_BY), name + "." + FIELD_BY));
    }

    private static Claim claim(Object value) {
        var object = mapping(value, FIELD_CLAIM);
        requireMembers(object, FIELD_CLAIM, CLAIM_MEMBERS, CLAIM_REQUIRED_MEMBERS);
        return new Claim(text(object.get(FIELD_PLAN_ID), FIELD_CLAIM + "." + FIELD_PLAN_ID),
                instant(object.get(FIELD_CLAIMED_AT), FIELD_CLAIM + "." + FIELD_CLAIMED_AT));
    }

    private static Retired retired(Object value) {
        var object = mapping(value, FIELD_RETIRED);
        requireMembers(object, FIELD_RETIRED, RETIRED_MEMBERS, RETIRED_REQUIRED_MEMBERS);
        var into = nullableText(object.get(FIELD_INTO), FIELD_RETIRED + "." + FIELD_INTO);
        return Retired.builder()
                .at(instant(object.get(FIELD_AT), FIELD_RETIRED + "." + FIELD_AT))
                .by(text(object.get(FIELD_BY), FIELD_RETIRED + "." + FIELD_BY))
                .verdict(closed(object.get(FIELD_VERDICT), FIELD_RETIRED + "." + FIELD_VERDICT,
                        Retired.Verdict::fromWireName))
                .into(into.map(LessonId::new).orElse(null))
                .reason(text(object.get(FIELD_REASON), FIELD_RETIRED + "." + FIELD_REASON))
                .coveringClause(nullableText(object.get(FIELD_COVERING_CLAUSE),
                        FIELD_RETIRED + "." + FIELD_COVERING_CLAUSE).orElse(null))
                .coveringInput(nullableText(object.get(FIELD_COVERING_INPUT),
                        FIELD_RETIRED + "." + FIELD_COVERING_INPUT).orElse(null))
                .lessonState(closed(object.get(FIELD_LESSON_STATE), FIELD_RETIRED + "." + FIELD_LESSON_STATE,
                        Retired.LessonState::fromWireName))
                .build();
    }

    private static void requireMembers(Map<String, Object> object, String name, List<String> members,
            List<String> required) {
        for (var member : object.keySet()) {
            if (!members.contains(member)) {
                throw StrictJson.unreadable("undeclared member '%s' of the %s".formatted(member, name));
            }
        }
        for (var member : required) {
            if (!object.containsKey(member)) {
                throw StrictJson.unreadable("member '%s' of the %s is missing".formatted(member, name));
            }
        }
    }

    private static Map<String, Object> mapping(Object value, String name) {
        if (!(value instanceof Map<?, ?> map)) {
            throw StrictJson.unreadable("'%s' is an object".formatted(name));
        }
        var object = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw StrictJson.unreadable("'%s' has a member that is no name".formatted(name));
            }
            object.put(key, entry.getValue());
        }
        return object;
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text)) {
            throw StrictJson.unreadable("'%s' is a string".formatted(name));
        }
        return text;
    }

    private static Optional<String> nullableText(@Nullable Object value, String name) {
        return value == null ? Optional.empty() : Optional.of(text(value, name));
    }

    private static Optional<String> optionalText(Map<String, Object> object, String field, String name) {
        return object.containsKey(field) ? Optional.of(text(object.get(field), name)) : Optional.empty();
    }

    private static int integer(Object value, String name) {
        if (!(value instanceof Integer number)) {
            throw StrictJson.unreadable("'%s' is an integer".formatted(name));
        }
        return number;
    }

    private static Optional<Integer> nullableInteger(@Nullable Object value, String name) {
        return value == null ? Optional.empty() : Optional.of(integer(value, name));
    }

    private static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean flag)) {
            throw StrictJson.unreadable("'%s' is a boolean".formatted(name));
        }
        return flag;
    }

    private static Instant instant(Object value, String name) {
        var text = text(value, name);
        try {
            var instant = Instant.parse(text);
            if (instant.toString().equals(text)) {
                return instant;
            }
        } catch (DateTimeException e) {
            throw StrictJson.unreadable("'%s' is not an instant: %s".formatted(name, text), e);
        }
        throw StrictJson.unreadable(
                "'%s' is not in the one shape of an instant (RFC 3339, UTC, suffix Z): %s".formatted(name, text));
    }

    private static Optional<LocalDate> nullableDate(@Nullable Object value, String name) {
        if (value == null) {
            return Optional.empty();
        }
        var text = text(value, name);
        try {
            var date = LocalDate.parse(text);
            if (date.toString().equals(text)) {
                return Optional.of(date);
            }
        } catch (DateTimeException e) {
            throw StrictJson.unreadable("'%s' is not a date: %s".formatted(name, text), e);
        }
        throw StrictJson.unreadable("'%s' is not in the one shape of a date (yyyy-MM-dd): %s".formatted(name, text));
    }

    private static <E> E closed(Object value, String name, Function<String, Optional<E>> lookup) {
        var wireName = text(value, name);
        return lookup.apply(wireName)
                .orElseThrow(() -> StrictJson.unreadable("'%s' has the undeclared value '%s'".formatted(name, wireName)));
    }

    private static <E> Optional<E> nullableClosed(@Nullable Object value, String name,
            Function<String, Optional<E>> lookup) {
        return value == null ? Optional.empty() : Optional.of(closed(value, name, lookup));
    }

    private static <V> List<V> requiredSequence(Map<String, Object> object, String field,
            SequenceReader<V> reader) {
        return elements(sequenceValue(object, field), field, reader);
    }

    private static <V> List<V> sequence(Map<String, Object> object, String field, SequenceReader<V> reader) {
        if (!object.containsKey(field)) {
            return List.of();
        }
        return elements(sequenceValue(object, field), field, reader);
    }

    private static List<?> sequenceValue(Map<String, Object> object, String field) {
        var value = object.get(field);
        if (!(value instanceof List<?> list)) {
            throw StrictJson.unreadable("'%s' is an array".formatted(field));
        }
        return list;
    }

    private static <V> List<V> elements(List<?> list, String field, SequenceReader<V> reader) {
        var elements = new ArrayList<V>(list.size());
        for (var index = 0; index < list.size(); index++) {
            elements.add(reader.read(list.get(index), field + "[" + index + "]"));
        }
        return List.copyOf(elements);
    }

    private static <V> Optional<V> nullable(Map<String, Object> object, String field, ValueReader<V> reader) {
        var value = object.get(field);
        return value == null ? Optional.empty() : Optional.of(reader.read(value));
    }

    /** Reads one element of an array, named by its position. */
    @FunctionalInterface
    private interface SequenceReader<V> {
        V read(Object value, String name);
    }

    /** Reads an object that the schema admits as {@code null}. */
    @FunctionalInterface
    private interface ValueReader<V> {
        V read(Object value);
    }

    private static byte[] writeFrontMatter(LessonFrontMatter frontMatter) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, frontMatter.formatVersion());
            generator.writeStringField(FIELD_LESSON_ID, frontMatter.lessonId().value());
            generator.writeStringField(FIELD_TITLE, frontMatter.title());
            generator.writeStringField(FIELD_COMPONENT, frontMatter.component());
            generator.writeStringField(FIELD_CATEGORY, frontMatter.category().wireName());
            writeNullable(generator, FIELD_BUNDLE, frontMatter.bundle());
            writeNullable(generator, FIELD_RULE, frontMatter.rule());
            writeRemedy(generator, frontMatter.remedy());
            writeNullable(generator, FIELD_RECURRENCE_COUNT, frontMatter.recurrenceCount());
            writeNullable(generator, FIELD_LAST_SEEN,
                    frontMatter.lastSeen() == null ? null : frontMatter.lastSeen().toString());
            generator.writeStringField(FIELD_STATUS, frontMatter.status().wireName());
            generator.writeStringField(FIELD_PROVENANCE, frontMatter.provenance().wireName());
            generator.writeBooleanField(FIELD_DERIVED_FROM_UNTRUSTED, frontMatter.derivedFromUntrusted());
            generator.writeStringField(FIELD_AUDIENCE, frontMatter.audience().wireName());
            writeNullable(generator, FIELD_LAST_FILING_FAILURE,
                    frontMatter.lastFilingFailure() == null ? null : frontMatter.lastFilingFailure().wireName());
            writeInstant(generator, FIELD_CREATED_AT, frontMatter.createdAt());
            generator.writeStringField(FIELD_TOOL_VERSION, frontMatter.toolVersion());
            writePromotions(generator, frontMatter.promotions());
            generator.writeNumberField(FIELD_REVISION, frontMatter.revision());
            writeSources(generator, FIELD_SOURCES, frontMatter.sources());
            writeRevisions(generator, frontMatter.revisions());
            writeMergedFrom(generator, frontMatter.mergedFrom());
            writeClaim(generator, frontMatter.claim());
            writeRetired(generator, frontMatter.retired());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Lesson front matter cannot be written", e);
        }
        return out.toByteArray();
    }

    private static void writeRemedy(JsonGenerator generator, Remedy remedy) throws IOException {
        generator.writeObjectFieldStart(FIELD_REMEDY);
        generator.writeStringField(FIELD_KIND, remedy.kind().wireName());
        generator.writeStringField(FIELD_STATEMENT, remedy.statement());
        writeNullable(generator, FIELD_CHECK, remedy.check());
        generator.writeEndObject();
    }

    private static void writePromotions(JsonGenerator generator, List<Promotion> promotions) throws IOException {
        generator.writeArrayFieldStart(FIELD_PROMOTIONS);
        for (var promotion : promotions) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_KIND, promotion.kind().wireName());
            generator.writeStringField(FIELD_REF, promotion.ref());
            writeInstant(generator, FIELD_AT, promotion.at());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeSources(JsonGenerator generator, String field, List<Source> sources) throws IOException {
        generator.writeArrayFieldStart(field);
        for (var source : sources) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_KIND, source.kind().wireName());
            generator.writeStringField(FIELD_REF, source.ref());
            if (source.digest() != null) {
                generator.writeStringField(FIELD_DIGEST, source.digest());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeRevisions(JsonGenerator generator, List<Revision> revisions) throws IOException {
        generator.writeArrayFieldStart(FIELD_REVISIONS);
        for (var revision : revisions) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_N, revision.n());
            writeInstant(generator, FIELD_AT, revision.at());
            writeSources(generator, FIELD_SOURCES, revision.sources());
            generator.writeStringField(FIELD_BODY_SHA256, revision.bodySha256());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeMergedFrom(JsonGenerator generator, List<MergedFrom> mergedFrom) throws IOException {
        generator.writeArrayFieldStart(FIELD_MERGED_FROM);
        for (var merged : mergedFrom) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_LESSON_ID, merged.lessonId().value());
            writeInstant(generator, FIELD_AT, merged.at());
            generator.writeStringField(FIELD_BY, merged.by());
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private static void writeClaim(JsonGenerator generator, @Nullable Claim claim) throws IOException {
        if (claim == null) {
            generator.writeNullField(FIELD_CLAIM);
            return;
        }
        generator.writeObjectFieldStart(FIELD_CLAIM);
        generator.writeStringField(FIELD_PLAN_ID, claim.planId());
        writeInstant(generator, FIELD_CLAIMED_AT, claim.claimedAt());
        generator.writeEndObject();
    }

    private static void writeRetired(JsonGenerator generator, @Nullable Retired retired) throws IOException {
        if (retired == null) {
            generator.writeNullField(FIELD_RETIRED);
            return;
        }
        generator.writeObjectFieldStart(FIELD_RETIRED);
        writeInstant(generator, FIELD_AT, retired.at());
        generator.writeStringField(FIELD_BY, retired.by());
        generator.writeStringField(FIELD_VERDICT, retired.verdict().wireName());
        if (retired.into() != null) {
            generator.writeStringField(FIELD_INTO, retired.into().value());
        }
        generator.writeStringField(FIELD_REASON, retired.reason());
        writeNullable(generator, FIELD_COVERING_CLAUSE, retired.coveringClause());
        writeNullable(generator, FIELD_COVERING_INPUT, retired.coveringInput());
        generator.writeStringField(FIELD_LESSON_STATE, retired.lessonState().wireName());
        generator.writeEndObject();
    }

    private static void writeInstant(JsonGenerator generator, String field, Instant instant) throws IOException {
        StrictJson.writeInstant(generator, field, instant);
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable Integer value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeNumberField(field, value);
        }
    }

    private static boolean isFence(String text, int start, int end) {
        var lineEnd = end;
        if (lineEnd > start && text.charAt(lineEnd - 1) == '\r') {
            lineEnd--;
        }
        return lineEnd - start == FENCE.length() && text.startsWith(FENCE, start);
    }

    private static int lineEnd(String text, int start) {
        var index = text.indexOf('\n', start);
        return index < 0 ? text.length() : index;
    }

    private static int fenceLineAfter(String text, int start) {
        var lineStart = start;
        while (lineStart <= text.length()) {
            var end = lineEnd(text, lineStart);
            if (isFence(text, lineStart, end)) {
                return lineStart;
            }
            if (end >= text.length()) {
                return -1;
            }
            lineStart = end + 1;
        }
        return -1;
    }

    /**
     * The YAML 1.2 core schema, so that an unquoted {@code 2026-10-31} stays a string and {@code yes} stays a string:
     * the front matter is restricted to the JSON data model, and the resolver is what makes the reader honour it.
     */
    private static final class JsonModelResolver extends Resolver {

        private static final Pattern BOOLEAN = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
        private static final Pattern INTEGER = Pattern.compile("^[-+]?[0-9]+$|^0o[0-7]+$|^0x[0-9a-fA-F]+$");
        private static final Pattern DECIMAL = Pattern.compile(
                "^[-+]?(?:\\.[0-9]+|[0-9]+(?:\\.[0-9]*)?)(?:[eE][-+]?[0-9]+)?$");
        private static final Pattern NOTHING = Pattern.compile("^(?:~|null|Null|NULL|)$");
        private static final Pattern EMPTY = Pattern.compile("^$");

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, BOOLEAN, "tTfF");
            addImplicitResolver(Tag.INT, INTEGER, "-+0123456789");
            addImplicitResolver(Tag.FLOAT, DECIMAL, "-+0123456789.");
            addImplicitResolver(Tag.NULL, NOTHING, "~nN");
            addImplicitResolver(Tag.NULL, EMPTY, null);
        }
    }
}
