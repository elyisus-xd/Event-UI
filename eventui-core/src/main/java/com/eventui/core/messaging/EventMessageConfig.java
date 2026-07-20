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

    private final boolean skillNodeLeveledUpEnabled;
    private final String skillNodeLeveledUpFormat;
    private final boolean skillNodeMaxedEnabled;
    private final String skillNodeMaxedFormat;
    private final boolean skillPointsGrantedEnabled;
    private final String skillPointsGrantedFormat;
    private final boolean skillInsufficientPointsEnabled;
    private final String skillInsufficientPointsFormat;
    private final boolean skillRequirementsNotMetEnabled;
    private final String skillRequirementsNotMetFormat;
    private final boolean skillAlreadyMaxedEnabled;
    private final String skillAlreadyMaxedFormat;
    private final boolean skillExclusiveBranchBlockedEnabled;
    private final String skillExclusiveBranchBlockedFormat;
    private final boolean pointSourceCooldownEnabled;
    private final String pointSourceCooldownFormat;

    public EventMessageConfig(
            boolean enabled,
            boolean progressEnabled,        String progressFormat,
            boolean objectiveCompletedEnabled, String objectiveCompletedFormat,
            boolean eventStartedEnabled,    String eventStartedFormat,
            boolean eventCompletedEnabled,  String eventCompletedFormat,
            boolean eventFailedEnabled,     String eventFailedFormat,
            boolean eventLockedEnabled,     String eventLockedFormat,
            boolean skillNodeLeveledUpEnabled, String skillNodeLeveledUpFormat,
            boolean skillNodeMaxedEnabled, String skillNodeMaxedFormat,
            boolean skillPointsGrantedEnabled, String skillPointsGrantedFormat,
            boolean skillInsufficientPointsEnabled, String skillInsufficientPointsFormat,
            boolean skillRequirementsNotMetEnabled, String skillRequirementsNotMetFormat,
            boolean skillAlreadyMaxedEnabled, String skillAlreadyMaxedFormat,
            boolean skillExclusiveBranchBlockedEnabled, String skillExclusiveBranchBlockedFormat,
            boolean pointSourceCooldownEnabled, String pointSourceCooldownFormat) {

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
        this.skillNodeLeveledUpEnabled = skillNodeLeveledUpEnabled;
        this.skillNodeLeveledUpFormat = skillNodeLeveledUpFormat != null ? skillNodeLeveledUpFormat : "<dark_gray>[<green>EventUI</green>]</dark_gray> <white><node_name></white> <gray>subió a nivel <current_level>/<max_level></gray>";
        this.skillNodeMaxedEnabled = skillNodeMaxedEnabled;
        this.skillNodeMaxedFormat = skillNodeMaxedFormat != null ? skillNodeMaxedFormat : "<gold>[EventUI]</gold> <green>¡<node_name> alcanzó el nivel máximo!</green>";
        this.skillPointsGrantedEnabled = skillPointsGrantedEnabled;
        this.skillPointsGrantedFormat = skillPointsGrantedFormat != null ? skillPointsGrantedFormat : "<yellow>[EventUI]</yellow> <green>Recibiste <amount> <point_type>!</green>";
        this.skillInsufficientPointsEnabled = skillInsufficientPointsEnabled;
        this.skillInsufficientPointsFormat = skillInsufficientPointsFormat != null ? skillInsufficientPointsFormat : "<red>[EventUI]</red> <gray>No tienes suficientes puntos. Necesitas <cost>, tienes <available>.</gray>";
        this.skillRequirementsNotMetEnabled = skillRequirementsNotMetEnabled;
        this.skillRequirementsNotMetFormat = skillRequirementsNotMetFormat != null ? skillRequirementsNotMetFormat : "<red>[EventUI]</red> <gray>No cumples los requisitos para desbloquear <node_name>.</gray>";
        this.skillAlreadyMaxedEnabled = skillAlreadyMaxedEnabled;
        this.skillAlreadyMaxedFormat = skillAlreadyMaxedFormat != null ? skillAlreadyMaxedFormat : "<red>[EventUI]</red> <gray><node_name> ya está en su nivel máximo.</gray>";
        this.skillExclusiveBranchBlockedEnabled = skillExclusiveBranchBlockedEnabled;
        this.skillExclusiveBranchBlockedFormat = skillExclusiveBranchBlockedFormat != null ? skillExclusiveBranchBlockedFormat : "<red>[EventUI]</red> <gray>Ya elegiste otra rama en el grupo '<group_name>'. No puedes desbloquear esta.</gray>";
        this.pointSourceCooldownEnabled = pointSourceCooldownEnabled;
        this.pointSourceCooldownFormat = pointSourceCooldownFormat != null ? pointSourceCooldownFormat : "<yellow>[EventUI]</yellow> <gray><source> está en cooldown. Espera <remaining> segundos.</gray>";
    }

    public static EventMessageConfig defaults() {
        return new EventMessageConfig(
                true,
                true, DEFAULT_PROGRESS,
                true, DEFAULT_OBJ_COMPLETED,
                true, DEFAULT_EVENT_STARTED,
                true, DEFAULT_EVENT_COMPLETED,
                true, DEFAULT_EVENT_FAILED,
                true, DEFAULT_EVENT_LOCKED,
                true, null,
                true, null,
                true, null,
                true, null,
                true, null,
                true, null,
                true, null,
                true, null
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
    public boolean isSkillNodeLeveledUpEnabled(){ return skillNodeLeveledUpEnabled; }
    public String getSkillNodeLeveledUpFormat() { return skillNodeLeveledUpFormat; }
    public boolean isSkillNodeMaxedEnabled()    { return skillNodeMaxedEnabled; }
    public String getSkillNodeMaxedFormat()     { return skillNodeMaxedFormat; }
    public boolean isSkillPointsGrantedEnabled(){ return skillPointsGrantedEnabled; }
    public String getSkillPointsGrantedFormat() { return skillPointsGrantedFormat; }
    public boolean isSkillInsufficientPointsEnabled(){ return skillInsufficientPointsEnabled; }
    public String getSkillInsufficientPointsFormat() { return skillInsufficientPointsFormat; }
    public boolean isSkillRequirementsNotMetEnabled(){ return skillRequirementsNotMetEnabled; }
    public String getSkillRequirementsNotMetFormat() { return skillRequirementsNotMetFormat; }
    public boolean isSkillAlreadyMaxedEnabled() { return skillAlreadyMaxedEnabled; }
    public String getSkillAlreadyMaxedFormat()  { return skillAlreadyMaxedFormat; }
    public boolean isSkillExclusiveBranchBlockedEnabled() { return skillExclusiveBranchBlockedEnabled; }
    public String getSkillExclusiveBranchBlockedFormat() { return skillExclusiveBranchBlockedFormat; }
    public boolean isPointSourceCooldownEnabled() { return pointSourceCooldownEnabled; }
    public String getPointSourceCooldownFormat() { return pointSourceCooldownFormat; }
}
