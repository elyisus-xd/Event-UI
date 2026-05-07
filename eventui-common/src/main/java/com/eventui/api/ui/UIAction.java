package com.eventui.api.ui;

/**
 * Define la acción que ejecuta un botón al hacer click.
 */
public class UIAction {

    private final ActionType type;
    private final String target;

    public UIAction(ActionType type, String target) {
        this.type = type;
        this.target = target;
    }

    public ActionType getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public enum ActionType {
        OPEN_SCREEN,
        START_EVENT,
        CLOSE_SCREEN,
        EXECUTE_COMMAND
    }
}
