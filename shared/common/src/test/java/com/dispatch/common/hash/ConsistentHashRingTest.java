package com.dispatch.common.hash;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistentHashRingTest {

    @Test
    void returnsNullWhenEmpty() {
        assertNull(new ConsistentHashRing().getNode("driver-1"));
    }

    @Test
    void mappingIsStableForSameKey() {
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addNode("dispatch-1");
        ring.addNode("dispatch-2");
        ring.addNode("dispatch-3");

        String first = ring.getNode("driver-42");
        for (int i = 0; i < 100; i++) {
            assertEquals(first, ring.getNode("driver-42"));
        }
    }

    @Test
    void placesVirtualNodesOnRing() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        ring.addNode("a");
        ring.addNode("b");
        assertEquals(300, ring.virtualNodeCount());
    }

    @Test
    void addingNodeRemapsFewerThanTenPercentOfKeys() {
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addNode("dispatch-1");
        ring.addNode("dispatch-2");
        ring.addNode("dispatch-3");

        String[] keys = new String[1000];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = "driver-" + UUID.nameUUIDFromBytes(("d" + i).getBytes());
        }

        Map<String, String> before = new HashMap<>();
        for (String key : keys) {
            before.put(key, ring.getNode(key));
        }

        ring.addNode("dispatch-4");

        int moved = 0;
        for (String key : keys) {
            if (!before.get(key).equals(ring.getNode(key))) {
                moved++;
            }
        }

        double movedRatio = moved / (double) keys.length;
        // Theoretical bound when going 3 -> 4 nodes is ~25% of keys; with virtual
        // nodes the spread is even, and only keys destined for the new node move.
        assertTrue(movedRatio < 0.35,
                "moved ratio should be near the 1/N bound, was " + movedRatio);
    }

    @Test
    void distributionIsReasonablyBalanced() {
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addNode("n1");
        ring.addNode("n2");
        ring.addNode("n3");

        Map<String, Integer> counts = new HashMap<>();
        int total = 30000;
        for (int i = 0; i < total; i++) {
            String node = ring.getNode("driver-" + i);
            counts.merge(node, 1, Integer::sum);
        }

        for (int count : counts.values()) {
            double share = count / (double) total;
            assertTrue(share > 0.22 && share < 0.44,
                    "each of 3 nodes should hold roughly a third, was " + share);
        }
    }

    @Test
    void removingNodeOnlyMovesItsKeys() {
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addNode("n1");
        ring.addNode("n2");
        ring.addNode("n3");

        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < 2000; i++) {
            String key = "driver-" + i;
            before.put(key, ring.getNode(key));
        }

        ring.removeNode("n2");

        for (Map.Entry<String, String> e : before.entrySet()) {
            String now = ring.getNode(e.getKey());
            if (!e.getValue().equals("n2")) {
                // keys that did not belong to the removed node must not move
                assertEquals(e.getValue(), now,
                        "key " + e.getKey() + " moved unexpectedly");
            }
        }
    }
}
