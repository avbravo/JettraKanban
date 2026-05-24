package com.jettrakanban.ui;

import com.jettrakanban.config.AppConfig;
import com.jettrakanban.github.GitHubProjectService;
import com.jettrakanban.local.LocalProjectService;
import com.jettrakanban.model.KanbanCard;
import com.jettrakanban.model.KanbanColumn;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KanbanBoardView extends BorderPane {
    private static final Pattern PROJECT_URL_PATTERN = Pattern.compile(
            "^https?://github\\.com/(?:users|orgs)/([^/]+)/projects/(\\d+)(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final AppConfig appConfig;

    // Storage mode selection
    private final ComboBox<String> modeComboBox = new ComboBox<>();

    // GitHub fields
    private final TextField projectUrlField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField tokenField = new PasswordField();
    private final CheckBox rememberCredentialsBox = new CheckBox("Recordar credenciales");

    // Local storage fields
    private final Label localProjectLabel = new Label("Proyecto:");
    private final ComboBox<String> localProjectComboBox = new ComboBox<>();
    private final Button loadLocalBtn = new Button("Cargar");
    private final Button newLocalBtn = new Button("Nuevo Proyecto");

    private final Label projectTitleLabel = new Label("JettraKanban - Offline");
    private final Label statusLabel = new Label("Ingresa URL de proyecto + credenciales para sincronizar.");

    private final Map<KanbanColumn, VBox> columnPanes = new EnumMap<>(KanbanColumn.class);
    private final Map<String, KanbanCard> cardsByItemId = new HashMap<>();

    // Common buttons promoted to instance variables
    private final Button connectBtn = new Button("Conectar");
    private final Button forgetBtn = new Button("Olvidar credenciales");
    private final Button refreshBtn = new Button("Sincronizar");
    private final Button newCardBtn = new Button("+ Nueva Tarjeta");

    private GitHubProjectService service;
    private String statusFieldId = "";
    private Map<KanbanColumn, String> optionByColumn = new EnumMap<>(KanbanColumn.class);

    private boolean localMode = false;
    private String activeLocalProject = null;

    public KanbanBoardView() {
        this.appConfig = new AppConfig();
        buildUi();
        loadConfigDefaults();
    }

    private void buildUi() {
        getStyleClass().add("app-root");

        setTop(buildTopBar());
        setCenter(buildBoard());
        setBottom(buildFooter());
    }

    private HBox buildTopBar() {
        modeComboBox.getItems().addAll("Modo: GitHub", "Modo: Local");
        modeComboBox.setValue("Modo: GitHub");
        modeComboBox.setOnAction(event -> updateModeVisibility());

        projectUrlField.setPromptText("URL del GitHub Project (ej: https://github.com/users/usuario/projects/1)");
        projectUrlField.setMinWidth(420);
        usernameField.setPromptText("GitHub Username");
        passwordField.setPromptText("GitHub Password");
        tokenField.setPromptText("GitHub Token (opcional)");
        rememberCredentialsBox.setSelected(false);

        connectBtn.setOnAction(event -> connectAndRefresh());
        forgetBtn.setOnAction(event -> forgetCredentials());
        refreshBtn.setOnAction(event -> refreshBoard());

        newCardBtn.getStyleClass().add("cta-btn");
        newCardBtn.setOnAction(event -> onCreateCard());

        // Configure Local Storage controls
        localProjectComboBox.setMinWidth(180);
        localProjectComboBox.setPromptText("Selecciona proyecto");
        loadLocalBtn.setOnAction(event -> loadLocalProject(localProjectComboBox.getValue()));
        newLocalBtn.setOnAction(event -> onCreateLocalProject());

        // Hide local controls by default
        localProjectLabel.setVisible(false);
        localProjectLabel.setManaged(false);
        localProjectComboBox.setVisible(false);
        localProjectComboBox.setManaged(false);
        loadLocalBtn.setVisible(false);
        loadLocalBtn.setManaged(false);
        newLocalBtn.setVisible(false);
        newLocalBtn.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12,
                projectTitleLabel,
                modeComboBox,
                spacer,
                projectUrlField,
                usernameField,
                passwordField,
                tokenField,
                rememberCredentialsBox,
                connectBtn,
                forgetBtn,
                localProjectLabel,
                localProjectComboBox,
                loadLocalBtn,
                newLocalBtn,
                refreshBtn,
                newCardBtn
        );
        topBar.setPadding(new Insets(14));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");
        return topBar;
    }

    private void updateModeVisibility() {
        boolean isLocal = "Modo: Local".equals(modeComboBox.getValue());
        localMode = isLocal;

        // GitHub controls visibility
        projectUrlField.setVisible(!isLocal);
        projectUrlField.setManaged(!isLocal);
        usernameField.setVisible(!isLocal);
        usernameField.setManaged(!isLocal);
        passwordField.setVisible(!isLocal);
        passwordField.setManaged(!isLocal);
        tokenField.setVisible(!isLocal);
        tokenField.setManaged(!isLocal);
        rememberCredentialsBox.setVisible(!isLocal);
        rememberCredentialsBox.setManaged(!isLocal);
        connectBtn.setVisible(!isLocal);
        connectBtn.setManaged(!isLocal);
        forgetBtn.setVisible(!isLocal);
        forgetBtn.setManaged(!isLocal);

        // Local controls visibility
        localProjectLabel.setVisible(isLocal);
        localProjectLabel.setManaged(isLocal);
        localProjectComboBox.setVisible(isLocal);
        localProjectComboBox.setManaged(isLocal);
        loadLocalBtn.setVisible(isLocal);
        loadLocalBtn.setManaged(isLocal);
        newLocalBtn.setVisible(isLocal);
        newLocalBtn.setManaged(isLocal);

        // Update refresh button text
        refreshBtn.setText(isLocal ? "Recargar" : "Sincronizar");

        if (isLocal) {
            refreshLocalProjectsList();
            if (activeLocalProject != null) {
                localProjectComboBox.setValue(activeLocalProject);
            } else if (!localProjectComboBox.getItems().isEmpty()) {
                localProjectComboBox.setValue(localProjectComboBox.getItems().get(0));
            }
        }
    }

    private void refreshLocalProjectsList() {
        List<String> projects = new java.util.ArrayList<>();
        try {
            java.io.File dir = new java.io.File(".");
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".md"));
            if (files != null) {
                for (java.io.File file : files) {
                    String name = file.getName();
                    if (!name.equalsIgnoreCase("README.md") && 
                        !name.equalsIgnoreCase("credentials.md") && 
                        !name.equalsIgnoreCase("plan.md")) {
                        projects.add(name.substring(0, name.length() - 3));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        localProjectComboBox.getItems().setAll(projects);
    }

    private void onCreateLocalProject() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Nuevo Proyecto");
        dialog.setHeaderText("Crear nuevo proyecto local");
        dialog.setContentText("Nombre del proyecto:");

        dialog.showAndWait().ifPresent(name -> {
            name = name.trim();
            if (name.isEmpty()) return;

            String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            java.nio.file.Path path = java.nio.file.Path.of(safeName + ".md");
            if (java.nio.file.Files.exists(path)) {
                showError("Proyecto existente", "Ya existe un proyecto con ese nombre.");
                return;
            }

            try {
                com.jettrakanban.local.LocalProjectService.saveBoard(safeName, name, java.util.List.of());
                refreshLocalProjectsList();
                localProjectComboBox.setValue(safeName);
                loadLocalProject(safeName);
            } catch (IOException e) {
                showError("Error al crear proyecto", e.getMessage());
            }
        });
    }

    private void loadLocalProject(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            showError("Sin proyecto", "Selecciona o escribe un nombre de proyecto.");
            return;
        }
        activeLocalProject = projectName;
        refreshBoard();
    }

    private ScrollPane buildBoard() {
        HBox columns = new HBox(16);
        columns.setPadding(new Insets(14));

        for (KanbanColumn column : KanbanColumn.values()) {
            VBox container = createColumnPane(column);
            columnPanes.put(column, container);
            columns.getChildren().add(container.getParent());
            HBox.setHgrow(container.getParent(), Priority.ALWAYS);
        }

        ScrollPane scrollPane = new ScrollPane(columns);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("board-scroll");
        return scrollPane;
    }

    private HBox buildFooter() {
        HBox footer = new HBox(statusLabel);
        footer.setPadding(new Insets(10, 14, 14, 14));
        footer.getStyleClass().add("footer");
        return footer;
    }

    private VBox createColumnPane(KanbanColumn column) {
        Label title = new Label(column.displayName());
        title.getStyleClass().add("column-title");

        VBox cardsBox = new VBox(10);
        cardsBox.getStyleClass().add("cards-box");
        cardsBox.setMinWidth(250);
        cardsBox.setFillWidth(true);

        cardsBox.setOnDragOver(event -> {
            if (event.getGestureSource() != cardsBox && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        cardsBox.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean completed = false;
            if (dragboard.hasString()) {
                String itemId = dragboard.getString();
                onMoveCard(itemId, column);
                completed = true;
            }
            event.setDropCompleted(completed);
            event.consume();
        });

        VBox wrapper = new VBox(10, title, cardsBox);
        wrapper.setPadding(new Insets(12));
        wrapper.getStyleClass().add("column-pane");
        VBox.setVgrow(cardsBox, Priority.ALWAYS);

        HBox holder = new HBox(wrapper);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return cardsBox;
    }

    private VBox createCardNode(KanbanCard card) {
        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label body = new Label(card.body());
        body.getStyleClass().add("card-body");
        body.setWrapText(true);

        ComboBox<KanbanColumn> moveCombo = new ComboBox<>();
        moveCombo.getItems().addAll(KanbanColumn.values());
        moveCombo.setValue(card.column());
        moveCombo.setCellFactory(cell -> new KanbanColumnListCell());
        moveCombo.setButtonCell(new KanbanColumnListCell());
        moveCombo.setOnAction(event -> {
            KanbanColumn target = moveCombo.getValue();
            if (target != null && target != card.column()) {
                onMoveCard(card.itemId(), target);
            }
        });

        Button editBtn = new Button("Editar");
        editBtn.setOnAction(event -> onEditCard(card));

        HBox controls = new HBox(8, moveCombo, editBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox cardPane = new VBox(8, title, body, controls);
        cardPane.getStyleClass().add("card-pane");
        cardPane.setPadding(new Insets(10));

        cardPane.setOnDragDetected(event -> {
            Dragboard dragboard = cardPane.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(card.itemId());
            dragboard.setContent(content);
            event.consume();
        });

        return cardPane;
    }

    private void loadConfigDefaults() {
        AppConfig.StoredCredentials encrypted = appConfig.loadEncryptedCredentials();

        usernameField.setText(nonBlankOrDefault(appConfig.githubUsername(), encrypted.username()));
        passwordField.setText(nonBlankOrDefault(appConfig.githubPassword(), encrypted.password()));
        tokenField.setText(nonBlankOrDefault(appConfig.githubToken(), encrypted.token()));
        projectUrlField.setText(nonBlankOrDefault(appConfig.githubProjectUrl(), encrypted.projectUrl()));
        rememberCredentialsBox.setSelected(appConfig.hasEncryptedCredentials());
    }

    private String nonBlankOrDefault(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private void connectAndRefresh() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String token = tokenField.getText().trim();
        String projectUrl = projectUrlField.getText().trim();

        ProjectLocator locator = parseProjectUrl(projectUrl);
        if (locator == null) {
            showError("URL invalida", "Usa un URL como https://github.com/users/usuario/projects/1 o https://github.com/orgs/org/projects/1");
            return;
        }

        String authorizationHeader;
        if (!token.isBlank()) {
            authorizationHeader = "Bearer " + token;
        } else if (!username.isBlank() && !password.isBlank()) {
            String userPass = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8));
            authorizationHeader = "Basic " + encoded;
        } else {
            showError("Faltan credenciales", "Indica token o username + password.");
            return;
        }

        service = new GitHubProjectService(authorizationHeader, locator.ownerLogin(), locator.projectNumber());
        statusFieldId = "";
        optionByColumn = new EnumMap<>(KanbanColumn.class);
        cardsByItemId.clear();

        try {
            if (rememberCredentialsBox.isSelected()) {
                appConfig.saveEncryptedCredentials(username, password, token, projectUrl);
            } else {
                appConfig.deleteEncryptedCredentials();
            }
        } catch (IOException ex) {
            setStatus("No se pudo actualizar credentials.md: " + ex.getMessage());
        }

        setStatus("Proyecto activo: " + locator.ownerLogin() + " / #" + locator.projectNumber());
        refreshBoard();
    }

    private void forgetCredentials() {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("JettraKanban");
        confirm.setHeaderText("Olvidar credenciales");
        confirm.setContentText("Se eliminara credentials.md y se limpiaran los campos guardados. Quieres continuar?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            appConfig.deleteEncryptedCredentials();
            usernameField.clear();
            passwordField.clear();
            tokenField.clear();
            projectUrlField.clear();
            rememberCredentialsBox.setSelected(false);
            service = null;
            setStatus("Credenciales olvidadas y credentials.md eliminado.");
        } catch (IOException ex) {
            showError("No se pudo olvidar", ex.getMessage());
        }
    }

    private ProjectLocator parseProjectUrl(String url) {
        Matcher matcher = PROJECT_URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return null;
        }

        String owner = matcher.group(1);
        int number;
        try {
            number = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }

        return new ProjectLocator(owner, number);
    }

    private void refreshBoard() {
        if (localMode) {
            if (activeLocalProject == null || activeLocalProject.isBlank()) {
                showError("Sin proyecto", "Selecciona o crea un proyecto local primero.");
                return;
            }
            setStatus("Cargando tarjetas locales...");
            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .thenAccept(snapshot -> Platform.runLater(() -> {
                        projectTitleLabel.setText("JettraKanban | [Local] " + snapshot.projectTitle());
                        statusFieldId = "";
                        optionByColumn.clear();
                        renderCards(snapshot.cards());
                        setStatus("Proyecto local cargado: " + snapshot.cards().size() + " tarjetas.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
                            showError("Error al cargar proyecto", message);
                            setStatus("No se pudo cargar el proyecto local.");
                        });
                        return null;
                    });
        } else {
            if (service == null) {
                showError("Sin proyecto", "Conecta tus credenciales y define la URL del proyecto.");
                return;
            }

            setStatus("Sincronizando tarjetas...");

            CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return service.fetchBoard();
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .thenAccept(snapshot -> Platform.runLater(() -> {
                        projectTitleLabel.setText("JettraKanban | " + snapshot.projectTitle());
                        statusFieldId = snapshot.statusFieldId();
                        optionByColumn = new EnumMap<>(snapshot.statusOptionByColumn());
                        renderCards(snapshot.cards());
                        setStatus("Sincronizado: " + snapshot.cards().size() + " tarjetas cargadas.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
                            showError("Error de sincronizacion", message);
                            setStatus("No se pudo sincronizar. Revisa credenciales, permisos y URL del proyecto.");
                        });
                        return null;
                    });
        }
    }

    private void renderCards(List<KanbanCard> cards) {
        cardsByItemId.clear();
        for (VBox pane : columnPanes.values()) {
            pane.getChildren().clear();
        }

        List<KanbanCard> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparing(KanbanCard::title, String.CASE_INSENSITIVE_ORDER));

        for (KanbanCard card : sorted) {
            cardsByItemId.put(card.itemId(), card);
            columnPanes.get(card.column()).getChildren().add(createCardNode(card));
        }
    }

    private void onCreateCard() {
        if (localMode) {
            if (activeLocalProject == null) {
                showError("Sin proyecto", "Crea o selecciona un proyecto local primero.");
                return;
            }
            CardEditorDialog.show("Nueva tarjeta", "", "", KanbanColumn.BACKLOG)
                    .ifPresent(input -> CompletableFuture.runAsync(() -> {
                        try {
                            com.jettrakanban.github.GitHubProjectService.BoardSnapshot snapshot = 
                                    com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                            List<KanbanCard> cards = new ArrayList<>(snapshot.cards());
                            String itemId = UUID.randomUUID().toString();
                            cards.add(new KanbanCard(itemId, itemId, true, input.title(), input.body(), input.column()));
                            com.jettrakanban.local.LocalProjectService.saveBoard(activeLocalProject, snapshot.projectTitle(), cards);
                            Platform.runLater(() -> {
                                setStatus("Tarjeta creada localmente: " + input.title());
                                refreshBoard();
                            });
                        } catch (IOException e) {
                            Platform.runLater(() -> showError("No se pudo crear la tarjeta", e.getMessage()));
                        }
                    }));
        } else {
            if (service == null) {
                showError("Sin conexion", "Conectate primero a un GitHub Project.");
                return;
            }

            CardEditorDialog.show("Nueva tarjeta", "", "", KanbanColumn.BACKLOG)
                    .ifPresent(input -> CompletableFuture.runAsync(() -> {
                        try {
                            service.createCard(input.title(), input.body(), input.column(), statusFieldId, optionByColumn);
                            Platform.runLater(() -> {
                                setStatus("Tarjeta creada: " + input.title());
                                refreshBoard();
                            });
                        } catch (IOException | InterruptedException e) {
                            Platform.runLater(() -> showError("No se pudo crear", e.getMessage()));
                        }
                    }));
        }
    }

    private void onEditCard(KanbanCard card) {
        if (localMode) {
            if (activeLocalProject == null) return;
            CardEditorDialog.show("Editar tarjeta", card.title(), card.body(), card.column())
                    .ifPresent(input -> CompletableFuture.runAsync(() -> {
                        try {
                            com.jettrakanban.github.GitHubProjectService.BoardSnapshot snapshot = 
                                    com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                            List<KanbanCard> cards = snapshot.cards();
                            for (KanbanCard c : cards) {
                                if (c.itemId().equals(card.itemId())) {
                                    c.setTitle(input.title());
                                    c.setBody(input.body());
                                    c.setColumn(input.column());
                                    break;
                                }
                            }
                            com.jettrakanban.local.LocalProjectService.saveBoard(activeLocalProject, snapshot.projectTitle(), cards);
                            Platform.runLater(() -> {
                                setStatus("Tarjeta actualizada localmente: " + input.title());
                                refreshBoard();
                            });
                        } catch (IOException e) {
                            Platform.runLater(() -> showError("No se pudo editar la tarjeta", e.getMessage()));
                        }
                    }));
        } else {
            if (service == null) {
                return;
            }

            CardEditorDialog.show("Editar tarjeta", card.title(), card.body(), card.column())
                    .ifPresent(input -> CompletableFuture.runAsync(() -> {
                        try {
                            service.updateCard(card, input.title(), input.body());
                            if (input.column() != card.column()) {
                                service.moveCard(card.itemId(), input.column(), statusFieldId, optionByColumn);
                                card.setColumn(input.column());
                            }

                            Platform.runLater(() -> {
                                setStatus("Tarjeta actualizada: " + input.title());
                                refreshBoard();
                            });
                        } catch (IOException | InterruptedException e) {
                            Platform.runLater(() -> showError("No se pudo editar", e.getMessage()));
                        }
                    }));
        }
    }

    private void onMoveCard(String itemId, KanbanColumn targetColumn) {
        KanbanCard card = cardsByItemId.get(itemId);
        if (card == null || card.column() == targetColumn) {
            return;
        }

        if (localMode) {
            if (activeLocalProject == null) return;
            CompletableFuture.runAsync(() -> {
                try {
                    com.jettrakanban.github.GitHubProjectService.BoardSnapshot snapshot = 
                            com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                    List<KanbanCard> cards = snapshot.cards();
                    for (KanbanCard c : cards) {
                        if (c.itemId().equals(itemId)) {
                            c.setColumn(targetColumn);
                            break;
                        }
                    }
                    com.jettrakanban.local.LocalProjectService.saveBoard(activeLocalProject, snapshot.projectTitle(), cards);
                    Platform.runLater(() -> {
                        setStatus("Tarjeta movida localmente: " + card.title());
                        refreshBoard();
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> showError("No se pudo mover la tarjeta", e.getMessage()));
                }
            });
        } else {
            if (service == null) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    service.moveCard(itemId, targetColumn, statusFieldId, optionByColumn);
                    card.setColumn(targetColumn);
                    Platform.runLater(() -> {
                        setStatus("Tarjeta movida a " + targetColumn.displayName() + ": " + card.title());
                        refreshBoard();
                    });
                } catch (IOException | InterruptedException e) {
                    Platform.runLater(() -> showError("No se pudo mover", e.getMessage()));
                }
            });
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("JettraKanban");
        alert.setHeaderText(title);
        alert.setContentText(content == null ? "Error desconocido" : content);
        alert.showAndWait();
    }

    private record ProjectLocator(String ownerLogin, int projectNumber) {
    }
}
