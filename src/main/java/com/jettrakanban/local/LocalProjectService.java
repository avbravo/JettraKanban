package com.jettrakanban.local;

import com.jettrakanban.github.GitHubProjectService.BoardSnapshot;
import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalProjectService {

    public static final String ALL_PROJECT_NAME = "All";

    private static final String[] EXCLUDED_PROJECT_FILES = {"README.md", "credentials.md", "plan.md", "config.properties", "All.md"};

    private static final Pattern META_PATTERN = Pattern.compile("<!--\\s*jettra-meta\\s+.*-->");
    private static final Pattern META_ATTRIBUTE_PATTERN = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");

    public static List<String> listProjectNames() throws IOException {
        List<String> projects = new ArrayList<>();
        try (var stream = Files.list(Path.of("."))) {
            stream.filter(path -> path.getFileName().toString().endsWith(".md"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !isExcludedProjectFile(name))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(name -> projects.add(name.substring(0, name.length() - 3)));
        }
        return projects;
    }

    public static BoardSnapshot loadBoard(String projectName) throws IOException {
        if (ALL_PROJECT_NAME.equals(projectName)) {
            throw new IOException("El proyecto 'All' es una vista virtual y no tiene archivo propio.");
        }
        Path path = projectPath(projectName);
        if (!Files.exists(path)) {
            throw new IOException("El archivo " + projectName + ".md no existe.");
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String projectTitle = projectName;
        List<KanbanCard> cards = new ArrayList<>();

        KanbanColumn currentColumn = KanbanColumn.BACKLOG;
        String currentCardTitle = null;
        StringBuilder currentCardBody = new StringBuilder();
        String currentCreatedBy = null;
        LocalDateTime currentCreatedAt = null;
        String currentSprintId = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (line.startsWith("# ")) {
                projectTitle = line.substring(2).trim();
            } else if (line.startsWith("## ")) {
                // Save previous card if exists
                if (currentCardTitle != null) {
                    String itemId = UUID.randomUUID().toString();
                    cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn, currentCreatedBy, currentCreatedAt, currentSprintId));
                    currentCardTitle = null;
                    currentCardBody.setLength(0);
                    currentCreatedBy = null;
                    currentCreatedAt = null;
                    currentSprintId = null;
                }
                String colName = line.substring(3).trim();
                currentColumn = KanbanColumn.fromStatusName(colName);
            } else if (line.startsWith("### ")) {
                // Save previous card if exists
                if (currentCardTitle != null) {
                    String itemId = UUID.randomUUID().toString();
                    cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn, currentCreatedBy, currentCreatedAt, currentSprintId));
                    currentCardBody.setLength(0);
                    currentCreatedBy = null;
                    currentCreatedAt = null;
                    currentSprintId = null;
                }
                currentCardTitle = line.substring(4).trim();
            } else {
                if (currentCardTitle != null) {
                    Matcher m = META_PATTERN.matcher(trimmed);
                    if (m.matches()) {
                        String createdBy = extractMetaAttribute(trimmed, "created-by");
                        String sprintId = extractMetaAttribute(trimmed, "sprint-id");

                        currentCreatedBy = createdBy == null || createdBy.isBlank() ? null : createdBy;
                        currentSprintId = sprintId == null || sprintId.isBlank() ? null : sprintId;
                        try {
                            String atStr = extractMetaAttribute(trimmed, "created-at");
                            if (atStr != null && !atStr.isBlank()) {
                                currentCreatedAt = LocalDateTime.parse(atStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            }
                        } catch (Exception ignored) {
                        }
                    } else {
                        if (currentCardBody.length() > 0) {
                            currentCardBody.append("\n");
                        }
                        currentCardBody.append(line);
                    }
                }
            }
        }

        // Save last card if exists
        if (currentCardTitle != null) {
            String itemId = UUID.randomUUID().toString();
            cards.add(new KanbanCard(itemId, itemId, true, currentCardTitle, currentCardBody.toString().trim(), currentColumn, currentCreatedBy, currentCreatedAt, currentSprintId));
        }

        return new BoardSnapshot(projectTitle, cards, "", new HashMap<>());
    }

    public static List<KanbanCard> loadAllBoards(List<String> projectNames) throws IOException {
        List<KanbanCard> allCards = new ArrayList<>();
        for (String project : projectNames) {
            try {
                BoardSnapshot snapshot = loadBoard(project);
                for (KanbanCard card : snapshot.cards()) {
                    card.setSourceProject(project);
                    allCards.add(card);
                }
            } catch (IOException ignored) {
                // Skip projects that cannot be loaded
            }
        }
        return allCards;
    }

    public static void saveBoard(String projectName, String projectTitle, List<KanbanCard> cards) throws IOException {
        Path path = projectPath(projectName);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectTitle).append("\n\n");

        for (KanbanColumn col : KanbanColumn.values()) {
            sb.append("## ").append(col.displayName()).append("\n");
            for (KanbanCard card : cards) {
                if (card.column() == col) {
                    sb.append("### ").append(card.title()).append("\n");
                    if (card.createdBy() != null || card.createdAt() != null || card.sprintId() != null) {
                        String by = card.createdBy() != null ? card.createdBy() : "";
                        String at = card.createdAt() != null
                                ? card.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "";
                    String sprintId = card.sprintId() != null ? card.sprintId() : "";
                        sb.append("<!-- jettra-meta created-by=\"").append(by)
                        .append("\" created-at=\"").append(at)
                        .append("\" sprint-id=\"").append(sprintId)
                        .append("\" -->\n");
                    }
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

    public static void renameProject(String currentProjectName, String newProjectName, String newProjectTitle) throws IOException {
        String normalizedCurrent = normalizeProjectName(currentProjectName);
        String normalizedNew = normalizeProjectName(newProjectName);
        if (normalizedCurrent.isBlank()) {
            throw new IOException("El proyecto actual no es valido.");
        }

        if (normalizedNew.isBlank()) {
            throw new IOException("El nuevo nombre del proyecto no puede estar vacio.");
        }

        if (!normalizedCurrent.equals(normalizedNew) && Files.exists(projectPath(normalizedNew))) {
            throw new IOException("Ya existe un proyecto con ese nombre.");
        }

        BoardSnapshot snapshot = loadBoard(normalizedCurrent);
        List<KanbanCard> cards = new ArrayList<>(snapshot.cards());
        saveBoard(normalizedNew, newProjectTitle, cards);

        if (!normalizedCurrent.equals(normalizedNew)) {
            try {
                Files.deleteIfExists(projectPath(normalizedCurrent));
            } catch (IOException ex) {
                Files.deleteIfExists(projectPath(normalizedNew));
                throw ex;
            }
        }
    }

    public static void deleteProject(String projectName) throws IOException {
        String normalized = normalizeProjectName(projectName);
        if (normalized.isBlank()) {
            throw new IOException("El proyecto no es valido.");
        }

        Path path = projectPath(normalized);
        if (!Files.deleteIfExists(path)) {
            throw new IOException("El archivo " + normalized + ".md no existe.");
        }
    }

    private static Path projectPath(String projectName) {
        return Path.of(normalizeProjectName(projectName) + ".md");
    }

    private static boolean isExcludedProjectFile(String fileName) {
        for (String excluded : EXCLUDED_PROJECT_FILES) {
            if (excluded.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeProjectName(String projectName) {
        return projectName == null ? "" : projectName.trim();
    }

    private static String extractMetaAttribute(String line, String attributeName) {
        Matcher matcher = META_ATTRIBUTE_PATTERN.matcher(line);
        while (matcher.find()) {
            if (attributeName.equalsIgnoreCase(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }
}
