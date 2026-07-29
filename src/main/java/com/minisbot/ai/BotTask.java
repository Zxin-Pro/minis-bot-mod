package com.minisbot.ai;

/**
 * 单个任务单元
 */
public class BotTask {
    private final TaskType type;
    private final String target;       // 目标描述 (矿物名、玩家名、方块名)
    private int progress;               // 进度 0-100
    private boolean completed;
    private String result;

    public BotTask(TaskType type, String target) {
        this.type = type;
        this.target = target;
        this.progress = 0;
        this.completed = false;
        this.result = "";
    }

    public TaskType getType() { return type; }
    public String getTarget() { return target; }
    public int getProgress() { return progress; }
    public boolean isCompleted() { return completed; }
    public String getResult() { return result; }

    public void setProgress(int progress) { this.progress = Math.min(100, Math.max(0, progress)); }
    public void complete(String result) {
        this.completed = true;
        this.progress = 100;
        this.result = result;
    }

    public void fail(String reason) {
        this.completed = true;
        this.result = "❌ " + reason;
    }

    @Override
    public String toString() {
        return type.name() + "[" + target + "]" + (completed ? " ✓" : " " + progress + "%");
    }
}
