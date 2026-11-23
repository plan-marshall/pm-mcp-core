/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.jspecify.annotations.Nullable;

/**
 * The native variant of the git contract on JGit.
 * <p>
 * Every repository is opened with a {@link RefusingFS}, so no operation starts a process; an
 * operation that would run a hook or a filter driver ends {@link GitOutcome#CAPABILITY_MISSING}.
 * Commits are never signed. Transport operations contact only remotes the {@link RemotePolicy}
 * admits, checked against the effective URL after {@code insteadOf} rewriting, before anything is
 * sent.
 *
 * @since 0.1
 */
public final class JGitOperations implements GitOperations {

    /** The compiled transport timeout of {@code git.fetch-commit}, in seconds: connect and every read. */
    public static final int FETCH_TIMEOUT_SECONDS = 60;

    /** The refs that keep the pinned commits of a cache repository reachable. */
    static final String PINNED_REFS = "refs/pinned/";

    /** git's {@code upload-pack} refusing a want it does not serve, lower case. */
    private static final String NOT_OUR_REF = "not our ref";

    /** The detail of {@link GitOutcome#NOTHING_TO_COMMIT}. */
    private static final String NO_CHANGES = "no changes";

    /** The detail of a refused abbreviated start point or log revision. */
    private static final String ABBREVIATED_COMMIT_ID = "abbreviated commit id: ";

    /** A remote refusing a want by its object id: JGit's {@code UploadPack} or a remote reporting it absent. */
    private static final Pattern WANT_REFUSED =
            Pattern.compile("(?i)\\bwant [0-9a-f]{40}(?:[0-9a-f]{24})? not (?:valid|found)\\b");

    private final FS fs;
    private final RemotePolicy remotePolicy;

    /**
     * @param remotePolicy the remotes the transport may contact
     */
    public JGitOperations(RemotePolicy remotePolicy) {
        this.fs = new RefusingFS();
        this.remotePolicy = Objects.requireNonNull(remotePolicy, "remotePolicy");
    }

    /**
     * Initializes an empty repository with the refusing file system (setup and tests).
     *
     * @param root          the worktree root to create
     * @param initialBranch the initial branch name
     * @return {@code OK} with the repository facts, or {@code FAILED}
     */
    public GitResult<RepositoryInfo> init(Path root, String initialBranch) {
        try (Git git = Git.init().setDirectory(root.toFile()).setInitialBranch(initialBranch).setFs(fs).call()) {
            return GitResult.ok(info(git.getRepository()));
        } catch (GitAPIException | IOException | JGitInternalException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<RepositoryInfo> open(Path root) {
        try (Repository repository = openRepository(root)) {
            return GitResult.ok(info(repository));
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, root.toString());
        } catch (IOException | IllegalArgumentException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<WorktreeSha> worktreeSha(Path root) {
        String step = "open";
        try (Repository repository = openRepository(root)) {
            step = "head";
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                return GitResult.of(GitOutcome.UNAVAILABLE, "head: unresolvable HEAD");
            }
            step = "status";
            Status status = Git.wrap(repository).status().call();
            var paths = new TreeSet<String>();
            paths.addAll(status.getAdded());
            paths.addAll(status.getChanged());
            paths.addAll(status.getModified());
            paths.addAll(status.getRemoved());
            paths.addAll(status.getMissing());
            paths.addAll(status.getUntracked());
            paths.addAll(status.getConflicting());
            step = "read";
            String digest = WorktreeShaDigest.digest(head.name(), repository.getWorkTree().toPath(), paths);
            return GitResult.ok(new WorktreeSha(digest, WorktreeSha.CURRENT_VERSION));
        } catch (GitAPIException | IOException | JGitInternalException | IllegalArgumentException e) {
            return GitResult.of(GitOutcome.UNAVAILABLE, step + ": " + e.getClass().getName());
        }
    }

    @Override
    public GitResult<Worktree> worktreeAdd(WorktreeAddInput input) {
        Path target;
        try {
            Path root = input.root().toRealPath();
            target = canonical(input.path());
            if (!target.startsWith(root) || target.equals(root)) {
                return GitResult.of(GitOutcome.PATH_REFUSED, input.path() + " is not inside " + root);
            }
            if (Files.exists(target) && !isEmptyDirectory(target)) {
                return GitResult.of(GitOutcome.ALREADY_EXISTS, target.toString());
            }
        } catch (IOException e) {
            return failure(e);
        }
        try (Repository main = openRepository(input.root())) {
            return addWorktree(main, input, target);
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, input.root().toString());
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (IOException | JGitInternalException | IllegalArgumentException e) {
            return failure(e);
        }
    }

    private GitResult<Worktree> addWorktree(Repository main, WorktreeAddInput input, Path target) throws IOException {
        Path commonDir = main.getCommonDirectory().toPath().toRealPath();
        Optional<String> branchRef = Optional.ofNullable(input.branch()).map(b -> Constants.R_HEADS + b);
        var resolution = GitIdentity.resolution(main, input.startPoint());
        if (GitIdentity.isAmbiguous(resolution)) {
            return GitResult.of(GitOutcome.GIT_IDENTITY_AMBIGUOUS, input.startPoint());
        }
        if (resolution == GitIdentity.Resolution.ABBREVIATED_COMMIT_ID) {
            return GitResult.of(GitOutcome.UNKNOWN_REVISION, ABBREVIATED_COMMIT_ID + input.startPoint());
        }
        var resolved = branchRef.isPresent() && !input.createBranch()
                ? existingBranchStart(main, commonDir, Objects.requireNonNull(input.branch()), branchRef.get())
                : newStart(main, input, branchRef.orElse(null));
        if (resolved.refusal() != null) {
            return Objects.requireNonNull(resolved.refusal(), "refusal");
        }
        if (branchRef.isPresent() && !input.createBranch() && GitIdentity.isFullCommitId(input.startPoint())
                && !resolved.id().name().equalsIgnoreCase(input.startPoint())) {
            return GitResult.of(GitOutcome.GIT_IDENTITY_MISMATCH,
                    Objects.requireNonNull(input.branch()) + " is at " + resolved.id().name() + ", not "
                            + input.startPoint());
        }
        ObjectId start = resolved.id();
        Files.createDirectories(target);
        Path admin = LinkedWorktrees.create(commonDir, target, branchRef.orElse(start.name()));
        try (Repository linked = openRepository(target); RevWalk walk = new RevWalk(linked)) {
            RevCommit commit = walk.parseCommit(start);
            var checkout = new DirCacheCheckout(linked, linked.lockDirCache(), commit.getTree());
            checkout.setFailOnConflict(true);
            checkout.checkout();
            LinkedWorktrees.unlock(admin);
            return GitResult.ok(Worktree.builder()
                    .path(target)
                    .name(admin.getFileName().toString())
                    .branch(input.branch())
                    .head(start.name())
                    .main(false)
                    .locked(false)
                    .prunable(false)
                    .build());
        } catch (IOException | JGitInternalException e) {
            LinkedWorktrees.delete(commonDir, admin.getFileName().toString(), target);
            throw e;
        }
    }

    /** The commit a new worktree starts at, or the refusal that ends the operation. */
    private record StartPoint(@Nullable ObjectId id, @Nullable GitResult<Worktree> refusal) {

        static StartPoint of(ObjectId id) {
            return new StartPoint(id, null);
        }

        static StartPoint refused(GitOutcome outcome, String detail) {
            return new StartPoint(null, GitResult.of(outcome, detail));
        }
    }

    private static StartPoint existingBranchStart(Repository main, Path commonDir, String branch, String branchRef)
            throws IOException {
        Ref existing = main.exactRef(branchRef);
        if (existing == null) {
            return StartPoint.refused(GitOutcome.UNKNOWN_REVISION, branchRef);
        }
        if (checkedOut(main, commonDir, branch)) {
            return StartPoint.refused(GitOutcome.BRANCH_CHECKED_OUT, branch);
        }
        return StartPoint.of(existing.getObjectId());
    }

    private static StartPoint newStart(Repository main, WorktreeAddInput input, @Nullable String branchRef)
            throws IOException {
        var resolution = GitIdentity.resolution(main, input.startPoint());
        if (GitIdentity.isAmbiguous(resolution)) {
            return StartPoint.refused(GitOutcome.GIT_IDENTITY_AMBIGUOUS, input.startPoint());
        }
        if (resolution == GitIdentity.Resolution.ABBREVIATED_COMMIT_ID) {
            return StartPoint.refused(GitOutcome.UNKNOWN_REVISION, ABBREVIATED_COMMIT_ID + input.startPoint());
        }
        ObjectId start = main.resolve(input.startPoint() + "^{commit}");
        if (start == null) {
            return StartPoint.refused(GitOutcome.UNKNOWN_REVISION, input.startPoint());
        }
        if (resolution == GitIdentity.Resolution.FULL_COMMIT_ID && !start.name().equalsIgnoreCase(input.startPoint())) {
            return StartPoint.refused(GitOutcome.GIT_IDENTITY_MISMATCH,
                    "the pin " + input.startPoint() + " reached " + start.name());
        }
        if (branchRef != null) {
            if (main.exactRef(branchRef) != null) {
                return StartPoint.refused(GitOutcome.ALREADY_EXISTS, branchRef);
            }
            var update = main.updateRef(branchRef);
            update.setNewObjectId(start);
            update.setExpectedOldObjectId(ObjectId.zeroId());
            update.setRefLogMessage("branch: Created from " + input.startPoint(), false);
            Result result = update.update();
            if (result != Result.NEW) {
                return StartPoint.refused(GitOutcome.FAILED, "branch creation: " + result);
            }
        }
        return StartPoint.of(start);
    }

    private static boolean checkedOut(Repository main, Path commonDir, String branch) throws IOException {
        if (!main.isBare() && branch.equals(main.getBranch())) {
            return true;
        }
        return LinkedWorktrees.list(commonDir).stream().anyMatch(w -> branch.equals(w.branch()));
    }

    @Override
    public GitResult<List<Worktree>> worktreeList(Path root) {
        try (Repository repository = openRepository(root)) {
            Path commonDir = repository.getCommonDirectory().toPath().toRealPath();
            var worktrees = new ArrayList<Worktree>();
            try (Repository main = mainRepository(commonDir)) {
                Optional<ObjectId> head = Optional.ofNullable(main.resolve(Constants.HEAD));
                Ref headRef = main.exactRef(Constants.HEAD);
                Optional<String> branch = headRef != null && headRef.isSymbolic()
                        ? Optional.of(Repository.shortenRefName(headRef.getTarget().getName()))
                        : Optional.empty();
                worktrees.add(Worktree.builder()
                        .path(main.getWorkTree().toPath().toRealPath())
                        .name(null)
                        .branch(branch.orElse(null))
                        .head(head.map(ObjectId::name).orElse(null))
                        .main(true)
                        .locked(false)
                        .prunable(false)
                        .build());
            }
            worktrees.addAll(LinkedWorktrees.list(commonDir));
            return GitResult.ok(List.copyOf(worktrees));
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, root.toString());
        } catch (IOException | IllegalArgumentException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<Path> worktreeRemove(Path root, Path worktree) {
        GitResult<List<Worktree>> listed = worktreeList(root);
        if (!listed.isOk()) {
            return GitResult.of(listed.outcome(), listed.detail());
        }
        try {
            Path target = worktree.toAbsolutePath().normalize();
            Path real = Files.exists(target) ? target.toRealPath() : target;
            Optional<Worktree> match = Objects.requireNonNull(listed.value()).stream()
                    .filter(w -> w.path().equals(real) || w.path().equals(target)).findFirst();
            if (match.isEmpty()) {
                return GitResult.of(GitOutcome.WORKTREE_NOT_FOUND, target.toString());
            }
            Worktree found = match.get();
            if (found.main()) {
                return GitResult.of(GitOutcome.PATH_REFUSED, "the main worktree cannot be removed");
            }
            if (found.locked()) {
                return GitResult.of(GitOutcome.WORKTREE_LOCKED, found.path().toString());
            }
            Path commonDir;
            try (Repository repository = openRepository(root)) {
                commonDir = repository.getCommonDirectory().toPath().toRealPath();
            }
            if (!found.prunable()) {
                try (Repository linked = openRepository(found.path())) {
                    if (!Git.wrap(linked).status().call().isClean()) {
                        return GitResult.of(GitOutcome.WORKTREE_DIRTY, found.path().toString());
                    }
                }
            }
            LinkedWorktrees.delete(commonDir, Objects.requireNonNull(found.name()), found.path());
            return GitResult.ok(found.path());
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (GitAPIException | IOException | JGitInternalException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<CommitInfo> commit(CommitInput input) {
        List<String> paths = input.paths();
        var refused = refusedCommitPath(input.root(), paths);
        if (refused.isPresent()) {
            return GitResult.of(GitOutcome.PATH_REFUSED, refused.get());
        }
        try (Repository repository = openRepository(input.root()); Git git = Git.wrap(repository)) {
            var held = refusedState(repository, paths);
            if (held.isPresent()) {
                return held.get();
            }
            var command = commitCommand(git, input);
            if (!hasWorktreeChanges(git, paths)) {
                return GitResult.of(GitOutcome.NOTHING_TO_COMMIT, NO_CHANGES);
            }
            var staged = paths.isEmpty() ? wholeIndex(repository) : indexEntries(repository, paths);
            stage(git, command, paths);
            if (isEmptyAfterStaging(git, repository, paths)) {
                // the status saw a staged change the worktree reverted: staging would overwrite the index and the
                // commit would be empty, so the index is restored and the call commits nothing
                restore(repository, paths, staged);
                return GitResult.of(GitOutcome.NOTHING_TO_COMMIT, NO_CHANGES);
            }
            return GitResult.ok(toInfo(command.call()));
        } catch (EmptyCommitException _) {
            return GitResult.of(GitOutcome.NOTHING_TO_COMMIT, NO_CHANGES);
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, input.root().toString());
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (JGitInternalException e) {
            return failure(e);
        } catch (GitAPIException | IOException e) {
            return failure(e);
        }
    }

    /**
     * The conditions the commit refuses before it stages anything and that only an opened repository can answer: a name
     * of the tracked-tree rule and an index that holds unresolved conflict entries under the listed paths.
     *
     * @param repository the repository
     * @param paths      the listed paths; empty means the whole index
     * @return the refusal, empty when neither condition holds
     * @throws IOException when the index or the head tree cannot be read
     */
    private static Optional<GitResult<CommitInfo>> refusedState(Repository repository, List<String> paths)
            throws IOException {
        var trackedTree = CommitPathRefusals.refusedTrackedTrees(repository, paths);
        if (!trackedTree.isEmpty()) {
            return Optional.of(GitResult.of(GitOutcome.PATH_REFUSED, trackedTree.first()));
        }
        return refusedConflict(repository, paths)
                .map(path -> GitResult.of(GitOutcome.FAILED,
                        "the index of " + path + " holds unresolved conflict entries"));
    }

    private static CommitCommand commitCommand(Git git, CommitInput input) {
        return git.commit()
                .setMessage(input.message())
                .setAuthor(new PersonIdent(input.author().name(), input.author().email()))
                .setCommitter(new PersonIdent(input.committer().name(), input.committer().email()))
                .setSign(Boolean.FALSE)
                .setAllowEmpty(false);
    }

    /**
     * Stages the listed paths — every change of the worktree for an empty list — and narrows the command to them, so an
     * entry some other process staged earlier is not carried into the commit.
     */
    private static void stage(Git git, CommitCommand command, List<String> paths) throws GitAPIException {
        if (paths.isEmpty()) {
            git.add().addFilepattern(".").call();
            git.add().addFilepattern(".").setUpdate(true).call();
            return;
        }
        for (var path : paths) {
            git.add().addFilepattern(path).call();
            git.add().addFilepattern(path).setUpdate(true).call();
            command.setOnly(path);
        }
    }

    /**
     * Whether the staged state carries no change against {@code HEAD}, read after staging: for a listed path list the
     * listed entries only, for an empty list the whole index — never the worktree, whose dirt (a submodule's own
     * checkout among it) says nothing about what the commit would carry.
     */
    private static boolean isEmptyAfterStaging(Git git, Repository repository, List<String> paths)
            throws GitAPIException, IOException {
        return paths.isEmpty() ? indexMatchesHead(repository) : !hasChanges(repository, paths);
    }

    /**
     * Whether the whole index equals the head tree: the same path set, and the same object id and file mode for every
     * path. A repository without {@code HEAD} carries nothing, so its index is empty as well.
     */
    private static boolean indexMatchesHead(Repository repository) throws IOException {
        var headId = repository.resolve(Constants.HEAD + "^{commit}");
        var index = repository.readDirCache();
        if (headId == null) {
            return index.getEntryCount() == 0;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            var headTree = walk.parseCommit(headId).getTree();
            var headPaths = new TreeSet<String>();
            try (var treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(headTree);
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    headPaths.add(treeWalk.getPathString());
                }
            }
            var indexPaths = new TreeSet<String>();
            for (var position = 0; position < index.getEntryCount(); position++) {
                indexPaths.add(index.getEntry(position).getPathString());
            }
            if (!headPaths.equals(indexPaths)) {
                return false;
            }
            for (var path : indexPaths) {
                try (var treeWalk = TreeWalk.forPath(repository, path, headTree)) {
                    if (!sameEntry(index.getEntry(path), treeWalk)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Puts the index back as the snapshot took it: the listed entries, or the whole index for an empty path list. */
    private static void restore(Repository repository, List<String> paths, Map<String, DirCacheEntry> staged)
            throws IOException {
        if (paths.isEmpty()) {
            restoreWholeIndex(repository, staged);
        } else {
            restoreIndex(repository, paths, staged);
        }
    }

    /**
     * Whether staging the listed paths would make a change against {@code HEAD}, read from the worktree and the index
     * before anything is staged, so a call the status finds unchanged stages nothing. A change that exists only in the
     * index is caught after staging and undone there ({@link #hasChanges(Repository, List)}).
     *
     * @param git   the repository's git
     * @param paths the listed paths; empty means the whole worktree
     * @return whether a commit of the listed paths would carry a change
     * @throws GitAPIException when the status cannot be read
     */
    private static boolean hasWorktreeChanges(Git git, List<String> paths) throws GitAPIException {
        var status = git.status();
        for (var path : paths) {
            status.addPath(path);
        }
        return !status.call().isClean();
    }

    /**
     * The index entries of the listed paths before staging, copied, keyed by path; a path without an entry is absent
     * from the map.
     *
     * @param repository the repository
     * @param paths      the listed paths
     * @return the snapshot of their index entries
     * @throws IOException when the index cannot be read
     */
    private static Map<String, DirCacheEntry> indexEntries(Repository repository, List<String> paths)
            throws IOException {
        var index = repository.readDirCache();
        var entries = new HashMap<String, DirCacheEntry>();
        for (var path : paths) {
            var entry = index.getEntry(path);
            if (entry != null) {
                entries.put(path, new DirCacheEntry(entry));
            }
        }
        return entries;
    }

    /**
     * Puts the snapshotted entries of the listed paths back and removes the ones that had none, so a call that commits
     * nothing does not overwrite a staged entry the worktree reverted.
     *
     * @param repository the repository
     * @param paths      the listed paths
     * @param entries    the snapshot taken before staging
     * @throws IOException when the index cannot be written
     */
    private static void restoreIndex(Repository repository, List<String> paths,
            Map<String, DirCacheEntry> entries) throws IOException {
        var editor = repository.lockDirCache().editor();
        for (var path : paths) {
            var previous = entries.get(path);
            if (previous == null) {
                editor.add(new DirCacheEditor.DeletePath(path));
            } else {
                editor.add(new DirCacheEditor.PathEdit(path) {

                    @Override
                    public void apply(DirCacheEntry entry) {
                        entry.setObjectId(previous.getObjectId());
                        entry.setFileMode(previous.getFileMode());
                        entry.copyMetaData(previous);
                    }
                });
            }
        }
        if (!editor.commit()) {
            throw new IOException("the index of " + paths.size() + " paths could not be restored");
        }
    }

    /**
     * Whether the index differs from {@code HEAD} for the listed paths, so the commit would not be empty. JGit reports
     * an empty commit with a translated message on a generic exception, so the condition is read here, from the typed
     * index and tree instead of from that text.
     *
     * @param repository the repository
     * @param paths      the listed paths
     * @return whether the staged state of the paths differs from the head tree
     * @throws IOException when the index or the head tree cannot be read
     */
    private static boolean hasChanges(Repository repository, List<String> paths) throws IOException {
        var headId = repository.resolve(Constants.HEAD + "^{commit}");
        if (headId == null) {
            return true;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            var headTree = walk.parseCommit(headId).getTree();
            var index = repository.readDirCache();
            for (var path : paths) {
                var entry = index.getEntry(path);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, headTree)) {
                    if (!sameEntry(entry, treeWalk)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Compares a staged entry with the entry of the head tree: the object id and the file mode, so a change of the
     * executable bit alone is a change.
     */
    private static boolean sameEntry(@Nullable DirCacheEntry entry, @Nullable TreeWalk head) {
        if (entry == null || head == null || head.getObjectId(0) == null) {
            return entry == null && head == null;
        }
        return entry.getObjectId().equals(head.getObjectId(0))
                && entry.getFileMode() == head.getFileMode(0);
    }

    /**
     * Refuses, before anything is staged, a path that is no literal repository path of one file: the textual rule and
     * the file-system rule of {@link CommitPathRefusals}, shared with the staging set of a scope. The glob characters
     * {@code *}, {@code ?} and {@code [} are admitted: JGit's {@code addFilepattern} and {@code setOnly} stage a path
     * literally, so a file named {@code app/[slug]/page.tsx} is committed under that name (link:../../../pm-workflow/doc/adr/workflow-input/006-path-and-commit-body-rules-stated.adoc[ADR
     * workflow-input/006]); the bind admits the same characters.
     *
     * @param root  the worktree root
     * @param paths the repository-relative paths to stage and commit
     * @return the first refused path, empty when every path is a literal file path
     */
    private static Optional<String> refusedCommitPath(Path root, List<String> paths) {
        for (var path : paths) {
            if (isRefusedPathText(path) || CommitPathRefusals.isRefusedLocation(root, path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    /**
     * The first path of the index that holds an unresolved merge stage, empty when the index is merged. The restore
     * copies one entry per path, so a conflicted path would be written back as its base entry at stage 0 and the
     * conflict would disappear; the call is refused instead, before anything is staged. A listed name is matched
     * exactly and as the directory prefix of a descendant, because JGit's add filter is prefix-based: a listed symlink
     * that replaced a tracked directory must not let a conflict below it through. An empty list means the whole index.
     *
     * @param repository the repository
     * @param paths      the listed paths; empty means the whole index
     * @return the first conflicted path, empty when the index holds no conflict under the listed paths
     * @throws IOException when the index cannot be read
     */
    private static Optional<String> refusedConflict(Repository repository, List<String> paths) throws IOException {
        var index = repository.readDirCache();
        for (var position = 0; position < index.getEntryCount(); position++) {
            var entry = index.getEntry(position);
            var path = entry.getPathString();
            if (entry.getStage() != DirCacheEntry.STAGE_0 && (paths.isEmpty() || isListedOrBelow(paths, path))) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    /** Whether a path is a listed name or lies below one, the way JGit's prefix-based add filter reaches it. */
    private static boolean isListedOrBelow(List<String> paths, String path) {
        for (var listed : paths) {
            if (listed.equals(path) || path.startsWith(listed + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The whole index before staging, copied, keyed by path.
     *
     * @param repository the repository
     * @return the snapshot of every index entry
     * @throws IOException when the index cannot be read
     */
    private static Map<String, DirCacheEntry> wholeIndex(Repository repository) throws IOException {
        var index = repository.readDirCache();
        var entries = new HashMap<String, DirCacheEntry>();
        for (var position = 0; position < index.getEntryCount(); position++) {
            var entry = index.getEntry(position);
            entries.put(entry.getPathString(), new DirCacheEntry(entry));
        }
        return entries;
    }

    /**
     * Puts the whole index back as it was before staging, so a call of every change that commits nothing does not
     * overwrite the staged entries the worktree reverted. Every entry the staging step added and every entry it changed
     * is covered: the snapshot's paths and the current index's paths are restored together.
     *
     * @param repository the repository
     * @param entries    the snapshot taken before staging
     * @throws IOException when the index cannot be written
     */
    private static void restoreWholeIndex(Repository repository, Map<String, DirCacheEntry> entries)
            throws IOException {
        var index = repository.readDirCache();
        var paths = new TreeSet<>(entries.keySet());
        for (var position = 0; position < index.getEntryCount(); position++) {
            paths.add(index.getEntry(position).getPathString());
        }
        restoreIndex(repository, List.copyOf(paths), entries);
    }

    /** The textual refusals, the rule the commit shares with the staging set of a scope ({@link CommitPathRefusals}). */
    private static boolean isRefusedPathText(String path) {
        return CommitPathRefusals.isRefusedText(path);
    }

    @Override
    public GitResult<List<CommitInfo>> log(Path root, String revision, int maxCount) {
        try (Repository repository = openRepository(root); RevWalk walk = new RevWalk(repository)) {
            var resolution = GitIdentity.resolution(repository, revision);
            if (GitIdentity.isAmbiguous(resolution)) {
                return GitResult.of(GitOutcome.GIT_IDENTITY_AMBIGUOUS, revision);
            }
            if (resolution == GitIdentity.Resolution.ABBREVIATED_COMMIT_ID) {
                return GitResult.of(GitOutcome.UNKNOWN_REVISION, ABBREVIATED_COMMIT_ID + revision);
            }
            ObjectId start = repository.resolve(revision + "^{commit}");
            if (start == null) {
                return GitResult.of(GitOutcome.UNKNOWN_REVISION, revision);
            }
            if (resolution == GitIdentity.Resolution.FULL_COMMIT_ID && !start.name().equalsIgnoreCase(revision)) {
                return GitResult.of(GitOutcome.GIT_IDENTITY_MISMATCH,
                        "the pin " + revision + " reached " + start.name());
            }
            walk.markStart(walk.parseCommit(start));
            var commits = new ArrayList<CommitInfo>();
            for (RevCommit commit : walk) {
                if (commits.size() >= Math.max(1, maxCount)) {
                    break;
                }
                commits.add(toInfo(commit));
            }
            return GitResult.ok(List.copyOf(commits));
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, root.toString());
        } catch (IOException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<List<RefUpdate>> fetch(RemoteInput input) {
        try (Repository repository = openRepository(input.root()); Git git = Git.wrap(repository)) {
            Optional<GitResult<List<RefUpdate>>> refused = checkRemote(repository, input.remote(), false);
            if (refused.isPresent()) {
                return refused.get();
            }
            var command = git.fetch().setRemote(input.remote())
                    .setRefSpecs(input.refSpecs().stream().map(RefSpec::new).toList());
            GitCredential credential = input.credential();
            if (credential != null) {
                command.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(credential.username(), credential.secret()));
            }
            FetchResult result = command.call();
            var updates = result.getTrackingRefUpdates().stream()
                    .map(update -> RefUpdate.builder()
                            .ref(update.getLocalName())
                            .oldId(id(update.getOldObjectId()).orElse(null))
                            .newId(id(update.getNewObjectId()).orElse(null))
                            .status(update.getResult().name().toLowerCase(Locale.ROOT))
                            .build())
                    .toList();
            return GitResult.ok(updates);
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (InvalidRemoteException _) {
            return GitResult.of(GitOutcome.UNKNOWN_REVISION, input.remote());
        } catch (GitAPIException | IOException | JGitInternalException | URISyntaxException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<RefUpdate> push(PushInput input) {
        try (Repository repository = openRepository(input.root()); Git git = Git.wrap(repository)) {
            Optional<GitResult<RefUpdate>> refused = checkRemote(repository, input.remote(), true);
            if (refused.isPresent()) {
                return refused.get();
            }
            ObjectId local = repository.resolve(input.localRef());
            if (local == null) {
                return GitResult.of(GitOutcome.UNKNOWN_REVISION, input.localRef());
            }
            var command = git.push().setRemote(input.remote())
                    .setRefSpecs(new RefSpec(local.name() + ":" + input.remoteRef()));
            String expectedRemoteHead = input.expectedRemoteHead();
            if (expectedRemoteHead != null) {
                command.setRefLeaseSpecs(new RefLeaseSpec(input.remoteRef(), expectedRemoteHead));
            }
            GitCredential credential = input.credential();
            if (credential != null) {
                command.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(credential.username(), credential.secret()));
            }
            for (PushResult result : command.call()) {
                RemoteRefUpdate update = result.getRemoteUpdate(input.remoteRef());
                if (update != null) {
                    return toPushResult(update);
                }
            }
            return GitResult.of(GitOutcome.FAILED, "no update reported for " + input.remoteRef());
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (InvalidRemoteException _) {
            return GitResult.of(GitOutcome.UNKNOWN_REVISION, input.remote());
        } catch (GitAPIException | IOException | JGitInternalException | URISyntaxException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<Path> initCache(Path cache) {
        try {
            if (Files.exists(cache) && !isEmptyDirectory(cache)) {
                try (Repository repository = new FileRepositoryBuilder().setFS(fs).setMustExist(true)
                             .setGitDir(cache.toFile()).build()) {
                    return repository.isBare() ? GitResult.ok(cache.toRealPath())
                            : GitResult.of(GitOutcome.NOT_A_REPOSITORY, cache + " is no bare repository");
                }
            }
            Files.createDirectories(cache);
            try (var _ = Git.init().setBare(true).setGitDir(cache.toFile()).setFs(fs).call()) {
                return GitResult.ok(cache.toRealPath());
            }
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, cache.toString());
        } catch (GitAPIException | IOException | JGitInternalException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<String> fetchCommit(FetchCommitInput input) {
        URIish uri;
        try {
            uri = new URIish(input.url());
        } catch (URISyntaxException _) {
            return GitResult.of(GitOutcome.REMOTE_ORIGIN_MISMATCH, "not a URL: " + input.url());
        }
        if (!input.policy().permits(uri)) {
            return GitResult.of(GitOutcome.REMOTE_ORIGIN_MISMATCH, uri.toString());
        }
        if (!ObjectId.isId(input.commit())) {
            return GitResult.of(GitOutcome.UNKNOWN_REVISION, "not a full commit id: " + input.commit());
        }
        var commit = ObjectId.fromString(input.commit());
        try (Repository repository = openCache(input.cache())) {
            if (isCommit(repository, commit)) {
                return GitResult.ok(commit.name());
            }
            try (Transport transport = Transport.open(repository, uri)) {
                if (!input.policy().permits(transport.getURI())) {
                    return GitResult.of(GitOutcome.REMOTE_ORIGIN_MISMATCH, transport.getURI().toString());
                }
                transport.setTimeout(FETCH_TIMEOUT_SECONDS);
                transport.setTagOpt(TagOpt.NO_TAGS);
                transport.setCheckFetchedObjects(true);
                transport.fetch(NullProgressMonitor.INSTANCE,
                        List.of(new RefSpec("+" + commit.name() + ":" + PINNED_REFS + commit.name())));
            }
            return isCommit(repository, commit) ? GitResult.ok(commit.name())
                    : GitResult.of(GitOutcome.UNKNOWN_REVISION, uri + " did not serve " + commit.name());
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, input.cache().toString());
        } catch (RefusingFS.CapabilityRefusedException e) {
            return GitResult.of(GitOutcome.CAPABILITY_MISSING, e.getReason());
        } catch (TransportException e) {
            return isUnservedWant(e) ? GitResult.of(GitOutcome.UNKNOWN_REVISION, uri + ": " + e.getMessage())
                    : failure(e);
        } catch (IOException | JGitInternalException e) {
            return failure(e);
        }
    }

    @Override
    public GitResult<byte[]> readFile(ReadFileInput input) {
        if (!ObjectId.isId(input.commit())) {
            return GitResult.of(GitOutcome.UNKNOWN_REVISION, "not a full commit id: " + input.commit());
        }
        var commitId = ObjectId.fromString(input.commit());
        try (Repository repository = openCache(input.cache()); RevWalk walk = new RevWalk(repository)) {
            if (!isCommit(repository, commitId)) {
                return GitResult.of(GitOutcome.UNKNOWN_REVISION, commitId.name());
            }
            RevCommit commit = walk.parseCommit(commitId);
            try (TreeWalk tree = TreeWalk.forPath(repository, input.path(), commit.getTree())) {
                if (tree == null || (tree.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
                    return GitResult.of(GitOutcome.PATH_NOT_FOUND, input.path() + " at " + commitId.name());
                }
                try (var content = repository.open(tree.getObjectId(0), Constants.OBJ_BLOB).openStream()) {
                    return GitResult.ok(content.readAllBytes());
                }
            }
        } catch (RepositoryNotFoundException _) {
            return GitResult.of(GitOutcome.NOT_A_REPOSITORY, input.cache().toString());
        } catch (IllegalArgumentException e) {
            return GitResult.of(GitOutcome.PATH_NOT_FOUND, input.path() + ": " + e.getMessage());
        } catch (IOException | JGitInternalException e) {
            return failure(e);
        }
    }

    private Repository openCache(Path cache) throws IOException {
        return new FileRepositoryBuilder().setFS(fs).setMustExist(true).setGitDir(cache.toFile()).setBare().build();
    }

    private static boolean isCommit(Repository repository, ObjectId id) throws IOException {
        if (!repository.getObjectDatabase().has(id)) {
            return false;
        }
        return repository.open(id).getType() == Constants.OBJ_COMMIT;
    }

    /** @return {@code true} if the remote answered but refused to serve the wanted object */
    private static boolean isUnservedWant(TransportException e) {
        return isUnservedWant(e.getMessage());
    }

    /**
     * Recognises the closed set of messages by which a remote that answered refuses to serve a wanted object; the test
     * ignores case. A transport failure with any other message is {@code FAILED}.
     * <ul>
     * <li>{@code not our ref}: the wording of git's {@code upload-pack}, which GitHub and GitLab run, for a want it
     * does not advertise or no longer has, e.g. {@code upload-pack: not our ref <sha>} or
     * {@code remote error: upload-pack: not our ref <sha>}.</li>
     * <li>{@code want <object id> not valid}: the wording of JGit's {@code UploadPack}.</li>
     * <li>{@code want <object id> not found}: a want the remote reports as absent.</li>
     * </ul>
     *
     * @param message the message of a transport failure; {@code null} if it has none
     * @return {@code true} if the message is one of the set
     */
    static boolean isUnservedWant(@Nullable String message) {
        if (message == null) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(NOT_OUR_REF) || WANT_REFUSED.matcher(message).find();
    }

    private static GitResult<RefUpdate> toPushResult(RemoteRefUpdate update) {
        var ref = RefUpdate.builder()
                .ref(update.getRemoteName())
                .oldId(id(update.getExpectedOldObjectId()).orElse(null))
                .newId(id(update.getNewObjectId()).orElse(null))
                .status(update.getStatus().name().toLowerCase(Locale.ROOT))
                .build();
        return switch (update.getStatus()) {
            case OK, UP_TO_DATE -> GitResult.ok(ref);
            case REJECTED_NONFASTFORWARD -> GitResult.of(GitOutcome.REJECTED_NON_FAST_FORWARD, update.getRemoteName());
            case REJECTED_REMOTE_CHANGED -> GitResult.of(GitOutcome.REJECTED_LEASE, update.getRemoteName());
            default -> GitResult.of(GitOutcome.FAILED, update.getStatus() + ": " + update.getMessage());
        };
    }

    private <V> Optional<GitResult<V>> checkRemote(Repository repository, String remote, boolean push)
            throws URISyntaxException {
        var config = new RemoteConfig(repository.getConfig(), remote);
        List<URIish> uris = push && !config.getPushURIs().isEmpty() ? config.getPushURIs() : config.getURIs();
        if (uris.isEmpty()) {
            return Optional.of(GitResult.of(GitOutcome.UNKNOWN_REVISION, "remote " + remote));
        }
        for (URIish uri : uris) {
            if (!remotePolicy.permits(uri)) {
                return Optional.of(GitResult.of(GitOutcome.REMOTE_ORIGIN_MISMATCH, uri.toString()));
            }
        }
        return Optional.empty();
    }

    private Repository openRepository(Path root) throws IOException {
        return CommitPathRefusals.open(root, fs);
    }

    private Repository mainRepository(Path commonDir) throws IOException {
        return new FileRepositoryBuilder().setFS(fs).setMustExist(true).setGitDir(commonDir.toFile()).build();
    }

    private static RepositoryInfo info(Repository repository) throws IOException {
        Path gitDir = repository.getDirectory().toPath().toRealPath();
        Path commonDir = repository.getCommonDirectory().toPath().toRealPath();
        Ref head = repository.exactRef(Constants.HEAD);
        Optional<String> branch = head != null && head.isSymbolic()
                ? Optional.of(Repository.shortenRefName(head.getTarget().getName()))
                : Optional.empty();
        Optional<String> id = Optional.ofNullable(repository.resolve(Constants.HEAD)).map(ObjectId::name);
        return RepositoryInfo.builder()
                .worktreeRoot(repository.getWorkTree().toPath().toRealPath())
                .gitDir(gitDir)
                .commonDir(commonDir)
                .head(id.orElse(null))
                .branch(branch.orElse(null))
                .linked(!gitDir.equals(commonDir))
                .build();
    }

    private static CommitInfo toInfo(RevCommit commit) {
        var parents = Arrays.stream(commit.getParents()).map(RevCommit::name).toList();
        PersonIdent author = commit.getAuthorIdent();
        PersonIdent committer = commit.getCommitterIdent();
        return CommitInfo.builder()
                .id(commit.name())
                .parents(parents)
                .author(new Identity(author.getName(), author.getEmailAddress()))
                .authoredAt(author.getWhenAsInstant())
                .committer(new Identity(committer.getName(), committer.getEmailAddress()))
                .committedAt(committer.getWhenAsInstant())
                .message(commit.getFullMessage())
                .build();
    }

    private static Optional<String> id(@Nullable ObjectId id) {
        return id == null || ObjectId.zeroId().equals(id) ? Optional.empty() : Optional.of(id.name());
    }

    /**
     * Resolves a path that may not exist yet: the nearest existing ancestor is resolved with
     * {@link Path#toRealPath}, the missing segments are appended.
     */
    private static Path canonical(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (!Files.exists(existing)) {
            existing = existing.getParent();
        }
        return existing.toRealPath().resolve(existing.relativize(absolute));
    }

    private static boolean isEmptyDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (var entries = Files.list(path)) {
            return entries.findAny().isEmpty();
        }
    }

    private static <V> GitResult<V> failure(Exception e) {
        return GitResult.of(GitOutcome.FAILED, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
