/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.corpus.CorpusViolation.Rule;
import de.planmarshall.core.role.corpus.JsonValue.JsonBoolean;
import de.planmarshall.core.role.corpus.JsonValue.JsonList;
import de.planmarshall.core.role.corpus.JsonValue.JsonNull;
import de.planmarshall.core.role.corpus.JsonValue.JsonNumber;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;
import de.planmarshall.core.store.StoreRefusal;

/**
 * Validates the model verification corpus (PM-WORK-6): the Java form of {@code validate.py} of the corpus, plus the
 * rules of its sources. Every violation is reported as a {@link CorpusViolation}; none is thrown.
 * <p>
 * An item is every {@code *.json} file below the corpus root outside a directory {@code schema/} or {@code context/}
 * at any depth and outside the directory {@code sources/} of the corpus root. Each is checked against the item schema {@code schema/verification-item.schema.json} of the corpus
 * root with the keyword subset of {@link SchemaSubset}, and against the rules the schema cannot express: the id
 * equals the path without {@code .json}, the topic equals the directory, ids are unique, the canary is the corpus
 * canary, a closed answer ({@code exact_choice}, {@code label}) is one of the choices, a rubric method has rubric items
 * unless the item is a control ({@code expected_empty}), context files exist, and provenance, review, grading, tags,
 * group and the contamination note name no transient tool, stage or local path. The group and the contamination note
 * are covered beyond {@code validate.py}, which checks only the first four.
 * <p>
 * The {@code sources} entry of the corpus root is read by the rules of {@link CorpusImport}: absent, the corpus has
 * no sources; present but no directory, or a directory that cannot be listed, it is reported {@code unreadable}, as the
 * import refuses that corpus. Every directory below {@code sources/} is a source: its {@code manifest.json} and {@code overlay.json} must be valid
 * against {@code source-manifest.schema.json} and {@code source-overlay.schema.json} of this package and read with
 * their codecs, the manifest must name its directory, and the overlay its source and a file of the manifest.
 * {@code ATTRIBUTION.md} at the corpus root has exactly one {@code ## <name>} section per source. No third-party text
 * is stored below the corpus: no file has the SHA-256 of a file a manifest names, and no item is an imported one
 * ({@code provenance.kind} {@code fetched} with an origin naming the commit and the line of a source row).
 * <p>
 * The items an import generated below a cache root are checked as well, by {@link #validate(Path)}: against the item
 * schema, against {@code imported-item.schema.json}, and by the item rules, their ids unique together with the
 * corpus's own. An absent {@code items} directory holds no generated item; an {@code items} entry that is no directory,
 * or a directory whose files cannot be listed, is reported {@code unreadable} under the subject {@code items}.
 *
 * @since 0.1
 */
public final class CorpusValidator {

    /** The item schema, relative to the corpus root. */
    public static final String ITEM_SCHEMA = "schema/verification-item.schema.json";

    /** The attribution of the sources, relative to the corpus root. */
    public static final String ATTRIBUTION = "ATTRIBUTION.md";

    /**
     * What a transient tool, stage or local path looks like, as {@code validate.py} defines it, with the meaning
     * Python gives its {@code \b} and {@code \d} ({@link PythonRegex}).
     */
    public static final Pattern TRANSIENT = PythonRegex.compile(
            "fixtures/|\\.plan/|pull\\.py|judge\\.py|corpus\\.py|test-pull|\\bStage \\d\\b|\\bE\\d{1,2}\\b"
                    + "|\\bV\\d{1,2}\\b|Part A");

    /** The directories whose files are no items, at any depth, as {@code validate.py} skips them. */
    private static final Set<String> NO_ITEMS = Set.of("schema", "context");

    private static final String JSON = ".json";
    private static final String CORPUS = ".";
    private static final String NOT_JSON = "not JSON: ";
    private static final Set<String> CLOSED_METHODS = Set.of("exact_choice", "label");
    private static final Set<String> RUBRIC_METHODS = Set.of("rubric_items", "rubric_properties");
    private static final List<String> TRANSIENT_MEMBERS = List.of("provenance", "review", "grading", "tags", "group",
            "contamination");

    private final Path corpusRoot;

    /**
     * @param corpusRoot the corpus root, holding {@code schema/} and the items
     */
    public CorpusValidator(Path corpusRoot) {
        this.corpusRoot = Objects.requireNonNull(corpusRoot, "corpusRoot").toAbsolutePath().normalize();
    }

    /**
     * Validates the corpus.
     *
     * @return the violations, in the order of the checks; empty for a valid corpus
     */
    public List<CorpusViolation> validate() {
        return new Run().corpus();
    }

    /**
     * Validates the corpus and the items an import generated below the cache root.
     *
     * @param cacheRoot the cache root whose {@code items/} directory holds the generated items
     * @return the violations, in the order of the checks; empty if the corpus and the generated items are valid
     */
    public List<CorpusViolation> validate(Path cacheRoot) {
        var items = Objects.requireNonNull(cacheRoot, "cacheRoot").toAbsolutePath().normalize()
                .resolve(CorpusImport.ITEMS);
        var run = new Run();
        run.corpus();
        return run.generated(items);
    }

    /** The checks of one validation, collecting its violations. */
    private final class Run {

        private final List<CorpusViolation> violations = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private @Nullable JsonValue itemSchema;

        private List<CorpusViolation> corpus() {
            if (!Files.isDirectory(corpusRoot)) {
                add(CORPUS, Rule.UNREADABLE, "the corpus root %s is no directory".formatted(corpusRoot));
                return List.copyOf(violations);
            }
            itemSchema = itemSchema().orElse(null);
            var files = files(corpusRoot, CORPUS);
            for (var file : files) {
                var relative = relative(corpusRoot, file);
                if (relative.endsWith(JSON) && isItem(relative)) {
                    item(file, id(relative), false);
                }
            }
            var hashes = sources();
            thirdPartyFiles(files, hashes);
            return List.copyOf(violations);
        }

        /**
         * Checks the generated items. An absent items directory holds none; an entry that is no directory is a
         * violation, never read as no generated items.
         */
        private List<CorpusViolation> generated(Path itemsRoot) {
            if (Files.notExists(itemsRoot, LinkOption.NOFOLLOW_LINKS)) {
                return List.copyOf(violations);
            }
            if (!Files.isDirectory(itemsRoot)) {
                add(CorpusImport.ITEMS, Rule.UNREADABLE,
                        "the generated items %s are no directory, so no item can be read".formatted(itemsRoot));
            } else {
                for (var file : files(itemsRoot, CorpusImport.ITEMS)) {
                    var relative = relative(itemsRoot, file);
                    if (relative.endsWith(JSON)) {
                        item(file, id(relative), true);
                    }
                }
            }
            return List.copyOf(violations);
        }

        private Optional<JsonValue> itemSchema() {
            var file = corpusRoot.resolve(ITEM_SCHEMA);
            if (!Files.isRegularFile(file)) {
                add(ITEM_SCHEMA, Rule.ITEM_SCHEMA, "the item schema is missing");
                return Optional.empty();
            }
            try {
                return Optional.of(JsonValue.parse(Files.readAllBytes(file)));
            } catch (IOException e) {
                add(ITEM_SCHEMA, Rule.UNREADABLE, describe(e));
            } catch (MalformedJson e) {
                add(ITEM_SCHEMA, Rule.ITEM_SCHEMA, NOT_JSON + e.getMessage());
            }
            return Optional.empty();
        }

        private void item(Path file, String id, boolean generated) {
            var document = document(file, id);
            if (document.isPresent()) {
                var item = document.get();
                if (itemSchema != null) {
                    SchemaSubset.validate(item, itemSchema).forEach(error -> add(id, Rule.SCHEMA, error));
                }
                if (generated) {
                    SchemaSubset.validate(item, Schemas.IMPORTED_ITEM)
                            .forEach(error -> add(id, Rule.IMPORTED_ITEM, error));
                }
                if (item instanceof JsonObject object) {
                    rules(object, id);
                    if (!generated && isImported(object)) {
                        add(id, Rule.THIRD_PARTY_TEXT,
                                "an imported item is stored below the corpus; generated items belong below the cache "
                                        + "root");
                    }
                }
            }
            if (!seen.add(id)) {
                add(id, Rule.DUPLICATE_ID, "duplicate id");
            }
        }

        private Optional<JsonValue> document(Path file, String id) {
            try {
                return Optional.of(JsonValue.parse(Files.readAllBytes(file)));
            } catch (IOException e) {
                add(id, Rule.UNREADABLE, describe(e));
            } catch (MalformedJson e) {
                add(id, Rule.NOT_JSON, NOT_JSON + e.getMessage());
            }
            return Optional.empty();
        }

        private void rules(JsonObject item, String id) {
            identity(item, id);
            grading(item, id);
            contextFiles(item, id);
            transientNames(item, id);
        }

        /** Checks that the id equals the path, the topic the directory, and the canary is the corpus canary. */
        private void identity(JsonObject item, String id) {
            var itemId = item.get("id");
            if (!itemId.map(value -> value instanceof JsonString(var named) && named.equals(id)).orElse(false)) {
                add(id, Rule.ID_PATH, "id %s differs from the path '%s'".formatted(text(itemId), id));
            }
            var directory = id.contains("/") ? id.substring(0, id.lastIndexOf('/')) : id;
            if (!string(Optional.of(item), "topic").filter(directory::equals).isPresent()) {
                add(id, Rule.TOPIC_DIRECTORY, "topic differs from the directory");
            }
            if (!string(Optional.of(item), "canary").filter(ItemGenerator.CANARY::equals).isPresent()) {
                add(id, Rule.CANARY, "missing or wrong canary");
            }
        }

        /** Checks that a closed answer is one of the choices and a rubric has items unless the item is a control. */
        private void grading(JsonObject item, String id) {
            var method = string(object(item, "grading"), "method").orElse("");
            var target = object(item, "target");
            var input = object(item, "input");
            if (CLOSED_METHODS.contains(method) && !answerAmongChoices(target, input)) {
                add(id, Rule.CLOSED_ANSWER, "a closed answer must be one of the choices");
            }
            if (RUBRIC_METHODS.contains(method) && !truthy(target.flatMap(value -> value.get("items")))
                    && !truthy(target.flatMap(value -> value.get("expected_empty")))) {
                add(id, Rule.RUBRIC_ITEMS, "a rubric needs items, or expected_empty for a control");
            }
        }

        /** Checks that every context file the input names exists. */
        private void contextFiles(JsonObject item, String id) {
            var input = object(item, "input");
            if (input.flatMap(value -> value.get("context")).orElse(null) instanceof JsonList(var contexts)) {
                for (var context : contexts) {
                    if (context instanceof JsonString(var path) && !isFile(path)) {
                        add(id, Rule.CONTEXT_FILE, "context file %s is missing".formatted(path));
                    }
                }
            }
        }

        /**
         * Checks that provenance, review, grading, tags, group and the contamination note name no transient tool, stage
         * or local path.
         */
        private void transientNames(JsonObject item, String id) {
            var named = new LinkedHashMap<String, JsonValue>();
            TRANSIENT_MEMBERS.forEach(member -> named.put(member, item.get(member).orElse(new JsonNull())));
            var matcher = TRANSIENT.matcher(new JsonObject(named).toJson());
            while (matcher.find()) {
                add(id, Rule.TRANSIENT_NAME, "names a transient tool or stage: '%s'".formatted(matcher.group()));
            }
        }

        private boolean answerAmongChoices(Optional<JsonObject> target, Optional<JsonObject> input) {
            if (!(input.flatMap(value -> value.get("choices")).orElse(null) instanceof JsonList(var choices))
                    || choices.isEmpty()) {
                return false;
            }
            var answer = target.flatMap(value -> value.get("answer")).orElse(new JsonNull());
            return choices.stream().anyMatch(choice -> SchemaSubset.same(choice, answer));
        }

        private boolean isFile(String path) {
            try {
                return Files.isRegularFile(corpusRoot.resolve(path));
            } catch (InvalidPathException _) {
                return false;
            }
        }

        /**
         * Checks the sources by the rules {@link CorpusImport} reads them with: an absent {@code sources} entry is a
         * corpus without sources; an entry that is no directory (a file, a dangling link, an entry that cannot be told
         * to be absent) and a directory that cannot be listed are violations, never read as no sources.
         *
         * @return the SHA-256 of every file the manifests name
         */
        private Set<String> sources() {
            var sources = corpusRoot.resolve(CorpusImport.SOURCES);
            if (Files.notExists(sources, LinkOption.NOFOLLOW_LINKS)) {
                return Set.of();
            }
            if (!Files.isDirectory(sources)) {
                add(CorpusImport.SOURCES, Rule.UNREADABLE,
                        "the sources %s are no directory, so no source can be read".formatted(sources));
                return Set.of();
            }
            var hashes = new HashSet<String>();
            var names = new ArrayList<String>();
            for (var directory : directories(sources)) {
                var name = directory.getFileName().toString();
                names.add(name);
                var prefix = CorpusImport.SOURCES + "/" + name + "/";
                var manifestSubject = prefix + CorpusImport.MANIFEST;
                var manifest = format(directory.resolve(CorpusImport.MANIFEST), manifestSubject, Schemas.MANIFEST,
                        Rule.MANIFEST, SourceManifestCodec::read);
                manifest.ifPresent(document -> hashes.addAll(fileHashes(document.value())));
                var manifestRead = manifest.flatMap(Document::decoded);
                manifestRead.ifPresent(read -> manifest(read, manifestSubject, name));
                var overlaySubject = prefix + CorpusImport.OVERLAY;
                format(directory.resolve(CorpusImport.OVERLAY), overlaySubject, Schemas.OVERLAY, Rule.OVERLAY,
                        SourceOverlayCodec::read).flatMap(Document::decoded)
                        .ifPresent(read -> overlay(read, overlaySubject, name, manifestRead));
            }
            attribution(names);
            return hashes;
        }

        private void manifest(SourceManifest manifest, String subject, String name) {
            if (!manifest.name().equals(name)) {
                add(subject, Rule.MANIFEST, "the manifest names the source '%s' in the directory '%s'"
                        .formatted(manifest.name(), name));
            }
        }

        private void overlay(SourceOverlay overlay, String subject, String name, Optional<SourceManifest> manifest) {
            if (!overlay.source().equals(name)) {
                add(subject, Rule.OVERLAY, "the overlay names the source '%s' in the directory '%s'"
                        .formatted(overlay.source(), name));
            }
            if (manifest.isPresent() && manifest.get().file(overlay.file()).isEmpty()) {
                add(subject, Rule.OVERLAY,
                        "the overlay reads the file '%s' the manifest does not name".formatted(overlay.file()));
            }
        }

        /**
         * Reads a document of a source, checks it against its schema and, if it is valid, reads it with its codec.
         *
         * @return the document; empty if it is missing, unreadable or no JSON
         */
        private <T> Optional<Document<T>> format(Path file, String subject, JsonValue schema, Rule rule,
                BiFunction<Path, byte[], T> codec) {
            if (!Files.isRegularFile(file)) {
                add(subject, rule, "the file is missing");
                return Optional.empty();
            }
            try {
                var bytes = Files.readAllBytes(file);
                var value = JsonValue.parse(bytes);
                var errors = SchemaSubset.validate(value, schema);
                errors.forEach(error -> add(subject, rule, error));
                var decoded = errors.isEmpty() ? decode(file, bytes, subject, rule, codec) : Optional.<T>empty();
                return Optional.of(new Document<>(value, decoded));
            } catch (IOException e) {
                add(subject, Rule.UNREADABLE, describe(e));
            } catch (MalformedJson e) {
                add(subject, rule, NOT_JSON + e.getMessage());
            }
            return Optional.empty();
        }

        /** @return the document read with its codec; empty, with the refusal reported, if the codec refuses it */
        private <T> Optional<T> decode(Path file, byte[] bytes, String subject, Rule rule,
                BiFunction<Path, byte[], T> codec) {
            try {
                return Optional.of(codec.apply(file, bytes));
            } catch (StoreRefusal e) {
                add(subject, rule, e.getMessage());
                return Optional.empty();
            }
        }

        private void attribution(List<String> names) {
            if (names.isEmpty()) {
                return;
            }
            var file = corpusRoot.resolve(ATTRIBUTION);
            if (!Files.isRegularFile(file)) {
                names.forEach(name -> add(ATTRIBUTION, Rule.ATTRIBUTION,
                        "%s is missing, so the source '%s' has no section".formatted(ATTRIBUTION, name)));
                return;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                add(ATTRIBUTION, Rule.UNREADABLE, describe(e));
                return;
            }
            for (var name : names) {
                var heading = "## " + name;
                var sections = lines.stream().filter(line -> line.strip().equals(heading)).count();
                if (sections != 1) {
                    add(ATTRIBUTION, Rule.ATTRIBUTION, String.format(Locale.ROOT,
                            "the section '%s' appears %d times, not once", heading, sections));
                }
            }
        }

        private void thirdPartyFiles(List<Path> files, Set<String> hashes) {
            if (hashes.isEmpty()) {
                return;
            }
            for (var file : files) {
                var relative = relative(corpusRoot, file);
                try (var content = Files.newInputStream(file)) {
                    var hash = Sha256.hex(content);
                    if (hashes.contains(hash)) {
                        add(relative, Rule.THIRD_PARTY_TEXT,
                                "the file has the SHA-256 %s of a file a source manifest names".formatted(hash));
                    }
                } catch (IOException e) {
                    add(relative, Rule.UNREADABLE, describe(e));
                }
            }
        }

        /**
         * @param root    the directory whose regular files are listed
         * @param subject the subject a listing failure is reported under: {@code .} for the corpus, {@code items} for
         *                the generated items
         */
        private List<Path> files(Path root, String subject) {
            try (Stream<Path> walk = Files.walk(root)) {
                return walk.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(file -> relative(root, file))).toList();
            } catch (IOException | UncheckedIOException e) {
                add(subject, Rule.UNREADABLE,
                        "the files below %s cannot be listed: %s".formatted(root, e.getMessage()));
                return List.of();
            }
        }

        private List<Path> directories(Path root) {
            try (Stream<Path> entries = Files.list(root)) {
                return entries.filter(Files::isDirectory).sorted().toList();
            } catch (IOException | UncheckedIOException e) {
                add(CorpusImport.SOURCES, Rule.UNREADABLE,
                        "the sources cannot be listed: %s".formatted(e.getMessage()));
                return List.of();
            }
        }

        private void add(String subject, Rule rule, String detail) {
            violations.add(new CorpusViolation(subject, rule, detail));
        }
    }

    /**
     * A document of a source, read and checked against its schema.
     *
     * @param value   the JSON value
     * @param decoded the document read with its codec; empty if the value is not valid against its schema or the codec
     *                refuses it
     * @param <T>     the type the codec reads
     */
    private record Document<T>(JsonValue value, Optional<T> decoded) {
    }

    /** The schema resources of this package, read on first use. */
    private static final class Schemas {

        private static final JsonValue MANIFEST = resource("source-manifest.schema.json");
        private static final JsonValue OVERLAY = resource("source-overlay.schema.json");
        private static final JsonValue IMPORTED_ITEM = resource("imported-item.schema.json");

        private Schemas() {
        }

        private static JsonValue resource(String name) {
            try (var in = CorpusValidator.class.getResourceAsStream(name)) {
                if (in == null) {
                    throw new IllegalStateException("the schema resource %s is not on the class path".formatted(name));
                }
                return JsonValue.parse(in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException("the schema resource %s cannot be read".formatted(name), e);
            }
        }
    }

    private static Set<String> fileHashes(JsonValue manifest) {
        var hashes = new HashSet<String>();
        if (manifest instanceof JsonObject(var members) && members.get("files") instanceof JsonList(var files)) {
            for (var file : files) {
                if (file instanceof JsonObject(var entry) && entry.get("sha256") instanceof JsonString(var hash)
                        && SourceManifest.SHA256.matcher(hash).matches()) {
                    hashes.add(hash);
                }
            }
        }
        return hashes;
    }

    /**
     * @return {@code true} for an item in the imported form: fetched, its origin naming the commit and the line of a
     *         row of a source; an item of the corpus's own part fetched from a review comment names no line
     */
    private static boolean isImported(JsonObject item) {
        var provenance = object(item, "provenance");
        var origin = provenance.flatMap(value -> object(value, "origin"));
        return string(provenance, "kind").filter(ImportedItem.Provenance.KIND::equals).isPresent()
                && origin.flatMap(value -> value.get("commit")).isPresent()
                && origin.flatMap(value -> value.get("line")).isPresent();
    }

    /**
     * @return {@code true} for a file that is an item: outside every directory {@code schema/} and {@code context/}
     *         at any depth, as {@code validate.py} skips them, and outside the {@code sources/} directory of the corpus
     *         root, which {@link Run#sources()} reads; a {@code sources} directory deeper in the tree holds items
     */
    private static boolean isItem(String relative) {
        var segments = relative.split("/");
        return !CorpusImport.SOURCES.equals(segments[0]) && Stream.of(segments).noneMatch(NO_ITEMS::contains);
    }

    private static String id(String relative) {
        return relative.substring(0, relative.length() - JSON.length());
    }

    private static String relative(Path root, Path file) {
        var relative = root.relativize(file);
        var names = new ArrayList<String>();
        relative.forEach(name -> names.add(name.toString()));
        return String.join("/", names);
    }

    private static Optional<JsonObject> object(JsonObject parent, String name) {
        return parent.get(name).filter(JsonObject.class::isInstance).map(JsonObject.class::cast);
    }

    private static Optional<String> string(Optional<JsonObject> parent, String name) {
        return parent.flatMap(value -> value.get(name)).filter(JsonString.class::isInstance)
                .map(value -> ((JsonString) value).value());
    }

    private static String text(Optional<JsonValue> value) {
        return value.map(present -> present instanceof JsonString(var string) ? "'" + string + "'"
                : present.toJson()).orElse("None");
    }

    /** @return {@code true} for a value Python reads as true: present, and not null, false, zero or empty */
    private static boolean truthy(Optional<JsonValue> value) {
        return switch (value.orElse(null)) {
            case null -> false;
            case JsonNull _ -> false;
            case JsonBoolean(var bool) -> bool;
            case JsonNumber(var number, _) -> number.signum() != 0;
            case JsonString(var string) -> !string.isEmpty();
            case JsonList(var elements) -> !elements.isEmpty();
            case JsonObject(var members) -> !members.isEmpty();
        };
    }

    private static String describe(IOException e) {
        return "cannot be read: " + e;
    }
}
