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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.ClampedValue;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.WorkerState.EndReason;
import de.planmarshall.core.store.WorkerState.Form;
import de.planmarshall.core.store.WorkerState.RoleClass;
import de.planmarshall.core.store.WorkerState.State;
import de.planmarshall.core.store.WorkerState.Workdir;

/**
 * The codec of the job record keeps parity with the schema of Store Schemas § Job Record (PM-EXEC-4): the members of
 * the record and of its typed objects in schema order, the required members, the values of every enumeration, the
 * confinement pattern, the {@code job_id} pattern, the format version and the range of {@code outcome_row}. A member
 * added to or removed from the schema without the code fails here. The schema is the file
 * {@code de/planmarshall/core/store/job-record.schema.json} beside the codec, read from the class path.
 */
// Every datum in this class is a contract: the schema file, its members and bounds are the specification.
@DisplayName("Job record schema parity")
class JobRecordSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/job-record.schema.json";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        schema = SchemaResource.load(SCHEMA);
        properties = SchemaResource.object(schema.get("properties"));
    }

    @Test
    @DisplayName("the members of the record are the members of the schema in schema order, every one required")
    void membersEqualSchema() {
        var expected = names(properties.keySet());

        assertAll("the record members equal the schema's",
                () -> assertFalse(expected.isEmpty(), "the schema declares members"),
                () -> assertEquals("JobRecord", schema.get("title"), "the schema title names the job record"),
                () -> assertEquals(JobRecordCodec.MEMBERS, expected,
                        "the codec members follow the schema order"),
                () -> assertEquals(Set.copyOf(JobRecordCodec.MEMBERS),
                        Set.copyOf(names(SchemaResource.array(schema.get("required")))),
                        "the required members are every member"));
    }

    @Test
    @DisplayName("the members of the typed objects are those of the schema in schema order")
    void objectMembersEqualSchema() {
        var scope = member("scope");
        var derivation = member("budget_derivation");
        var contradiction = member("self_report_contradiction");
        var clamped = SchemaResource.object(member("clamped_values").get("items"));

        assertAll("the typed object members equal the schema's",
                () -> assertEquals(JobRecordCodec.SCOPE_MEMBERS, keys(scope),
                        "the scope members follow the schema order"),
                () -> assertEquals(JobRecordCodec.SCOPE_MEMBERS, required(scope),
                        "every scope member is required"),
                () -> assertEquals(JobRecordCodec.BUDGET_DERIVATION_MEMBERS, keys(derivation),
                        "the budget derivation members follow the schema"),
                () -> assertEquals(List.of("kind"), required(derivation),
                        "only the kind of the derivation is required"),
                () -> assertEquals(JobRecordCodec.CONTRADICTION_MEMBERS, keys(contradiction),
                        "the contradiction members follow the schema"),
                () -> assertEquals(JobRecordCodec.CONTRADICTION_MEMBERS, required(contradiction),
                        "every contradiction member is required"),
                () -> assertEquals(JobRecordCodec.CLAMPED_VALUE_MEMBERS, keys(clamped),
                        "the clamped value members follow the schema"),
                () -> assertEquals(JobRecordCodec.CLAMPED_VALUE_MEMBERS, required(clamped),
                        "every clamped value member is required"));
    }

    @Test
    @DisplayName("the values of every enumeration are those of the schema")
    void enumerationsEqualSchema() {
        var scopeProperties = SchemaResource.object(member("scope").get("properties"));
        var derivationProperties = SchemaResource.object(member("budget_derivation").get("properties"));
        var clampedProperties = SchemaResource.object(
                SchemaResource.object(member("clamped_values").get("items")).get("properties"));

        assertAll("the enumeration values equal the schema's",
                () -> assertEquals(enumOf(properties, "kind"), wireNames(JobId.Kind.values(), JobId.Kind::wireName),
                        "the job id kinds are the schema's kinds"),
                () -> assertEquals(enumOf(properties, "execution"),
                        wireNames(Execution.values(), Execution::wireName),
                        "the executions are the schema's executions"),
                () -> assertEquals(enumOf(scopeProperties, "type"),
                        wireNames(Scope.Type.values(), Scope.Type::wireName),
                        "the scope types are the schema's types"),
                () -> assertEquals(enumOf(properties, "status"), wireNames(Status.values(), Status::wireName),
                        "the statuses are the schema's statuses"),
                () -> assertEquals(enumOf(properties, "fate"), wireNames(Fate.values(), Fate::wireName),
                        "the fates are the schema's fates"),
                () -> assertEquals(enumOf(derivationProperties, "kind"),
                        wireNames(BudgetDerivation.Kind.values(), BudgetDerivation.Kind::wireName),
                        "the derivation kinds are the schema's kinds"),
                () -> assertEquals(enumOf(clampedProperties, "source"),
                        wireNames(ClampedValue.Source.values(), ClampedValue.Source::wireName),
                        "the clamped sources are the schema's sources"));
    }

    @Test
    @DisplayName("the confinement and job id patterns, the format version and the outcome rows are the schema's")
    void patternsEqualSchema() {
        var confinements = Arrays.stream(Confinement.values())
                .map(value -> value.wireName().replace("(", "\\(").replace(")", "\\)"))
                .collect(Collectors.joining("|", "^(", ")$"));
        var prefixes = Arrays.stream(JobId.Kind.values()).map(kind -> String.valueOf(kind.prefix())).sorted()
                .collect(Collectors.joining("", "^[", "]-[0-9a-f]{16}$"));
        var outcomeRow = member("outcome_row");

        assertAll("the patterns and bounds equal the schema's",
                () -> assertEquals(confinements, member("confinement").get("pattern"),
                        "the confinement pattern is the schema's"),
                () -> assertEquals(prefixes, member("job_id").get("pattern"),
                        "the job id pattern is the schema's"),
                () -> assertEquals(JobRecord.FORMAT_VERSION, number(member("format_version").get("const")),
                        "the format version is the schema's const"),
                () -> assertEquals(JobRecord.FIRST_OUTCOME_ROW, number(outcomeRow.get("minimum")),
                        "the first outcome row is the schema's minimum"),
                () -> assertEquals(JobRecord.LAST_OUTCOME_ROW, number(outcomeRow.get("maximum")),
                        "the last outcome row is the schema's maximum"),
                () -> assertEquals(JobRecord.MAX_BUDGET_KEY, number(member("budget_key").get("maxLength")),
                        "the budget key bound is the schema's"));
    }

    @Test
    @DisplayName("the members of the worker block are those of the schema in schema order, every one required")
    void workerMembersEqualSchema() {
        var worker = member("worker");
        var workerProperties = SchemaResource.object(worker.get("properties"));
        var workdir = SchemaResource.object(workerProperties.get("workdir"));
        var mark = SchemaResource.object(workerProperties.get("worktree_mark"));
        var skill = SchemaResource.object(
                SchemaResource.object(workerProperties.get("delivered_skills")).get("items"));

        assertAll("the worker members equal the schema's",
                () -> assertFalse(WorkerStateCodec.MEMBERS.isEmpty(), "the worker block declares members"),
                () -> assertEquals(WorkerStateCodec.MEMBERS, keys(worker),
                        "the worker members follow the schema order"),
                () -> assertEquals(WorkerStateCodec.REQUIRED_MEMBERS, required(worker),
                        "every worker member is required"),
                () -> assertEquals(WorkerStateCodec.WORKDIR_MEMBERS, keys(workdir),
                        "the workdir members follow the schema order"),
                () -> assertEquals(WorkerStateCodec.WORKDIR_MEMBERS, required(workdir),
                        "every workdir member is required"),
                () -> assertEquals(WorkerStateCodec.WORKTREE_MARK_MEMBERS, keys(mark),
                        "the worktree mark members follow the schema"),
                () -> assertEquals(WorkerStateCodec.WORKTREE_MARK_MEMBERS, required(mark),
                        "every worktree mark member is required"),
                () -> assertEquals(WorkerStateCodec.DELIVERED_SKILL_MEMBERS, keys(skill),
                        "the delivered skill members follow the schema"),
                () -> assertEquals(WorkerStateCodec.DELIVERED_SKILL_MEMBERS, required(skill),
                        "every delivered skill member is required"));
    }

    @Test
    @DisplayName("the values of the worker enumerations are those of the schema")
    void workerEnumerationsEqualSchema() {
        var workerProperties = SchemaResource.object(member("worker").get("properties"));
        var workdirProperties = SchemaResource.object(
                SchemaResource.object(workerProperties.get("workdir")).get("properties"));

        assertAll("the worker enumeration values equal the schema's",
                () -> assertEquals(enumOf(workerProperties, "class"),
                        wireNames(RoleClass.values(), RoleClass::wireName),
                        "the role classes are the schema's classes"),
                () -> assertEquals(enumOf(workerProperties, "form"), wireNames(Form.values(), Form::wireName),
                        "the forms are the schema's forms"),
                () -> assertEquals(enumOf(workerProperties, "state"), wireNames(State.values(), State::wireName),
                        "the states are the schema's states"),
                () -> assertEquals(enumOf(workerProperties, "end_reason"),
                        wireNames(EndReason.values(), EndReason::wireName),
                        "the end reasons are the schema's reasons"),
                () -> assertEquals(enumOf(workdirProperties, "kind"),
                        wireNames(Workdir.Kind.values(), Workdir.Kind::wireName),
                        "the workdir kinds are the schema's kinds"));
    }

    @Test
    @DisplayName("the bounds of the worker block are those of the schema")
    void workerBoundsEqualSchema() {
        var workerProperties = SchemaResource.object(member("worker").get("properties"));

        assertAll("the worker bounds equal the schema's",
                () -> assertEquals(WorkerState.MAX_MODEL, number(
                        SchemaResource.object(workerProperties.get("model")).get("maxLength")),
                        "the model bound is the schema's"),
                () -> assertEquals(WorkerState.MAX_HARNESS_ERROR, number(
                        SchemaResource.object(workerProperties.get("harness_error")).get("maxLength")),
                        "the harness error bound is the schema's"),
                () -> assertEquals(0, number(
                        SchemaResource.object(workerProperties.get("idle_wakeups")).get("minimum")),
                        "the idle wakeup minimum is the schema's"));
    }

    private static Map<?, ?> member(String name) {
        return SchemaResource.object(properties.get(name));
    }

    private static List<String> keys(Map<?, ?> object) {
        return names(SchemaResource.object(object.get("properties")).keySet());
    }

    private static List<String> required(Map<?, ?> object) {
        return names(SchemaResource.array(object.get("required")));
    }

    private static Set<String> enumOf(Map<?, ?> owner, String name) {
        return SchemaResource.array(SchemaResource.object(owner.get(name)).get("enum")).stream()
                .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
