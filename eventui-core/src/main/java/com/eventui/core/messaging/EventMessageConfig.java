package com.eventui.core.messaging;

public class EventMessageConfig {

    private static final String DEFAULT_PROGRESS        = "<dark_gray>[<green>EventUI</green>]</dark_gray> <white><description></white> <gray>(<current>/<target>)</gray>";
    private static final String DEFAULT_OBJ_COMPLETED   = "<gold>[EventUI]</gold> <green>¡Objetivo completado! ✔</green>";
    private static final String DEFAULT_EVENT_STARTED   = "<yellow>[EventUI]</yellow> <green>Misión iniciada: </green><white><event_name></white>";
    private static final String DEFAULT_EVENT_COMPLETED = "<gold><bold>[EventUI] ¡Evento completado: <event_name>!</bold></gold>";
    private static final String DEFAULT_EVENT_FAILED    = "<red>[EventUI]</red> <dark_red>Evento fallado: <event_name></dark_red>";
    private static final String DEFAULT_EVENT_LOCKED    = "<red>[EventUI]</red> <gray>Este evento está bloqueado.</gray>";

    private final boolean enabled;

    private final boolean progressEnabled;
    private final String  progressFormat;

    private final boolean objectiveCompletedEnabled;
    private final String  objectiveCompletedFormat;

    private final boolean eventStartedEnabled;
    private final String  eventStartedFormat;

    private final boolean eventCompletedEnabled;
    private final String  eventCompletedFormat;

    private final boolean eventFailedEnabled;
    private final String  eventFailedFormat;

    private final boolean eventLockedEnabled;
    private final String  eventLockedFormat;

    public EventMessageConfig(
            boolean enabled,
            boolean progressEnabled,        String progressFormat,
            boolean objectiveCompletedEnabled, String objectiveCompletedFormat,
            boolean eventStartedEnabled,    String eventStartedFormat,
            boolean eventCompletedEnabled,  String eventCompletedFormat,
            boolean eventFailedEnabled,     String eventFailedFormat,
            boolean eventLockedEnabled,     String eventLockedFormat) {

        this.enabled = enabled;
        this.progressEnabled = progressEnabled;
        this.progressFormat  = progressFormat != null ? progressFormat : DEFAULT_PROGRESS;
        this.objectiveCompletedEnabled = objectiveCompletedEnabled;
        this.objectiveCompletedFormat  = objectiveCompletedFormat != null ? objectiveCompletedFormat : DEFAULT_OBJ_COMPLETED;
        this.eventStartedEnabled = eventStartedEnabled;
        this.eventStartedFormat  = eventStartedFormat != null ? eventStartedFormat : DEFAULT_EVENT_STARTED;
        this.eventCompletedEnabled = eventCompletedEnabled;
        this.eventCompletedFormat  = eventCompletedFormat != null ? eventCompletedFormat : DEFAULT_EVENT_COMPLETED;
        this.eventFailedEnabled = eventFailedEnabled;
        this.eventFailedFormat  = eventFailedFormat != null ? eventFailedFormat : DEFAULT_EVENT_FAILED;
        this.eventLockedEnabled = eventLockedEnabled;
        this.eventLockedFormat  = eventLockedFormat != null ? eventLockedFormat : DEFAULT_EVENT_LOCKED;
    }

    public static EventMessageConfig defaults() {
        return new EventMessageConfig(
                true,
                true, DEFAULT_PROGRESS,
                true, DEFAULT_OBJ_COMPLETED,
                true, DEFAULT_EVENT_STARTED,
                true, DEFAULT_EVENT_COMPLETED,
                true, DEFAULT_EVENT_FAILED,
                true, DEFAULT_EVENT_LOCKED
        );
    }

    public boolean isEnabled()                  { return enabled; }
    public boolean isProgressEnabled()          { return progressEnabled; }
    public String  getProgressFormat()          { return progressFormat; }
    public boolean isObjectiveCompletedEnabled(){ return objectiveCompletedEnabled; }
    public String  getObjectiveCompletedFormat(){ return objectiveCompletedFormat; }
    public boolean isEventStartedEnabled()      { return eventStartedEnabled; }
    public String  getEventStartedFormat()      { return eventStartedFormat; }
    public boolean isEventCompletedEnabled()    { return eventCompletedEnabled; }
    public String  getEventCompletedFormat()    { return eventCompletedFormat; }
    public boolean isEventFailedEnabled()       { return eventFailedEnabled; }
    public String  getEventFailedFormat()       { return eventFailedFormat; }
    public boolean isEventLockedEnabled()       { return eventLockedEnabled; }
    public String  getEventLockedFormat()       { return eventLockedFormat; }
}
