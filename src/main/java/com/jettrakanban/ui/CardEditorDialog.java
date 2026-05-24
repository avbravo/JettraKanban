package com.jettrakanban.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import com.jettrakanban.model.KanbanColumn;

import java.util.Optional;

public final class CardEditorDialog {
    private CardEditorDialog() {
    }

    public static Optional<CardInput> show(String header, String initialTitle, String initialBody, KanbanColumn initialColumn) {
        Dialog<CardInput> dialog = new Dialog<>();
        dialog.setTitle("JettraKanban");
        dialog.setHeaderText(header);

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField titleField = new TextField(initialTitle);
        titleField.setPromptText("Titulo de la tarjeta");

        TextArea bodyArea = new TextArea(initialBody);
        bodyArea.setPromptText("Descripcion / acceptance criteria");
        bodyArea.setPrefRowCount(5);

        ComboBox<KanbanColumn> columnBox = new ComboBox<>();
        columnBox.getItems().addAll(KanbanColumn.values());
        columnBox.setValue(initialColumn);
        columnBox.setCellFactory(cell -> new KanbanColumnListCell());
        columnBox.setButtonCell(new KanbanColumnListCell());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10, 0, 0, 0));

        grid.add(new Label("Titulo"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Descripcion"), 0, 1);
        grid.add(bodyArea, 1, 1);
        grid.add(new Label("Columna"), 0, 2);
        grid.add(columnBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(titleField.textProperty().isEmpty());

        dialog.setResultConverter(button -> {
            if (button == saveButton) {
                return new CardInput(titleField.getText().trim(), bodyArea.getText().trim(), columnBox.getValue());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public record CardInput(String title, String body, KanbanColumn column) {
    }
}
