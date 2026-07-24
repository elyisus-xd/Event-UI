package com.eventui.fabric.client.ui;

import com.eventui.api.ui.UIConfig;
import com.eventui.api.ui.UIElement;
import com.eventui.api.ui.UIElementType;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.bridge.UIStateCache;
import com.eventui.fabric.client.ui.animation.Easing;
import com.eventui.fabric.client.ui.sound.UISoundHandler;
import com.eventui.fabric.client.ui.transition.ScreenTransition;
import com.eventui.fabric.client.ui.transition.ScreenTransitionRenderer;
import com.eventui.fabric.client.viewmodel.EventViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;

public class ConfigurableUIScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableUIScreen.class);

    private final Stack<String> navigationStack = new Stack<>();
    private final String currentScreenId;
    private final UIConfig uiConfig;
    private final UIElementRenderer renderer;
    private final EventViewModel viewModel;
    private List<EventViewModel.EventData> events;

    private ScreenTransition transitionIn;
    private ScreenTransition transitionOut;
    private long transitionStartTime;
    private int transitionDuration;

    private boolean enableBlur;
    private boolean isClosing = false;
    private boolean debugOverlayEnabled = false;

    private int offsetX;
    private int offsetY;
    private float uiScale;

    private final Consumer<Map<String, String>> uiStateListener = this::onUIStateChanged;
    private Easing transitionEasing;

    public ConfigurableUIScreen(UIConfig config) {
        super(Component.literal(config.getTitle()));
        this.uiConfig = config;
        this.renderer = new UIElementRenderer();
        this.renderer.setScreenId(config.getId());
        this.currentScreenId = config.getId();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            this.viewModel = ClientEventBridge.getInstance().getOrCreateViewModel(player.getUUID());
            this.events = viewModel.getAllEvents();
            viewModel.addChangeListener(this::onEventsUpdated);

            if (this.events.isEmpty()) {
                viewModel.requestEvents();
            }
        } else {
            this.viewModel = null;
            this.events = List.of();
        }
        parseTransitionConfig();
        UIStateCache.getInstance().addListener(uiStateListener);
        ClientEventBridge.getInstance().requestUIState();
        LOGGER.info("Created ConfigurableUIScreen with config: {} ({} events)",
                config.getId(), events.size());

        Map<String, String> props = uiConfig.getScreenProperties();
        String openSound = props.get("open_sound");
        if (openSound != null && !openSound.isEmpty()) {
            float volume = parseFloat(props.get("open_sound_volume"), 0.5f);
            float pitch = parseFloat(props.get("open_sound_pitch"), 1.0f);
            UISoundHandler.playSound(openSound, volume, pitch);
        }

        boolean hasSkillTree = uiConfig.getRootElements().stream()
            .anyMatch(e -> e.getType() == UIElementType.SKILL_TREE);
        if (hasSkillTree) {
            ClientEventBridge.getInstance().requestSkillData();
        }
    }

    private void parseTransitionConfig() {
        Map<String, String> props = uiConfig.getScreenProperties();
        String transInStr = props.getOrDefault("transition_in", "fade");
        this.transitionIn = parseTransitionType(transInStr);
        String transOutStr = props.getOrDefault("transition_out", "fade");
        this.transitionOut = parseTransitionType(transOutStr);
        String durationStr = props.getOrDefault("transition_duration", "300");
        this.transitionDuration = parseInt(durationStr);
        String easingStr = props.getOrDefault("transition_easing", "ease_out_cubic");
        this.transitionEasing = parseEasingType(easingStr);
        this.transitionStartTime = System.currentTimeMillis();
        this.enableBlur = !"false".equalsIgnoreCase(props.get("blur"));

        LOGGER.debug("Transition config: in={}, out={}, duration={}ms, easing={}, blur={}",
                transitionIn, transitionOut, transitionDuration, easingStr, enableBlur);
    }

    private ScreenTransition parseTransitionType(String type) {
        return switch (type.toLowerCase()) {
            case "none" -> ScreenTransition.NONE;
            case "slide_left" -> ScreenTransition.SLIDE_LEFT;
            case "slide_right" -> ScreenTransition.SLIDE_RIGHT;
            case "slide_up" -> ScreenTransition.SLIDE_UP;
            case "slide_down" -> ScreenTransition.SLIDE_DOWN;
            case "scale_up" -> ScreenTransition.SCALE_UP;
            case "scale_down" -> ScreenTransition.SCALE_DOWN;
            default -> ScreenTransition.FADE;
        };
    }
    private Easing parseEasingType(String type) {
        return switch (type.toLowerCase()) {
            case "linear"           -> Easing.LINEAR;
            case "ease_in_quad"     -> Easing.EASE_IN_QUAD;
            case "ease_out_quad"    -> Easing.EASE_OUT_QUAD;
            case "ease_in_out_quad" -> Easing.EASE_IN_OUT_QUAD;
            case "ease_in_cubic"    -> Easing.EASE_IN_CUBIC;
            case "ease_out_cubic"   -> Easing.EASE_OUT_CUBIC;
            case "ease_in_out_cubic"-> Easing.EASE_IN_OUT_CUBIC;
            case "ease_out_expo"    -> Easing.EASE_OUT_EXPO;
            case "ease_in_expo"     -> Easing.EASE_IN_EXPO;
            case "ease_out_elastic" -> Easing.EASE_OUT_ELASTIC;
            case "ease_out_back"    -> Easing.EASE_OUT_BACK;
            case "ease_in_back"     -> Easing.EASE_IN_BACK;
            case "ease_out_bounce"  -> Easing.EASE_OUT_BOUNCE;
            default -> {
                LOGGER.warn("Unknown easing '{}', using ease_out_cubic", type);
                yield Easing.EASE_OUT_CUBIC;
            }
        };
    }

    private void onEventsUpdated(List<EventViewModel.EventData> newEvents) {
        Minecraft.getInstance().execute(() -> {
            this.events = newEvents;
            LOGGER.debug("UI updated with {} events", events.size());
        });
    }

    private void onUIStateChanged(Map<String, String> newState) {
        LOGGER.debug("UIState changed, screen will re-evaluate visible_if on next frame");
    }

    @Override
    protected void init() {
        super.init();

        float scaleX = (float) this.width  / uiConfig.getScreenWidth();
        float scaleY = (float) this.height / uiConfig.getScreenHeight();
        this.uiScale = Math.min(scaleX, scaleY);

        int scaledWidth  = (int)(uiConfig.getScreenWidth()  * uiScale);
        int scaledHeight = (int)(uiConfig.getScreenHeight() * uiScale);
        this.offsetX = (this.width  - scaledWidth)  / 2;
        this.offsetY = (this.height - scaledHeight) / 2;

        LOGGER.debug("uiScale: {} | offset: ({}, {})", String.format("%.4f", uiScale), offsetX, offsetY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderer.resetHover();

        if (enableBlur) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        float transitionProgress = getTransitionProgress();
        ScreenTransition activeTransition = isClosing ? transitionOut : transitionIn;

        boolean hasTransform = activeTransition != ScreenTransition.NONE &&
                transitionProgress < 1.0f;

        boolean shouldRender = !isClosing || transitionProgress < 1.0f;

        float fadeAlpha = 1.0f;
        if (activeTransition == ScreenTransition.FADE && transitionProgress < 1.0f) {
            if (isClosing) {
                fadeAlpha = 1.0f - transitionProgress;
            } else {
                fadeAlpha = transitionProgress;
            }
        }

        if (hasTransform) {
            graphics.pose().pushPose();
            if (isClosing) {
                ScreenTransitionRenderer.applyTransitionOut(
                        activeTransition, transitionProgress, graphics,
                        this.width, this.height, this.transitionEasing);
            } else {
                ScreenTransitionRenderer.applyTransitionIn(
                        activeTransition, transitionProgress, graphics,
                        this.width, this.height, this.transitionEasing);
            }
        }

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);

        if (shouldRender) {
            EntityRenderCache.tick();
        }

        Map<String, Object> context = createDataContext();
        context.put("uiScale", uiScale);
        context.put("uiOffsetX", offsetX);
        context.put("uiOffsetY", offsetY);

        int localMouseX = (int)((mouseX - offsetX) / uiScale);
        int localMouseY = (int)((mouseY - offsetY) / uiScale);

        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0);
        graphics.pose().scale(uiScale, uiScale, 1.0f);

        renderer.setDesignSize(uiConfig.getScreenWidth(), uiConfig.getScreenHeight());
        renderer.setUiScale(uiScale);
        renderer.setScreenMouse(mouseX, mouseY);

        if (shouldRender) {
            for (UIElement element : uiConfig.getRootElements()) {
                renderer.render(element, graphics, this.font, localMouseX, localMouseY, context);
            }
        }

        graphics.pose().popPose();

        if (hasTransform) {
            graphics.pose().popPose();
        }

        if (shouldRender) {
            renderer.renderPendingTooltips(graphics, this.font, mouseX, mouseY, context);
        }

        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        if (debugOverlayEnabled) {
            UIDebugOverlay.render(graphics, this.font, uiConfig,
                    mouseX, mouseY, offsetX, offsetY, uiScale);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (isClosing && transitionProgress >= 1.0f) {
            actuallyClose();
        }
    }

    private float getTransitionProgress() {
        long elapsed = System.currentTimeMillis() - transitionStartTime;
        return Math.min(1.0f, elapsed / (float) transitionDuration);
    }

    private Map<String, Object> createDataContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("eventcount", events.size());
        context.put("events", events);

        if (uiConfig.getAssociatedEventId() != null && !events.isEmpty()) {
            var associatedEvent = events.stream()
                    .filter(e -> e.id.equals(uiConfig.getAssociatedEventId()))
                    .findFirst()
                    .orElse(null);

            if (associatedEvent != null) {
                context.put("event", associatedEvent);
                context.put("progress", Map.of(
                        "current", associatedEvent.currentProgress,
                        "target", associatedEvent.targetProgress,
                        "percentage", associatedEvent.getProgressPercentage() * 100
                ));
            }
        } else if (!events.isEmpty()) {
            var firstEvent = events.getFirst();
            context.put("event", firstEvent);
            context.put("progress", Map.of(
                    "current", firstEvent.currentProgress,
                    "target", firstEvent.targetProgress,
                    "percentage", firstEvent.getProgressPercentage() * 100
            ));
        }

        Map<String, Object> skillsMap = new HashMap<>();
        var cachedPoints = ClientEventBridge.getInstance().getCache().getCachedSkillPoints();
        for (var entry : cachedPoints.entrySet()) {
            Map<String, Object> pointData = new HashMap<>();
            pointData.put("available", entry.getValue().available());
            pointData.put("totalEarned", entry.getValue().totalEarned());
            skillsMap.put(entry.getKey(), pointData);
        }
        
        context.put("skills", skillsMap);
        if (!skillsMap.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(getClass()).debug("Added {} skill point types to context", skillsMap.size());
        } else {
            org.slf4j.LoggerFactory.getLogger(getClass()).debug("No skill points data available, added empty skills map to context");
        }

        return context;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int localX = (int)((mouseX - offsetX) / uiScale);
            int localY = (int)((mouseY - offsetY) / uiScale);

            for (UIElement element : uiConfig.getRootElements()) {
                if (element.getType() == UIElementType.SKILL_TREE) {
                    SkillTreeRenderer.handleMousePress(element, (int)mouseX, (int)mouseY, button);
                }
            }

            for (UIElement element : uiConfig.getRootElements()) {
                if (checkElementClick(element, localX, localY)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F3
                && (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0) {
            debugOverlayEnabled = !debugOverlayEnabled;
            LOGGER.info("Debug overlay: {}", debugOverlayEnabled ? "ON" : "OFF");
            return true;
        }

        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            Map<String, String> props = uiConfig.getScreenProperties();
            String closeSound = props.get("close_sound");
            if (closeSound != null && !closeSound.isEmpty()) {
                float volume = parseFloat(props.get("close_sound_volume"), 0.5f);
                float pitch = parseFloat(props.get("close_sound_pitch"), 1.0f);
                UISoundHandler.playSound(closeSound, volume, pitch);
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {

        for (UIElement element : uiConfig.getRootElements()) {
            if (element.getType() == UIElementType.SKILL_TREE) {
                boolean ctrlPressed = hasControlDown();
                SkillTreeRenderer.handleMouseWheel(element, (int)mouseX, (int)mouseY,
                        horizontal, vertical, ctrlPressed);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {

        for (UIElement element : uiConfig.getRootElements()) {
            if (element.getType() == UIElementType.SKILL_TREE) {
                SkillTreeRenderer.handleMouseDrag(element, (int)mouseX, (int)mouseY, button);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {

        for (UIElement element : uiConfig.getRootElements()) {
            if (element.getType() == UIElementType.SKILL_TREE) {
                SkillTreeRenderer.handleMouseRelease(element, button);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean checkElementClick(UIElement element, int mouseX, int mouseY) {
        return checkElementClickInternal(element, mouseX, mouseY,
                uiConfig.getScreenWidth(), uiConfig.getScreenHeight(), false);
    }

    private boolean checkElementClickInternal(UIElement element, int mouseX, int mouseY,
                                              int containerWidth, int containerHeight,
                                              boolean isChild) {
        String visibleIf = element.getProperties().get("visible_if");
        if (visibleIf != null && !DataBinder.evaluateCondition(visibleIf)) {
            return false;
        }

        int resolvedX = element.getX();
        int resolvedY = element.getY();
        if (element.getProperties().containsKey("anchor")) {
            AnchorResolver.ResolvedPos resolved = isChild
                    ? AnchorResolver.resolveRelative(element, containerWidth, containerHeight)
                    : AnchorResolver.resolve(element, containerWidth, containerHeight);
            resolvedX = resolved.x();
            resolvedY = resolved.y();
        }

        if (element.getType() == UIElementType.BUTTON ||
                element.getType() == UIElementType.IMAGE_BUTTON) {
            if (mouseX >= resolvedX && mouseX <= resolvedX + element.getWidth() &&
                    mouseY >= resolvedY && mouseY <= resolvedY + element.getHeight()) {
                handleButtonClick(element);
                return true;
            }
        }

        if (element.getType() == UIElementType.SKILL_TREE) {
            
            if (mouseX >= resolvedX && mouseX <= resolvedX + element.getWidth() &&
                    mouseY >= resolvedY && mouseY <= resolvedY + element.getHeight()) {
                
                int localX = mouseX - resolvedX;
                int localY = mouseY - resolvedY;
                String clickedNodeId = SkillTreeRenderer.getClickedNodeId(element, localX, localY);
                if (clickedNodeId != null) {
                    String treeId = element.getProperties().get("tree_id");
                    ClientEventBridge.getInstance().requestSkillSpend(treeId, clickedNodeId);

                    String clickAnim = element.getProperties().getOrDefault("click_animation", "none");
                    if (!"none".equals(clickAnim)) {
                        int clickDuration = Integer.parseInt(
                                element.getProperties().getOrDefault("click_animation_duration", "150"));
                        ClickAnimationManager.getInstance().triggerClick(
                                "click:" + treeId + ":" + clickedNodeId, clickAnim, clickDuration);
                    }

                    String clickSound = element.getProperties().get("click_sound");
                    if (clickSound != null && !clickSound.isEmpty()) {
                        float vol   = parseFloat(element.getProperties().get("click_sound_volume"),  1.0f);
                        float pitch = parseFloat(element.getProperties().get("click_sound_pitch"), 1.0f);
                        UISoundHandler.playSound(clickSound, vol, pitch);
                    }
                    return true;
                }
            }
        }

        for (UIElement child : element.getChildren()) {
            if (checkElementClickInternal(
                    child,
                    mouseX - resolvedX,
                    mouseY - resolvedY,
                    element.getWidth(),
                    element.getHeight(),
                    true)) {
                return true;
            }
        }

        return false;
    }

    private void handleButtonClick(UIElement button) {
        String action = button.getProperties().get("action");
        LOGGER.info("Button clicked: {} - action: {}", button.getId(), action);

        if ("close".equals(action)) {
            Map<String, String> props = uiConfig.getScreenProperties();
            String closeSound = props.get("close_sound");
            if (closeSound != null && !closeSound.isEmpty()) {
                float volume = parseFloat(props.get("close_sound_volume"), 0.5f);
                float pitch = parseFloat(props.get("close_sound_pitch"), 1.0f);
                UISoundHandler.playSound(closeSound, volume, pitch);
            } else {
                UISoundHandler.playSound(UISoundHandler.Sounds.BUTTON_CLICK);
            }
        } else {
            String clickSound = button.getProperties().get("click_sound");
            if (clickSound != null && !clickSound.isEmpty()) {
                float volume = parseFloat(button.getProperties().get("click_sound_volume"), 1.0f);
                float pitch  = parseFloat(button.getProperties().get("click_sound_pitch"), 1.0f);
                UISoundHandler.playSound(clickSound, volume, pitch);
            } else {
                UISoundHandler.playSound(UISoundHandler.Sounds.BUTTON_CLICK);
            }
        }

        String clickAnim = button.getProperties().getOrDefault("click_animation", "none");
        if (!"none".equals(clickAnim)) {
            int clickDuration = Integer.parseInt(
                    button.getProperties().getOrDefault("click_animation_duration", "150"));
            ClickAnimationManager.getInstance().triggerClick(
                    button.getId(), clickAnim, clickDuration);
        }

        if (action == null || action.isBlank() || action.equalsIgnoreCase("none")) return;

        if (action.startsWith("open:")) {
            navigateToScreen(action.substring(5));
        } else if ("back".equals(action)) {
            navigateBack();
        } else if ("close".equals(action)) {
            this.onClose();
        } else {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                ClientEventBridge.getInstance().notifyButtonClick(button.getId(), currentScreenId, player.getUUID());
            }
        }
    }

    public void navigateToScreen(String screenId) {
        LOGGER.debug("Navigating to screen: {}", screenId);

        Stack<String> stackForNext = new Stack<>();
        stackForNext.addAll(this.navigationStack);
        stackForNext.push(this.currentScreenId); 

        var bridge = ClientEventBridge.getInstance();
        bridge.requestUIConfig(screenId).thenAccept(config ->
                Minecraft.getInstance().execute(() -> {
                    if (config == null) {
                        LOGGER.warn("No se encontró la pantalla '{}'. " +
                                "Verifica que el archivo exista en uis/ con ese id.", screenId);
                        return;
                    }
                    ConfigurableUIScreen nextScreen = new ConfigurableUIScreen(config);
                    nextScreen.navigationStack.addAll(stackForNext);
                    Minecraft.getInstance().setScreen(nextScreen);
                })
        );
    }

    public void navigateBack() {
        if (navigationStack.isEmpty()) {
            LOGGER.debug("navigationStack vacío, cerrando pantalla.");
            this.onClose();
            return;
        }

        String previousScreenId = navigationStack.pop();
        LOGGER.debug("Navigating back to: {}", previousScreenId);

        Stack<String> remainingStack = new Stack<>();
        remainingStack.addAll(this.navigationStack);

        var bridge = ClientEventBridge.getInstance();
        bridge.requestUIConfig(previousScreenId).thenAccept(prevConfig ->
                Minecraft.getInstance().execute(() -> {
                    if (prevConfig == null) {
                        LOGGER.warn("No se encontró la pantalla anterior '{}' al navegar atrás. " +
                                "Cerrando.", previousScreenId);
                        actuallyClose();
                        return;
                    }
                    ConfigurableUIScreen prevScreen = new ConfigurableUIScreen(prevConfig);
                    prevScreen.navigationStack.addAll(remainingStack);
                    Minecraft.getInstance().setScreen(prevScreen);
                })
        );
    }

    @Override
    public void onClose() {
        if (transitionOut != ScreenTransition.NONE && !isClosing) {
            isClosing = true;
            transitionStartTime = System.currentTimeMillis();

            Map<String, String> props = uiConfig.getScreenProperties();
            String closeSound = props.get("close_sound");

            if (closeSound != null && !closeSound.isEmpty()) {
                float volume = parseFloat(props.get("close_sound_volume"), 0.5f);
                float pitch = parseFloat(props.get("close_sound_pitch"), 1.0f);
                UISoundHandler.playSound(closeSound, volume, pitch);
            } else {
                UISoundHandler.playSound("minecraft:ui.button.click", 0.5f, 0.8f);
            }
        } else {
            actuallyClose();
        }
    }

    private void actuallyClose() {
        UIStateCache.getInstance().removeListener(uiStateListener);
        if (viewModel != null) {
            viewModel.removeChangeListener(this::onEventsUpdated);
        }
        renderer.clearHoverState();
        MiniMessageHelper.clearCache();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        String pauseGame = uiConfig.getScreenProperties().get("pause_game");
        return "true".equalsIgnoreCase(pauseGame);
    }

    private float parseFloat(String value, float defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 300;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 300;
        }
    }

    public String getCurrentScreenId() {
        return currentScreenId;
    }
}
