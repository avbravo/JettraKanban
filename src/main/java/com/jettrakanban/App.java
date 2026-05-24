package com.jettrakanban;

import com.jettrakanban.ui.KanbanBoardView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        KanbanBoardView root = new KanbanBoardView();
        Scene scene = new Scene(root, 1480, 860);
        scene.getStylesheets().add(getClass().getResource("/com/jettrakanban/ui/theme.css").toExternalForm());

        stage.setTitle("JettraKanban");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
