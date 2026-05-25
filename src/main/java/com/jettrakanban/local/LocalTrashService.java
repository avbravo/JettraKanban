package com.jettrakanban.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LocalTrashService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TRASH_ROOT = Path.of(".jettrakanban-trash");
    private static final Path TRASH_PROJECTS = TRASH_ROOT.resolve("projects");
    private static final Path TRASH_CARDS = TRASH_ROOT.resolve("cards");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private LocalTrashService() {
    }

    public enum TrashKind {
        PROJECT,
        CARD
    }

    public record TrashEntry(TrashKind kind, String displayName, String sourceProject, String sourceTitle,
                             Path filePath, LocalDateTime trashedAt) {
        public String label() {
            String when = trashedAt == null ? "" : trashedAt.format(DISPLAY_TS);
            if (kind == TrashKind.PROJECT) {
                String projectPart = sourceProject == null || sourceProject.isBlank() ? "" : "  ·  Proyecto: " + sourceProject;
                return "[Proyecto] " + displayName + projectPart + (when.isBlank() ? "" : "  ·  " + when);
            }
            String projectPart = sourceProject == null || sourceProject.isBlank() ? "" : "  ·  Proyecto: " + sourceProject;
            String titlePart = sourceTitle == null || sourceTitle.isBlank() ? "" : "  ·  Tablero: " + sourceTitle;
            return "[Tarjeta] " + displayName + projectPart + titlePart + (when.isBlank() ? "" : "  ·  " + when);
        }
    }

    public static List<TrashEntry> listTrashEntries() throws IOException {
        ensureTrashDirectories();
        List<TrashEntry> entries = new ArrayList<>();

        try (var stream = Files.list(TRASH_PROJECTS)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> entries.add(readTrashEntry(path)));
        }

        try (var stream = Files.list(TRASH_CARDS)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> entries.add(readTrashEntry(path)));
        }

        entries.sort(Comparator.comparing(TrashEntry::trashedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return entries;
    }

    public static void trashProject(String projectName) throws IOException {
        String normalizedProjectName = normalize(projectName);
        if (normalizedProjectName.isBlank()) {
            throw new IOException("El proyecto no es valido.");
        }

        Path source = Path.of(normalizedProjectName + ".md");
        if (!Files.exists(source)) {
            throw new IOException("El archivo " + normalizedProjectName + ".md no existe.");
        }

        BoardSnapshot snapshot = LocalProjectService.loadBoard(normalizedProjectName);
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("kind", TrashKind.PROJECT.name());
        payload.put("projectName", normalizedProjectName);
        payload.put("projectTitle", snapshot.projectTitle());
        payload.put("markdown", Files.readString(source, StandardCharsets.UTF_8));
        payload.put("trashedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Path target = TRASH_PROJECTS.resolve(timestamp() + "__" + safeToken(normalizedProjectName) + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
        Files.deleteIfExists(source);
    }

    public static void trashCard(String projectName, KanbanCard referenceCard) throws IOException {
        String normalizedProjectName = normalize(projectName);
        if (normalizedProjectName.isBlank()) {
            throw new IOException("El proyecto no es valido.");
        }

        BoardSnapshot snapshot = LocalProjectService.loadBoard(normalizedProjectName);
        List<KanbanCard> remainingCards = new ArrayList<>(snapshot.cards());
        boolean removed = removeMatchingCard(remainingCards, referenceCard);
        if (!removed) {
            throw new IOException("No se encontro la tarjeta en el proyecto local.");
        }

        LocalProjectService.saveBoard(normalizedProjectName, snapshot.projectTitle(), remainingCards);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("kind", TrashKind.CARD.name());
        payload.put("projectName", normalizedProjectName);
        payload.put("projectTitle", snapshot.projectTitle());
        payload.put("title", referenceCard.title());
        payload.put("body", referenceCard.body() == null ? "" : referenceCard.body());
        payload.put("column", referenceCard.column().name());
        payload.put("createdBy", referenceCard.createdBy() == null ? "" : referenceCard.createdBy());
        payload.put("createdAt", referenceCard.createdAt() == null ? "" : referenceCard.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("sprintId", referenceCard.sprintId() == null ? "" : referenceCard.sprintId());
        payload.put("trashedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        Path target = TRASH_CARDS.resolve(timestamp() + "__" + safeToken(normalizedProjectName) + "__" + safeToken(referenceCard.title()) + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
    }

    public static void restoreTrashEntry(TrashEntry entry) throws IOException {
        if (entry.kind() == TrashKind.PROJECT) {
            restoreProject(entry.filePath());
        } else {
            restoreCard(entry.filePath());
        }
    }

    public static void deleteTrashEntry(TrashEntry entry) throws IOException {
        Files.deleteIfExists(entry.filePath());
    }

    private static void restoreProject(Path filePath) throws IOException {
        JsonNode payload = MAPPER.readTree(filePath.toFile());
        String projectName = payload.path("projectName").asText("").trim();
        String markdown = payload.path("markdown").asText("");
        if (projectName.isBlank()) {
            throw new IOException("La entrada de papelera no tiene proyecto de origen.");
        }

        Path target = Path.of(projectName + ".md");
        if (Files.exists(target)) {
            throw new IOException("Ya existe un proyecto activo con ese nombre.");
        }

        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        Files.deleteIfExists(filePath);
    }

    private static void restoreCard(Path filePath) throws IOException {
        JsonNode payload = MAPPER.readTree(filePath.toFile());
        String projectName = payload.path("projectName").asText("").trim();
        if (projectName.isBlank()) {
            throw new IOException("La tarjeta no tiene proyecto de origen.");
        }

        BoardSnapshot snapshot = LocalProjectService.loadBoard(projectName);
        List<KanbanCard> cards = new ArrayList<>(snapshot.cards());

        String title = payload.path("title").asText("");
        String body = payload.path("body").asText("");
        KanbanColumn column = KanbanColumn.fromStatusName(payload.path("column").asText(KanbanColumn.BACKLOG.displayName()));
        String createdBy = payload.path("createdBy").asText("");
        String createdAtText = payload.path("createdAt").asText("");
        String sprintId = payload.path("sprintId").asText("");
        LocalDateTime createdAt = createdAtText.isBlank() ? null : LocalDateTime.parse(createdAtText, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        cards.add(new KanbanCard(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true,
                title, body, column,
                createdBy.isBlank() ? null : createdBy,
            createdAt,
            sprintId.isBlank() ? null : sprintId));

        LocalProjectService.saveBoard(projectName, snapshot.projectTitle(), cards);
        Files.deleteIfExists(filePath);
    }

    private static TrashEntry readTrashEntry(Path path) {
        try {
            JsonNode payload = MAPPER.readTree(path.toFile());
            TrashKind kind = TrashKind.valueOf(payload.path("kind").asText(TrashKind.CARD.name()));
            LocalDateTime trashedAt = parseDateTime(payload.path("trashedAt").asText(""));
            if (kind == TrashKind.PROJECT) {
                String projectName = payload.path("projectName").asText("");
                String projectTitle = payload.path("projectTitle").asText(projectName);
                return new TrashEntry(kind, projectTitle, projectName, projectTitle, path, trashedAt);
            }

            String title = payload.path("title").asText("Tarjeta sin titulo");
            String projectName = payload.path("projectName").asText("");
            String projectTitle = payload.path("projectTitle").asText(projectName);
            return new TrashEntry(kind, title, projectName, projectTitle, path, trashedAt);
        } catch (Exception ex) {
            return new TrashEntry(TrashKind.CARD, path.getFileName().toString(), "", "", path, null);
        }
    }

    private static boolean removeMatchingCard(List<KanbanCard> cards, KanbanCard referenceCard) {
        for (int i = 0; i < cards.size(); i++) {
            KanbanCard candidate = cards.get(i);
            if (Objects.equals(candidate.title(), referenceCard.title())
                    && Objects.equals(candidate.body(), referenceCard.body())
                    && candidate.column() == referenceCard.column()
                    && Objects.equals(candidate.createdBy(), referenceCard.createdBy())
                    && Objects.equals(candidate.createdAt(), referenceCard.createdAt())) {
                cards.remove(i);
                return true;
            }
        }
        return false;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureTrashDirectories() throws IOException {
        Files.createDirectories(TRASH_PROJECTS);
        Files.createDirectories(TRASH_CARDS);
    }

    private static String timestamp() {
        return LocalDateTime.now().format(FILE_TS);
    }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}