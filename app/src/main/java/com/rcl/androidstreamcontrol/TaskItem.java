package com.rcl.androidstreamcontrol;

public class TaskItem {
    public String id;
    public String title;
    public boolean isCompleted;
    public boolean isHabit;

    public TaskItem(String id, String title, boolean isCompleted, boolean isHabit) {
        this.id = id;
        this.title = title;
        this.isCompleted = isCompleted;
        this.isHabit = isHabit;
    }
}
