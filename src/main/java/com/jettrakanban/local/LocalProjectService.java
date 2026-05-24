package com.jettrakanban.local;

import com.jettrakanban.github.GitHubProjectService.BoardSnapshot;
import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class LocalProjectService {

    public static BoardSnapshot loadBoard(String projectName) throws IOException {
        Path path = Path.of(projectName + ".md");
        if (!Files.exists(path)) {
            throw new IOException("El archivo " + projectName + ".md no existe.");
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String projectTitle = projectName;
        List<KanbanCard> cards = new ArrayList<>();

        KanbanColumn currentColumn = KanbanColumn.BACKLOG;
        String currentCardTitle = null;
        StringBuilder currentCardBody = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (line.startsWith("# ")) {
                projectTitle = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                // Save previous card if exists
                if (currentCardTitle != null) {
                    String itemId = UUID.randomUUID().toString();
                    cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn));
                    currentCardTitle = null;
                    currentCardBody.setLength(0);
                }
                String colName = line.substring(3).trim();
                currentColumn = KanbanColumn.fromStatusName(colName);
            } else if (line.startsWith("### ")) {
                // Save previous card if exists
                if (currentCardTitle != null) {
                    String itemId = UUID.randomUUID().toString();
                    cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn));
                    currentCardBody.setLength(0);
                }
                currentCardTitle = line.substring(4).trim();
            } else {
                if (currentCardTitle != null) {
                    if (currentCardBody.length() > 0) {
                        currentCardBody.append("\n");
                    }
                    currentCardBody.append(line);
                }
            }
        }

        // Save last card if exists
        if (currentCardTitle != null) {
            String itemId = UUID.randomUUID().toString();
            cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn));
        }

        return new BoardSnapshot(projectTitle, cards, "", new HashMap<>());
    }

    public static void saveBoard(String projectName, String projectTitle, List<KanbanCard> cards) throws IOException {
        Path path = Path.of(projectName + ".md");
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectTitle).append("\n\n");

        for (KanbanColumn col : KanbanColumn.values()) {
            sb.append("## ").append(col.displayName()).append("\n");
            for (KanbanCard card : cards) {
                if (card.column() == col) {
                    sb.append("### ").append(card.title()).append("\n");
                    if (card.body() != null && !card.body().isBlank()) {
                        sb.append(card.body()).append("\n");
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }
}
