package common.cn.kafei.simukraft.path;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Small bounded cache of recently captured chunk data, keyed by their sampled box.
 *
 * <p>When several citizens path within the same area on the same tick they would otherwise each
 * re-read the same blocks from the world. Citizens whose path boxes fall inside the same aligned
 * cell share a single {@link PathSnapshotBuilder.ChunkDataCapture} per tick burst. The capture is
 * then handed to a worker thread for the CPU-heavy {@link PathSnapshotBuilder#buildFromCapture}
 * step, so the server thread only pays the fast block-read cost once per area per burst.
 *
 * <p>Only accessed from the server thread, so it needs no synchronization.
 */
final class PathSnapshotCache {
    private static final int MAX_ENTRIES = 8;
    private static final long REUSE_TTL_TICKS = 4L;
    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Returns a {@link PathSnapshotBuilder.ChunkDataCapture} covering the request, reusing a
     * still-fresh containing entry when one exists, otherwise capturing and caching a new one.
     */
    PathSnapshotBuilder.ChunkDataCapture acquire(ServerLevel level, BlockPos start, BlockPos target, int radius) {
        PathSnapshotBuilder.SnapshotBounds bounds = PathSnapshotBuilder.bounds(level, start, target, radius);
        long now = level.getGameTime();
        for (Entry entry : entries) {
            if (now - entry.capture().createdAt() <= REUSE_TTL_TICKS && entry.bounds().contains(bounds)) {
                return entry.capture();
            }
        }
        BlockPos buildStart = new BlockPos(
                Math.floorDiv(start.getX(), 16) * 16 + 8,
                start.getY(),
                Math.floorDiv(start.getZ(), 16) * 16 + 8);
        int buildRadius = radius + 16;
        PathSnapshotBuilder.SnapshotBounds buildBounds = PathSnapshotBuilder.bounds(level, buildStart, target, buildRadius);
        PathSnapshotBuilder.ChunkDataCapture capture = PathSnapshotBuilder.capture(level, buildStart, target, buildRadius);
        if (capture.complete()) {
            entries.addLast(new Entry(capture, buildBounds));
            while (entries.size() > MAX_ENTRIES) {
                entries.removeFirst();
            }
        }
        return capture;
    }

    void clear() {
        entries.clear();
    }

    private record Entry(PathSnapshotBuilder.ChunkDataCapture capture, PathSnapshotBuilder.SnapshotBounds bounds) {
    }
}
