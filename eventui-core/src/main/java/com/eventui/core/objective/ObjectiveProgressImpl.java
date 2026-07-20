package com.eventui.core.objective;

import com.eventui.api.objective.ObjectiveProgress;

public class ObjectiveProgressImpl implements ObjectiveProgress {

    private final String objectiveId;
    private final int targetAmount;
    private volatile int currentAmount;
    private volatile boolean completed;

    public ObjectiveProgressImpl(String objectiveId, int targetAmount) {
        this.objectiveId = objectiveId;
        this.targetAmount = targetAmount;
        this.currentAmount = 0;
        this.completed = false;
    }

    public ObjectiveProgressImpl(String objectiveId, int targetAmount, int currentAmount) {
        this.objectiveId = objectiveId;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.completed = currentAmount >= targetAmount;
    }

    @Override
    public String getObjectiveId() {
        return objectiveId;
    }

    @Override
    public int getCurrentAmount() {
        return currentAmount;
    }

    @Override
    public int getTargetAmount() {
        return targetAmount;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

        public synchronized boolean increment(int amount) {
        if (completed) {
            return false;
        }

        currentAmount += amount;

        if (currentAmount >= targetAmount) {
            currentAmount = targetAmount;
            completed = true;
            return true;
        }

        return false;
    }
    
    public synchronized void decrement(int amount) {
        currentAmount = Math.max(0, currentAmount - amount);
        completed = currentAmount >= targetAmount;
    }

        public synchronized boolean setProgress(int amount) {
        boolean wasCompleted = this.completed;
        this.currentAmount = Math.max(0, Math.min(amount, targetAmount));
        this.completed = this.currentAmount >= targetAmount;
        return !wasCompleted && this.completed;
    }

        public synchronized void reset() {
        this.currentAmount = 0;
        this.completed = false;
    }
}
