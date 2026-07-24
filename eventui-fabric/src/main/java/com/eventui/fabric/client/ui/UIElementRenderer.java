package com.eventui.fabric.client.ui;

import com.eventui.api.ui.*;
import com.eventui.fabric.client.ui.sound.UISoundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UIElementRenderer {

    private final Map<String, GlowData> pendingGlowElements = new LinkedHashMap<>();
    private record GlowData(UIElement element, int color) {}
    private static final Logger LOGGER = LoggerFactory.getLogger(UIElementRenderer.class);
    private final com.eventui.fabric.client.ui.animation.AnimationManager animationManager =
            new com.eventui.fabric.client.ui.animation.AnimationManager();

    private UIElement hoveredElement = null;
    private int lastScreenMouseX = 0;
    private int lastScreenMouseY = 0;

    private final HoverAnimationManager hoverAnimationManager = new HoverAnimationManager();

    private int designWidth  = 0;
    private int designHeight = 0;

    private float uiScale = 1.0f;
    private String currentScreenId = "unknown";
    public void setScreenId(String id) { this.currentScreenId = (id == null || id.isEmpty()) ? "unknown" : id; }

    public void setUiScale(float uiScale) {
        this.uiScale = uiScale;
    }

    private record PositionedElement(UIElement delegate, int resolvedX, int resolvedY) implements UIElement {

        @Override public String getId()                          { return delegate.getId(); }
        @Override public UIElementType getType()                 { return delegate.getType(); }
        @Override public int getX()                              { return resolvedX; }
        @Override public int getY()                              { return resolvedY; }
        @Override public int getWidth()                          { return delegate.getWidth(); }
        @Override public int getHeight()                         { return delegate.getHeight(); }
        @Override public Map<String, String> getProperties()     { return delegate.getProperties(); }
        @Override public List<UIElement> getChildren()           { return delegate.getChildren(); }
        @Override public boolean isVisible()                     { return delegate.isVisible(); }
        @Override public int getZIndex()                         { return delegate.getZIndex(); }
    }

    public void render(UIElement element, GuiGraphics graphics, Font font,
                       int mouseX, int mouseY, Map<String, Object> context) {

        String visibleIf = element.getProperties().get("visible_if");
        if (visibleIf != null && !DataBinder.evaluateCondition(visibleIf)) return;
        if (!element.isVisible()) return;

        UIElement original = element;
        if (designWidth > 0) {
            AnchorResolver.ResolvedPos resolved =
                    AnchorResolver.resolve(element, designWidth, designHeight);
            if (resolved.x() != element.getX() || resolved.y() != element.getY()) {
                element = new PositionedElement(element, resolved.x(), resolved.y());
            }
        }

        animationManager.tick();
        boolean isHovered = isMouseOver(element, mouseX, mouseY);

        if (element.getType() == UIElementType.SKILL_TREE) {
            isHovered = false;
        }

        if (isHovered) {

            boolean hasTooltip = element.getChildren().stream()
                    .anyMatch(c -> c.getType() == UIElementType.TOOLTIP);
            if (hasTooltip) {
                hoveredElement = element;
            }

            UIBadge badge = com.eventui.api.ui.UIElementHelper.parseBadge(element);
            if (badge != null) {
                BadgeRenderer.handleBadgeInteraction(element, badge,
                        BadgeRenderer.InteractionType.HOVER, this.currentScreenId);
            }
        }

        boolean hasHoverTransform = applyHoverTransform(element, graphics, isHovered);

        switch (element.getType()) {
            case IMAGE         -> renderImage(element, graphics);
            case TEXT          -> renderText(element, graphics, font, context);
            case BUTTON        -> renderButton(element, graphics, font, mouseX, mouseY);
            case IMAGE_BUTTON  -> renderImageButton(element, graphics, mouseX, mouseY, context);
            case PROGRESS_BAR  -> renderProgressBar(element, graphics, context);
            case PANEL         -> renderPanel(element, graphics, font, mouseX, mouseY, context);
            case ICON          -> renderIcon(element, graphics);
            case TOOLTIP       -> {}
            case ENTITY_RENDER -> renderEntityRender(element, graphics, mouseX, mouseY);
            case ITEM_RENDER   -> renderItemRender(element, graphics);
            case BLOCK_RENDER  -> renderBlockRender(element, graphics);
            case SKILL_TREE    -> {
                Map<String, Object> skillContext = new HashMap<>(context);
                skillContext.put("mouseX", mouseX);
                skillContext.put("mouseY", mouseY);
                SkillTreeRenderer.render(
                        graphics, font, element,
                        element.getX(), element.getY(), element.getWidth(), element.getHeight(),
                        skillContext,
                        mouseX, mouseY,
                        lastScreenMouseX, lastScreenMouseY
                );
            }
            default -> LOGGER.warn("Unsupported element type: {}", element.getType());
        }

        GlowData glow = pendingGlowElements.remove(element.getId());
        if (glow != null) {
            graphics.fill(
                    glow.element().getX(),
                    glow.element().getY(),
                    glow.element().getX() + glow.element().getWidth(),
                    glow.element().getY() + glow.element().getHeight(),
                    glow.color()
            );
        }

        for (UIElement child : element.getChildren()) {
            if (child.getType() != UIElementType.TOOLTIP) {
                var poseStack = graphics.pose();
                poseStack.pushPose();
                poseStack.translate(element.getX(), element.getY(), 0);
                UIElement resolvedChild = child;
                AnchorResolver.ResolvedPos childResolved =
                        AnchorResolver.resolveRelative(child, element.getWidth(), element.getHeight());
                if (childResolved.x() != child.getX() || childResolved.y() != child.getY()) {
                    resolvedChild = new PositionedElement(child, childResolved.x(), childResolved.y());
                }

                render(resolvedChild, graphics, font,
                        mouseX - element.getX(),
                        mouseY - element.getY(),
                        context);

                poseStack.popPose();
            }
        }

        if (hasHoverTransform) {
            graphics.pose().popPose();
        }
    }

    private boolean applyHoverTransform(UIElement element, GuiGraphics graphics, boolean isHovered) {
        String hoverAnim = element.getProperties().get("hover_animation");
        String loopAnim = element.getProperties().get("loop_animation");

        boolean isLoop = loopAnim != null && !loopAnim.isEmpty() && !loopAnim.equals("none");
        boolean hasHover = hoverAnim != null && !hoverAnim.isEmpty() && !hoverAnim.equals("none");

        if (!isLoop && !hasHover) {
            return false;
        }

        if (element.getType() == UIElementType.SKILL_TREE) {
            return false;
        }

        HoverAnimation animation = parseHoverAnimation(element, isLoop ? loopAnim : hoverAnim);

        if (isLoop) {
            
            hoverAnimationManager.startAnimation(element.getId(), animation, true);
        } else if (isHovered) {
            
            hoverAnimationManager.startAnimation(element.getId(), animation, false);
        } else {
            hoverAnimationManager.stopAnimation(element.getId());
        }

        float progress = hoverAnimationManager.getProgress(element.getId());
        if (progress <= 0f) return false;

        var poseStack = graphics.pose();
        poseStack.pushPose();

        hoverAnimationManager.applyTransform(
                element.getId(),
                poseStack,
                element.getX(),
                element.getY(),
                element.getWidth(),
                element.getHeight()
        );

        int glowColor = hoverAnimationManager.getGlowColor(element.getId());
        if (glowColor != 0) {
            pendingGlowElements.put(element.getId(), new GlowData(element, glowColor));
        }

        return true;
    }

    private HoverAnimation parseHoverAnimation(UIElement element) {
        return parseHoverAnimation(element, element.getProperties().getOrDefault("hover_animation", "zoom_in"));
    }

    private HoverAnimation parseHoverAnimation(UIElement element, String animTypeStr) {
        float intensity  = parseFloatProp(element, "hover_animation_intensity", 1.1f);
        int duration     = parseIntProp(element, "hover_animation_duration", 200);
        String easing    = element.getProperties().getOrDefault("hover_animation_easing", "ease_out");

        HoverAnimation.AnimationType type = switch (animTypeStr.toLowerCase()) {
            case "zoom_in"    -> HoverAnimation.AnimationType.ZOOM_IN;
            case "zoom_out"   -> HoverAnimation.AnimationType.ZOOM_OUT;
            case "shake"      -> HoverAnimation.AnimationType.SHAKE;
            case "bounce"     -> HoverAnimation.AnimationType.BOUNCE;
            case "rotate"     -> HoverAnimation.AnimationType.ROTATE;
            case "swing"      -> HoverAnimation.AnimationType.SWING;
            case "float"      -> HoverAnimation.AnimationType.FLOAT;
            case "wave"       -> HoverAnimation.AnimationType.WAVE;
            case "heartbeat"  -> HoverAnimation.AnimationType.HEARTBEAT;
            case "jelly"      -> HoverAnimation.AnimationType.JELLY;
            case "spin_3d"    -> HoverAnimation.AnimationType.SPIN_3D;
            case "glow"       -> HoverAnimation.AnimationType.GLOW;
            default -> {
                LOGGER.warn("Unknown animation '{}' on element '{}'", animTypeStr, element.getId());
                yield HoverAnimation.AnimationType.ZOOM_IN;
            }
        };

        return new HoverAnimation(type, duration, intensity, easing);
    }

    private static final String TEXTURE_PATH_HINT =
            "Las texturas deben incluir la ruta completa, ej: 'namespace:textures/carpeta/archivo.png'";

    private boolean validateTexturePath(String texture, String elementId) {
        if (texture == null || texture.isEmpty()) {
            LOGGER.warn("Elemento '{}': propiedad 'texture' vacía o ausente.", elementId);
            return false;
        }
        int colonIdx = texture.indexOf(':');
        if (colonIdx < 0 || !texture.substring(colonIdx + 1).contains("/")) {
            LOGGER.warn("Elemento '{}': textura inválida '{}'. {}",
                    elementId, texture, TEXTURE_PATH_HINT);
            return false;
        }
        return true;
    }

    private void renderImage(UIElement element, GuiGraphics graphics) {
        String texture = element.getProperties().get("texture");
        if (!validateTexturePath(texture, element.getId())) {
            renderErrorPlaceholder(graphics, element);
            return;
        }
        try {
            ResourceLocation textureLocation = ResourceLocation.parse(texture);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            graphics.blit(textureLocation, element.getX(), element.getY(),
                    0, 0, element.getWidth(), element.getHeight(),
                    element.getWidth(), element.getHeight());
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } catch (Exception e) {
            LOGGER.warn("Elemento '{}': no se encontró la textura '{}'. " +
                            "Verifica que el archivo exista en el resource pack.",
                    element.getId(), texture);
            renderErrorPlaceholder(graphics, element);
        }
    }

    private void renderErrorPlaceholder(GuiGraphics graphics, UIElement element) {
        graphics.fill(element.getX(), element.getY(),
                element.getX() + element.getWidth(),
                element.getY() + element.getHeight(),
                0x88FF0000);
    }

    private void renderText(UIElement element, GuiGraphics graphics, Font font, Map<String, Object> context) {
        String content = element.getProperties().get("content");
        if (content == null) content = "Missing content";

        if (context != null) {
            content = DataBinder.resolveBindings(content, context);
        }

        String align    = element.getProperties().getOrDefault("align", "left");
        boolean shadow  = Boolean.parseBoolean(element.getProperties().getOrDefault("shadow", "true"));
        float fontSize  = parseFloatProp(element, "font_size", 1.0f);
        fontSize        = Math.max(0.1f, fontSize);
        int effectiveWidth = Math.max(element.getWidth(), 0);
        
        int anchorOffsetX = parseIntProp(element, "anchor_offset_x", 0);
        int anchorOffsetY = parseIntProp(element, "anchor_offset_y", 0);
        int lineSpacing = parseIntProp(element, "line_spacing", 0);
        int paragraphSpacing = parseIntProp(element, "paragraph_spacing", 10);


        String[] paragraphs = content.split("\n\s*\n");
        List<String[]> paragraphLines = new ArrayList<>();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            

            String[] rawLines = paragraph.split("\n");
            List<String> wrappedLines = new ArrayList<>();
            
            for (String rawLine : rawLines) {
                rawLine = rawLine.trim();
                if (rawLine.isEmpty()) continue;
                
                boolean hasMM = MiniMessageHelper.hasMiniMessageTags(rawLine);
                
                if (!hasMM && effectiveWidth > 0 && font.width(rawLine) > effectiveWidth) {

                    String[] words = rawLine.split(" ");
                    StringBuilder currentLine = new StringBuilder();
                    for (String word : words) {
                        String test = currentLine.isEmpty() ? word : currentLine + " " + word;
                        if (font.width(test) <= effectiveWidth) {
                            if (!currentLine.isEmpty()) currentLine.append(" ");
                            currentLine.append(word);
                        } else {
                            if (!currentLine.isEmpty()) wrappedLines.add(currentLine.toString());
                            currentLine = new StringBuilder(word);
                        }
                    }
                    if (!currentLine.isEmpty()) wrappedLines.add(currentLine.toString());
                } else {
                    wrappedLines.add(rawLine);
                }
            }
            
            paragraphLines.add(wrappedLines.toArray(new String[0]));
        }

        var poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(element.getX() + anchorOffsetX, element.getY() + anchorOffsetY, 0);

        int baseLineHeight = (int)(font.lineHeight * fontSize);
        int lineHeight = baseLineHeight + lineSpacing;
        
        // Render
        int localY = 0;
        for (int p = 0; p < paragraphLines.size(); p++) {
            for (String line : paragraphLines.get(p)) {
                var lineComponent = MiniMessageHelper.parse(line);
                int textWidth = font.width(lineComponent);
                int scaledTextWidth = (int)(textWidth * fontSize);
                int localX = 0;

                if ("center".equalsIgnoreCase(align)) {
                    int avail = effectiveWidth > 0 ? effectiveWidth : scaledTextWidth;
                    localX = (avail / 2) - (scaledTextWidth / 2);
                } else if ("right".equalsIgnoreCase(align)) {
                    int avail = effectiveWidth > 0 ? effectiveWidth : scaledTextWidth;
                    localX = avail - scaledTextWidth;
                }

                poseStack.pushPose();
                poseStack.translate(localX, localY, 0);
                poseStack.scale(fontSize, fontSize, 1.0f);
                graphics.drawString(font, lineComponent, 0, 0, 0xFFFFFF, shadow);
                poseStack.popPose();

                localY += lineHeight;
            }
            
            // Add paragraph_spacing after each paragraph (except last)
            if (p < paragraphLines.size() - 1) {
                localY += paragraphSpacing;
            }
        }

        poseStack.popPose();
    }

    private void renderButton(UIElement element, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        boolean isHovered = isMouseOver(element, mouseX, mouseY);

        int backgroundColor = isHovered ? 0xFFFFAA00 : 0xFF555555;
        int borderColor = isHovered ? 0xFFFFFF00 : 0xFF888888;

        graphics.fill(
                element.getX(),
                element.getY(),
                element.getX() + element.getWidth(),
                element.getY() + element.getHeight(),
                backgroundColor
        );

        graphics.fill(element.getX(), element.getY(),
                element.getX() + element.getWidth(), element.getY() + 1, borderColor);
        graphics.fill(element.getX(), element.getY() + element.getHeight() - 1,
                element.getX() + element.getWidth(), element.getY() + element.getHeight(), borderColor);
        graphics.fill(element.getX(), element.getY(),
                element.getX() + 1, element.getY() + element.getHeight(), borderColor);
        graphics.fill(element.getX() + element.getWidth() - 1, element.getY(),
                element.getX() + element.getWidth(), element.getY() + element.getHeight(), borderColor);

        String text = element.getProperties().getOrDefault("text", "Button");
        var textComponent = MiniMessageHelper.parse(text);
        int textWidth = font.width(textComponent);
        int textX = element.getX() + (element.getWidth() / 2) - (textWidth / 2);
        int textY = element.getY() + (element.getHeight() / 2) - 4;
        graphics.drawString(font, textComponent, textX, textY, 0xFFFFFF);
    }

    private void renderProgressBar(UIElement element, GuiGraphics graphics, Map<String, Object> context) {
        float progress = resolveProgressValue(element, context);

        String bgColorStr = element.getProperties().getOrDefault("background_color", "1A1A1A");
        int bgColor = parseColor(bgColorStr);
        graphics.fill(
                element.getX(), element.getY(),
                element.getX() + element.getWidth(),
                element.getY() + element.getHeight(),
                bgColor
        );

        int fillWidth = (int)(element.getWidth() * progress);
        if (fillWidth > 0) {
            String fgColorStr = element.getProperties().getOrDefault("color", "5cb85c");
            int fgColor = parseColor(fgColorStr);
            graphics.fill(
                    element.getX(), element.getY(),
                    element.getX() + fillWidth,
                    element.getY() + element.getHeight(),
                    fgColor
            );
        }

        graphics.fill(element.getX(), element.getY(),
                element.getX() + element.getWidth(), element.getY() + 1, 0xFF666666);
        graphics.fill(element.getX(), element.getY() + element.getHeight() - 1,
                element.getX() + element.getWidth(), element.getY() + element.getHeight(), 0xFF444444);
    }

    private float resolveProgressValue(UIElement element, Map<String, Object> context) {
        String eventId     = element.getProperties().get("event_id");
        String objectiveId = element.getProperties().get("objective_id");

        if (eventId != null && !eventId.isEmpty() && objectiveId != null && !objectiveId.isEmpty()) {
            if (context != null) {
                @SuppressWarnings("unchecked")
                var events = (java.util.List<com.eventui.fabric.client.viewmodel.EventViewModel.EventData>)
                        context.get("events");

                if (events != null) {
                    for (var event : events) {
                        if (event.id.equals(eventId)) {
                            if (event.state == com.eventui.api.event.EventState.COMPLETED) return 1.0f;
                            if (event.state != com.eventui.api.event.EventState.IN_PROGRESS) return 0.0f;
                            return event.getProgressPercentage();
                        }
                    }
                }
            }
            LOGGER.warn("PROGRESS_BAR '{}': no se encontró el evento '{}' en el contexto.",
                    element.getId(), eventId);
            return 0.0f;
        }

        if (eventId != null && !eventId.isEmpty()) {
            if (context != null) {
                @SuppressWarnings("unchecked")
                var events = (java.util.List<com.eventui.fabric.client.viewmodel.EventViewModel.EventData>)
                        context.get("events");

                if (events != null) {
                    for (var event : events) {
                        if (event.id.equals(eventId)) {
                            if (event.state == com.eventui.api.event.EventState.COMPLETED) return 1.0f;
                            return event.getProgressPercentage();
                        }
                    }
                }
            }
            LOGGER.warn("PROGRESS_BAR '{}': no se encontró el evento '{}' en el contexto.",
                    element.getId(), eventId);
            return 0.0f;
        }

        String progressStr = element.getProperties().getOrDefault("progress", "0");
        if (progressStr.contains("{{")) {
            progressStr = DataBinder.resolveBindings(progressStr, context != null ? context : Map.of());
        }
        try {
            float val = Float.parseFloat(progressStr);
            return val > 1.0f ? Math.min(1.0f, val / 100.0f) : Math.max(0.0f, val);
        } catch (NumberFormatException e) {
            LOGGER.warn("PROGRESS_BAR '{}': valor de progreso inválido '{}'.", element.getId(), progressStr);
            return 0.0f;
        }
    }

    @SuppressWarnings("unused")
    private void renderPanel(UIElement element, GuiGraphics graphics, Font font, int mouseX, int mouseY, Map<String, Object> context) {
        String bgColor = element.getProperties().get("background_color");
        if (bgColor != null) {
            int color = parseColor(bgColor);
            graphics.fill(
                    element.getX(),
                    element.getY(),
                    element.getX() + element.getWidth(),
                    element.getY() + element.getHeight(),
                    color
            );
        }
    }

    private boolean isMouseOver(UIElement element, int mouseX, int mouseY) {
        if (mouseX < element.getX() || mouseX > element.getX() + element.getWidth() ||
                mouseY < element.getY() || mouseY > element.getY() + element.getHeight()) {
            return false;
        }
        if (com.eventui.api.ui.UIElementHelper.useTextureAlphaHitbox(element)) {
            return isMouseOverTextureAlpha(element, mouseX, mouseY);
        }
        return true;
    }

    private boolean isMouseOverTextureAlpha(UIElement element, int mouseX, int mouseY) {
        String texture = element.getProperties().get("texture");

        if (texture == null || texture.isEmpty()) {
            return true;
        }

        try {
            ResourceLocation textureLocation = ResourceLocation.parse(texture);
            TextureAlphaCache.AlphaData alphaData =
                    TextureAlphaCache.getAlphaData(textureLocation);

            return TextureAlphaCache.isMouseOverOpaque(
                    alphaData,
                    element.getX(),
                    element.getY(),
                    element.getWidth(),
                    element.getHeight(),
                    mouseX,
                    mouseY
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to check texture alpha for {}, falling back to bounds", texture);
            return true;
        }
    }

    private int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }

            if (hex.length() == 6) {
                hex = "FF" + hex;
            }

            return (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid color format: {}, using gray", hex);
            return 0xFF808080;
        }
    }

    private void renderImageButton(UIElement element, GuiGraphics graphics,
                                   int mouseX, int mouseY, Map<String, Object> context) {
        boolean isHovered = isMouseOver(element, mouseX, mouseY);

        String texture = StateTextureResolver.resolveTexture(element, context, isHovered);

        if (!validateTexturePath(texture, element.getId())) {
            renderErrorPlaceholder(graphics, element);
            return;
        }

        try {
            ResourceLocation textureLocation = ResourceLocation.parse(texture);
            var poseStack = graphics.pose();
            java.util.List<com.eventui.api.ui.HoverEffect> effects =
                    com.eventui.api.ui.UIElementHelper.parseHoverEffects(element);

            String elementId = element.getId();
            boolean shouldApplyTransform = false;

            float clickProgress = ClickAnimationManager.getInstance().getProgress(element.getId());
            if (clickProgress > 0f) {
                String clickAnimType = ClickAnimationManager.getInstance()
                        .getAnimationType(element.getId());
                float cx = element.getX() + element.getWidth() / 2f;
                float cy = element.getY() + element.getHeight() / 2f;
                float t = 1f - clickProgress;

                poseStack.pushPose();

                switch (clickAnimType) {
                    case "punch" -> {
                        float shrink = (float) Math.sin(t * Math.PI) * 0.18f;
                        float scale = 1f - shrink;
                        poseStack.translate(cx, cy, 0);
                        poseStack.scale(scale, scale, 1f);
                        poseStack.translate(-cx, -cy, 0);
                    }
                    case "shake" -> {
                        float offsetX = (float) Math.sin(t * Math.PI * 5) * 3f * clickProgress;
                        poseStack.translate(offsetX, 0, 0);
                    }
                    case "bounce" -> {
                        float offsetY = -(float) Math.abs(Math.sin(t * Math.PI)) * 5f * clickProgress;
                        poseStack.translate(0, offsetY, 0);
                    }
                }

                if (!effects.isEmpty()) {
                    applyAllHoverEffects(element, poseStack, effects, elementId, isHovered);
                }

                graphics.blit(
                        textureLocation,
                        element.getX(),
                        element.getY(),
                        0, 0,
                        element.getWidth(),
                        element.getHeight(),
                        element.getWidth(),
                        element.getHeight()
                );
                renderCenteredIcon(element, graphics, poseStack);
                String overlay = StateTextureResolver.resolveOverlay(element, context);
                if (overlay != null && !overlay.isEmpty()) {
                    renderOverlay(element, graphics, overlay);
                }

                com.eventui.api.ui.UIBadge badge = com.eventui.api.ui.UIElementHelper.parseBadge(element);
                if (badge != null) {
                    BadgeRenderer.renderBadge(element, badge, graphics, element.getX(), element.getY(), context, this.currentScreenId);
                }

                poseStack.popPose();
                return;
            }

            if (!effects.isEmpty()) {
                shouldApplyTransform = true;
                poseStack.pushPose();
                applyAllHoverEffects(element, poseStack, effects, elementId, isHovered);
            }
            graphics.blit(
                    textureLocation,
                    element.getX(),
                    element.getY(),
                    0, 0,
                    element.getWidth(),
                    element.getHeight(),
                    element.getWidth(),
                    element.getHeight()
            );
            renderCenteredIcon(element, graphics, poseStack);
            String overlay = StateTextureResolver.resolveOverlay(element, context);
            if (overlay != null && !overlay.isEmpty()) {
                renderOverlay(element, graphics, overlay);
            }

            com.eventui.api.ui.UIBadge badge = com.eventui.api.ui.UIElementHelper.parseBadge(element);
            if (badge != null) {
                BadgeRenderer.renderBadge(element, badge, graphics, element.getX(), element.getY(), context, this.currentScreenId);
            }

            if (shouldApplyTransform) {
                poseStack.popPose();
            }

            if (isHovered && !wasHoveredLastFrame(element.getId())) {
                String hoverSound = element.getProperties().get("hover_sound");
                if (hoverSound != null && !hoverSound.isEmpty()) {
                    float volume = parseFloat(element.getProperties().get("hover_sound_volume"), 0.5f);
                    float pitch = parseFloat(element.getProperties().get("hover_sound_pitch"), 1.0f);
                    UISoundHandler.playSound(hoverSound, volume, pitch);
                }
            }
            updateHoverState(element.getId(), isHovered);

            if (isHovered && effects.isEmpty() && element.getType() != UIElementType.SKILL_TREE) {
                graphics.fill(
                        element.getX(),
                        element.getY(),
                        element.getX() + element.getWidth(),
                        element.getY() + element.getHeight(),
                        0x30FFFFFF
                );

            } else if (!isHovered && effects.isEmpty()) {
                hoverAnimationManager.stopAnimation(element.getId() + "_overlay");
            }

            if (effects.isEmpty() && element.getType() != UIElementType.SKILL_TREE) {
                if (isHovered) {
                    hoverAnimationManager.startAnimation(element.getId() + "_overlay",
                            new HoverAnimation(HoverAnimation.AnimationType.ZOOM_IN, 150, 1.0f, "ease_out"));
                }
                float overlayProgress = hoverAnimationManager.getProgress(element.getId() + "_overlay");
                if (overlayProgress > 0f) {
                    int overlayAlpha = (int)(0x30 * overlayProgress);
                    graphics.fill(
                            element.getX(), element.getY(),
                            element.getX() + element.getWidth(),
                            element.getY() + element.getHeight(),
                            (overlayAlpha << 24) | 0xFFFFFF
                    );
                }
            }

            if (clickProgress > 0f) {
                String clickAnimType = ClickAnimationManager.getInstance()
                        .getAnimationType(element.getId());
                if (!"flash".equals(clickAnimType) && !"none".equals(clickAnimType)) {
                    poseStack.popPose();
                }
                
                if ("flash".equals(clickAnimType)) {
                    int flashAlpha = (int)(clickProgress * 160);
                    graphics.fill(element.getX(), element.getY(),
                            element.getX() + element.getWidth(),
                            element.getY() + element.getHeight(),
                            (flashAlpha << 24) | 0xFFFFFF);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to render image button texture: {}", texture, e);
        }
    }

    private final Set<String> previouslyHovered = new HashSet<>();

    private boolean wasHoveredLastFrame(String elementId) {
        return previouslyHovered.contains(elementId);
    }

    private void updateHoverState(String elementId, boolean isHovered) {
        if (isHovered) {
            previouslyHovered.add(elementId);
        } else {
            previouslyHovered.remove(elementId);
        }
    }

    private void applyAllHoverEffects(UIElement element,
                                      com.mojang.blaze3d.vertex.PoseStack poseStack,
                                      java.util.List<com.eventui.api.ui.HoverEffect> effects,
                                      String elementId,
                                      boolean isHovered) {
        com.eventui.api.ui.HoverEffect primary = effects.stream()
                .filter(e -> !e.getType().equalsIgnoreCase("glow"))
                .findFirst()
                .orElse(null);

        if (primary == null) return;

        HoverAnimation.AnimationType type = switch (primary.getType().toLowerCase()) {
            case "zoom_in"    -> HoverAnimation.AnimationType.ZOOM_IN;
            case "zoom_out"   -> HoverAnimation.AnimationType.ZOOM_OUT;
            case "shake"      -> HoverAnimation.AnimationType.SHAKE;
            case "bounce"     -> HoverAnimation.AnimationType.BOUNCE;
            case "rotate"     -> HoverAnimation.AnimationType.ROTATE;
            case "swing"      -> HoverAnimation.AnimationType.SWING;
            case "float"      -> HoverAnimation.AnimationType.FLOAT;
            case "wave"       -> HoverAnimation.AnimationType.WAVE;
            case "heartbeat", "pulse" -> HoverAnimation.AnimationType.HEARTBEAT;
            case "jelly"      -> HoverAnimation.AnimationType.JELLY;
            case "spin_3d"    -> HoverAnimation.AnimationType.SPIN_3D;
            default -> {
                LOGGER.warn("Unknown hover effect '{}' on element '{}'", primary.getType(), elementId);
                yield HoverAnimation.AnimationType.ZOOM_IN;
            }
        };

        int duration = parseIntProp(element, "hover_animation_duration", 200);
        String easing = element.getProperties().getOrDefault("hover_animation_easing", "ease_out");
        HoverAnimation animation = new HoverAnimation(type, duration, primary.getIntensity(), easing);

        if (isHovered) {
            hoverAnimationManager.startAnimation(elementId, animation);
        } else {
            hoverAnimationManager.stopAnimation(elementId);
        }

        float progress = hoverAnimationManager.getProgress(elementId);
        if (progress <= 0f) return;

        hoverAnimationManager.applyTransform(
                elementId,
                poseStack,
                element.getX(),
                element.getY(),
                element.getWidth(),
                element.getHeight()
        );
    }

    private void renderCenteredIcon(UIElement element, GuiGraphics graphics,
                                    com.mojang.blaze3d.vertex.PoseStack poseStack) {
        if (!com.eventui.api.ui.UIElementHelper.hasIcon(element)) return;

        String iconId = com.eventui.api.ui.UIElementHelper.getIconItem(element);
        if (iconId == null || iconId.isEmpty()) return;

        try {
            ResourceLocation itemLocation = ResourceLocation.parse(iconId);
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLocation);

            if (item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("Invalid icon item: {}", iconId);
                return;
            }

            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);

            float iconScaleProp = com.eventui.api.ui.UIElementHelper.getIconScale(element);
            int offsetX = com.eventui.api.ui.UIElementHelper.getIconOffsetX(element);
            int offsetY = com.eventui.api.ui.UIElementHelper.getIconOffsetY(element);

            float autoScale = (Math.min(element.getWidth(), element.getHeight()) / 16f) * 0.7f;
            float scale = autoScale * iconScaleProp;

            float centerX = element.getX() + element.getWidth() / 2f + offsetX;
            float centerY = element.getY() + element.getHeight() / 2f + offsetY;

            poseStack.pushPose();
            poseStack.translate(centerX, centerY, -200f);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-8, -8, 0);

            graphics.renderItem(stack, 0, 0);

            poseStack.popPose();

        } catch (Exception e) {
            LOGGER.error("Failed to render icon: {}", iconId, e);
        }
    }

    private void renderOverlay(UIElement element, GuiGraphics graphics, String overlayTexture) {
        try {
            ResourceLocation overlayLocation = ResourceLocation.parse(overlayTexture);
            graphics.blit(
                    overlayLocation,
                    element.getX(),
                    element.getY(),
                    0, 0,
                    element.getWidth(),
                    element.getHeight(),
                    element.getWidth(),
                    element.getHeight()
            );

        } catch (Exception e) {
            LOGGER.warn("Failed to render overlay: {}", overlayTexture, e);
        }
    }

    private void renderIcon(UIElement element, GuiGraphics graphics) {
        String itemId = element.getProperties().get("item");
        if (itemId == null || itemId.isEmpty()) {
            LOGGER.warn("ICON element {} missing 'item' property", element.getId());
            return;
        }

        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemLocation);

            if (item == net.minecraft.world.item.Items.AIR) {
                LOGGER.warn("Invalid item ID: {}", itemId);
                return;
            }

            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);

            float scaleX = element.getWidth() / 16f;
            float scaleY = element.getHeight() / 16f;
            float scale = Math.min(scaleX, scaleY);

            var poseStack = graphics.pose();
            poseStack.pushPose();

            float centerX = element.getX() + element.getWidth() / 2f;
            float centerY = element.getY() + element.getHeight() / 2f;

            poseStack.translate(centerX, centerY, 0);
            poseStack.scale(scale, scale, 1.0f);
            poseStack.translate(-8, -8, 0);

            graphics.renderItem(stack, 0, 0);

            String countStr = element.getProperties().get("count");
            if (countStr != null) {
                try {
                    int count = Integer.parseInt(countStr);
                    graphics.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0, String.valueOf(count));
                } catch (NumberFormatException ignored) {}
            }

            poseStack.popPose();

        } catch (Exception e) {
            LOGGER.error("Failed to render icon: {}", itemId, e);
            graphics.fill(element.getX(), element.getY(),
                    element.getX() + element.getWidth(),
                    element.getY() + element.getHeight(),
                    0xFFFF0000);
        }
    }

    public void renderPendingTooltips(GuiGraphics graphics, Font font,
                                      int mouseX, int mouseY, Map<String, Object> context) {
        if (hoveredElement == null) return;

        for (UIElement child : hoveredElement.getChildren()) {
            if (child.getType() == UIElementType.TOOLTIP) {
                TooltipConfig tooltipConfig =
                        com.eventui.api.ui.UIElementHelper.parseTooltipConfig(child);
                if (tooltipConfig != null) {
                    TooltipRenderer.renderTooltip(tooltipConfig, graphics, font,
                            mouseX, mouseY);
                    return;
                }
            }
        }
        hoveredElement = null;
    }

    public void resetHover() {
        hoveredElement = null;
    }

    public void clearHoverState() {
        hoveredElement = null;
        previouslyHovered.clear();
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

    private void renderEntityRender(UIElement element, GuiGraphics graphics, int mouseX, int mouseY) {
        String entityId = element.getProperties().get("entity");
        if (entityId == null || entityId.isEmpty()) {
            LOGGER.warn("ENTITY_RENDER element '{}' missing 'entity' property", element.getId());
            return;
        }

        LivingEntity entity = EntityRenderCache.getOrCreate(entityId);
        if (entity == null) return;

        int scale      = parseIntProp(element, "scale", 40);
        float yOffset  = parseFloatProp(element, "y_offset", 0f);
        float spinSpeed = parseFloatProp(element, "spin_speed", 1.0f);
        float staticRotY = parseFloatProp(element, "rotation_y", 0f);
        String rotationMode = element.getProperties().getOrDefault("rotation_mode", "spin");

        float centerX = element.getX() + element.getWidth() / 2f;
        float baseY   = element.getY() + element.getHeight();

        switch (rotationMode.toLowerCase()) {
            case "follow_mouse" -> {
                Quaternionf pose = new Quaternionf().rotateX((float) Math.PI);
                float dx = (mouseX - centerX) / 150f;
                float dy = (mouseY - (element.getY() + element.getHeight() / 2f)) / 150f;

                dx = Math.clamp(dx, -1.0f, 1.0f);
                dy = Math.clamp(dy, -0.5f, 0.5f);
                pose.rotateY(dx);
                pose.rotateX(dy * 0.5f);

                try {
                    InventoryScreen.renderEntityInInventory(
                            graphics,
                            centerX, baseY,
                            scale,
                            new Vector3f(0, yOffset, 0),
                            pose,
                            null,
                            entity
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to render entity with follow_mouse: {}", e.getMessage(), e);
                }
                return;
            }
            default -> {
                Quaternionf pose = new Quaternionf().rotateX((float) Math.PI);
                Quaternionf cameraOrientation = null;

                switch (rotationMode.toLowerCase()) {
                    case "static", "fixed" -> {
                        pose.rotateY(staticRotY * (float)(Math.PI / 180.0));
                    }
                    case "spin" -> {
                        long time = System.currentTimeMillis();
                        float angle = (time % (long)(6000.0 / spinSpeed))
                                / (6000.0f / spinSpeed) * 360.0f;
                        pose.rotateY(angle * (float)(Math.PI / 180.0));
                    }
                    default -> {
                        LOGGER.warn("Unknown rotation_mode '{}' on element '{}'", rotationMode, element.getId());
                    }
                }

                try {
                    InventoryScreen.renderEntityInInventory(
                            graphics,
                            centerX, baseY,
                            scale,
                            new Vector3f(0, yOffset, 0),
                            pose,
                            cameraOrientation,
                            entity
                    );
                } catch (Exception e) {
                    LOGGER.error("Failed to render entity: {}", e.getMessage());
                }
            }
        }
    }

    private void renderItemRender(UIElement element, GuiGraphics graphics) {
        String itemId = element.getProperties().get("item");
        if (itemId == null || itemId.isEmpty()) {
            LOGGER.warn("ITEM_RENDER element '{}' missing 'item' property", element.getId());
            return;
        }

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            LOGGER.warn("Unknown item: {}", itemId);
            return;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(loc));
        renderScaledItem(element, graphics, stack);
    }

    private void renderBlockRender(UIElement element, GuiGraphics graphics) {
        String blockId = element.getProperties().get("block");
        if (blockId == null || blockId.isEmpty()) {
            LOGGER.warn("BLOCK_RENDER element '{}' missing 'block' property", element.getId());
            return;
        }

        ResourceLocation loc = ResourceLocation.tryParse(blockId);
        if (loc == null || !BuiltInRegistries.BLOCK.containsKey(loc)) {
            LOGGER.warn("Unknown block: {}", blockId);
            return;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.get(loc));
        renderScaledItem(element, graphics, stack);
    }

    private void renderScaledItem(UIElement element, GuiGraphics graphics, ItemStack stack) {
        float scale = parseFloatProp(element, "scale", 3.0f);
        float centerX = element.getX() + element.getWidth() / 2f;
        float centerY = element.getY() + element.getHeight() / 2f;

        var poseStack = graphics.pose();
        poseStack.pushPose();

        poseStack.translate(centerX, centerY, 200);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-8, -8, 0);

        graphics.renderItem(stack, 0, 0);

        poseStack.popPose();
    }

    private int parseIntProp(UIElement element, String key, int defaultValue) {
        String val = element.getProperties().get(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private float parseFloatProp(UIElement element, String key, float defaultValue) {
        String val = element.getProperties().get(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try { return Float.parseFloat(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public void setDesignSize(int width, int height) {
        this.designWidth  = width;
        this.designHeight = height;
    }
    public void setScreenMouse(int screenMouseX, int screenMouseY) {
        this.lastScreenMouseX = screenMouseX;
        this.lastScreenMouseY = screenMouseY;
    }
}