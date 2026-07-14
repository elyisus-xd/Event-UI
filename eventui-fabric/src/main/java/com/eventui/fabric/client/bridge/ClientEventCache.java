package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.BridgeMessage;
import com.eventui.api.event.EventDefinition;
import com.eventui.api.event.EventProgress;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ClientEventCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEventCache.class);
    private final Map<String, String> uiConfigs;
    private final Map<String, EventDefinition> eventDefinitions;
    private final Map<String, EventProgress> eventProgress;
    private final Map<UUID, CompletableFuture<?>> pendingRequests;
    private final Map<String, SkillTreeData> cachedSkillTrees;
    private final Map<String, SkillPointsData> cachedSkillPoints;

    public ClientEventCache() {
        this.eventDefinitions = new ConcurrentHashMap<>();
        this.eventProgress = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.uiConfigs = new ConcurrentHashMap<>();
        this.cachedSkillTrees = new ConcurrentHashMap<>();
        this.cachedSkillPoints = new ConcurrentHashMap<>();
    }

    public void cacheUIConfig(String uiId, String uiDataJson) {
        uiConfigs.put(uiId, uiDataJson);
        LOGGER.info("Cached UI config: {}", uiId);
    }

    public String getCachedUIConfig(String uiId) {
        return uiConfigs.get(uiId);
    }

    public void registerPendingRequest(UUID messageId, CompletableFuture<?> future) {
        pendingRequests.put(messageId, future);
    }
    @SuppressWarnings("unchecked")
    <T> void completePendingRequest(UUID messageId, T data) {
        CompletableFuture<?> future = pendingRequests.remove(messageId);

        if (future != null) {
            ((CompletableFuture<T>) future).complete(data);
        }
    }

    void failPendingRequest(UUID messageId, Throwable error) {
        CompletableFuture<?> future = pendingRequests.remove(messageId);

        if (future != null) {
            future.completeExceptionally(error);
        }
    }

    public void handleEventDataResponse(BridgeMessage message) {
        UUID replyTo = message.getReplyToMessageId();

        if (replyTo == null) {
            LOGGER.warn("EVENT_DATA_RESPONSE without replyTo ID");
            return;
        }

        try {
            String eventId = message.getPayload().get("event_id");
            LOGGER.info("Received event data for: {}", eventId);

        } catch (Exception e) {
            LOGGER.error("Failed to parse EVENT_DATA_RESPONSE", e);
            failPendingRequest(replyTo, e);
        }
    }

    public void handleProgressResponse(BridgeMessage message) {
        UUID replyTo = message.getReplyToMessageId();

        if (replyTo == null) {
            LOGGER.warn("EVENT_PROGRESS_RESPONSE without replyTo ID");
            return;
        }

        try {
            String eventId = message.getPayload().get("event_id");
            LOGGER.info("Received progress data for: {}", eventId);

        } catch (Exception e) {
            LOGGER.error("Failed to parse EVENT_PROGRESS_RESPONSE", e);
            failPendingRequest(replyTo, e);
        }
    }

    public void invalidateEvent(String eventId) {
        eventDefinitions.remove(eventId);
        LOGGER.debug("Invalidated event cache: {}", eventId);
    }

    public void invalidateProgress(String eventId) {
        eventProgress.remove(eventId);
        LOGGER.debug("Invalidated progress cache: {}", eventId);
    }

    public void clear() {
        eventDefinitions.clear();
        eventProgress.clear();
        cachedSkillTrees.clear();
        cachedSkillPoints.clear();
        pendingRequests.values().forEach(future ->
                future.completeExceptionally(new IllegalStateException("Bridge disconnected"))
        );
        pendingRequests.clear();

        LOGGER.info("Cache cleared");
    }

    public EventDefinition getCachedEvent(String eventId) {
        return eventDefinitions.get(eventId);
    }

    public EventProgress getCachedProgress(String eventId) {
        return eventProgress.get(eventId);
    }

    public void updateSkillData(Map<String, SkillTreeData> trees, Map<String, SkillPointsData> points) {
        cachedSkillTrees.clear();
        cachedSkillTrees.putAll(trees);
        cachedSkillPoints.clear();
        cachedSkillPoints.putAll(points);
        LOGGER.info("Updated skill data: {} trees, {} point types", trees.size(), points.size());
    }

    public Map<String, SkillTreeData> getCachedSkillTrees() {
        return new ConcurrentHashMap<>(cachedSkillTrees);
    }

    public Map<String, SkillPointsData> getCachedSkillPoints() {
        return new ConcurrentHashMap<>(cachedSkillPoints);
    }

    public void updateNodeLevel(String treeId, String nodeId, int newLevel, String newState,
                                int costNextLevel, int pointsAvailable) {
        SkillTreeData tree = cachedSkillTrees.get(treeId);
        if (tree != null && tree.nodes().containsKey(nodeId)) {
            SkillNodeData oldNode = tree.nodes().get(nodeId);
            SkillNodeData newNode = new SkillNodeData(
                    oldNode.id(),
                    oldNode.displayName(),
                    oldNode.description(),
                    oldNode.icon(),
                    oldNode.maxLevel(),
                    newLevel,
                    costNextLevel,
                    newState,
                    oldNode.requires(),
                    oldNode.requiresMode(),
                    oldNode.positionX(),
                    oldNode.positionY(),
                    oldNode.textureOverrideLocked(),
                    oldNode.textureOverrideAvailable(),
                    oldNode.textureOverridePartial(),
                    oldNode.textureOverrideMaxed()
            );
            tree.nodes().put(nodeId, newNode);
            LOGGER.debug("Updated node {}.{} to level {}", treeId, nodeId, newLevel);
            
            // Also update available points for the relevant point type
            if (tree.pointType() != null) {
                String pointType = tree.pointType();
                SkillPointsData oldPoints = cachedSkillPoints.get(pointType);
                int totalEarned = (oldPoints != null) ? oldPoints.totalEarned() : 0;
                cachedSkillPoints.put(pointType, new SkillPointsData(pointsAvailable, totalEarned));
                LOGGER.debug("Updated points for type '{}': available={}", pointType, pointsAvailable);
            }

            // Recalculate states for all nodes in this tree
            // so neighboring nodes that depend on this node update immediately
            recalculateAllNodeStates(treeId);
            ClientEventBridge.skillDataDirty = true;
        }
    }

    /**
     * Recalculates the state of every node in a tree based on current node levels.
     * Called after any node level change so neighboring nodes update immediately
     * (e.g. a LOCKED node that just became AVAILABLE because its requirement was met).
     */
    private void recalculateAllNodeStates(String treeId) {
        SkillTreeData tree = cachedSkillTrees.get(treeId);
        if (tree == null) return;

        Map<String, SkillNodeData> nodes = tree.nodes();

        boolean changed = true;
        int maxPasses = 10; // safety limit to prevent infinite loops
        int pass = 0;
        while (changed && pass < maxPasses) {
            changed = false;
            pass++;
            for (Map.Entry<String, SkillNodeData> entry : nodes.entrySet()) {
                String nodeId = entry.getKey();
                SkillNodeData node = entry.getValue();
                String newState = calculateClientNodeState(node, nodes);

                if (!newState.equals(node.state())) {
                    SkillNodeData updated = new SkillNodeData(
                            node.id(), node.displayName(), node.description(), node.icon(),
                            node.maxLevel(), node.currentLevel(), node.costNextLevel(),
                            newState, node.requires(), node.requiresMode(),
                            node.positionX(), node.positionY(),
                            node.textureOverrideLocked(),
                            node.textureOverrideAvailable(),
                            node.textureOverridePartial(),
                            node.textureOverrideMaxed()
                    );
                    nodes.put(nodeId, updated);
                    LOGGER.debug("Pass {}: recalculated state for {}.{}: {} -> {}",
                            pass, treeId, nodeId, node.state(), newState);
                    changed = true;
                }
            }
        }
    }

    /**
     * Calculates the state of a single node based on its current level
     * and the levels of its required nodes.
     */
    private String calculateClientNodeState(SkillNodeData node,
                                            Map<String, SkillNodeData> allNodes) {
        int currentLevel = node.currentLevel();

        if (currentLevel >= node.maxLevel()) return "MAXED";
        if (currentLevel > 0) return "PARTIAL";

        // currentLevel == 0: check requirements
        java.util.List<SkillRequirementData> requires = node.requires();
        if (requires == null || requires.isEmpty()) return "AVAILABLE";

        boolean isAll = !"any".equalsIgnoreCase(node.requiresMode());

        for (SkillRequirementData req : requires) {
            SkillNodeData reqNode = allNodes.get(req.nodeId());
            int reqCurrentLevel = (reqNode != null) ? reqNode.currentLevel() : 0;
            boolean met = reqCurrentLevel >= req.minLevel();

            if (isAll && !met) return "LOCKED";
            if (!isAll && met) return "AVAILABLE";
        }

        // All mode: all requirements passed -> AVAILABLE
        // Any mode: no requirement passed -> LOCKED
        return isAll ? "AVAILABLE" : "LOCKED";
    }
}
