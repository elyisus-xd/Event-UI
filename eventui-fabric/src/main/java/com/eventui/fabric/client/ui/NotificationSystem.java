package com.eventui.fabric.client.ui;

import com.eventui.api.ui.HoverAnimation;
import com.eventui.api.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NotificationSystem {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSystem.class);
    private static NotificationSystem instance;

    private final List<Notification> activeNotifications = new ArrayList<>();
    private final List<QueuedNotification> notificationQueue = new LinkedList<>();
    private final Queue<Notification> notificationPool = new LinkedList<>();
    private UIElementRenderer elementRenderer;
    private QuestTrackerConfig config;
    final HoverAnimationManager animationManager;
    
    private NotificationSystem() {
        this.elementRenderer = new UIElementRenderer();
        this.config = QuestTrackerConfig.getInstance();
        this.animationManager = new HoverAnimationManager();
    }
    
    public static NotificationSystem getInstance() {
        if (instance == null) {
            instance = new NotificationSystem();
        }
        return instance;
    }

    public void clear() {
        activeNotifications.clear();
        notificationQueue.clear();
        notificationPool.clear();
    }

    public void invalidateConfig() {
        
        this.config = QuestTrackerConfig.getInstance();
        
        
        for (Notification notification : activeNotifications) {
            QuestTrackerConfig.NotificationTemplate newTemplate = config.getNotificationTemplates().get(notification.templateId);
            if (newTemplate != null) {
                notification.template = newTemplate;
            }
            notification.elementDirty = true;
            notification.rootElement = null;
        }
    }

    private Notification acquireNotification(QuestTrackerConfig.NotificationTemplate template, String templateId,
                                           Map<String, Object> data, String entryAnimation, String exitAnimation,
                                           int durationOverride, int priority) {
        Notification notification = notificationPool.poll();
        if (notification != null) {
            
            notification.reset(template, templateId, data, entryAnimation, exitAnimation, durationOverride, priority);
        } else {
            
            notification = new Notification(template, templateId, data, entryAnimation, exitAnimation, durationOverride, priority);
        }
        return notification;
    }

    private void releaseNotification(Notification notification) {
        notification.rootElement = null;
        notificationPool.offer(notification);
    }

    public void showNotification(String templateId, Map<String, Object> data) {
        showNotification(templateId, data, null, null);
    }
    
    public void showNotification(String templateId, Map<String, Object> data, String entryAnimation, String exitAnimation) {
        showNotification(templateId, data, entryAnimation, exitAnimation, -1);
    }

    public void showNotification(String templateId, Map<String, Object> data, String entryAnimation, String exitAnimation, int durationOverride) {
        if (!config.isNotificationsEnabled()) {
            LOGGER.warn("Notifications are disabled in config");
            return;
        }

        QuestTrackerConfig.NotificationTemplate template = config.getNotificationTemplates().get(templateId);
        if (template == null) {
            LOGGER.warn("Notification template not found: {}", templateId);
            return;
        }

        String entryAnim = entryAnimation != null ? entryAnimation : config.getDefaultAnimation().getEntry();
        String exitAnim = exitAnimation != null ? exitAnimation : config.getDefaultAnimation().getExit();

        int priority = getNotificationPriority(templateId);
        QueuedNotification queued = new QueuedNotification(template, templateId, data, entryAnim, exitAnim, durationOverride, priority);
        notificationQueue.add(queued);

        processQueue();
    }

    private int getNotificationPriority(String templateId) {
        return switch (templateId) {
            case "event_completed", "event_started", "event_failed" -> 3; 
            case "objective_complete" -> 2; 
            case "quest_progress" -> 1; 
            default -> 1; 
        };
    }
    
    private void processQueue() {
        
        notificationQueue.sort((a, b) -> Integer.compare(b.priority, a.priority));

        while (!notificationQueue.isEmpty()) {
            QueuedNotification queued = notificationQueue.remove(0);

            
            if (activeNotifications.size() >= config.getMaxNotifications()) {
                if (queued.priority >= 3) {
                    
                    Notification toRemove = null;
                    for (int i = 0; i < activeNotifications.size(); i++) {
                        Notification notif = activeNotifications.get(i);
                        if (notif.priority < 3) { 
                            toRemove = notif;
                            break;
                        }
                    }
                    if (toRemove != null) {
                        activeNotifications.remove(toRemove);
                    } else {
                        
                        continue;
                    }
                } else {
                    
                    continue;
                }
            }

            Notification notification = acquireNotification(queued.template, queued.templateId, queued.data,
                queued.entryAnimation, queued.exitAnimation, queued.durationOverride, queued.priority);
            activeNotifications.add(notification);
        }
    }
    
    public void render(GuiGraphics graphics) {
        if (activeNotifications.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        long now = System.currentTimeMillis();

        
        Iterator<Notification> iterator = activeNotifications.iterator();
        int index = 0;

        while (iterator.hasNext()) {
            Notification notification = iterator.next();

            
            notification.update(now);

            
            if (notification.isExpired()) {
                iterator.remove();
                releaseNotification(notification);
                processQueue();
                continue;
            }
            
            
            Map<String, Object> context = new HashMap<>(notification.data);
            context.put("screen_width", screenWidth);
            context.put("screen_height", screenHeight);

            
            if (notification.elementDirty || notification.rootElement == null) {
                
                UIElement rootElement = buildNotificationElement(notification.template, context, 0, 0); 

                
                int notificationWidth = rootElement.getWidth();
                int yOffset = calculateYOffset(index, notification);
                int margin = config.getNotificationMargin();
                int x = screenWidth - notificationWidth - margin;
                int y = calculateYPosition(screenHeight, yOffset);

                
                rootElement = buildNotificationElement(notification.template, context, x, y);
                notification.rootElement = rootElement;
                notification.elementDirty = false;
            }

            UIElement rootElement = notification.rootElement;
            int notificationWidth = rootElement.getWidth();
            int yOffset = calculateYOffset(index, notification);
            int margin = config.getNotificationMargin();
            int x = screenWidth - notificationWidth - margin;
            int y = calculateYPosition(screenHeight, yOffset);

            boolean poseWasPushed = false;
            try {
                if (!"none".equals(notification.currentAnimation)) {
                    applyAnimationTransform(graphics, notification, x, y);
                    poseWasPushed = true;
                }

                elementRenderer.render(rootElement, graphics, mc.font, 0, 0, context);

            } catch (Exception e) {
                LOGGER.error("Error rendering notification", e);
            } finally {
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                if (poseWasPushed) {
                    graphics.pose().popPose();
                }
            }
            
            index++;
        }
    }
    
    private UIElement buildNotificationElement(QuestTrackerConfig.NotificationTemplate template,
                                              Map<String, Object> context, int x, int y) {
        List<UIElement> children = new ArrayList<>();

        
        int maxWidth = 200;
        Minecraft mc = Minecraft.getInstance();

        for (QuestTrackerConfig.ElementConfig elementConfig : template.getElements()) {
            if ("text".equals(elementConfig.getType())) {
                String content = elementConfig.getProperties().get("content");
                if (content != null && context != null) {
                    content = DataBinder.resolveBindings(content, context);
                    if (content != null) {
                        int textWidth = mc.font.width(content);
                        maxWidth = Math.clamp(textWidth + 40, maxWidth, 300); 
                    }
                }
            }
        }

        for (QuestTrackerConfig.ElementConfig elementConfig : template.getElements()) {
            UIElement element = HUDElementFactory.createElement(elementConfig, context, x, y, maxWidth, 60);
            children.add(element);
        }

        return new com.eventui.core.config.UIElementImpl(
            "notification_root",
            com.eventui.api.ui.UIElementType.PANEL,
            x, y, maxWidth, 60,
            new java.util.HashMap<>(),
            children,
            true,
            0
        );
    }
    
    private int calculateYPosition(int screenHeight, int yOffset) {
        String stackDir = config.getStackDirection();
        if ("up".equals(stackDir)) {
            return screenHeight - 50 - yOffset;
        } else {
            return 10 + yOffset;
        }
    }
    
    private int calculateYOffset(int index, Notification notification) {
        int spacing = 50; 
        return index * spacing;
    }
    
    private void applyAnimationTransform(GuiGraphics graphics, Notification notification, int x, int y) {
        graphics.pose().pushPose();

        float progress = notification.animationProgress;
        String animation = notification.currentAnimation;

        
        String elementId = "notification_" + notification.hashCode();
        HoverAnimation.AnimationType animType = mapAnimationType(animation);

        if (animType != null) {
            HoverAnimation hoverAnim = new HoverAnimation(animType, 300, 1.0f, "ease_out");

            if (!notification.animationStarted) {
                animationManager.startAnimation(elementId, hoverAnim, false);
                notification.animationStarted = true;
            }

            
            animationManager.applyTransform(elementId, graphics.pose(), x, y, 200, 50);
        } else {
            
            applyCustomAnimation(graphics, notification, x, y, progress, animation);
        }
    }
    
    private HoverAnimation.AnimationType mapAnimationType(String animation) {
        return switch (animation) {
            case "shake" -> HoverAnimation.AnimationType.SHAKE;
            case "bounce" -> HoverAnimation.AnimationType.BOUNCE;
            case "rotate" -> HoverAnimation.AnimationType.ROTATE;
            case "swing" -> HoverAnimation.AnimationType.SWING;
            case "float" -> HoverAnimation.AnimationType.FLOAT;
            case "wave" -> HoverAnimation.AnimationType.WAVE;
            case "heartbeat" -> HoverAnimation.AnimationType.HEARTBEAT;
            case "jelly" -> HoverAnimation.AnimationType.JELLY;
            case "spin_3d" -> HoverAnimation.AnimationType.SPIN_3D;
            case "zoom_in", "scale" -> HoverAnimation.AnimationType.ZOOM_IN;
            case "zoom_out" -> HoverAnimation.AnimationType.ZOOM_OUT;
            
            default -> null;
        };
    }
    
    private void applyCustomAnimation(GuiGraphics graphics, Notification notification, int x, int y, float progress, String animation) {
        switch (animation) {
            case "slide_left":
                float startX = x + 200;
                float currentX = startX + (x - startX) * progress;
                graphics.pose().translate(currentX - x, 0, 0);
                break;
            case "slide_right":
                float endX = x + 200;
                float currentXRight = x + (endX - x) * progress;
                graphics.pose().translate(currentXRight - x, 0, 0);
                break;
            case "fade":
            case "fade_out":
                
                float alpha = Math.max(0f, Math.min(1f, progress));
                graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
                break;
            case "scale":
                float scale = 0.5f + 0.5f * progress;
                float centerX = x + 100;
                float centerY = y + 25;
                graphics.pose().translate(centerX, centerY, 0);
                graphics.pose().scale(scale, scale, 1.0f);
                graphics.pose().translate(-centerX, -centerY, 0);
                break;
        }
    }
    
    private static class Notification {
        QuestTrackerConfig.NotificationTemplate template;
        String templateId;
        Map<String, Object> data;
        String entryAnimation;
        String exitAnimation;
        int priority;

        UIElement rootElement;

        long startTime;
        long entryEndTime;
        long exitStartTime;
        long endTime;

        String currentAnimation;
        float animationProgress;
        boolean isExiting;
        boolean animationStarted;
        boolean elementDirty; 

        Notification(QuestTrackerConfig.NotificationTemplate template, String templateId, Map<String, Object> data,
                    String entryAnimation, String exitAnimation, int durationOverride, int priority) {
            this.template = template;
            this.templateId = templateId;
            this.data = data;
            this.entryAnimation = entryAnimation;
            this.exitAnimation = exitAnimation;
            this.priority = priority;

            long now = System.currentTimeMillis();
            QuestTrackerConfig.AnimationConfig animConfig = QuestTrackerConfig.getInstance().getDefaultAnimation();

            this.startTime = now;
            this.entryEndTime = now + animConfig.getEntryDuration();
            int duration = durationOverride > 0 ? durationOverride : animConfig.getDuration();
            this.exitStartTime = now + animConfig.getEntryDuration() + duration;
            this.endTime = now + animConfig.getEntryDuration() + duration + animConfig.getExitDuration();

            this.currentAnimation = entryAnimation;
            this.animationProgress = 0f;
            this.isExiting = false;
            this.animationStarted = false;
            this.elementDirty = true; 
        }

        void reset(QuestTrackerConfig.NotificationTemplate template, String templateId, Map<String, Object> data,
                   String entryAnimation, String exitAnimation, int durationOverride, int priority) {
            this.template = template;
            this.templateId = templateId;
            this.data = data;
            this.entryAnimation = entryAnimation;
            this.exitAnimation = exitAnimation;
            this.priority = priority;

            long now = System.currentTimeMillis();
            QuestTrackerConfig.AnimationConfig animConfig = QuestTrackerConfig.getInstance().getDefaultAnimation();

            this.startTime = now;
            this.entryEndTime = now + animConfig.getEntryDuration();
            int duration = durationOverride > 0 ? durationOverride : animConfig.getDuration();
            this.exitStartTime = now + animConfig.getEntryDuration() + duration;
            this.endTime = now + animConfig.getEntryDuration() + duration + animConfig.getExitDuration();

            this.currentAnimation = entryAnimation;
            this.animationProgress = 0f;
            this.isExiting = false;
            this.animationStarted = false;
            this.elementDirty = true;
        }

        void update(long now) {
            if (now < entryEndTime) {
                
                currentAnimation = entryAnimation;
                animationProgress = (float)(now - startTime) / (entryEndTime - startTime);
            } else if (now < exitStartTime) {
                
                currentAnimation = "none";
                animationProgress = 1f;
            } else if (now < endTime) {
                
                if (!isExiting) {
                    isExiting = true;
                    
                    String elementId = "notification_" + hashCode();
                    NotificationSystem.getInstance().animationManager.stopAnimation(elementId);
                    animationStarted = false;
                }
                currentAnimation = exitAnimation;
                animationProgress = 1f - (float)(now - exitStartTime) / (endTime - exitStartTime);
            } else {
                
                animationProgress = 0f;
            }
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() >= endTime;
        }
    }
    
    private static class QueuedNotification {
        final QuestTrackerConfig.NotificationTemplate template;
        final String templateId;
        final Map<String, Object> data;
        final String entryAnimation;
        final String exitAnimation;
        final int durationOverride;
        final int priority;

        QueuedNotification(QuestTrackerConfig.NotificationTemplate template, String templateId, Map<String, Object> data,
                          String entryAnimation, String exitAnimation, int durationOverride, int priority) {
            this.template = template;
            this.templateId = templateId;
            this.data = data;
            this.entryAnimation = entryAnimation;
            this.exitAnimation = exitAnimation;
            this.durationOverride = durationOverride;
            this.priority = priority;
        }
    }
}
