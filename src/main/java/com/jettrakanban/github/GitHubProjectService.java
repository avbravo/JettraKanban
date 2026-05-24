package com.jettrakanban.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GitHubProjectService {
    private static final URI GITHUB_GRAPHQL = URI.create("https://api.github.com/graphql");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String authorizationHeader;
    private final String ownerLogin;
    private final int projectNumber;

    private String resolvedProjectId;

    public GitHubProjectService(String authorizationHeader, String ownerLogin, int projectNumber) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
        this.authorizationHeader = authorizationHeader;
        this.ownerLogin = ownerLogin;
        this.projectNumber = projectNumber;
    }

    public BoardSnapshot fetchBoard() throws IOException, InterruptedException {
        JsonNode project = resolveProjectNode();
        StatusMeta statusMeta = resolveStatusMeta(project.path("fields").path("nodes"));
        List<KanbanCard> cards = new ArrayList<>();

        for (JsonNode item : project.path("items").path("nodes")) {
            JsonNode content = item.path("content");
            if (content.isMissingNode() || content.isNull()) {
                continue;
            }

            String title = content.path("title").asText("");
            if (title.isBlank()) {
                continue;
            }

            String body = content.path("body").asText("");
            String itemId = item.path("id").asText();
            String contentId = content.path("id").asText();
            boolean isDraft = "DraftIssue".equals(content.path("__typename").asText(""));

            String statusName = "Backlog";
            for (JsonNode value : item.path("fieldValues").path("nodes")) {
                JsonNode field = value.path("field");
                if (statusMeta.fieldId().equals(field.path("id").asText())) {
                    statusName = value.path("name").asText("Backlog");
                    break;
                }
            }

            cards.add(new KanbanCard(itemId, contentId, isDraft, title, body, KanbanColumn.fromStatusName(statusName)));
        }

        return new BoardSnapshot(
                project.path("title").asText("Jettra Kanban"),
                cards,
                statusMeta.fieldId(),
                statusMeta.optionByColumn()
        );
    }

    public KanbanCard createCard(String title, String body, KanbanColumn column,
                                 String statusFieldId, Map<KanbanColumn, String> optionByColumn)
            throws IOException, InterruptedException {
        String mutation = """
                mutation($projectId: ID!, $title: String!, $body: String) {
                  addProjectV2DraftIssue(input: {
                    projectId: $projectId,
                    title: $title,
                    body: $body
                  }) {
                    projectItem {
                      id
                      content {
                        ... on DraftIssue {
                          id
                        }
                      }
                    }
                  }
                }
                """;

        JsonNode response = callGraphQl(mutation, Map.of(
                "projectId", ensureProjectId(),
                "title", title,
                "body", body
        ));

        JsonNode item = response.path("data").path("addProjectV2DraftIssue").path("projectItem");
        String itemId = item.path("id").asText();
        String contentId = item.path("content").path("id").asText();

        moveCard(itemId, column, statusFieldId, optionByColumn);

        return new KanbanCard(itemId, contentId, true, title, body, column);
    }

    public void updateCard(KanbanCard card, String newTitle, String newBody) throws IOException, InterruptedException {
        String mutation;
        Map<String, Object> variables;

        if (card.isDraftIssue()) {
            mutation = """
                    mutation($draftIssueId: ID!, $title: String!, $body: String) {
                      updateProjectV2DraftIssue(input: {
                        draftIssueId: $draftIssueId,
                        title: $title,
                        body: $body
                      }) {
                        draftIssue {
                          id
                        }
                      }
                    }
                    """;
            variables = Map.of(
                    "draftIssueId", card.contentId(),
                    "title", newTitle,
                    "body", newBody
            );
        } else {
            mutation = """
                    mutation($issueId: ID!, $title: String!, $body: String) {
                      updateIssue(input: {
                        id: $issueId,
                        title: $title,
                        body: $body
                      }) {
                        issue {
                          id
                        }
                      }
                    }
                    """;
            variables = Map.of(
                    "issueId", card.contentId(),
                    "title", newTitle,
                    "body", newBody
            );
        }

        callGraphQl(mutation, variables);
        card.setTitle(newTitle);
        card.setBody(newBody);
    }

    public void moveCard(String itemId, KanbanColumn targetColumn,
                         String statusFieldId, Map<KanbanColumn, String> optionByColumn)
            throws IOException, InterruptedException {
        String optionId = optionByColumn.get(targetColumn);
        if (optionId == null || optionId.isBlank()) {
            throw new IOException("No status option found for column " + targetColumn.displayName());
        }

        String mutation = """
                mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
                  updateProjectV2ItemFieldValue(input: {
                    projectId: $projectId,
                    itemId: $itemId,
                    fieldId: $fieldId,
                    value: {
                      singleSelectOptionId: $optionId
                    }
                  }) {
                    projectV2Item {
                      id
                    }
                  }
                }
                """;

        callGraphQl(mutation, Map.of(
                "projectId", ensureProjectId(),
                "itemId", itemId,
                "fieldId", statusFieldId,
                "optionId", optionId
        ));
    }

    private JsonNode resolveProjectNode() throws IOException, InterruptedException {
        String query = """
                query($login: String!, $number: Int!) {
                  userOwner: user(login: $login) {
                    projectV2(number: $number) {
                      id
                      title
                      fields(first: 50) {
                        nodes {
                          ... on ProjectV2SingleSelectField {
                            id
                            name
                            options {
                              id
                              name
                            }
                          }
                        }
                      }
                      items(first: 100) {
                        nodes {
                          id
                          fieldValues(first: 30) {
                            nodes {
                              ... on ProjectV2ItemFieldSingleSelectValue {
                                name
                                optionId
                                field {
                                  ... on ProjectV2SingleSelectField {
                                    id
                                    name
                                  }
                                }
                              }
                            }
                          }
                          content {
                            __typename
                            ... on DraftIssue {
                              id
                              title
                              body
                            }
                            ... on Issue {
                              id
                              title
                              body
                            }
                          }
                        }
                      }
                    }
                  }
                  orgOwner: organization(login: $login) {
                    projectV2(number: $number) {
                      id
                      title
                      fields(first: 50) {
                        nodes {
                          ... on ProjectV2SingleSelectField {
                            id
                            name
                            options {
                              id
                              name
                            }
                          }
                        }
                      }
                      items(first: 100) {
                        nodes {
                          id
                          fieldValues(first: 30) {
                            nodes {
                              ... on ProjectV2ItemFieldSingleSelectValue {
                                name
                                optionId
                                field {
                                  ... on ProjectV2SingleSelectField {
                                    id
                                    name
                                  }
                                }
                              }
                            }
                          }
                          content {
                            __typename
                            ... on DraftIssue {
                              id
                              title
                              body
                            }
                            ... on Issue {
                              id
                              title
                              body
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        JsonNode response = callGraphQl(query, Map.of(
                "login", ownerLogin,
                "number", projectNumber
        ));

        JsonNode userProject = response.path("data").path("userOwner").path("projectV2");
        if (!userProject.isMissingNode() && !userProject.isNull()) {
            resolvedProjectId = userProject.path("id").asText("");
            return userProject;
        }

        JsonNode orgProject = response.path("data").path("orgOwner").path("projectV2");
        if (!orgProject.isMissingNode() && !orgProject.isNull()) {
            resolvedProjectId = orgProject.path("id").asText("");
            return orgProject;
        }

        throw new IOException("Project not found. Verifica que el URL de proyecto sea valido y accesible.");
    }

    private String ensureProjectId() throws IOException, InterruptedException {
        if (resolvedProjectId == null || resolvedProjectId.isBlank()) {
            resolveProjectNode();
        }
        return resolvedProjectId;
    }

    private StatusMeta resolveStatusMeta(JsonNode fields) throws IOException {
        for (JsonNode field : fields) {
            String name = field.path("name").asText("");
            if (!"status".equalsIgnoreCase(name)) {
                continue;
            }

            String fieldId = field.path("id").asText("");
            if (fieldId.isBlank()) {
                break;
            }

            Map<KanbanColumn, String> options = new LinkedHashMap<>();
            for (JsonNode option : field.path("options")) {
                String optionId = option.path("id").asText("");
                KanbanColumn column = KanbanColumn.fromStatusName(option.path("name").asText("Backlog"));
                if (!optionId.isBlank() && !options.containsKey(column)) {
                    options.put(column, optionId);
                }
            }

            for (KanbanColumn column : KanbanColumn.values()) {
                options.putIfAbsent(column, options.get(KanbanColumn.BACKLOG));
            }

            return new StatusMeta(fieldId, options);
        }

        throw new IOException("Status field not found in Project. Add a single-select field named 'Status'.");
    }

    private JsonNode callGraphQl(String query, Map<String, Object> variables) throws IOException, InterruptedException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("variables", variables);

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder(GITHUB_GRAPHQL)
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", authorizationHeader)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode errors = json.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            JsonNode firstError = errors.get(0);
            String message = firstError.path("message").asText("Unknown error");

            if (message.toLowerCase().contains("required scopes")) {
                throw new IOException(
                        "Tu token no tiene permisos suficientes para GitHub Projects v2. "
                                + "Crea un token con scope 'project' y, si usas repos privados, tambien 'repo'. "
                                + "Si usas fine-grained token, habilita Projects (Read and write) e Issues (Read and write)."
                );
            }

            throw new IOException("GitHub GraphQL error: " + message);
        }

        return json;
    }

    private record StatusMeta(String fieldId, Map<KanbanColumn, String> optionByColumn) {
    }

    public record BoardSnapshot(String projectTitle, List<KanbanCard> cards,
                                String statusFieldId, Map<KanbanColumn, String> statusOptionByColumn) {
    }
}
