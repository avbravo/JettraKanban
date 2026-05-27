package com.jettrakanban.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KanbanCard {
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String itemId;
    private final String contentId;
    private final boolean draftIssue;
    private String title;
    private String body;
    private KanbanColumn column;
    private String createdBy;
    private LocalDateTime createdAt;
    private String sprintId;
    private String sourceProject;

    public KanbanCard(String itemId, String contentId, boolean draftIssue, String title, String body, KanbanColumn column) {
        this(itemId, contentId, draftIssue, title, body, column, null, null, null);
    }

    public KanbanCard(String itemId, String contentId, boolean draftIssue, String title, String body, KanbanColumn column, String createdBy, LocalDateTime createdAt) {
        this(itemId, contentId, draftIssue, title, body, column, createdBy, createdAt, null);
    }

    public KanbanCard(String itemId, String contentId, boolean draftIssue, String title, String body,
                      KanbanColumn column, String createdBy, LocalDateTime createdAt, String sprintId) {
        this.itemId = itemId;
        this.contentId = contentId;
        this.draftIssue = draftIssue;
        this.title = title;
        this.body = body;
        this.column = column;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.sprintId = sprintId;
    }

    public String itemId() {
        return itemId;
    }

    public String contentId() {
        return contentId;
    }

    public boolean isDraftIssue() {
        return draftIssue;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String body() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public KanbanColumn column() {
        return column;
    }

    public void setColumn(KanbanColumn column) {
        this.column = column;
    }

    public String createdBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String createdAtFormatted() {
        if (createdAt == null) return "";
        return createdAt.format(DISPLAY_FMT);
    }

    public String sprintId() {
        return sprintId;
    }

    public void setSprintId(String sprintId) {
        this.sprintId = sprintId;
    }

    public String sourceProject() {
        return sourceProject;
    }

    public void setSourceProject(String sourceProject) {
        this.sourceProject = sourceProject;
    }
}
