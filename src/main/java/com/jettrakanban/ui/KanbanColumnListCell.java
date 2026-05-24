package com.jettrakanban.ui;

import com.jettrakanban.model.KanbanColumn;
import javafx.scene.control.ListCell;

public class KanbanColumnListCell extends ListCell<KanbanColumn> {
    @Override
    protected void updateItem(KanbanColumn item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.displayName());
    }
}
