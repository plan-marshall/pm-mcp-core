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
 * The watch of the model that answered a worker generation's lines (harness-profiles/01, The Model That Answered).
 * <p>
 * The watch holds the launch model, the comparison that decides whether two identifiers name one model, and the models
 * the current attempt has seen. A model that differs from the launch model and from the previous turn's model is a
 * change, and the caller records it once; a return to the launch model records nothing, and the model still enters the
 * list, so a fallback that switches back is recorded rather than only the last line. The models seen are the attempt's
 * {@link ServedModels}, in first-appearance order.
 * <p>
 * The launch model is the worker generation's and does not change, while one generation serves task after task. A watch
 * therefore belongs to one generation and is restarted at every attempt, with the previous model equal to the launch
 * model again, so a warm worker that stays on a fallback model shows the change in each task's journal.
 * <p>
 * The comparison is a seam: the default treats two equal identifiers as one model and an identifier and the same model
 * with a trailing snapshot suffix of eight digits or an ISO date as one model, in either order; two different releases
 * stay two models, and a switch between them is a change. The assembly may supply its own rule, for example one that
 * resolves an alias through the harness's {@code models_probe} listing.
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
     * Begins the next attempt: the previous model is the launch model again and the models seen start over. The launch
     * model is the worker generation's and stays, so one watch serves the attempts of one generation. A watch that
     * records nothing stays silent.
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
     * @return the change to record, or empty when the model is the launch model, the previous model, nothing was
     *         reported, or the value is not an identifier of the column's length
     */
    public Optional<Difference> see(@Nullable String model) {
        if (launchModel == null || model == null || model.isEmpty() || model.length() > ServedModels.MAX_MODEL) {
            return Optional.empty();
        }
        served.add(model);
        var last = previous == null ? launchModel : previous;
        if (sameModel.test(last, model)) {
            previous = model;
            return Optional.empty();
        }
        if (sameModel.test(launchModel, model) && !sameModel.test(launchModel, last)) {
            // Back in the launch family from another model: a return records nothing, the reported release still wins.
            previous = model;
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
     * The default comparison: two equal identifiers name one model, and so does an identifier and the same model with a
     * trailing snapshot suffix, in either order — a launch alias and the release it was reported as are one model, and
     * so are the two spellings of one release. Two different releases stay two models.
     *
     * @param first  one identifier
     * @param second the other identifier
     * @return whether the two identifiers name one model
     */
    public static boolean sameModel(String first, String second) {
        return first.equals(second) || carriesSnapshotOf(second, first) || carriesSnapshotOf(first, second);
    }

    private static boolean carriesSnapshotOf(String candidate, String base) {
        return candidate.length() > base.length() && candidate.startsWith(base)
                && SNAPSHOT_SUFFIX.matcher(candidate.substring(base.length())).matches();
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
