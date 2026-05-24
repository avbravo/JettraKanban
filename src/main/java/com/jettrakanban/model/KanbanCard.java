package com.jettrakanban.model;

public class KanbanCard {
    private final String itemId;
    private final String contentId;
    private final boolean draftIssue;
    private String title;
    private String body;
    private KanbanColumn column;

    public KanbanCard(String itemId, String contentId, boolean draftIssue, String title, String body, KanbanColumn column) {
        this.itemId = itemId;
        this.contentId = contentId;
        this.draftIssue = draftIssue;
        this.title = title;
        this.body = body;
        this.column = column;
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
}
