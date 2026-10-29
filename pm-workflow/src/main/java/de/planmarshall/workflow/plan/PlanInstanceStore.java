/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static de.planmarshall.workflow.log.WorkflowLogMessages.INFO;
import static de.planmarshall.workflow.log.WorkflowLogMessages.WARN;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The versions of a plan instance, the composition log and the texts of the bound sub-units, in the plan's directory
 * of the machine store (PM-IMPL-5, PM-WF-1), written through a {@link ScopeTransaction} of the plan the caller holds
 * and commits:
 * <ul>
 * <li>{@code instance/plan.v<N>.pmwf}: the plan unit of version {@code N} with its header, written once and never
 * rewritten;</li>
 * <li>{@code instance/<unit>.v<N>.pmwf}: the composed file of every phase sub-workflow and bound axis variant of the
 * version, written in the same transaction, once, and never rewritten;</li>
 * <li>{@code instance/composition.jsonl}: one line per composition attempt, whatever its result; the line of a
 * composed version carries its {@code diff_summary} against the recorded version before it ({@link DiffSummary}),
 * {@code null} for version 1;</li>
 * <li>{@code mcp/units/<digest>.pmwf}: the verified text of each sub-unit a version names, content-addressed, written
 * in the transaction of the first version naming the digest and never rewritten.</li>
 * </ul>
 * The set of files is one value: the header names every composed file by digest ({@code inputs.templates}), so a
 * header digest that names no file and a file the header does not name are both refused before anything is written.
 * The digest sidecar of the plan covers every file through the commit of the transaction. A refused composition writes
 * only its log line. The store never replaces a version file, and it refuses one twice: {@link #recordComposed} refuses
 * a version whose file exists when it is called, before anything is recorded, and the version file is collected with
 * {@link ScopeTransaction#create(String, byte[])}, so the commit refuses the whole transaction, under the plan lock
 * and before anything is written, when another transaction created the file in between, such as a second transaction
 * of the same lock transaction that recorded the same version. The texts of the sub-units are plain replaces: they are
 * content-addressed, so a second writer of a digest writes the same bytes.
 *
 * @since 0.1
 */
public final class PlanInstanceStore {

    /** The composition log, relative to the directory of the plan. */
    public static final String COMPOSITION_LOG = "instance/composition.jsonl";

    private static final String INSTANCE_DIRECTORY = "instance";

    private static final String UNITS_DIRECTORY = "mcp/units";

    private static final CuiLogger LOGGER = new CuiLogger(PlanInstanceStore.class);

    private final JournalScope scope;

    /**
     * @param scope the scope of the plan
     * @throws IllegalArgumentException if the scope is no plan scope
     */
    public PlanInstanceStore(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "the scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("the plan instance lives in a plan scope, not " + scope.type());
        }
    }

    /**
     * @param version an instance version
     * @return the file of the version, relative to the directory of the plan
     */
    public static String versionPath(int version) {
        return unitPath(PlanInstanceComposer.PLAN_UNIT, version);
    }

    /**
     * @param unit    the id of a composed unit
     * @param version an instance version
     * @return the file of the composed unit, relative to the directory of the plan
     */
    public static String unitPath(String unit, int version) {
        return "%s/%s.v%d.pmwf".formatted(INSTANCE_DIRECTORY, unit, version);
    }

    /**
     * @param digest the SHA-256 of a sub-unit text
     * @return the file of the text, relative to the directory of the plan
     */
    public static String subUnitPath(String digest) {
        return "%s/%s.pmwf".formatted(UNITS_DIRECTORY, digest);
    }

    /**
     * Records a composed version in the transaction: its text, the text of every sub-unit its header names whose
     * digest has no file yet, and its log line.
     *
     * @param transaction       the open transaction of the plan
     * @param instance          the composed version
     * @param texts             the verified texts of the sub-units; every sub-unit the header names needs its text
     * @param compositionRecord the log line of the composition
     * @throws IllegalArgumentException if the composition record is not the composed record of the version (its
     *                                  result, version, event, trigger and input digests are those of the version, its
     *                                  diff summary the version against the recorded version before it), a version
     *                                  after the first follows no recorded version, a text does not match its digest,
     *                                  or a sub-unit the header names has no text; nothing was recorded
     * @throws IllegalStateException    also if the recorded version before it carries no readable header
     * @throws IllegalStateException    if the version file exists already; nothing was recorded. A version file that
     *                                  another transaction creates after this call is refused by the commit of
     *                                  {@code transaction} instead, which then throws this exception and writes
     *                                  nothing
     * @throws UncheckedIOException     if a directory of the plan cannot be created
     */
    public void recordComposed(ScopeTransaction transaction, PlanInstance instance, List<SubUnitText> texts,
            CompositionRecord compositionRecord) {
        Objects.requireNonNull(transaction, "the transaction");
        Objects.requireNonNull(instance, "the instance");
        Objects.requireNonNull(compositionRecord, "the composition record");
        if (!recordsComposition(compositionRecord, instance)) {
            throw new IllegalArgumentException("the record does not record the composition of version "
                    + instance.version());
        }
        checkComposedSet(instance);
        var byDigest = new HashMap<String, SubUnitText>();
        for (var text : texts) {
            if (!text.matchesDigest()) {
                throw new IllegalArgumentException("the text of the sub-unit '%s' does not match its digest %s"
                        .formatted(text.reference().unit(), text.reference().digest()));
            }
            byDigest.put(text.reference().digest(), text);
        }
        var named = instance.header().inputs().subUnits();
        for (var reference : named) {
            if (!byDigest.containsKey(reference.digest())) {
                throw new IllegalArgumentException("the sub-unit '%s' the version names has no text"
                        .formatted(reference.unit()));
            }
        }
        var versionPath = versionPath(instance.version());
        if (exists(versionPath)) {
            throw new IllegalStateException("the version %d of the plan '%s' exists already and is never rewritten"
                    .formatted(instance.version(), scope.scopeId()));
        }
        createDirectory(INSTANCE_DIRECTORY);
        createDirectory(UNITS_DIRECTORY);
        transaction.create(versionPath, instance.text().getBytes(StandardCharsets.UTF_8));
        instance.units().forEach(composed -> transaction.create(composed.file(),
                composed.text().getBytes(StandardCharsets.UTF_8)));
        named.stream().map(UnitReference::digest).distinct().filter(digest -> !exists(subUnitPath(digest)))
                .forEach(digest -> transaction.replace(subUnitPath(digest), byDigest.get(digest).bytes()));
        transaction.append(COMPOSITION_LOG, line(compositionRecord));
        LOGGER.info(INFO.PLAN_INSTANCE_VERSION_RECORDED, instance.version(), scope.scopeId(),
                instance.header().event().wireName());
    }

    /**
     * The set the version is: the header names the plan unit's file and one composed file per composed unit, each by
     * the digest of its text, and the version composes exactly those files.
     *
     * @param instance the composed version
     * @throws IllegalArgumentException if the header names no file for the plan unit or a file the version does not
     *                                  compose, a composed file the header does not name, a digest that is not the
     *                                  digest of its file, or a file twice; nothing was recorded
     */
    private static void checkComposedSet(PlanInstance instance) {
        var version = instance.version();
        var named = new HashMap<String, String>();
        for (var reference : instance.header().inputs().templates()) {
            reference.file().ifPresent(file -> named.put(file, reference.digest()));
        }
        var planFile = versionPath(version);
        if (!named.containsKey(planFile)) {
            throw new IllegalArgumentException("the header of version %d names no file for the plan unit"
                    .formatted(version));
        }
        var composedFiles = new HashSet<String>();
        for (var composed : instance.units()) {
            if (!composedFiles.add(composed.file())) {
                throw new IllegalArgumentException("the version composes the file '%s' twice".formatted(composed.file()));
            }
            var digest = named.get(composed.file());
            if (digest == null) {
                throw new IllegalArgumentException("the header of version %d does not name the composed file '%s'"
                        .formatted(version, composed.file()));
            }
            if (!digest.equals(composed.digest())) {
                throw new IllegalArgumentException("the header names the digest %s for the composed file '%s', whose"
                        .formatted(digest, composed.file()) + " text has the digest " + composed.digest());
            }
        }
        for (var file : named.keySet()) {
            if (!file.equals(planFile) && !composedFiles.contains(file)) {
                throw new IllegalArgumentException("the header of version %d names the file '%s', which the version"
                        .formatted(version, file) + " does not compose");
            }
        }
    }

    /**
     * Records a composition attempt that composed no version, its log line only.
     *
     * @param transaction       the open transaction of the plan
     * @param compositionRecord the log line of the attempt
     * @throws IllegalArgumentException if the composition record is the record of a composed version
     * @throws UncheckedIOException     if the directory of the log cannot be created
     */
    public void recordAttempt(ScopeTransaction transaction, CompositionRecord compositionRecord) {
        Objects.requireNonNull(transaction, "the transaction");
        Objects.requireNonNull(compositionRecord, "the composition record");
        if (compositionRecord.result() == CompositionRecord.Result.COMPOSED) {
            throw new IllegalArgumentException("a composed version is recorded with its text");
        }
        createDirectory(INSTANCE_DIRECTORY);
        transaction.append(COMPOSITION_LOG, line(compositionRecord));
        if (compositionRecord.result() == CompositionRecord.Result.REFUSED) {
            LOGGER.warn(WARN.PLAN_INSTANCE_COMPOSITION_REFUSED, compositionRecord.event().wireName(),
                    scope.scopeId(), compositionRecord.cause().flatMap(CompositionCause::refusal).map(CompositionResult.Refusal::wireName)
                            .orElse("unknown"));
        }
    }

    /**
     * @param version an instance version
     * @return the text of the version, if it was written
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public Optional<String> readVersion(int version) {
        return read(versionPath(version));
    }

    /**
     * @param version an instance version
     * @param unit    the id of a composed unit
     * @return the text of the composed file of the unit, if the version wrote it
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public Optional<String> readUnit(int version, String unit) {
        return read(unitPath(unit, version));
    }

    /**
     * @param digest the SHA-256 of a sub-unit text
     * @return the text, if a version named the digest
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public Optional<String> readSubUnit(String digest) {
        return read(subUnitPath(digest));
    }

    /**
     * The composition record is the composed record of the version: its result, version, event, trigger, input
     * digests and diff summary, the latter against the recorded version before it.
     */
    private boolean recordsComposition(CompositionRecord compositionRecord, PlanInstance instance) {
        var header = instance.header();
        return compositionRecord.result() == CompositionRecord.Result.COMPOSED
                && compositionRecord.version().equals(Optional.of(instance.version()))
                && compositionRecord.event() == header.event()
                && compositionRecord.trigger() == header.event().trigger()
                && compositionRecord.inputDigests().equals(CompositionRecord.inputDigests(header.inputs()))
                && compositionRecord.diffSummary().equals(previousHeader(instance.version())
                .map(previous -> DiffSummary.between(previous, header)));
    }

    /**
     * @return the header of the recorded version before the version, empty for version 1
     * @throws IllegalArgumentException if a version after the first has no recorded version before it
     * @throws IllegalStateException    if the recorded version before it carries no readable header, or a header
     *                                  that fails the schema of the instance header
     */
    private Optional<PlanInstanceHeader> previousHeader(int version) {
        if (version == 1) {
            return Optional.empty();
        }
        var path = versionPath(version - 1);
        var text = read(path).orElseThrow(() -> new IllegalArgumentException(
                "version %d of the plan '%s' follows version %d, which is not recorded".formatted(version,
                        scope.scopeId(), version - 1)));
        var parseResult = WorkflowParser.parse(path, text, UnitOrigin.DEFAULT);
        if (parseResult instanceof ParseResult.Parsed(var definition) && definition.instance().isPresent()) {
            try {
                return Optional.of(HeaderJson.read(definition.instance().orElseThrow().json()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("the recorded version %d of the plan '%s' carries no readable header"
                        .formatted(version - 1, scope.scopeId()), e);
            }
        }
        if (parseResult instanceof ParseResult.Refused(var diagnostic)) {
            throw new IllegalStateException("the recorded version %d of the plan '%s' was refused by the parser: %s"
                    .formatted(version - 1, scope.scopeId(), diagnostic));
        }
        throw new IllegalStateException("the recorded version %d of the plan '%s' carries no readable header"
                .formatted(version - 1, scope.scopeId()));
    }

    private static byte[] line(CompositionRecord compositionRecord) {
        return (compositionRecord.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private boolean exists(String path) {
        return Files.exists(scope.resolve(path), LinkOption.NOFOLLOW_LINKS);
    }

    private void createDirectory(String path) {
        try {
            Files.createDirectories(scope.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException("the directory %s of the plan '%s' cannot be created"
                    .formatted(path, scope.scopeId()), e);
        }
    }

    private Optional<String> read(String path) {
        try {
            return Optional.of(Files.readString(scope.resolve(path), StandardCharsets.UTF_8));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("the file %s of the plan '%s' cannot be read".formatted(path,
                    scope.scopeId()), e);
        }
    }
}
