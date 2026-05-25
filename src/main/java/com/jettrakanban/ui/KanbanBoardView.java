package com.jettrakanban.ui;

import com.jettrakanban.config.AppConfig;
import com.jettrakanban.github.GitHubProjectService;
import com.jettrakanban.export.PdfExportService;
import com.jettrakanban.local.LocalProjectService;
import com.jettrakanban.local.LocalTrashService;
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
import javafx.scene.control.Dialog;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
    private final Button loadLocalBtn = new Button("↺");
    private final Button newLocalBtn = new Button("＋");
    private final Button editLocalBtn = new Button("✎");
    private final Button deleteLocalBtn = new Button("🗑");
    private final Button trashBtn = new Button("♻");
    private final Button exportPdfBtn = new Button("📄");

    private final Label projectTitleLabel = new Label("JettraKanban - Offline");
    private final Label statusLabel = new Label("Ingresa URL de proyecto + credenciales para sincronizar.");

    private final Map<KanbanColumn, VBox> columnPanes = new EnumMap<>(KanbanColumn.class);
    private final Map<String, KanbanCard> cardsByItemId = new HashMap<>();
    private final StackPane boardLayer = new StackPane();

    private final StackPane cardModalOverlay = new StackPane();
    private final Label cardModalHeader = new Label();
    private final Label cardMetaLabel = new Label();
    private final TextField cardTitleField = new TextField();
    private final TextArea cardBodyArea = new TextArea();
    private final ComboBox<KanbanColumn> cardColumnBox = new ComboBox<>();
    private Consumer<CardInput> cardModalSaveAction;

    // Common buttons promoted to instance variables
    private final Button connectBtn = new Button("🔗");
    private final Button forgetBtn = new Button("🧼");
    private final Button refreshBtn = new Button("⟳");
    private final Button newCardBtn = new Button("＋");

    private GitHubProjectService service;
    private String statusFieldId = "";
    private Map<KanbanColumn, String> optionByColumn = new EnumMap<>(KanbanColumn.class);
    private String currentProjectTitle = "JettraKanban - Offline";
    private List<KanbanCard> currentCardsSnapshot = new ArrayList<>();

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
        updateModeVisibility();
    }

    private HBox buildTopBar() {
        modeComboBox.getItems().addAll("Modo: GitHub", "Modo: Local");
        modeComboBox.setValue("Modo: Local");
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
        iconButton(connectBtn, "Conectar y cargar el tablero");
        iconButton(forgetBtn, "Olvidar credenciales guardadas");
        iconButton(refreshBtn, "Recargar tablero");

        newCardBtn.getStyleClass().add("cta-btn");
        newCardBtn.setOnAction(event -> onCreateCard());
        iconButton(newCardBtn, "Nueva tarjeta");
        exportPdfBtn.setOnAction(event -> onExportCardsPdf());
        iconButton(exportPdfBtn, "Exportar tarjetas a PDF");
        trashBtn.setOnAction(event -> onOpenTrash());
        iconButton(trashBtn, "Abrir papelera");

        // Configure Local Storage controls
        localProjectComboBox.setMinWidth(180);
        localProjectComboBox.setPromptText("Selecciona proyecto");
        localProjectComboBox.setOnAction(event -> {
            String selectedProject = localProjectComboBox.getValue();
            if (localMode && selectedProject != null && !selectedProject.isBlank() && !selectedProject.equals(activeLocalProject)) {
                loadLocalProject(selectedProject);
            }
        });
        loadLocalBtn.setOnAction(event -> loadLocalProject(localProjectComboBox.getValue()));
        newLocalBtn.setOnAction(event -> onCreateLocalProject());
        editLocalBtn.setOnAction(event -> onEditLocalProject());
        deleteLocalBtn.setOnAction(event -> onDeleteLocalProject());
        iconButton(loadLocalBtn, "Cargar proyecto local");
        iconButton(newLocalBtn, "Nuevo proyecto local");
        iconButton(editLocalBtn, "Editar proyecto local");
        iconButton(deleteLocalBtn, "Enviar proyecto a la papelera");

        // Hide local controls by default
        localProjectLabel.setVisible(false);
        localProjectLabel.setManaged(false);
        localProjectComboBox.setVisible(false);
        localProjectComboBox.setManaged(false);
        loadLocalBtn.setVisible(false);
        loadLocalBtn.setManaged(false);
        newLocalBtn.setVisible(false);
        newLocalBtn.setManaged(false);
        editLocalBtn.setVisible(false);
        editLocalBtn.setManaged(false);
        deleteLocalBtn.setVisible(false);
        deleteLocalBtn.setManaged(false);
        trashBtn.setVisible(false);
        trashBtn.setManaged(false);
        exportPdfBtn.setVisible(false);
        exportPdfBtn.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12,
                projectTitleLabel,
            newCardBtn,
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
                editLocalBtn,
                deleteLocalBtn,
                trashBtn,
                exportPdfBtn,
                refreshBtn
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
        editLocalBtn.setVisible(isLocal);
        editLocalBtn.setManaged(isLocal);
        deleteLocalBtn.setVisible(isLocal);
        deleteLocalBtn.setManaged(isLocal);
        trashBtn.setVisible(isLocal);
        trashBtn.setManaged(isLocal);
        exportPdfBtn.setVisible(true);
        exportPdfBtn.setManaged(true);

        // Keep icon-only button and adjust action hint by mode.
        refreshBtn.setTooltip(new Tooltip(isLocal ? "Recargar tablero local" : "Sincronizar con GitHub"));

        if (isLocal) {
            refreshLocalProjectsList();
            if (activeLocalProject != null) {
                localProjectComboBox.setValue(activeLocalProject);
            } else if (!localProjectComboBox.getItems().isEmpty()) {
                String firstProject = localProjectComboBox.getItems().get(0);
                localProjectComboBox.setValue(firstProject);
            }
        }
    }

    private void refreshLocalProjectsList() {
        try {
            List<String> projects = LocalProjectService.listProjectNames();
            localProjectComboBox.getItems().setAll(projects);
        } catch (IOException ex) {
            localProjectComboBox.getItems().clear();
            setStatus("No se pudo cargar la lista de proyectos locales: " + ex.getMessage());
        }
    }

    private void onCreateLocalProject() {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
        dialog.setTitle("Nuevo Proyecto");
        dialog.setHeaderText("Crear nuevo proyecto local");
        dialog.setContentText("Nombre del proyecto:");

        dialog.showAndWait().ifPresent(name -> {
            name = name.trim();
            if (name.isEmpty()) return;

            String safeName = sanitizeProjectName(name);
            if (safeName.isBlank()) {
                showError("Nombre invalido", "El nombre del proyecto no puede quedar vacio.");
                return;
            }

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

    private void onEditLocalProject() {
        if (activeLocalProject == null || activeLocalProject.isBlank()) {
            showError("Sin proyecto", "Selecciona un proyecto local para editarlo.");
            return;
        }

        try {
            GitHubProjectService.BoardSnapshot snapshot = LocalProjectService.loadBoard(activeLocalProject);
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(snapshot.projectTitle());
            dialog.setTitle("Editar Proyecto");
            dialog.setHeaderText("Renombrar proyecto local");
            dialog.setContentText("Nuevo nombre del proyecto:");

            dialog.showAndWait().ifPresent(name -> {
                String trimmed = name == null ? "" : name.trim();
                if (trimmed.isBlank()) {
                    showError("Nombre invalido", "El nombre del proyecto no puede quedar vacio.");
                    return;
                }

                String safeName = sanitizeProjectName(trimmed);
                try {
                    LocalProjectService.renameProject(activeLocalProject, safeName, trimmed);
                    activeLocalProject = safeName;
                    refreshLocalProjectsList();
                    localProjectComboBox.setValue(safeName);
                    loadLocalProject(safeName);
                    setStatus("Proyecto actualizado: " + trimmed);
                } catch (IOException ex) {
                    showError("Error al editar proyecto", ex.getMessage());
                }
            });
        } catch (IOException ex) {
            showError("Error al editar proyecto", ex.getMessage());
        }
    }

    private void onDeleteLocalProject() {
        if (activeLocalProject == null || activeLocalProject.isBlank()) {
            showError("Sin proyecto", "Selecciona un proyecto local para eliminarlo.");
            return;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("JettraKanban");
        confirm.setHeaderText("Eliminar proyecto local");
        try {
            GitHubProjectService.BoardSnapshot snapshot = LocalProjectService.loadBoard(activeLocalProject);
            confirm.setContentText("Se enviara a la papelera el proyecto \"" + snapshot.projectTitle() + "\" (archivo " + activeLocalProject + ".md). Quieres continuar?");
        } catch (IOException ex) {
            confirm.setContentText("Se enviara a la papelera el proyecto \"" + activeLocalProject + "\". Quieres continuar?");
        }

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            LocalTrashService.trashProject(activeLocalProject);
            String deletedProject = activeLocalProject;
            activeLocalProject = null;
            refreshLocalProjectsList();

            if (!localProjectComboBox.getItems().isEmpty()) {
                String nextProject = localProjectComboBox.getItems().get(0);
                localProjectComboBox.setValue(nextProject);
                loadLocalProject(nextProject);
            } else {
                clearLocalBoard("Proyecto eliminado: " + deletedProject);
            }
        } catch (IOException ex) {
            showError("Error al eliminar proyecto", ex.getMessage());
        }
    }

    private void loadLocalProject(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            showError("Sin proyecto", "Selecciona o escribe un nombre de proyecto.");
            return;
        }
        activeLocalProject = projectName;
        refreshBoard();
    }

    private void clearLocalBoard(String statusText) {
        cardsByItemId.clear();
        currentCardsSnapshot = new ArrayList<>();
        currentProjectTitle = "JettraKanban - Offline";
        for (VBox pane : columnPanes.values()) {
            pane.getChildren().clear();
        }
        projectTitleLabel.setText("JettraKanban - Offline");
        setStatus(statusText);
    }

    private StackPane buildBoard() {
        HBox columns = new HBox(16);
        columns.setPadding(new Insets(14));

        for (KanbanColumn column : KanbanColumn.values()) {
            VBox container = createColumnPane(column);
            columnPanes.put(column, container);
            columns.getChildren().add(container.getParent());
            HBox.setHgrow(container.getParent(), Priority.ALWAYS);
        }

        ScrollPane scrollPane = new ScrollPane(columns);
        // Keep content free to grow vertically so the board can scroll down with many cards.
        scrollPane.setFitToHeight(false);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("board-scroll");

        cardModalOverlay.getStyleClass().add("card-modal-overlay");
        cardModalOverlay.setVisible(false);
        cardModalOverlay.setManaged(false);

        VBox modalCard = new VBox(12);
        modalCard.getStyleClass().add("card-modal");
        modalCard.setMaxWidth(540);
        modalCard.setPadding(new Insets(20));

        cardModalHeader.getStyleClass().add("card-modal-title");

        cardMetaLabel.getStyleClass().add("card-meta");
        cardMetaLabel.setVisible(false);
        cardMetaLabel.setManaged(false);

        cardTitleField.setPromptText("Titulo de la tarjeta");
        cardBodyArea.setPromptText("Descripcion / acceptance criteria");
        cardBodyArea.setPrefRowCount(6);
        cardBodyArea.getStyleClass().add("modal-description-area");

        cardColumnBox.getItems().addAll(KanbanColumn.values());
        cardColumnBox.setCellFactory(cell -> new KanbanColumnListCell());
        cardColumnBox.setButtonCell(new KanbanColumnListCell());

        Label titleLabel = new Label("Titulo");
        Label bodyLabel = new Label("Descripcion");
        Label columnLabel = new Label("Columna");

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("subtle-btn");
        cancelBtn.setOnAction(event -> hideCardModal());
        cancelBtn.setText("✕");
        iconButton(cancelBtn, "Cerrar sin guardar");

        Button saveBtn = new Button("Guardar");
        saveBtn.disableProperty().bind(cardTitleField.textProperty().isEmpty());
        saveBtn.setOnAction(event -> {
            if (cardModalSaveAction == null) {
                hideCardModal();
                return;
            }
            CardInput input = new CardInput(
                    cardTitleField.getText().trim(),
                    cardBodyArea.getText().trim(),
                    cardColumnBox.getValue()
            );
            Consumer<CardInput> action = cardModalSaveAction;
            hideCardModal();
            action.accept(input);
        });
        saveBtn.setText("✔");
        iconButton(saveBtn, "Guardar tarjeta");

        HBox actions = new HBox(10, cancelBtn, saveBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        modalCard.getChildren().addAll(
                cardModalHeader,
                cardMetaLabel,
                titleLabel,
                cardTitleField,
                bodyLabel,
                cardBodyArea,
                columnLabel,
                cardColumnBox,
                actions
        );

        cardModalOverlay.getChildren().add(modalCard);
        cardModalOverlay.setAlignment(Pos.CENTER);

        boardLayer.getChildren().setAll(scrollPane, cardModalOverlay);
        return boardLayer;
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
        cardsBox.setMinHeight(420);
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

        // Accept drop on the whole column so moving cards also works when a column is empty.
        wrapper.setOnDragOver(event -> {
            if (event.getGestureSource() != wrapper && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        wrapper.setOnDragDropped(event -> {
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

        HBox holder = new HBox(wrapper);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        return cardsBox;
    }

    private VBox createCardNode(KanbanCard card) {
        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMouseTransparent(true);

        Label body = new Label(card.body());
        body.getStyleClass().add("card-body");
        body.setWrapText(true);
        body.setMouseTransparent(true);

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

        Button editBtn = new Button("✎");
        editBtn.setOnAction(event -> onEditCard(card));
        iconButton(editBtn, "Editar tarjeta");

        Button deleteBtn = new Button("🗑");
        deleteBtn.setOnAction(event -> onDeleteCard(card));
        iconButton(deleteBtn, "Enviar tarjeta a la papelera");

        HBox controls = new HBox(8, moveCombo, editBtn, deleteBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox cardPane = new VBox(8, title, body, controls);
        if (card.createdBy() != null || card.createdAt() != null) {
            StringBuilder metaText = new StringBuilder();
            if (card.createdBy() != null) metaText.append(card.createdBy());
            if (card.createdAt() != null) {
                if (metaText.length() > 0) metaText.append(" · ");
                metaText.append(card.createdAtFormatted());
            }
            Label metaLabel = new Label(metaText.toString());
            metaLabel.getStyleClass().add("card-meta");
            metaLabel.setMouseTransparent(true);
            cardPane.getChildren().add(metaLabel);
        }
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
                        currentProjectTitle = snapshot.projectTitle();
                        currentCardsSnapshot = new ArrayList<>(snapshot.cards());
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
                        currentProjectTitle = snapshot.projectTitle();
                        currentCardsSnapshot = new ArrayList<>(snapshot.cards());
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
        currentCardsSnapshot = new ArrayList<>(cards);
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
            String currentUser = resolveCurrentUser();
            openCardModal("Nueva tarjeta", "", "", KanbanColumn.BACKLOG, null, null, input -> CompletableFuture.runAsync(() -> {
                try {
                    com.jettrakanban.github.GitHubProjectService.BoardSnapshot snapshot =
                            com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                    List<KanbanCard> cards = new ArrayList<>(snapshot.cards());
                    String itemId = UUID.randomUUID().toString();
                    cards.add(new KanbanCard(itemId, itemId, true, input.title(), input.body(), input.column(), currentUser, LocalDateTime.now()));
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

            openCardModal("Nueva tarjeta", "", "", KanbanColumn.BACKLOG, null, null, input -> CompletableFuture.runAsync(() -> {
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

    private void onDeleteCard(KanbanCard card) {
        if (card == null) {
            return;
        }

        if (localMode) {
            if (activeLocalProject == null || activeLocalProject.isBlank()) {
                showError("Sin proyecto", "Selecciona un proyecto local primero.");
                return;
            }

            Alert confirm = new Alert(AlertType.CONFIRMATION);
            confirm.setTitle("JettraKanban");
            confirm.setHeaderText("Enviar tarjeta a la papelera");
            confirm.setContentText("Se movera la tarjeta \"" + card.title() + "\" del proyecto \"" + currentProjectTitle + "\" a la papelera. Quieres continuar?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    LocalTrashService.trashCard(activeLocalProject, card);
                    Platform.runLater(() -> {
                        setStatus("Tarjeta enviada a la papelera: " + card.title());
                        refreshBoard();
                    });
                } catch (IOException ex) {
                    Platform.runLater(() -> showError("No se pudo eliminar la tarjeta", ex.getMessage()));
                }
            });
        } else {
            showError("No soportado", "Eliminar tarjetas de GitHub todavía no esta implementado.");
        }
    }

    private void onEditCard(KanbanCard card) {
        if (localMode) {
            if (activeLocalProject == null) return;
            openCardModal("Editar tarjeta", card.title(), card.body(), card.column(), card.createdBy(), card.createdAt(), input -> CompletableFuture.runAsync(() -> {
                try {
                    com.jettrakanban.github.GitHubProjectService.BoardSnapshot snapshot =
                            com.jettrakanban.local.LocalProjectService.loadBoard(activeLocalProject);
                    List<KanbanCard> cards = snapshot.cards();
                    for (KanbanCard c : cards) {
                        if (matchesLocalCard(c, card)) {
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

            openCardModal("Editar tarjeta", card.title(), card.body(), card.column(), card.createdBy(), card.createdAt(), input -> CompletableFuture.runAsync(() -> {
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

    private void openCardModal(String header,
                               String initialTitle,
                               String initialBody,
                               KanbanColumn initialColumn,
                               String createdBy,
                               LocalDateTime createdAt,
                               Consumer<CardInput> onSave) {
        cardModalHeader.setText(header);
        cardTitleField.setText(initialTitle == null ? "" : initialTitle);
        cardBodyArea.setText(initialBody == null ? "" : initialBody);
        cardColumnBox.setValue(initialColumn == null ? KanbanColumn.BACKLOG : initialColumn);
        if (createdBy != null || createdAt != null) {
            String by = createdBy != null ? createdBy : "—";
            String at = createdAt != null
                    ? createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—";
            cardMetaLabel.setText("Creado por: " + by + "  ·  " + at);
            cardMetaLabel.setVisible(true);
            cardMetaLabel.setManaged(true);
        } else {
            cardMetaLabel.setText("");
            cardMetaLabel.setVisible(false);
            cardMetaLabel.setManaged(false);
        }
        cardModalSaveAction = onSave;
        cardModalOverlay.setManaged(true);
        cardModalOverlay.setVisible(true);
        Platform.runLater(cardTitleField::requestFocus);
    }

    private void hideCardModal() {
        cardModalSaveAction = null;
        cardModalOverlay.setVisible(false);
        cardModalOverlay.setManaged(false);
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
                        if (matchesLocalCard(c, card)) {
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

    private void onExportCardsPdf() {
        List<KanbanCard> cards = new ArrayList<>(currentCardsSnapshot);
        if (cards.isEmpty()) {
            showError("Sin tarjetas", "No hay tarjetas para exportar.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar tarjetas a PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        String suggested = sanitizePdfFileName(currentProjectTitle) + "-tarjetas.pdf";
        chooser.setInitialFileName(suggested);

        Window window = getScene() == null ? null : getScene().getWindow();
        java.io.File selected = chooser.showSaveDialog(window);
        if (selected == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                PdfExportService.exportBoard(selected.toPath(), currentProjectTitle, cards);
                Platform.runLater(() -> setStatus("PDF exportado: " + selected.getName()));
            } catch (IOException ex) {
                Platform.runLater(() -> showError("No se pudo exportar el PDF", ex.getMessage()));
            }
        });
    }

    private void onOpenTrash() {
        if (!localMode) {
            showError("Modo no soportado", "La papelera esta disponible en modo local.");
            return;
        }

        try {
            List<LocalTrashService.TrashEntry> entries = LocalTrashService.listTrashEntries();
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("JettraKanban");
            dialog.setHeaderText("Papelera de reciclaje");

            ListView<LocalTrashService.TrashEntry> listView = new ListView<>();
            listView.getItems().setAll(entries);
            listView.setCellFactory(view -> new ListCell<>() {
                @Override
                protected void updateItem(LocalTrashService.TrashEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.label());
                }
            });

            Label info = new Label("Restaurar recupera la tarjeta o proyecto. Eliminar permanente borra la entrada de la papelera.");
            info.setWrapText(true);

            Button restoreBtn = new Button("↺");
            Button deleteBtn = new Button("🗑");
            Button refreshTrashBtn = new Button("⟳");
            iconButton(restoreBtn, "Restaurar elemento seleccionado");
            iconButton(deleteBtn, "Eliminar permanentemente de la papelera");
            iconButton(refreshTrashBtn, "Actualizar papelera");

            Runnable reloadTrash = () -> {
                try {
                    listView.getItems().setAll(LocalTrashService.listTrashEntries());
                } catch (IOException ex) {
                    showError("No se pudo cargar la papelera", ex.getMessage());
                }
            };

            restoreBtn.setOnAction(event -> {
                LocalTrashService.TrashEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    return;
                }
                try {
                    LocalTrashService.restoreTrashEntry(selected);
                    reloadTrash.run();
                    refreshLocalProjectsList();
                    refreshBoard();
                    setStatus("Elemento restaurado desde la papelera: " + selected.displayName());
                } catch (IOException ex) {
                    showError("No se pudo restaurar", ex.getMessage());
                }
            });

            deleteBtn.setOnAction(event -> {
                LocalTrashService.TrashEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    return;
                }

                Alert confirm = new Alert(AlertType.CONFIRMATION);
                confirm.setTitle("JettraKanban");
                confirm.setHeaderText("Eliminar permanentemente");
                confirm.setContentText("Se borrara definitivamente \"" + selected.label() + "\" de la papelera. Quieres continuar?");
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }

                try {
                    LocalTrashService.deleteTrashEntry(selected);
                    reloadTrash.run();
                    setStatus("Entrada eliminada permanentemente: " + selected.displayName());
                } catch (IOException ex) {
                    showError("No se pudo eliminar", ex.getMessage());
                }
            });

            refreshTrashBtn.setOnAction(event -> reloadTrash.run());

            HBox actions = new HBox(10, restoreBtn, deleteBtn, refreshTrashBtn);
            actions.setAlignment(Pos.CENTER_RIGHT);

            VBox content = new VBox(12, info, listView, actions);
            content.setPrefSize(720, 420);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (IOException ex) {
            showError("No se pudo abrir la papelera", ex.getMessage());
        }
    }

    private boolean matchesLocalCard(KanbanCard loadedCard, KanbanCard referenceCard) {
        if (loadedCard.itemId().equals(referenceCard.itemId())) {
            return true;
        }
        return Objects.equals(loadedCard.title(), referenceCard.title())
                && Objects.equals(loadedCard.body(), referenceCard.body())
                && loadedCard.column() == referenceCard.column();
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("JettraKanban");
        alert.setHeaderText(title);
        alert.setContentText(content == null ? "Error desconocido" : content);
        alert.showAndWait();
    }

    private void iconButton(Button button, String tooltipText) {
        button.setMinWidth(34);
        button.setPrefWidth(34);
        button.setMaxWidth(34);
        button.setTooltip(new Tooltip(tooltipText));
    }

    private String sanitizePdfFileName(String value) {
        if (value == null || value.isBlank()) {
            return "JettraKanban";
        }
        return value.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private record ProjectLocator(String ownerLogin, int projectNumber) {
    }

    private String resolveCurrentUser() {
        String username = usernameField.getText().trim();
        if (!username.isBlank()) return username;
        return System.getProperty("user.name", "unknown");
    }

    private String sanitizeProjectName(String name) {
        return name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record CardInput(String title, String body, KanbanColumn column) {
    }
}
