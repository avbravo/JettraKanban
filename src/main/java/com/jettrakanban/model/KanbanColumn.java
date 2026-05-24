package com.jettrakanban.model;

public enum KanbanColumn {
    BACKLOG("Backlog"),
    TODO("To Do"),
    IN_PROGRESS("In Progress"),
    REVIEW("Review"),
    DONE("Done");

    private final String displayName;

    KanbanColumn(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static KanbanColumn fromStatusName(String statusName) {
        if (statusName == null) {
            return BACKLOG;
        }

        return switch (statusName.trim().toLowerCase()) {
            case "todo", "to do", "to-do" -> TODO;
            case "in progress", "in-progress", "doing" -> IN_PROGRESS;
            case "review", "in review" -> REVIEW;
            case "done", "closed", "complete", "completed" -> DONE;
            default -> BACKLOG;
        };
    }
}
