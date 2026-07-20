package com.eventui.core.skill;

import com.eventui.api.skill.PlayerSkillProgress;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSkillProgressImpl implements PlayerSkillProgress {

    private final UUID playerId;
    private final Map<String, Map<String, Integer>> nodeLevels;      // treeId -> (nodeId -> level)
    private final Map<String, Integer> availablePoints;               // pointType -> amount
    private final Map<String, Integer> totalEarnedPoints;             // pointType -> amount
    private final Map<String, Map<String, String>> selectedBranches;  // treeId -> (groupId -> branchId)

    public PlayerSkillProgressImpl(UUID playerId) {
        this.playerId = playerId;
        this.nodeLevels = new ConcurrentHashMap<>();
        this.availablePoints = new ConcurrentHashMap<>();
        this.totalEarnedPoints = new ConcurrentHashMap<>();
        this.selectedBranches = new ConcurrentHashMap<>();
    }

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public int getNodeLevel(String treeId, String nodeId) {
        Map<String, Integer> tree = nodeLevels.get(treeId);
        if (tree == null) return 0;
        return tree.getOrDefault(nodeId, 0);
    }

    @Override
    public void setNodeLevel(String treeId, String nodeId, int level) {
        nodeLevels.computeIfAbsent(treeId, k -> new ConcurrentHashMap<>())
                .put(nodeId, Math.max(0, level));
    }

    @Override
    public int getAvailablePoints(String pointType) {
        return availablePoints.getOrDefault(pointType, 0);
    }

    @Override
    public int getTotalEarnedPoints(String pointType) {
        return totalEarnedPoints.getOrDefault(pointType, 0);
    }

    @Override
    public void setAvailablePoints(String pointType, int amount) {
        availablePoints.put(pointType, Math.max(0, amount));
    }

    @Override
    public void setTotalEarnedPoints(String pointType, int amount) {
        totalEarnedPoints.put(pointType, Math.max(0, amount));
    }

    @Override
    public void addEarnedPoints(String pointType, int amount) {
        if (amount <= 0) return;
        availablePoints.put(pointType, getAvailablePoints(pointType) + amount);
        totalEarnedPoints.put(pointType, getTotalEarnedPoints(pointType) + amount);
    }

    @Override
    public void resetTreeProgress(String treeId) {
        nodeLevels.remove(treeId);
        selectedBranches.remove(treeId);
    }

    @Override
    public String getSelectedBranch(String treeId, String groupId) {
        Map<String, String> treeBranches = selectedBranches.get(treeId);
        if (treeBranches == null) return null;
        return treeBranches.get(groupId);
    }

    @Override
    public void setSelectedBranch(String treeId, String groupId, String branchId) {
        selectedBranches.computeIfAbsent(treeId, k -> new ConcurrentHashMap<>())
                .put(groupId, branchId);
    }

    @Override
    public Map<String, String> getSelectedBranches(String treeId) {
        Map<String, String> treeBranches = selectedBranches.get(treeId);
        if (treeBranches == null) return Map.of();
        return Map.copyOf(treeBranches);
    }

    public void spendPoints(String pointType, int amount) {
        if (amount <= 0) return;
        int current = availablePoints.getOrDefault(pointType, 0);
        int remaining = Math.max(0, current - amount);
        availablePoints.put(pointType, remaining);
    }

    public void resetAllPoints() {
        availablePoints.clear();
        totalEarnedPoints.clear();
    }

    public void resetPoints(String pointType) {
        availablePoints.remove(pointType);
        totalEarnedPoints.remove(pointType);
    }

    // Helper methods for persistence
    public Map<String, Map<String, Integer>> getNodeLevelsSnapshot() {
        Map<String, Map<String, Integer>> snapshot = new HashMap<>();
        for (var entry : nodeLevels.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public Map<String, Integer> getAvailablePointsSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(availablePoints));
    }

    public Map<String, Integer> getTotalEarnedPointsSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(totalEarnedPoints));
    }

    public java.util.Set<String> getAllPointTypes() {
        return Collections.unmodifiableSet(availablePoints.keySet());
    }
}
