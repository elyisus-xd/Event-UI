package com.eventui.fabric.client.bridge;

import com.eventui.api.bridge.SkillNodeData;
import com.eventui.api.bridge.SkillRequirementData;
import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SkillNodeDataAdapter implements JsonDeserializer<SkillNodeData> {
    @Override
    public SkillNodeData deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        
        String id = obj.get("id").getAsString();
        String displayName = obj.get("displayName").getAsString();
        String description = obj.get("description").getAsString();
        String icon = obj.get("icon").getAsString();
        int maxLevel = obj.get("maxLevel").getAsInt();
        int currentLevel = obj.get("currentLevel").getAsInt();
        int costNextLevel = obj.get("costNextLevel").getAsInt();
        String state = obj.get("state").getAsString();

        List<SkillRequirementData> requires = new ArrayList<>();
        JsonElement requiresElem = obj.get("requires");
        if (requiresElem != null && requiresElem.isJsonArray()) {
            for (JsonElement reqElem : requiresElem.getAsJsonArray()) {
                JsonObject reqObj = reqElem.getAsJsonObject();
                String nodeId = reqObj.get("nodeId").getAsString();
                int minLevel = reqObj.get("minLevel").getAsInt();
                requires.add(new SkillRequirementData(nodeId, minLevel));
            }
        }
        
        String requiresMode = obj.has("requiresMode") ? obj.get("requiresMode").getAsString() : "all";
        int positionX = obj.get("positionX").getAsInt();
        int positionY = obj.get("positionY").getAsInt();
        
        String textureOverrideLocked = obj.has("textureOverrideLocked") ? obj.get("textureOverrideLocked").getAsString() : null;
        String textureOverrideAvailable = obj.has("textureOverrideAvailable") ? obj.get("textureOverrideAvailable").getAsString() : null;
        String textureOverridePartial = obj.has("textureOverridePartial") ? obj.get("textureOverridePartial").getAsString() : null;
        String textureOverrideMaxed = obj.has("textureOverrideMaxed") ? obj.get("textureOverrideMaxed").getAsString() : null;
        
        String exclusiveGroupId = obj.has("exclusiveGroupId") ? obj.get("exclusiveGroupId").getAsString() : null;
        String exclusiveBranchId = obj.has("exclusiveBranchId") ? obj.get("exclusiveBranchId").getAsString() : null;

        String pointType = obj.has("pointType") ? obj.get("pointType").getAsString() : null;
        
        return new SkillNodeData(
            id, displayName, description, icon, maxLevel, currentLevel, costNextLevel, state,
            requires, requiresMode, positionX, positionY,
            textureOverrideLocked, textureOverrideAvailable, textureOverridePartial, textureOverrideMaxed,
            exclusiveGroupId, exclusiveBranchId, pointType
        );
    }
}
