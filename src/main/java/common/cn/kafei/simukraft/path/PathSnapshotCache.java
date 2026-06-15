package common.cn.kafei.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small bounded cache of recently built path snapshots, keyed by their sampled box.
 *
 * <p>When several citizens path within the same area on the same tick they would otherwise each
 * rebuild largely the same volume, which is the single most expensive main-thread step. A new
 * request may instead reuse a still-fresh snapshot whose box fully contains the new request's box:
 * an immutable snapshot that strictly contains the needed volume only ever adds cells, so it can
 * never produce an invalid path. Reuse therefore requires strict three-axis containment and a tight
 * freshness window, and the cache is cleared whenever the world changes so a block edit cannot be
 * pathed through stale terrain.
 *
 * <p>Only accessed from the server thread, so it needs no synchronization.
 */
final class PathSnapshotCache {
    private static final int MAX_ENTRIES = 8;
    private static final long REUSE_TTL_TICKS = 4L;
    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Returns a fresh snapshot covering the request, reusing a still-fresh containing entry when one
     * exists and otherwise building, caching and returning a new snapshot.
     *
     * @param level the server level to sample
     * @param start the citizen's start block
     * @param target the destination block
     * @param radius the local path radius that bounds the sampled box
     * @return a snapshot whose box fully contains the request volume
     */
    PathSnapshot acquire(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        PathSnapshotBuilder.SnapshotBounds bounds = PathSnapshotBuilder.bounds(level, start, target, radius);
        long now = level.getGameTime();
        for (Entry entry : entries) {
            if (now - entry.snapshot().createdAt() <= REUSE_TTL_TICKS && entry.bounds().contains(bounds)) {
                return entry.snapshot();
            }
        }
        int buildRadius = radius + 16;
        PathSnapshotBuilder.SnapshotBounds buildBounds = PathSnapshotBuilder.bounds(level, start, target, buildRadius);
        PathSnapshot snapshot = PathSnapshotBuilder.build(level, start, target, buildRadius);
        // Only cache snapshots that fully sampled their box. A build skips columns whose chunk was
        // not loaded, so an incomplete snapshot can be missing cells for columns a later contained
        // request needs; caching only complete snapshots keeps box containment a sound reuse test.
        if (snapshot.complete()) {
            entries.addLast(new Entry(snapshot, buildBounds));
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        return snapshot;
    }

    /**
     * Drops every cached snapshot, e.g. after a block change invalidates the sampled terrain.
     */
    void clear() {
        entries.clear();
    }

    private record Entry(PathSnapshot snapshot, PathSnapshotBuilder.SnapshotBounds bounds) {
    }
}
