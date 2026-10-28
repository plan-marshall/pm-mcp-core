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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.workflow.ast.CallFrame;
import de.planmarshall.workflow.ast.CallValue;

/**
 * The call stack of a scope: the active sub-workflow call frames, bottom to top (PM-WF-16: a sub-workflow call pushes a
 * frame, its return pops exactly one, and the stack is persisted with the status, so a resting state inside a
 * sub-workflow survives a restart).
 * <p>
 * The value is immutable; {@link #push(CallFrame)} and {@link #pop()} return a new stack. It holds at most
 * {@value #MAX_DEPTH} frames, the depth the status store admits, and converts to and from the frames of the store
 * ({@link #toStore()}, {@link #fromStore(List)}), the entry each frame was entered through included.
 *
 * @param frames the frames, bottom to top
 * @since 0.1
 */
public record CallStack(List<CallFrame> frames) {

    /** The deepest call stack: the store admits no more frames. */
    public static final int MAX_DEPTH = WorkflowStatus.MAX_CALL_DEPTH;

    private static final CallStack EMPTY = new CallStack(List.of());

    /**
     * Copies the frames into an immutable list.
     *
     * @param frames the frames, bottom to top
     * @throws NullPointerException     if the list or a frame is {@code null}
     * @throws IllegalArgumentException if there are more than {@value #MAX_DEPTH} frames
     */
    public CallStack {
        frames = List.copyOf(frames);
        if (frames.size() > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "a call stack holds at most %d frames, not %d".formatted(MAX_DEPTH, frames.size()));
        }
    }

    /** @return the stack of a scope that is in no sub-workflow */
    public static CallStack empty() {
        return EMPTY;
    }

    /**
     * Reads the stack from the frames of the store.
     *
     * @param frames the frames of {@code call_stack}, bottom to top
     * @return the stack
     * @throws NullPointerException if the list or a frame is {@code null}
     * @throws ArithmeticException  if an integer argument does not fit the range of a call value
     */
    public static CallStack fromStore(List<WorkflowStatus.CallFrame> frames) {
        return new CallStack(frames.stream().map(CallStack::fromStoreFrame).toList());
    }

    /** @return the frames as the store keeps them, bottom to top */
    public List<WorkflowStatus.CallFrame> toStore() {
        return frames.stream().map(CallStack::toStoreFrame).toList();
    }

    /** @return the number of frames */
    public int depth() {
        return frames.size();
    }

    /** @return whether the scope is in no sub-workflow */
    public boolean isEmpty() {
        return frames.isEmpty();
    }

    /** @return the frame of the active sub-workflow; empty when the scope is in no sub-workflow */
    public Optional<CallFrame> top() {
        return frames.isEmpty() ? Optional.empty() : Optional.of(frames.getLast());
    }

    /**
     * Pushes the frame of a call.
     *
     * @param frame the frame of the calling state
     * @return the stack with the frame on top
     * @throws NullPointerException  if the frame is {@code null}
     * @throws IllegalStateException if the stack holds {@value #MAX_DEPTH} frames already
     */
    public CallStack push(CallFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (frames.size() == MAX_DEPTH) {
            throw new IllegalStateException("the call of '%s' would exceed the call stack depth %d"
                    .formatted(frame.subWorkflowId(), MAX_DEPTH));
        }
        var pushed = new ArrayList<>(frames);
        pushed.add(frame);
        return new CallStack(pushed);
    }

    /**
     * Pops exactly one frame: the return of a terminal or a step-out.
     *
     * @return the popped frame and the stack below it
     * @throws IllegalStateException if the stack is empty
     */
    public Popped pop() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("the call stack is empty, there is no frame to pop");
        }
        return new Popped(frames.getLast(), new CallStack(frames.subList(0, frames.size() - 1)));
    }

    /**
     * Unwinds every frame at once: the step-out of a spent condition budget, which leaves the call stack empty.
     *
     * @return the unwound frames, top to bottom; empty when the stack is empty
     */
    public List<CallFrame> unwound() {
        return frames.reversed();
    }

    /**
     * One frame popped from a stack.
     *
     * @param frame     the popped frame
     * @param remaining the stack below it
     * @since 0.1
     */
    public record Popped(CallFrame frame, CallStack remaining) {

        /** @throws NullPointerException if a component is {@code null} */
        public Popped {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(remaining, "remaining");
        }
    }

    private static WorkflowStatus.CallFrame toStoreFrame(CallFrame frame) {
        var args = new LinkedHashMap<String, BoundArg>();
        frame.boundArgs().forEach((name, value) -> args.put(name, toBoundArg(value)));
        return new WorkflowStatus.CallFrame(frame.callerWorkflowId(), frame.callerStateId(), frame.callerPhase(),
                frame.subWorkflowId(), frame.entry(), args, Instant.parse(frame.pushedAt()));
    }

    private static CallFrame fromStoreFrame(WorkflowStatus.CallFrame frame) {
        var args = new LinkedHashMap<String, CallValue>();
        frame.boundArgs().forEach((name, value) -> args.put(name, toCallValue(value)));
        return new CallFrame(frame.callerWorkflowId(), frame.callerStateId(), frame.callerPhase(),
                frame.subworkflowId(), frame.entry(), args, frame.pushedAt().toString());
    }

    private static BoundArg toBoundArg(CallValue value) {
        return switch (value) {
            case CallValue.EnumValue(var text) -> new BoundArg.StringArg(text);
            case CallValue.IntValue(var number) -> new BoundArg.IntegerArg(number);
            case CallValue.EnumList(var texts) -> new BoundArg.StringSetArg(new LinkedHashSet<>(texts));
        };
    }

    private static CallValue toCallValue(BoundArg value) {
        return switch (value) {
            case BoundArg.StringArg(var text) -> new CallValue.EnumValue(text);
            case BoundArg.IntegerArg(var number) -> new CallValue.IntValue(Math.toIntExact(number));
            case BoundArg.StringSetArg(var texts) -> new CallValue.EnumList(List.copyOf(texts));
        };
    }
}
