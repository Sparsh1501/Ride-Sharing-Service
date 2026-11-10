package com.dispatch.util;

import java.util.Collections;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Consistent hash ring with virtual nodes, implemented from scratch.
 *
 * <p>Each physical node (a dispatch-service instance) is placed on the ring at
 * {@code VIRTUAL_NODES} positions to smooth out key distribution. A key (driverId)
 * maps to the first node clockwise from its hash position. Adding or removing a
 * node only remaps the keys that fall between the affected virtual node positions,
 * keeping reshuffling well under the theoretical {@code 1/N} bound.
 */
public class ConsistentHashRing {

    public static final int DEFAULT_VIRTUAL_NODES = 150;

    private final int virtualNodes;
    private final ConcurrentSkipListMap<Long, String> ring = new ConcurrentSkipListMap<>();
    private final Set<String> physicalNodes = new CopyOnWriteArraySet<>();

    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = virtualNodes;
    }

    public synchronized void addNode(String nodeId) {
        if (!physicalNodes.add(nodeId)) {
            return;
        }
        for (int i = 0; i < virtualNodes; i++) {
            ring.put(virtualNodeHash(nodeId, i), nodeId);
        }
    }

    public synchronized void removeNode(String nodeId) {
        if (!physicalNodes.remove(nodeId)) {
            return;
        }
        for (int i = 0; i < virtualNodes; i++) {
            ring.remove(virtualNodeHash(nodeId, i));
        }
    }

    /**
     * Returns the node that owns the given key, or {@code null} if the ring is empty.
     */
    public String getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long h = hash(key);
        SortedMap<Long, String> tail = ring.tailMap(h);
        Long position = tail.isEmpty() ? ring.firstKey() : tail.firstKey();
        return ring.get(position);
    }

    public Set<String> nodes() {
        return Collections.unmodifiableSet(physicalNodes);
    }

    public int virtualNodeCount() {
        return ring.size();
    }

    /** Exposed package-private snapshot for tests/diagnostics. */
    TreeMap<Long, String> snapshot() {
        return new TreeMap<>(ring);
    }

    private long virtualNodeHash(String nodeId, int replica) {
        return hash(nodeId + "#vn" + replica);
    }

    private long hash(String key) {
        return MurmurHash3.hashToRing(key);
    }
}
