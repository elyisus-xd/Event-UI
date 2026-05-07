package com.eventui.fabric.client.ui;

import com.eventui.api.event.EventState;
import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIElementHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class StateTextureResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateTextureResolver.class);

    public static String resolveTexture(UIElement element, Map<String, Object> context, boolean isHovered) {
        EventStateInfo stateInfo = getEventStateInfo(element, context);
        String texture = null;

        if (stateInfo != null) {
            if (stateInfo.isLocked) {
                texture = UIElementHelper.getLockedTexture(element);
            } else {
                switch (stateInfo.state) {
                    case IN_PROGRESS:
                        texture = UIElementHelper.getInProgressTexture(element);
                        break;
                    case COMPLETED:
                        texture = UIElementHelper.getCompletedTexture(element);
                        break;
                    case FAILED:
                        texture = UIElementHelper.getFailedTexture(element);
                        break;
                    case AVAILABLE:

                        break;
                }
            }
        }

        if (texture == null || texture.isEmpty()) {
            texture = isHovered
                    ? UIElementHelper.getHoverTexture(element)
                    : UIElementHelper.getTexture(element);
        }
        if (texture == null) {
            texture = UIElementHelper.getTexture(element);
        }
        return texture;
    }

    public static String resolveOverlay(UIElement element, Map<String, Object> context) {
        EventStateInfo stateInfo = getEventStateInfo(element, context);

        if (stateInfo == null) {
            return null;
        }

        if (stateInfo.isLocked) {
            return UIElementHelper.getLockedOverlay(element);
        }

        return switch (stateInfo.state) {
            case COMPLETED -> UIElementHelper.getCompletedOverlay(element);
            case FAILED -> UIElementHelper.getFailedOverlay(element);
            default -> null;
        };
    }

    private static EventStateInfo getEventStateInfo(UIElement element, Map<String, Object> context) {
        if (context == null) return null;

        String action = element.getProperties().get("action");
        String eventId;

        if (action != null && action.startsWith("start_event:")) {
            eventId = action.substring("start_event:".length());
        } else {
            eventId = element.getProperties().get("event_id");
        }

        if (eventId == null || eventId.isEmpty()) return null;

        @SuppressWarnings("unchecked")
        java.util.List<com.eventui.fabric.client.viewmodel.EventViewModel.EventData> events =
                (java.util.List<com.eventui.fabric.client.viewmodel.EventViewModel.EventData>) context.get("events");

        if (events == null) return null;

        for (var event : events) {
            if (event.id.equals(eventId)) {
                return new EventStateInfo(event.state, event.isLocked);
            }
        }

        return null;
    }

    private record EventStateInfo(EventState state, boolean isLocked) {}
}
