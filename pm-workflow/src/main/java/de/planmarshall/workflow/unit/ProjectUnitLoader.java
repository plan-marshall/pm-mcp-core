/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static de.planmarshall.workflow.log.WorkflowLogMessages.INFO;
import static de.planmarshall.workflow.log.WorkflowLogMessages.WARN;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.core.store.PathConfinement;
import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.StepCompletion;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.UnitFrontmatterReader;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.finalize.FinalizePipelineValidator;
import de.planmarshall.workflow.finalize.FinalizeStepDescriptor;
import de.planmarshall.workflow.finalize.PipelineViolation;
import de.planmarshall.workflow.verify.CompiledInvariants;
import de.planmarshall.workflow.verify.DeclarationBounds;

/**
 * Loads the units a project owns from {@code .marshall/config/} and verifies them before any plan of the project runs
 * (PM-IMPL-5). The load returns either every verified project unit or every finding, each with its file and line;
 * a load with findings returns the {@code config_invalid} refusal ({@link UnitLoadResult#refusal()}) that the assembly
 * ({@code pm-mcp-server} in plan-marshall-mcp) applies at plan start and at resume, and no default unit ever stands in
 * for a refused project unit. Nothing in this module calls the loader on a lifecycle path.
 * <p>
 * The loader is built once per assembly with the inputs of the static verifier, the verified default units (the
 * reference of the invariants), the default finalize descriptors and the names of the project-ownable files; each
 * load takes the project's workspace roots, its declared project step descriptors and the ids of the steps bound to
 * an enrolled script. A load runs these stages and collects every finding:
 * <ol>
 * <li>Read {@code .marshall/config/<file>} for every project-ownable file; every path is confined to the workspace
 * ({@link PathConfinement}), a file above {@value #MAX_UNIT_FILE_BYTES} bytes is refused before it is read whole, a
 * refused path is a {@link UnitFinding.Kind#CONFINEMENT} finding and a missing file a
 * {@link UnitFinding.Kind#MISSING} finding.</li>
 * <li>Parse as a {@link UnitOrigin#PROJECT} unit and refuse a DSL format other than {@value #COMPILED_FORMAT_VERSION}
 * with {@value UnitFinding#FORMAT_OUTDATED} or {@value UnitFinding#FORMAT_NEWER}; a unit whose frontmatter names the
 * author {@value #RESERVED_AUTHOR} after stripping, removing its format characters, NFKC normalisation and case folding
 * is a {@value UnitFinding#AUTHOR_RESERVED} finding at its author line, so a placed unit never passes as a delivered
 * one; a file that declares the identifier
 * of an earlier file, or {@value PlanUnitMerger#PLAN_UNIT}, is a {@value UnitFinding#DUPLICATE_UNIT_ID} finding and
 * never replaces the earlier unit.</li>
 * <li>Merge the phase files into the plan unit ({@link PlanUnitMerger}) and verify every unit with Rules 1-12, Rule 5
 * excluded: a project unit has no released baseline.</li>
 * <li>Check the declarations against their compiled bounds ({@link DeclarationBounds}).</li>
 * <li>Build the project roster, the default descriptors of the steps the plan unit completes plus the declared
 * project steps, and run the {@link FinalizePipelineValidator}; a violation is located at the first state completing
 * the step, or at the unit header.</li>
 * <li>Check the compiled invariants against the default units ({@link CompiledInvariants}) as project replacements
 * ({@link CompiledInvariants.Subject#PROJECT_REPLACEMENT}), so a unit whose type differs from its default unit's, the
 * type {@code instance} included, is refused; a violation that only re-reports a finding of the earlier stages is not
 * listed again. A project unit is a template whose lane is unknown, so the check runs without a lane and INV-INIT-2
 * requires the {@code light -> deep} ratchet.</li>
 * <li>Report {@value UnitFinding#PROJECT_STEP_UNBOUND} where the unit, the declared steps and the bound step ids
 * disagree.</li>
 * </ol>
 * Every finding is logged as WARN {@code PROJECT_UNIT_REFUSED}; a load without findings logs INFO
 * {@code PROJECT_UNITS_VERIFIED}.
 *
 * @since 0.1
 */
public final class ProjectUnitLoader {

    /** The directory of the project-owned units, relative to the project root. */
    public static final String CONFIG_DIRECTORY = ".marshall/config/";

    /** The DSL format this release compiles. */
    public static final int COMPILED_FORMAT_VERSION = 1;

    /** The prefix of the identifier of a project-local finalize step. */
    public static final String PROJECT_STEP_PREFIX = "project:";

    /** The author only a unit compiled into the binary names; a project unit that names it is refused. */
    public static final String RESERVED_AUTHOR = "plan-marshall";

    /** The most bytes a project unit file may hold; a larger file is refused before it is read whole. */
    public static final int MAX_UNIT_FILE_BYTES = 1_048_576;

    /** The line that opens and closes the frontmatter of a unit. */
    private static final String FRONTMATTER_DELIMITER = "---";

    /** The key of the frontmatter line that names the author. */
    private static final String AUTHOR_KEY = "author:";

    private static final CuiLogger LOGGER = new CuiLogger(ProjectUnitLoader.class);

    private final VerificationContext context;

    private final Map<String, WorkflowDefinition> defaultUnits;

    private final List<FinalizeStepDescriptor> defaultRoster;

    private final List<String> unitFiles;

    /**
     * @param context       the inputs of the static verifier
     * @param defaultUnits  the verified default units by identifier, the reference of the invariants
     * @param defaultRoster the descriptors of the default finalize steps
     * @param unitFiles     the names of the project-ownable unit files; a name of {@link PlanUnitMerger#PHASE_FILES}
     *                      is a part of the plan unit
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    public ProjectUnitLoader(VerificationContext context, Map<String, WorkflowDefinition> defaultUnits,
            List<FinalizeStepDescriptor> defaultRoster, List<String> unitFiles) {
        this.context = Objects.requireNonNull(context, "context");
        this.defaultUnits = Map.copyOf(defaultUnits);
        this.defaultRoster = List.copyOf(defaultRoster);
        this.unitFiles = List.copyOf(unitFiles);
    }

    /**
     * Loads and verifies the units of one project. The result carries the refusal the assembly applies at plan start
     * and at resume when it holds a finding; the load itself refuses no lifecycle.
     *
     * @param projectId    the project, named in the log
     * @param roots        the enrolled root of the project and its live linked worktrees
     * @param projectSteps the declared project step descriptors
     * @param boundStepIds the ids of the project steps bound to an enrolled project script
     * @return the verified project units by identifier, the plan unit first, or every finding
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    public UnitLoadResult load(String projectId, WorkspaceRoots roots, List<FinalizeStepDescriptor> projectSteps,
            Set<String> boundStepIds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(roots, "roots");
        var steps = List.copyOf(projectSteps);
        var bound = Set.copyOf(boundStepIds);
        var findings = new ArrayList<UnitFinding>();
        var units = parseUnits(roots, findings);
        var subUnits = units.values().stream().filter(unit -> !PlanUnitMerger.PLAN_UNIT.equals(unit.id())).toList();
        var invariants = new CompiledInvariants(context.registry(), defaultUnits.values(), defaultRoster,
                CompiledInvariants.Subject.PROJECT_REPLACEMENT);
        for (var unit : units.values()) {
            var rules = context.validator(subUnits, List.of()).verify(unit);
            rules.violations().forEach(violation -> findings.add(UnitFinding.at(UnitFinding.Kind.RULE,
                    violation.rule().name(), violation.location(), violation.message())));
            DeclarationBounds.check(unit).forEach(finding -> findings.add(UnitFinding.at(
                    UnitFinding.Kind.DECLARATION, finding.code(), finding.location(),
                    "'%s' (bound %s): %s".formatted(finding.name(), finding.bound(), finding.message()))));
            var pipeline = PlanUnitMerger.PLAN_UNIT.equals(unit.id())
                    ? FinalizePipelineValidator.validate(roster(unit, steps)) : List.<PipelineViolation>of();
            pipeline.forEach(violation -> findings.add(UnitFinding.at(UnitFinding.Kind.PIPELINE,
                    violation.rule().name(), completingStateOrHeader(unit, violation.stepName()),
                    "step '%s': %s".formatted(violation.stepName(), violation.message()))));
            invariants.check(unit, rules, pipeline, Optional.empty()).stream()
                    .filter(violation -> !CompiledInvariants.reReports(violation, unit, rules))
                    .forEach(violation -> findings.add(UnitFinding.at(UnitFinding.Kind.INVARIANT,
                            violation.id().wireId(), violation.location(), violation.message())));
            if (PlanUnitMerger.PLAN_UNIT.equals(unit.id())) {
                checkProjectSteps(unit, steps, bound, findings);
            }
        }
        if (findings.isEmpty()) {
            LOGGER.info(INFO.PROJECT_UNITS_VERIFIED, projectId, units.size());
            return UnitLoadResult.verified(units);
        }
        findings.forEach(finding -> LOGGER.warn(WARN.PROJECT_UNIT_REFUSED, finding.file(), finding.line(),
                finding.code()));
        return UnitLoadResult.refused(findings);
    }

    /**
     * Stages 1 to 3 up to the merge: reads, parses and format-checks every file and merges the phase files.
     *
     * @return the units that passed, the plan unit first
     */
    private Map<String, WorkflowDefinition> parseUnits(WorkspaceRoots roots, List<UnitFinding> findings) {
        var parts = new LinkedHashMap<String, WorkflowDefinition>();
        var subUnits = new LinkedHashMap<String, WorkflowDefinition>();
        var planFile = CONFIG_DIRECTORY + PlanUnitMerger.PHASE_FILES.getFirst();
        for (var file : unitFiles) {
            var path = CONFIG_DIRECTORY + file;
            read(roots, path, findings).flatMap(source -> parse(path, source, findings)).ifPresent(unit -> {
                if (PlanUnitMerger.PHASE_FILES.contains(file)) {
                    parts.put(file, unit);
                } else {
                    UnitFinding.putUnique(subUnits, unit, planFile, findings);
                }
            });
        }
        var units = new LinkedHashMap<String, WorkflowDefinition>();
        var planFiles = unitFiles.stream().filter(PlanUnitMerger.PHASE_FILES::contains).toList();
        if (!planFiles.isEmpty() && parts.size() == planFiles.size()) {
            var ordered = PlanUnitMerger.PHASE_FILES.stream().filter(parts::containsKey).map(parts::get).toList();
            var merge = PlanUnitMerger.merge(ordered);
            findings.addAll(merge.findings());
            merge.unit().ifPresent(plan -> units.put(plan.id(), plan));
        }
        units.putAll(subUnits);
        return units;
    }

    private static Optional<String> read(WorkspaceRoots roots, String path, List<UnitFinding> findings) {
        return switch (PathConfinement.confine(roots, path)) {
            case ConfinementOutcome.Rejected rejected -> {
                findings.add(new UnitFinding(UnitFinding.Kind.CONFINEMENT, rejected.code(), path, 0,
                        "the unit file '%s' lies outside the workspace (%s)".formatted(path, rejected.reason())));
                yield Optional.empty();
            }
            case ConfinementOutcome.Confined(var confinedPath) -> {
                try {
                    var size = Files.size(confinedPath);
                    if (size > MAX_UNIT_FILE_BYTES) {
                        findings.add(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_TOO_LARGE, path, 0,
                                "the project unit '%s' holds %d bytes, above the bound of %d".formatted(path, size,
                                        MAX_UNIT_FILE_BYTES)));
                        yield Optional.empty();
                    }
                    yield Optional.of(Files.readString(confinedPath));
                } catch (NoSuchFileException _) {
                    findings.add(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_MISSING, path, 0,
                            "the project unit '%s' does not exist".formatted(path)));
                    yield Optional.empty();
                } catch (IOException e) {
                    findings.add(new UnitFinding(UnitFinding.Kind.MISSING, UnitFinding.UNIT_UNREADABLE, path, 0,
                            "the project unit '%s' cannot be read: %s".formatted(path, e.getMessage())));
                    yield Optional.empty();
                }
            }
        };
    }

    private static Optional<WorkflowDefinition> parse(String path, String source, List<UnitFinding> findings) {
        checkAuthor(path, source, findings);
        return switch (WorkflowParser.parse(path, source, UnitOrigin.PROJECT)) {
            case ParseResult.Refused(var diagnostic) -> {
                findings.add(new UnitFinding(UnitFinding.Kind.PARSE, diagnostic.rule().name(), path,
                        diagnostic.line(), diagnostic.message()));
                yield Optional.empty();
            }
            case ParseResult.Parsed(var unit) -> {
                if (unit.formatVersion() == COMPILED_FORMAT_VERSION) {
                    yield Optional.of(unit);
                }
                var code = unit.formatVersion() < COMPILED_FORMAT_VERSION ? UnitFinding.FORMAT_OUTDATED
                        : UnitFinding.FORMAT_NEWER;
                findings.add(UnitFinding.at(UnitFinding.Kind.FORMAT, code, unit.location(),
                        "'%s' declares format %d, this release compiles format %d".formatted(path,
                                unit.formatVersion(), COMPILED_FORMAT_VERSION)));
                yield Optional.empty();
            }
        };
    }

    /**
     * Stage 2, the author: a project unit whose author equals {@value #RESERVED_AUTHOR} after stripping its surrounding
     * whitespace, removing its format characters, NFKC normalising it and folding its case is a
     * {@value UnitFinding#AUTHOR_RESERVED} finding at its author line; the unit is verified on, so the load reports
     * every other finding of it as well. The author is read from the raw head of the source before the parse, so a
     * unit whose frontmatter is refused for a format character is still reported as naming the reserved author.
     */
    private static void checkAuthor(String path, String source, List<UnitFinding> findings) {
        var author = rawAuthor(source);
        if (author.isPresent() && RESERVED_AUTHOR.equals(normalised(author.get()))) {
            findings.add(new UnitFinding(UnitFinding.Kind.RULE, UnitFinding.AUTHOR_RESERVED, path,
                    authorLine(source, 1),
                    "the project unit '%s' names the author '%s', which only a delivered unit carries"
                            .formatted(path, RESERVED_AUTHOR)));
        }
    }

    /**
     * @param source the text of the unit file
     * @return the author value of the frontmatter, read from the raw source as the reader would read it, without the
     *         reader's refusals
     */
    private static Optional<String> rawAuthor(String source) {
        var iterator = source.lines().iterator();
        if (!iterator.hasNext() || !FRONTMATTER_DELIMITER.equals(iterator.next().strip())) {
            return Optional.empty();
        }
        var lines = 1;
        while (iterator.hasNext() && ++lines <= UnitFrontmatterReader.MAX_FRONTMATTER_LINES) {
            var line = iterator.next();
            if (FRONTMATTER_DELIMITER.equals(line.strip())) {
                return Optional.empty();
            }
            if (line.startsWith(AUTHOR_KEY)) {
                return Optional.of(rawValue(line.substring(AUTHOR_KEY.length())));
            }
        }
        return Optional.empty();
    }

    /**
     * @param raw the raw value of an author line
     * @return the value, without its surrounding whitespace and, for a double-quoted value, without the quotes and
     *         with the reader's two escapes resolved
     */
    private static String rawValue(String raw) {
        var value = raw.strip();
        if (value.length() < 2 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            return value;
        }
        var unquoted = new StringBuilder();
        for (var index = 1; index < value.length() - 1; index++) {
            var character = value.charAt(index);
            if (character == '\\' && index + 1 < value.length() - 1
                    && (value.charAt(index + 1) == '"' || value.charAt(index + 1) == '\\')) {
                unquoted.append(value.charAt(++index));
            } else {
                unquoted.append(character);
            }
        }
        return unquoted.toString();
    }

    /**
     * @param author the author of a frontmatter
     * @return the author with its surrounding whitespace stripped, its format characters removed, NFKC normalised and
     *         its case folded, the form the reserved author is compared on
     */
    private static String normalised(String author) {
        var withoutFormat = new StringBuilder();
        author.codePoints().filter(codePoint -> Character.getType(codePoint) != Character.FORMAT)
                .forEach(withoutFormat::appendCodePoint);
        return Normalizer.normalize(withoutFormat.toString().strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * @param source  the text of the unit file
     * @param opening the line of the opening {@code ---} of the frontmatter, counted from 1
     * @return the line of the {@code author} key of the frontmatter, counted from 1; the opening line when there is
     *         none
     */
    private static int authorLine(String source, int opening) {
        var lines = source.lines().toList();
        for (var index = opening; index < lines.size(); index++) {
            var line = lines.get(index);
            if (FRONTMATTER_DELIMITER.equals(line.strip())) {
                break;
            }
            if (line.startsWith(AUTHOR_KEY)) {
                return index + 1;
            }
        }
        return opening;
    }

    /**
     * @return the default descriptors of the steps the plan unit completes, in roster order, then the declared
     *         project steps
     */
    private List<FinalizeStepDescriptor> roster(WorkflowDefinition plan, List<FinalizeStepDescriptor> projectSteps) {
        var completed = completedSteps(plan);
        var roster = new ArrayList<FinalizeStepDescriptor>();
        defaultRoster.stream().filter(step -> completed.contains(step.name())).forEach(roster::add);
        roster.addAll(projectSteps);
        return roster;
    }

    private static Set<String> completedSteps(WorkflowDefinition unit) {
        var steps = new LinkedHashSet<String>();
        completions(unit).forEach(completion -> steps.add(completion.stepId()));
        return steps;
    }

    private static List<StepCompletion> completions(WorkflowDefinition unit) {
        var completions = new ArrayList<StepCompletion>();
        for (var state : unit.states().values()) {
            transitions(state).forEach(transition -> completions.addAll(transition.completions()));
        }
        return completions;
    }

    /**
     * @return the transitions of the state, those of its declared links included, in declaration order
     */
    private static List<OutcomeTransition> transitions(StateNode state) {
        var transitions = new ArrayList<OutcomeTransition>();
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto -> transitions.addAll(auto.transitions());
            case StateBehavior.JobStep job -> transitions.addAll(job.transitions());
            case StateBehavior.ModelJob job -> transitions.addAll(job.transitions());
            case StateBehavior.SubWorkflowCall call -> transitions.addAll(call.transitions());
            case StateBehavior.QuestionWait question -> {
                transitions.addAll(question.transitions());
                question.links().forEach(link -> transitions.addAll(link.transitions()));
            }
            case StateBehavior.InteractiveLinks(var links) ->
                links.forEach(link -> transitions.addAll(link.transitions()));
            case StateBehavior.TerminalState _ -> {
                // a terminal state has no transition
            }
        }
        return transitions;
    }

    /**
     * @return the location of the first state, in declaration order, with a transition that completes the step, or
     *         the unit header
     */
    private static SourceLocation completingStateOrHeader(WorkflowDefinition unit, String step) {
        return unit.states().values().stream()
                .filter(state -> completes(state, step))
                .map(StateNode::location)
                .findFirst()
                .orElse(unit.location());
    }

    private static boolean completes(StateNode state, String step) {
        return transitions(state).stream()
                .anyMatch(transition -> transition.completions().stream()
                        .anyMatch(completion -> step.equals(completion.stepId())));
    }

    /**
     * Stage 7: the project steps the unit completes, the declared steps and the bound step ids must agree.
     */
    private static void checkProjectSteps(WorkflowDefinition plan, List<FinalizeStepDescriptor> projectSteps,
            Set<String> boundStepIds, List<UnitFinding> findings) {
        var declared = new LinkedHashSet<String>();
        projectSteps.forEach(step -> declared.add(step.name()));
        var reported = new LinkedHashSet<String>();
        for (var completion : completions(plan)) {
            var step = completion.stepId();
            if (step.startsWith(PROJECT_STEP_PREFIX) && !declared.contains(step) && reported.add(step)) {
                findings.add(UnitFinding.at(UnitFinding.Kind.PROJECT_STEP_UNBOUND, UnitFinding.PROJECT_STEP_UNBOUND,
                        completion.location(), "the unit completes the project step '%s', which is not declared"
                        .formatted(step)));
            }
        }
        var completed = completedSteps(plan);
        for (var step : declared) {
            if (!boundStepIds.contains(step)) {
                findings.add(UnitFinding.at(UnitFinding.Kind.PROJECT_STEP_UNBOUND, UnitFinding.PROJECT_STEP_UNBOUND,
                        completingStateOrHeader(plan, step),
                        "the project step '%s' is bound to no enrolled project script".formatted(step)));
            }
            if (!completed.contains(step)) {
                findings.add(UnitFinding.at(UnitFinding.Kind.PROJECT_STEP_UNBOUND, UnitFinding.PROJECT_STEP_UNBOUND,
                        plan.location(), "the declared project step '%s' is completed by no state of the unit"
                        .formatted(step)));
            }
        }
    }
}
