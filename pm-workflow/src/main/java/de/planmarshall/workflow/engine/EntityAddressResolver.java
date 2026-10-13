/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.PendingEntryReader;
import de.planmarshall.core.spi.PendingEntryReader.EntryRead;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.TaskEntry;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.AddressResolution.Reason;
import de.planmarshall.workflow.engine.AddressResolution.Refused;
import de.planmarshall.workflow.engine.AddressResolution.Resolved;
import de.planmarshall.workflow.verify.EntityKindRegistry;

/**
 * Resolves an {@link EntityAddress} within the scope it addresses (PM-ARCH-4: an entity is addressed with its scope and
 * resolved from persisted state only; PM-WF-1: the workspace scope hosts the plan and epic bootstraps and its own task
 * entities).
 * <p>
 * The resolution checks the grammar first ({@link AddressResolution.Reason#INVALID_ARGUMENTS}), then the scope: an
 * identifier is resolved only within the scope the address names, and a valid identifier that names nothing there is
 * {@link AddressResolution.Reason#SCOPE_NOT_FOUND}.
 * <ul>
 * <li>An {@code entry_id} names a pending plan or epic bootstrap, an entity of the workspace scope only. It is read
 * through the {@link PendingEntryReader}: a live record resolves to {@value AddressResolution#BOOTSTRAP_PLAN} or
 * {@value AddressResolution#BOOTSTRAP_EPIC} by its kind, a deleted one is {@code scope_not_found}, and one that cannot
 * be read is {@link AddressResolution.Reason#ENTRY_UNREADABLE}, never resolved. Under a plan or epic scope it is
 * {@code scope_not_found} without a read.</li>
 * <li>Any other identifier names a task of the addressed scope's own current task state: in a fan-out state
 * ({@code each}, {@code role each}) the entity the task works on, which keys it in {@code active_tasks}, for example
 * {@code enrich-<module_id>}; in a state that names no scope entity, the {@code queue_task_id} of its one task. It
 * resolves to the registered entity kind of that state.</li>
 * </ul>
 * The resolver writes nothing and logs nothing; the caller reports a refusal.
 *
 * @since 0.1
 */
public final class EntityAddressResolver {

    private static final String PLAN_KIND = "plan";
    private static final String EPIC_KIND = "epic";

    private final PendingEntryReader entries;
    private final EntityKindRegistry entityKinds;

    /**
     * @param entries     the port that reads the pending entry record of a bootstrap
     * @param entityKinds the entity kinds the units of the scopes are verified against
     * @throws NullPointerException if an argument is {@code null}
     */
    public EntityAddressResolver(PendingEntryReader entries, EntityKindRegistry entityKinds) {
        this.entries = Objects.requireNonNull(entries, "entries");
        this.entityKinds = Objects.requireNonNull(entityKinds, "entityKinds");
    }

    /**
     * Resolves an address within its scope.
     *
     * @param address    the address
     * @param hostStatus the status of the addressed scope as the store holds it
     * @param hostUnit   the unit that holds the current state of {@code hostStatus}: the sub-workflow of the top call
     *                   frame, or the top-level unit when there is none
     * @return the resolved entity, or the refusal
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the status is not that of the addressed scope, the unit holds no state of
     *                                  the status, or the task state names an entity kind that is not registered
     */
    public AddressResolution resolve(EntityAddress address, WorkflowStatus hostStatus, WorkflowDefinition hostUnit) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(hostStatus, "hostStatus");
        Objects.requireNonNull(hostUnit, "hostUnit");
        if (address.scopeType() != hostStatus.scopeType()
                || !address.scopeId().equals(Optional.ofNullable(hostStatus.scopeId()))) {
            throw new IllegalArgumentException("the status of the %s scope %s is not that of the addressed scope"
                    .formatted(hostStatus.scopeType().wireName(), hostStatus.scopeId()));
        }
        var entity = address.entity();
        var violation = EntityIdGrammar.violation(entity);
        if (violation.isPresent()) {
            return new Refused(Reason.INVALID_ARGUMENTS, violation.get());
        }
        if (EntityIdGrammar.formOf(entity) == EntityIdGrammar.Form.ENTRY) {
            return resolveEntry(address);
        }
        return resolveTask(address, hostStatus, hostUnit);
    }

    private AddressResolution resolveEntry(EntityAddress address) {
        var entryId = address.entity();
        if (!address.isWorkspace()) {
            return notInScope(address, "an entry is a bootstrap of the workspace scope");
        }
        return switch (entries.read(new PendingEntryReader.ReadRequest(entryId))) {
            case EntryRead.Live(var kind) when PLAN_KIND.equals(kind) ->
                new Resolved(address, AddressResolution.BOOTSTRAP_PLAN, Optional.empty());
            case EntryRead.Live(var kind) when EPIC_KIND.equals(kind) ->
                new Resolved(address, AddressResolution.BOOTSTRAP_EPIC, Optional.empty());
            case EntryRead.Live(var kind) -> new Refused(Reason.ENTRY_UNREADABLE,
                    "the entry %s names the kind '%s', neither plan nor epic".formatted(entryId, kind));
            case EntryRead.Deleted _ -> new Refused(Reason.SCOPE_NOT_FOUND,
                    "the entry %s does not exist: its bootstrap ended or was swept".formatted(entryId));
            case EntryRead.Unreadable(var reason) -> new Refused(Reason.ENTRY_UNREADABLE,
                    "the entry %s could not be read: %s".formatted(entryId, reason));
        };
    }

    private AddressResolution resolveTask(EntityAddress address, WorkflowStatus hostStatus,
            WorkflowDefinition hostUnit) {
        var node = hostUnit.states().get(hostStatus.state());
        if (node == null) {
            throw new IllegalArgumentException("the unit '%s' has no state '%s'".formatted(hostUnit.id(),
                    hostStatus.state()));
        }
        if (!(node.behavior() instanceof StateBehavior.ModelJob modelJob)) {
            return notInScope(address, "its current state '%s' hosts no task".formatted(node.id()));
        }
        var kind = entityKinds.find(modelJob.entityKind()).orElseThrow(() -> new IllegalArgumentException(
                "the task state '%s' names the unregistered entity kind '%s'".formatted(node.id(),
                        modelJob.entityKind())));
        var entity = address.entity();
        Optional<TaskEntry> task;
        if (modelJob.fanOutPrimitiveId().isPresent() || modelJob.rolesPrimitiveId().isPresent()) {
            task = Optional.ofNullable(hostStatus.activeTasks().get(entity));
        } else {
            task = hostStatus.activeTasks().values().stream()
                    .filter(entry -> entry.queueTaskId().equals(entity))
                    .findFirst();
        }
        return task.<AddressResolution>map(entry -> new Resolved(address, kind.name(),
                Optional.of(entry.queueTaskId())))
                .orElseGet(() -> notInScope(address, "the state '%s' holds no such task".formatted(node.id())));
    }

    private static Refused notInScope(EntityAddress address, String why) {
        var scope = address.isWorkspace() ? "the workspace scope"
                : "the %s %s".formatted(address.scopeType().wireName(), address.scopeId().orElseThrow());
        return new Refused(Reason.SCOPE_NOT_FOUND, "%s names nothing in %s: %s".formatted(address.entity(), scope,
                why));
    }
}
