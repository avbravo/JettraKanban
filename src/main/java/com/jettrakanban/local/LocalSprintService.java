package com.jettrakanban.local;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LocalSprintService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path SPRINT_ROOT = Path.of(".jettra-sprints");

    private LocalSprintService() {
    }

    public enum SprintStatus {
        OPEN,
        CLOSED
    }

    public record SprintSummary(int finished, int pending, int inProgress, int total) {
    }

    public record Sprint(String id,
                         String name,
                         String startDate,
                         String endDate,
                         SprintStatus status,
                         String createdAt,
                         String closedAt,
                         SprintSummary summary) {
        public String label() {
            String period = (startDate == null ? "" : startDate) + " -> " + (endDate == null ? "" : endDate);
            if (status == SprintStatus.OPEN) {
                return name + " [Abierto] (" + period + ")";
            }
            return name + " [Cerrado] (" + period + ")";
        }
    }

    public record CreateSprintResult(Sprint sprint, List<KanbanCard> updatedCards, int movedCards) {
    }

    public record CloseSprintResult(Sprint sprint, SprintSummary summary) {
    }

    public record DeleteSprintResult(Sprint deletedSprint, List<KanbanCard> updatedCards, int unassignedCards) {
    }

    public static List<Sprint> listSprints(String projectName) throws IOException {
        ensureRoot();
        Path path = sprintFile(projectName);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        List<Sprint> sprints = MAPPER.readValue(path.toFile(), new TypeReference<>() {
        });
        sprints.sort(Comparator.comparing(Sprint::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return sprints;
    }

    public static Sprint findOpenSprint(String projectName) throws IOException {
        for (Sprint sprint : listSprints(projectName)) {
            if (sprint.status() == SprintStatus.OPEN) {
                return sprint;
            }
        }
        return null;
    }

    public static CreateSprintResult createSprint(String projectName,
                                                  String sprintName,
                                                  LocalDate startDate,
                                                  LocalDate endDate,
                                                  List<KanbanCard> cards) throws IOException {
        if (sprintName == null || sprintName.isBlank()) {
            throw new IOException("El nombre del sprint no puede estar vacio.");
        }

        List<Sprint> sprints = listSprints(projectName);
        for (Sprint sprint : sprints) {
            if (sprint.status() == SprintStatus.OPEN) {
                throw new IOException("Solo puede haber un sprint abierto por proyecto.");
            }
        }

        Sprint sprint = new Sprint(
                UUID.randomUUID().toString(),
                sprintName.trim(),
                startDate.toString(),
                endDate.toString(),
                SprintStatus.OPEN,
                LocalDateTime.now().toString(),
                "",
                null
        );

        Set<String> closedSprintIds = new HashSet<>();
        for (Sprint existing : sprints) {
            if (existing.status() == SprintStatus.CLOSED) {
                closedSprintIds.add(existing.id());
            }
        }

        List<KanbanCard> updatedCards = new ArrayList<>(cards);
        int movedCards = 0;
        for (KanbanCard card : updatedCards) {
            if (card.column() == KanbanColumn.DONE) {
                continue;
            }

            String sprintId = card.sprintId();
            if (sprintId == null || sprintId.isBlank() || closedSprintIds.contains(sprintId)) {
                card.setSprintId(sprint.id());
                movedCards++;
            }
        }

        sprints.add(sprint);
        saveSprints(projectName, sprints);
        return new CreateSprintResult(sprint, updatedCards, movedCards);
    }

    public static CloseSprintResult closeOpenSprint(String projectName, List<KanbanCard> cards) throws IOException {
        List<Sprint> sprints = listSprints(projectName);
        Sprint openSprint = null;
        int openIndex = -1;

        for (int i = 0; i < sprints.size(); i++) {
            if (sprints.get(i).status() == SprintStatus.OPEN) {
                openSprint = sprints.get(i);
                openIndex = i;
                break;
            }
        }

        if (openSprint == null) {
            throw new IOException("No hay sprint abierto para cerrar.");
        }

        SprintSummary summary = summarizeSprint(cards, openSprint.id());
        Sprint closed = new Sprint(
                openSprint.id(),
                openSprint.name(),
                openSprint.startDate(),
                openSprint.endDate(),
                SprintStatus.CLOSED,
                openSprint.createdAt(),
                LocalDateTime.now().toString(),
                summary
        );

        sprints.set(openIndex, closed);
        saveSprints(projectName, sprints);
        return new CloseSprintResult(closed, summary);
    }

    public static Sprint reopenSprint(String projectName, String sprintId) throws IOException {
        if (sprintId == null || sprintId.isBlank()) {
            throw new IOException("Sprint invalido para reabrir.");
        }

        List<Sprint> sprints = listSprints(projectName);
        Sprint target = null;
        int targetIndex = -1;

        for (int i = 0; i < sprints.size(); i++) {
            Sprint sprint = sprints.get(i);
            if (sprint.status() == SprintStatus.OPEN && !Objects.equals(sprint.id(), sprintId)) {
                throw new IOException("Ya existe un sprint abierto en este proyecto.");
            }
            if (Objects.equals(sprint.id(), sprintId)) {
                target = sprint;
                targetIndex = i;
            }
        }

        if (target == null) {
            throw new IOException("No se encontro el sprint seleccionado.");
        }
        if (target.status() == SprintStatus.OPEN) {
            return target;
        }

        Sprint reopened = new Sprint(
                target.id(),
                target.name(),
                target.startDate(),
                target.endDate(),
                SprintStatus.OPEN,
                target.createdAt(),
                "",
                null
        );

        sprints.set(targetIndex, reopened);
        saveSprints(projectName, sprints);
        return reopened;
    }

    public static Sprint editSprint(String projectName,
                                    String sprintId,
                                    String sprintName,
                                    LocalDate startDate,
                                    LocalDate endDate) throws IOException {
        if (sprintId == null || sprintId.isBlank()) {
            throw new IOException("Sprint invalido para editar.");
        }
        if (sprintName == null || sprintName.isBlank()) {
            throw new IOException("El nombre del sprint no puede estar vacio.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IOException("La fecha fin no puede ser menor que la fecha inicio.");
        }

        List<Sprint> sprints = listSprints(projectName);
        for (int i = 0; i < sprints.size(); i++) {
            Sprint sprint = sprints.get(i);
            if (Objects.equals(sprint.id(), sprintId)) {
                Sprint edited = new Sprint(
                        sprint.id(),
                        sprintName.trim(),
                        startDate.toString(),
                        endDate.toString(),
                        sprint.status(),
                        sprint.createdAt(),
                        sprint.closedAt(),
                        sprint.summary()
                );
                sprints.set(i, edited);
                saveSprints(projectName, sprints);
                return edited;
            }
        }

        throw new IOException("No se encontro el sprint seleccionado.");
    }

    public static DeleteSprintResult deleteSprint(String projectName,
                                                  String sprintId,
                                                  List<KanbanCard> cards) throws IOException {
        if (sprintId == null || sprintId.isBlank()) {
            throw new IOException("Sprint invalido para eliminar.");
        }

        List<Sprint> sprints = listSprints(projectName);
        Sprint deleted = null;
        for (int i = 0; i < sprints.size(); i++) {
            Sprint sprint = sprints.get(i);
            if (Objects.equals(sprint.id(), sprintId)) {
                deleted = sprint;
                sprints.remove(i);
                break;
            }
        }

        if (deleted == null) {
            throw new IOException("No se encontro el sprint seleccionado.");
        }

        List<KanbanCard> updatedCards = new ArrayList<>(cards);
        int unassigned = 0;
        for (KanbanCard card : updatedCards) {
            if (Objects.equals(sprintId, card.sprintId())) {
                card.setSprintId(null);
                unassigned++;
            }
        }

        saveSprints(projectName, sprints);
        return new DeleteSprintResult(deleted, updatedCards, unassigned);
    }

    public static Sprint sprintById(List<Sprint> sprints, String sprintId) {
        if (sprintId == null || sprintId.isBlank()) {
            return null;
        }
        for (Sprint sprint : sprints) {
            if (Objects.equals(sprint.id(), sprintId)) {
                return sprint;
            }
        }
        return null;
    }

    private static SprintSummary summarizeSprint(List<KanbanCard> cards, String sprintId) {
        int finished = 0;
        int pending = 0;
        int inProgress = 0;

        for (KanbanCard card : cards) {
            if (!Objects.equals(sprintId, card.sprintId())) {
                continue;
            }

            if (card.column() == KanbanColumn.DONE) {
                finished++;
            } else if (card.column() == KanbanColumn.BACKLOG || card.column() == KanbanColumn.TODO) {
                pending++;
            } else {
                inProgress++;
            }
        }

        return new SprintSummary(finished, pending, inProgress, finished + pending + inProgress);
    }

    private static void saveSprints(String projectName, List<Sprint> sprints) throws IOException {
        ensureRoot();
        Path path = sprintFile(projectName);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), sprints);
    }

    private static Path sprintFile(String projectName) {
        String safe = projectName == null ? "" : projectName.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
        return SPRINT_ROOT.resolve(safe + ".json");
    }

    private static void ensureRoot() throws IOException {
        Files.createDirectories(SPRINT_ROOT);
    }
}
