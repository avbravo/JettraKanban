package com.jettrakanban.ui;

import com.jettrakanban.config.AppConfig;
import com.jettrakanban.github.GitHubProjectService;
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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KanbanBoardView extends BorderPane {
    private static final Pattern PROJECT_URL_PATTERN = Pattern.compile(
            "^https?://github\\.com/(?:users|orgs)/([^/]+)/projects/(\\d+)(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final AppConfig appConfig;

    private final TextField projectUrlField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField tokenField = new PasswordField();
    private final CheckBox rememberCredentialsBox = new CheckBox("Recordar credenciales");
    private final Label projectTitleLabel = new Label("JettraKanban - Offline");
    private final Label statusLabel = new Label("Ingresa URL de proyecto + credenciales para sincronizar.");

    private final Map<KanbanColumn, VBox> columnPanes = new EnumMap<>(KanbanColumn.class);
    private final Map<String, KanbanCard> cardsByItemId = new HashMap<>();

    private GitHubProjectService service;
    private String statusFieldId = "";
    private Map<KanbanColumn, String> optionByColumn = new EnumMap<>(KanbanColumn.class);

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
        projectUrlField.setPromptText("URL del GitHub Project (ej: https://github.com/users/usuario/projects/1)");
        projectUrlField.setMinWidth(420);
        usernameField.setPromptText("GitHub Username");
        passwordField.setPromptText("GitHub Password");
        tokenField.setPromptText("GitHub Token (opcional)");
        rememberCredentialsBox.setSelected(false);

        Button connectBtn = new Button("Conectar");
        connectBtn.setOnAction(event -> connectAndRefresh());

        Button forgetBtn = new Button("Olvidar credenciales");
        forgetBtn.setOnAction(event -> forgetCredentials());

        Button refreshBtn = new Button("Sincronizar");
        refreshBtn.setOnAction(event -> refreshBoard());

        Button newCardBtn = new Button("+ Nueva Tarjeta");
        newCardBtn.getStyleClass().add("cta-btn");
        newCardBtn.setOnAction(event -> onCreateCard());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12,
                projectTitleLabel,
                spacer,
                projectUrlField,
                usernameField,
                passwordField,
                tokenField,
                rememberCredentialsBox,
                connectBtn,
                forgetBtn,
                refreshBtn,
                newCardBtn
        );
        topBar.setPadding(new Insets(14));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");
        return topBar;
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

    private void onEditCard(KanbanCard card) {
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

    private void onMoveCard(String itemId, KanbanColumn targetColumn) {
        KanbanCard card = cardsByItemId.get(itemId);
        if (card == null || service == null || card.column() == targetColumn) {
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
