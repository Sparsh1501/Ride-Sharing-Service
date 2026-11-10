package com.dispatch.service;

import com.dispatch.util.MurmurHash3;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Distributed consistent hash ring whose virtual-node positions are stored in a
 * shared Redis ZSET (score = hash position, member = {nodeId}#{vnode}). Every
 * dispatch-service instance registers its virtual nodes on startup, so all
 * instances resolve {@code getNode(driverId)} to the same owner — this is what
 * prevents two instances from assigning the same driver.
 */
@Component
public class DistributedHashRing {

    private static final Logger log = LoggerFactory.getLogger(DistributedHashRing.class);

    private final StringRedisTemplate redis;
    private final String ringKey;
    private final int virtualNodes;
    private final String nodeId;

    public DistributedHashRing(StringRedisTemplate redis,
                               @Value("${dispatch.consistent-hash.ring-key:dispatch:hash:ring}") String ringKey,
                               @Value("${dispatch.consistent-hash.virtual-nodes:150}") int virtualNodes,
                               @Value("${dispatch.node-id:${HOSTNAME:dispatch-1}}") String nodeId) {
        this.redis = redis;
        this.ringKey = ringKey;
        this.virtualNodes = virtualNodes;
        this.nodeId = nodeId;
    }

    @PostConstruct
    public void register() {
        addNode(nodeId);
        log.info("Registered dispatch node '{}' with {} virtual nodes on ring '{}'",
                nodeId, virtualNodes, ringKey);
    }

    @PreDestroy
    public void deregister() {
        removeNode(nodeId);
    }

    public void addNode(String node) {
        for (int i = 0; i < virtualNodes; i++) {
            String member = node + "#" + i;
            redis.opsForZSet().add(ringKey, member, MurmurHash3.hashToRing(member));
        }
    }

    public void removeNode(String node) {
        for (int i = 0; i < virtualNodes; i++) {
            redis.opsForZSet().remove(ringKey, node + "#" + i);
        }
    }

    /** Returns the dispatch node that owns the given key (driverId), or null if ring empty. */
    public String getNode(String key) {
        long h = MurmurHash3.hashToRing(key);
        Set<String> next = redis.opsForZSet()
                .rangeByScore(ringKey, h, Double.POSITIVE_INFINITY, 0, 1);
        String member = (next == null || next.isEmpty()) ? firstMember() : next.iterator().next();
        if (member == null) {
            return null;
        }
        int idx = member.lastIndexOf('#');
        return idx > 0 ? member.substring(0, idx) : member;
    }

    public boolean ownsKey(String key) {
        return nodeId.equals(getNode(key));
    }

    public String currentNodeId() {
        return nodeId;
    }

    private String firstMember() {
        Set<String> first = redis.opsForZSet().range(ringKey, 0, 0);
        return (first == null || first.isEmpty()) ? null : first.iterator().next();
    }
}
