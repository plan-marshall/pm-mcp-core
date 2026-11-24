/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.ServedModels;

/**
 * One task attempt's watch of the model that answered its turns (harness-profiles/01, The Model That Answered).
 * <p>
 * The watch holds the launch model, the comparison that decides whether two identifiers name one model, and the models
 * the attempt has seen. A model that differs from the launch model and from the previous turn's model is a change, and
 * the caller records it once; a return to the launch model records nothing, and the model still enters the list, so a
 * fallback that switches back is recorded rather than only the last line. The models seen are the attempt's
 * {@link ServedModels}, in first-appearance order.
 * <p>
 * A watch starts with the previous model equal to the launch model, so a warm worker that stays on a fallback model
 * shows the change in each task's journal. {@link #restart()} begins the next attempt of the same task.
 * <p>
 * The comparison is a seam: the default treats a launch alias and the full identifier it stands for as one model (the
 * release plus a trailing snapshot suffix of eight digits or an ISO date), so that an alias writes no entry and a
 * switch from one release to another is seen; the assembly may supply its own rule, for example one that resolves an
 * alias through the harness's {@code models_probe} listing.
 *
 * @since 0.1
 */
public final class ServedModelWatch {

    /** The snapshot suffix a harness appends to a release: eight digits or an ISO date, after a hyphen. */
    private static final Pattern SNAPSHOT_SUFFIX = Pattern.compile("-\\d{8}|-\\d{4}-\\d{2}-\\d{2}");

    private final @Nullable String launchModel;
    private final BiPredicate<String, String> sameModel;
    private @Nullable String previous;
    private final LinkedHashSet<String> served = new LinkedHashSet<>();

    private ServedModelWatch() {
        this.launchModel = null;
        this.sameModel = ServedModelWatch::sameModel;
        this.previous = null;
    }

    /**
     * @param launchModel the model the task was launched with
     */
    public ServedModelWatch(String launchModel) {
        this(launchModel, ServedModelWatch::sameModel);
    }

    /**
     * @param launchModel the model the task was launched with
     * @param sameModel   the comparison that decides whether two identifiers name one model
     */
    public ServedModelWatch(String launchModel, BiPredicate<String, String> sameModel) {
        this.launchModel = Objects.requireNonNull(launchModel, "launchModel");
        this.sameModel = Objects.requireNonNull(sameModel, "sameModel");
        this.previous = launchModel;
    }

    /**
     * @return a watch that records nothing, for a caller that names no launch model
     */
    public static ServedModelWatch recordingNothing() {
        return new ServedModelWatch();
    }

    /**
     * Begins the next attempt of the same task: the previous model is the launch model again and the models seen start
     * over. A watch that records nothing stays silent.
     */
    public void restart() {
        if (launchModel != null) {
            previous = launchModel;
            served.clear();
        }
    }

    /**
     * Feeds one reported model.
     *
     * @param model the model the line reported, or {@code null} when it reported none
     * @return the change to record, or empty when the model is the launch model, the previous model, or nothing was
     *         reported
     */
    public Optional<Difference> see(@Nullable String model) {
        if (launchModel == null || model == null || model.isEmpty()) {
            return Optional.empty();
        }
        served.add(model);
        if (sameModel.test(launchModel, model)) {
            previous = launchModel;
            return Optional.empty();
        }
        var last = previous == null ? launchModel : previous;
        if (sameModel.test(last, model)) {
            return Optional.empty();
        }
        previous = model;
        return Optional.of(new Difference(launchModel, model));
    }

    /**
     * @return the models this attempt has seen in first-appearance order, or {@code unmeasured} when none was reported
     */
    public ServedModels served() {
        return served.isEmpty() ? ServedModels.unmeasured() : ServedModels.of(List.copyOf(served));
    }

    /**
     * The default comparison: two equal identifiers name one model, and so do a launch model and the same model with a
     * trailing snapshot suffix.
     *
     * @param launch the model the task was launched with
     * @param served the model a turn reported
     * @return whether the two identifiers name one model
     */
    public static boolean sameModel(String launch, String served) {
        if (launch.equals(served)) {
            return true;
        }
        return served.length() > launch.length() && served.startsWith(launch)
                && SNAPSHOT_SUFFIX.matcher(served.substring(launch.length())).matches();
    }

    /**
     * A model that differs from the one the task was launched with.
     *
     * @param launch the model the task was launched with
     * @param served the model that answered the turn
     * @since 0.1
     */
    public record Difference(String launch, String served) {
    }
}
