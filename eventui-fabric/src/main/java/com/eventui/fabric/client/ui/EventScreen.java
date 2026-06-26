package com.eventui.fabric.client.ui;

import com.eventui.api.bridge.MessageType;
import com.eventui.fabric.client.bridge.BridgeMessageImpl;
import com.eventui.fabric.client.bridge.ClientEventBridge;
import com.eventui.fabric.client.viewmodel.EventViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.eventui.fabric.client.ui.animation.*;

import java.util.*;


public class EventScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventScreen.class);
    private final EventViewModel viewModel;
    private List<EventViewModel.EventData> events;
    private record CategorySection(String category, String displayName, int yPosition, int eventCount, int color) {}
    private List<CategorySection> categorySections = new ArrayList<>();

    public enum TabFilter {
        ALL,
        IN_PROGRESS,
        AVAILABLE,
        LOCKED,
        COMPLETED
    }

    private TabFilter activeTab = TabFilter.AVAILABLE;
    private double miniNavScrollOffset = 0;
    private double miniNavMaxScroll = 0;

    private static final int SECTION_HEADER_HEIGHT = 15;
    private static final int CARD_GAP = 15;
    private static final int SECTION_PADDING = 20;
    private static final int FINAL_PADDING = 50;

    private int completedCount = 0;
    private int inProgressCount = 0;
    private int availableCount = 0;

    private net.minecraft.client.gui.components.EditBox searchField;
    private String searchQuery = "";
    private List<EventViewModel.EventData> filteredEvents;

    private double scrollOffset = 0;
    private double targetScrollOffset = 0;
    private double maxScrollOffset = 0;
    private static final int SCROLL_SPEED = 20;
    private static final int TOP_MARGIN = 105;
    private static final int BOTTOM_MARGIN = 50;

    private final List<ClickableButton> clickableButtons = new ArrayList<>();


    private final AnimationManager animationManager = new AnimationManager();
    private float screenAlpha = 0f;
    private float overlayAlpha = 0f;
    private FloatAnimation currentScrollAnimation = null;
    private final Map<String, Float> cardHoverAlpha = new java.util.HashMap<>();
    private final Map<String, Float> buttonHoverScale = new java.util.HashMap<>();
    private final Map<String, Float> animatedProgress = new java.util.HashMap<>();

    public EventScreen() {
        super(Component.literal("Events"));

        var player = Minecraft.getInstance().player;
        if (player != null) {
            this.viewModel = ClientEventBridge.getInstance().getOrCreateViewModel(player.getUUID());
            this.events = viewModel.getAllEvents();
            this.filteredEvents = new ArrayList<>(this.events);
            LOGGER.info("EventScreen constructor - Events in cache: {}", events.size());
            viewModel.addChangeListener(this::onEventsUpdated);
            if (this.events.isEmpty()) {
                LOGGER.info("Cache empty, requesting events from server...");
                viewModel.requestEvents();
            } else {
                LOGGER.info("Using cached events, no server request needed");
            }
            applyTabFilter();
        } else {
            this.viewModel = null;
            this.events = List.of();
            this.filteredEvents = List.of();
        }
    }


    @Override
    protected void init() {
        super.init();
        int searchWidth = 200;
        int searchHeight = 20;
        int searchX = (this.width / 2) - (searchWidth / 2);
        int searchY = 78;

        this.searchField = new net.minecraft.client.gui.components.EditBox(
                this.font,
                searchX,
                searchY,
                searchWidth,
                searchHeight,
                Component.literal("Search events...")
        );

        this.searchField.setMaxLength(50);
        this.searchField.setHint(Component.literal("§7Search events..."));
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);
        this.addRenderableWidget(Button.builder(
                Component.literal("Close"),
                button -> this.onClose()
        ).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());

        updateMaxScroll();

        playScreenFadeIn();

    }

    private void onEventsUpdated(List<EventViewModel.EventData> newEvents) {
        Minecraft.getInstance().execute(() -> {
            LOGGER.info("onEventsUpdated - New events: {}", newEvents.size());
            this.events = newEvents;
            updateFilteredEvents();
            updateMaxScroll();
        });
    }

    private void playScreenFadeIn() {
        FloatAnimation fadeIn = new FloatAnimation(
                0f,
                1f,
                300,
                Easing.EASE_OUT_CUBIC,
                alpha -> {
                    this.screenAlpha = alpha;
                    this.overlayAlpha = alpha * 0.8f;
                }
        );
        animationManager.play(fadeIn);
    }
    @Override
    public void tick() {
        super.tick();
        animationManager.tick();
        if (Math.abs(scrollOffset - targetScrollOffset) > 0.5) {
            double diff = targetScrollOffset - scrollOffset;
            scrollOffset += diff * 0.35;
        } else {
            scrollOffset = targetScrollOffset;
        }
        cardHoverAlpha.replaceAll((id, currentAlpha) -> {
            if (currentAlpha > 0.01f) {
                return currentAlpha * 0.85f;
            }
            return 0f;
        });
        animatedProgress.replaceAll((eventId, currentProgress) -> {
            EventViewModel.EventData event = events.stream()
                    .filter(e -> e.id.equals(eventId))
                    .findFirst()
                    .orElse(null);

            if (event != null) {
                float targetProgress = event.getProgressPercentage();
                if (Math.abs(currentProgress - targetProgress) > 0.005f) {
                    float diff = targetProgress - currentProgress;
                    return currentProgress + diff * 0.15f;
                } else {
                    return targetProgress;
                }
            }
            return currentProgress;
        });

    }

    private void updateMaxScroll() {
        if (this.height == 0) return;
        int contentHeight = calculateContentHeight();
        int visibleHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScrollOffset));
    }

    private int calculateContentHeight() {
        int totalHeight = 0;
        Map<String, List<EventViewModel.EventData>> eventsByCategory = new LinkedHashMap<>();

        for (EventViewModel.EventData event : filteredEvents) {
            eventsByCategory
                    .computeIfAbsent(event.category, k -> new ArrayList<>())
                    .add(event);
        }

        for (Map.Entry<String, List<EventViewModel.EventData>> entry : eventsByCategory.entrySet()) {
            List<EventViewModel.EventData> categoryEvents = entry.getValue();
            if (categoryEvents.isEmpty()) continue;
            totalHeight += SECTION_HEADER_HEIGHT;
            for (EventViewModel.EventData event : categoryEvents) {
                totalHeight += calculateEventHeight(event);
                totalHeight += CARD_GAP;
            }
            totalHeight += SECTION_PADDING;
        }
        totalHeight += FINAL_PADDING;

        return totalHeight;
    }




    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int navX = this.width - 30;
        int navStartY = TOP_MARGIN + 10;
        int navWidth = 22;
        int maxNavHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN - 20;

        boolean isOverMiniNav = mouseX >= navX - 2 && mouseX <= navX + navWidth + 4 &&
                mouseY >= navStartY && mouseY <= navStartY + maxNavHeight;

        if (isOverMiniNav && miniNavMaxScroll > 0) {
            miniNavScrollOffset -= (verticalAmount * 15);
            miniNavScrollOffset = Math.max(0, Math.min(miniNavScrollOffset, miniNavMaxScroll));
            return true;
        }

        if (maxScrollOffset > 0) {
            targetScrollOffset -= (verticalAmount * SCROLL_SPEED);
            targetScrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScrollOffset));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (checkTabClick(mouseX, mouseY)) {
                return true;
            }

            if (checkMiniNavClick(mouseX, mouseY)) {
                return true;
            }

            for (ClickableButton btn : clickableButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkMiniNavClick(double mouseX, double mouseY) {
        if (categorySections.isEmpty()) return false;

        int navX = this.width - 30;
        int navStartY = TOP_MARGIN + 10;
        int navWidth = 22;
        int navItemHeight = 20;
        int navItemSpacing = 4;
        int maxNavHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN - 20;
        int yPos = navStartY - (int) miniNavScrollOffset;

        for (CategorySection section : categorySections) {
            if (mouseX >= navX && mouseX <= navX + navWidth &&
                    mouseY >= yPos && mouseY <= yPos + navItemHeight &&
                    mouseY >= navStartY && mouseY <= navStartY + maxNavHeight) {
                targetScrollOffset = Math.max(0, Math.min(section.yPosition, maxScrollOffset));

                if (this.minecraft.player != null) {
                    this.minecraft.player.playSound(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                            0.5f, 1.4f
                    );
                }

                LOGGER.info("Mini-nav: Jumping to category '{}' at position {}",
                        section.category, section.yPosition);

                return true;
            }

            yPos += navItemHeight + navItemSpacing;
        }

        return false;
    }

    private boolean checkTabClick(double mouseX, double mouseY) {
        int panelY = 25;
        int panelCenterX = this.width / 2;
        int panelWidth = 370;
        int panelX = panelCenterX - panelWidth / 2;

        int boxWidth = 85;
        int boxHeight = 16;
        int boxSpacing = 5;
        int startX = panelX + 5;
        int boxY = panelY + 3;

        TabFilter[] tabs = {TabFilter.COMPLETED, TabFilter.IN_PROGRESS, TabFilter.AVAILABLE, TabFilter.LOCKED};

        for (int i = 0; i < tabs.length; i++) {
            int tabX = startX + i * (boxWidth + boxSpacing);

            if (mouseX >= tabX && mouseX <= tabX + boxWidth &&
                    mouseY >= boxY && mouseY <= boxY + boxHeight) {

                TabFilter clickedTab = tabs[i];

                if (this.activeTab != clickedTab) {
                    this.activeTab = clickedTab;
                    applyTabFilter();
                    scrollOffset = 0;
                    targetScrollOffset = 0;
                    if (this.minecraft.player != null) {
                        this.minecraft.player.playSound(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                                0.5f, 1.2f
                        );
                    }
                    LOGGER.info("Tab changed to: {}", clickedTab);
                }
                return true;
            }
        }
        return false;
    }

    private void applyTabFilter() {
        List<EventViewModel.EventData> baseList = events;

        if (!searchQuery.isEmpty()) {
            baseList = events.stream()
                    .filter(event ->
                            event.displayName.toLowerCase().contains(searchQuery) ||
                                    event.description.toLowerCase().contains(searchQuery) ||
                                    event.category.toLowerCase().contains(searchQuery)
                    )
                    .toList();
        }

        this.filteredEvents = baseList.stream()
                .filter(this::matchesActiveTab)
                .collect(java.util.stream.Collectors.toList());

        updateMaxScroll();

        LOGGER.info("Filtered events by tab {}: {} events", activeTab, filteredEvents.size());
    }

    private boolean matchesActiveTab(EventViewModel.EventData event) {
        return switch (activeTab) {
            case ALL -> true;
            case IN_PROGRESS -> event.state == com.eventui.api.event.EventState.IN_PROGRESS;
            case AVAILABLE -> event.state == com.eventui.api.event.EventState.AVAILABLE && !event.isLocked;
            case LOCKED -> event.isLocked;
            case COMPLETED -> event.state == com.eventui.api.event.EventState.COMPLETED;
        };
    }


    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (screenAlpha < 0.99f) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            int overlayColor = (int)(overlayAlpha * 128) << 24;
            graphics.fill(0, 0, this.width, this.height, overlayColor);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.fill(0, 0, this.width, this.height, 0x80000000);
        }

        graphics.drawCenteredString(
                this.font,
                "§6§lEVENTS",
                this.width / 2,
                12,
                0xFFFFFF
        );

        renderStatsPanel(graphics);
        renderGlobalProgress(graphics);

        if (!searchQuery.isEmpty()) {
            String resultsText = String.format("§7Found §e%d§7 event(s)", filteredEvents.size());
            graphics.drawCenteredString(this.font, resultsText, this.width / 2, 102, 0xFFFFFF);
        }

        if (filteredEvents.isEmpty() && !searchQuery.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    "§7No events match '§e" + searchQuery + "§7'",
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
        } else if (events.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    "§7No events available",
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
        } else {
            clickableButtons.clear();
            renderScrollableContent(graphics, mouseX, mouseY);

            if (!categorySections.isEmpty() && maxScrollOffset > 0) {
                renderMiniNav(graphics, mouseX, mouseY);
            }

            renderScrollFadeGradients(graphics);
            if (maxScrollOffset > 0) {
                renderScrollIndicator(graphics);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMiniNav(GuiGraphics graphics, int mouseX, int mouseY) {
        if (categorySections.isEmpty()) return;

        int navX = this.width - 30;
        int navStartY = TOP_MARGIN + 10;
        int navWidth = 22;
        int navItemHeight = 20;
        int navItemSpacing = 4;
        int maxNavHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN - 20;
        int totalNavContent = categorySections.size() * (navItemHeight + navItemSpacing);
        miniNavMaxScroll = Math.max(0, totalNavContent - maxNavHeight);
        Component tooltipToRender = null;
        int tooltipMouseX = 0;
        int tooltipMouseY = 0;

        graphics.enableScissor(navX - 2, navStartY, navX + navWidth + 2, navStartY + maxNavHeight);

        int yPos = navStartY - (int) miniNavScrollOffset;
        int currentScrollMid = (int) scrollOffset + (this.height - TOP_MARGIN - BOTTOM_MARGIN) / 2;

        for (int i = 0; i < categorySections.size(); i++) {
            CategorySection section = categorySections.get(i);

            boolean isHovered = mouseX >= navX && mouseX <= navX + navWidth &&
                    mouseY >= yPos && mouseY <= yPos + navItemHeight &&
                    mouseY >= navStartY && mouseY <= navStartY + maxNavHeight;

            boolean isActive = currentScrollMid >= section.yPosition &&
                    (i == categorySections.size() - 1 ||
                            currentScrollMid < categorySections.get(i + 1).yPosition);

            int bgColor;
            if (isActive) {
                bgColor = 0xDD000000;
            } else if (isHovered) {
                bgColor = 0xAA222222;
            } else {
                bgColor = 0x66000000;
            }

            graphics.fill(navX, yPos, navX + navWidth, yPos + navItemHeight, bgColor);
            int borderWidth = isActive ? 3 : 2;
            int borderColor = isActive ? section.color : (section.color & 0xFFFFFF) | 0x66000000;
            graphics.fill(navX, yPos, navX + borderWidth, yPos + navItemHeight, borderColor);

            String icon = getCategoryInitial(section.category);
            int iconWidth = this.font.width(icon);
            int iconX = navX + (navWidth - iconWidth) / 2;
            int iconY = yPos + (navItemHeight - 8) / 2;
            int textColor = isActive ? 0xFFFFFFFF : 0xFFCCCCCC;
            graphics.drawString(this.font, icon, iconX, iconY, textColor, false);
            if (isHovered) {
                tooltipToRender = Component.literal(
                        "§f" + section.displayName + "\n" +
                                "§7" + section.eventCount + " eventos"
                );
                tooltipMouseX = mouseX;
                tooltipMouseY = mouseY;
            }

            yPos += navItemHeight + navItemSpacing;
        }

        graphics.disableScissor();
        if (tooltipToRender != null) {
            graphics.renderTooltip(this.font, tooltipToRender, tooltipMouseX, tooltipMouseY);
        }

        if (miniNavMaxScroll > 0) {
            int scrollBarX = navX + navWidth + 2;
            int scrollBarWidth = 2;
            int scrollBarY = navStartY;
            int scrollBarHeight = maxNavHeight;

            graphics.fill(scrollBarX, scrollBarY, scrollBarX + scrollBarWidth,
                    scrollBarY + scrollBarHeight, 0x44FFFFFF);

            double scrollPercentage = miniNavScrollOffset / miniNavMaxScroll;
            int thumbHeight = Math.max(10, (int)(scrollBarHeight * ((double)maxNavHeight / totalNavContent)));
            int thumbY = scrollBarY + (int)((scrollBarHeight - thumbHeight) * scrollPercentage);

            graphics.fill(scrollBarX, thumbY, scrollBarX + scrollBarWidth,
                    thumbY + thumbHeight, 0xFFFFAA00);
        }
    }

    private void renderScrollableContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int startY = TOP_MARGIN;
        int endY = this.height - BOTTOM_MARGIN;
        int leftMargin = 45;
        int rightMargin = this.width - 45;
        graphics.enableScissor(0, startY, this.width, endY);
        int yOffset = startY - (int) scrollOffset;
        categorySections.clear();
        int absoluteYPosition = 0;

        Map<String, List<EventViewModel.EventData>> eventsByCategory = new LinkedHashMap<>();
        for (EventViewModel.EventData event : filteredEvents) {
            eventsByCategory
                    .computeIfAbsent(event.category, k -> new ArrayList<>())
                    .add(event);
        }

        for (Map.Entry<String, List<EventViewModel.EventData>> entry : eventsByCategory.entrySet()) {
            String category = entry.getKey();
            List<EventViewModel.EventData> categoryEvents = entry.getValue();

            if (categoryEvents.isEmpty()) continue;
            categorySections.add(new CategorySection(
                    category,
                    getCategoryDisplayName(category),
                    absoluteYPosition,
                    categoryEvents.size(),
                    getCategoryColor(category)
            ));

            if (yOffset >= startY - SECTION_HEADER_HEIGHT && yOffset <= endY) {
                String header = String.format("§f§l%s §7(%d)",
                        getCategoryDisplayName(category).toUpperCase(),
                        categoryEvents.size());
                graphics.drawString(this.font, header, leftMargin, yOffset, 0xFFFFFF);
            }
            yOffset += SECTION_HEADER_HEIGHT;
            absoluteYPosition += SECTION_HEADER_HEIGHT;
            for (EventViewModel.EventData event : categoryEvents) {
                int eventHeight = calculateEventHeight(event);

                if (yOffset + eventHeight >= startY && yOffset <= endY) {
                    yOffset = renderEvent(graphics, event, yOffset, leftMargin, rightMargin, mouseX, mouseY);
                } else {
                    yOffset += eventHeight;
                }

                absoluteYPosition += eventHeight;
                yOffset += CARD_GAP;
                absoluteYPosition += CARD_GAP;
            }

            yOffset += SECTION_PADDING;
            absoluteYPosition += SECTION_PADDING;
        }

        graphics.disableScissor();
    }

    private String getCategoryDisplayName(String category) {
        return switch (category.toLowerCase()) {
            case "mining" -> "Minería";
            case "combat" -> "Combate";
            case "farming" -> "Granja";
            case "exploration" -> "Exploración";
            case "tutorial" -> "Tutorial";
            case "alchemy" -> "Alquimia";
            case "collecting" -> "Colección";
            case "construction" -> "Construcción";
            case "crafting" -> "Crafteo";
            case "enchanting" -> "Encantamiento";
            case "survival" -> "Supervivencia";
            case "taming" -> "Domesticación";
            case "progression" -> "Progresión";
            case "interact" -> "Interacción";
            default -> category;
        };
    }


    private int getCategoryColor(String category) {
        return switch (category.toLowerCase()) {
            case "alchemy" -> 0xFFFF00FF;
            case "collecting" -> 0xFF00FFFF;
            case "construction" -> 0xFF00FF00;
            case "crafting" -> 0xFF3333FF;
            case "enchanting" -> 0xFFCC9900;
            case "mining" -> 0xFF666666;
            case "combat" -> 0xFFDD0000;
            case "farming" -> 0xFF00AA00;
            case "building" -> 0xFFFFAA00;
            case "exploration" -> 0xFF5555FF;
            case "tutorial" -> 0xFF00DDDD;
            case "survival" -> 0xFFFF5555;
            case "taming" -> 0xFFFFDD55;
            case "progression" -> 0xFFFFAA55;
            case "interact" -> 0xFF55AAFF;
            default -> 0xFF888888;
        };
    }



    private String getCategoryInitial(String category) {
        return switch (category.toLowerCase()) {
            case "mining" -> "⛏";
            case "combat" -> "⚔";
            case "farming" -> "🌾";
            case "building" -> "🏗";
            case "exploration" -> "🧭";
            case "tutorial" -> "📖";
            case "alchemy" -> "⚗";
            case "collecting" -> "📦";
            case "construction" -> "🔨";
            case "crafting" -> "🔧";
            case "enchanting" -> "✨";
            case "survival" -> "❤";
            case "taming" -> "🐾";
            case "progression" -> "⭐";
            case "interact" -> "👋";
            default -> category.substring(0, 1).toUpperCase();
        };
    }

    private void renderScrollIndicator(GuiGraphics graphics) {
        int barX = this.width - 10;
        int barY = TOP_MARGIN;
        int barHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        int barWidth = 4;

        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0x40FFFFFF);

        double scrollPercentage = scrollOffset / maxScrollOffset;
        int indicatorHeight = Math.max(20, (int)(barHeight * ((double)barHeight / (barHeight + maxScrollOffset))));
        int indicatorY = barY + (int)((barHeight - indicatorHeight) * scrollPercentage);

        graphics.fill(barX, indicatorY, barX + barWidth, indicatorY + indicatorHeight, 0xFFFFAA00);
    }

    private void renderScrollFadeGradients(GuiGraphics graphics) {
        int fadeHeight = 20;
        int leftMargin = 0;
        int rightMargin = this.width;
        int topY = TOP_MARGIN;

        for (int i = 0; i < fadeHeight; i++) {
            float alpha = 1.0f - ((float) i / fadeHeight);
            int alphaValue = (int)(alpha * 200);
            int color = (alphaValue << 24);
            graphics.fill(leftMargin, topY + i, rightMargin, topY + i + 1, color);
        }

        int bottomY = this.height - BOTTOM_MARGIN;
        for (int i = 0; i < fadeHeight; i++) {
            float alpha = (float) i / fadeHeight;
            int alphaValue = (int)(alpha * 200);
            int color = (alphaValue << 24);
            graphics.fill(leftMargin, bottomY - fadeHeight + i, rightMargin, bottomY - fadeHeight + i + 1, color);
        }
    }

    private int renderEvent(GuiGraphics graphics, EventViewModel.EventData event, int y, int leftMargin, int rightMargin, int mouseX, int mouseY) {
        int eventHeight = calculateEventHeight(event);
        int originalY = y;
        int cardTop = originalY - 5;
        int cardBottom = originalY + eventHeight;
        int cardLeft = leftMargin - 5;
        int cardRight = rightMargin;
        boolean isInVisibleArea = mouseY >= TOP_MARGIN && mouseY <= (this.height - BOTTOM_MARGIN);
        boolean isHovered = isInVisibleArea &&
                mouseX >= cardLeft && mouseX <= cardRight &&
                mouseY >= cardTop && mouseY <= cardBottom;
        String eventId = event.id;
        float currentHoverAlpha = cardHoverAlpha.getOrDefault(eventId, 0f);
        float targetHoverAlpha = isHovered ? 1f : 0f;

        if (Math.abs(currentHoverAlpha - targetHoverAlpha) > 0.01f) {
            float newAlpha = currentHoverAlpha + (targetHoverAlpha - currentHoverAlpha) * 0.3f;
            cardHoverAlpha.put(eventId, newAlpha);
            currentHoverAlpha = newAlpha;
        }
        int liftOffset = (int)(currentHoverAlpha * -3);
        y += liftOffset;




        boolean isInProgress = event.state == com.eventui.api.event.EventState.IN_PROGRESS;
        boolean isCompleted = event.state == com.eventui.api.event.EventState.COMPLETED;
        boolean isLocked = event.isLocked;

        int bgAlpha = isInProgress ? 0xEE : 0xDD;
        int topColor = (bgAlpha << 24) | (isInProgress ? 0x1A1A0F : isLocked ? 0x1A0F0F : 0x1F1F1F);
        int bottomColor = (bgAlpha << 24) | (isInProgress ? 0x0F0F08 : isLocked ? 0x0F0505 : 0x0A0A0A);

        renderGradientBox(graphics, leftMargin - 5, y - 5, rightMargin - leftMargin + 5, eventHeight, topColor, bottomColor);
        graphics.fill(leftMargin - 3, y + eventHeight - 2, rightMargin - 2, y + eventHeight, 0x44000000);
        if (currentHoverAlpha > 0.01f) {
            int glowAlpha = (int)(currentHoverAlpha * 100);
            int glowColor = (glowAlpha << 24) | 0xFFAA00;

            graphics.fill(leftMargin - 6, y - 6, rightMargin + 1, y - 5, glowColor);
            graphics.fill(leftMargin - 6, y + eventHeight, rightMargin + 1, y + eventHeight + 1, glowColor);
            graphics.fill(leftMargin - 6, y - 6, leftMargin - 5, y + eventHeight + 1, glowColor);
            graphics.fill(rightMargin, y - 6, rightMargin + 1, y + eventHeight + 1, glowColor);
        }
        if (isLocked) {
            graphics.fill(leftMargin - 5, y - 5, rightMargin, y + eventHeight, 0x66000000);
        }

        net.minecraft.world.item.ItemStack iconStack = parseItemStack(event.icon);
        if (!iconStack.isEmpty()) {
            if (isLocked) {
                graphics.fill(leftMargin - 2, y - 4, leftMargin + 18, y + 16, 0x66000000);
            }
            graphics.renderItem(iconStack, leftMargin, y - 2);
        }

        if (isLocked) {
            graphics.drawString(this.font, "§c🔒", leftMargin + 10, y + 6, 0xFFFFFF, true);
        }

        String title = event.displayName;
        int titleX = leftMargin + 18;
        graphics.drawString(this.font, title, titleX, y - 2, isLocked ? 0xCCCCCC : 0xFFFFFF, true);

        int badgeX = titleX + this.font.width(title) + 5;
        badgeX = renderBadge(graphics, badgeX, y - 2, event.category, getBadgeColor(event.category));
        badgeX = renderBadge(graphics, badgeX, y - 2, event.difficulty, getDifficultyColor(event.difficulty));
        y += 12;

        if (isLocked) {
            int leftColumnX = leftMargin + 10;
            int rightColumnX = leftMargin + (rightMargin - leftMargin) / 2 + 20;
            graphics.drawString(this.font, "§c🔒 LOCKED", leftColumnX, y, 0xFFFFFF, true);
            List<String> missingDeps = getMissingDependencyNames(event.dependencies);
            if (!missingDeps.isEmpty()) {
                graphics.drawString(this.font, "§7Complete first:", rightColumnX, y, 0xFFFFFF);
                y += 12;

                for (String depName : missingDeps) {
                    String displayName = depName;
                    int maxWidth = rightMargin - rightColumnX - 15;
                    if (this.font.width("  • " + displayName) > maxWidth) {
                        while (this.font.width("  • " + displayName + "...") > maxWidth && displayName.length() > 5) {
                            displayName = displayName.substring(0, displayName.length() - 1);
                        }
                        displayName += "...";
                    }

                    graphics.drawString(this.font, "  §e• " + displayName, rightColumnX, y, 0xFFFFFF);
                    y += 12;
                }
            } else {
                y += 12;
            }
            y += 10;
        } else {
            graphics.drawString(this.font, "§7" + event.description, leftMargin + 10, y, 0xFFFFFF);
            y += 12;
            if (event.currentObjectiveDescription != null &&
                    (event.state == com.eventui.api.event.EventState.AVAILABLE ||
                            event.state == com.eventui.api.event.EventState.IN_PROGRESS)) {
                graphics.drawString(this.font, "§e→ " + event.currentObjectiveDescription, leftMargin + 10, y, 0xFFFFFF);
                y += 12;

                if (event.hasRewards()) {
                    y = renderRewardsVisual(graphics, event, leftMargin + 10, y) + 12;
                }
            }
            if (event.state == com.eventui.api.event.EventState.IN_PROGRESS) {
                eventId = event.id;
                float currentAnimatedProgress = animatedProgress.getOrDefault(eventId, event.getProgressPercentage());
                if (!animatedProgress.containsKey(eventId)) {
                    animatedProgress.put(eventId, event.getProgressPercentage());
                }

                String progressText = String.format("Progress: %d/%d (%d%%)",
                        event.currentProgress, event.targetProgress, (int)(event.getProgressPercentage() * 100));
                graphics.drawString(this.font, "§6" + progressText, leftMargin + 10, y, 0xFFFFFF);
                y += 12;

                int barWidth = rightMargin - leftMargin - 20;
                int barHeight = 6;
                int barX = leftMargin + 10;
                graphics.fill(barX, y, barX + barWidth, y + barHeight, 0xFF222222);

                int fillWidth = (int) (barWidth * currentAnimatedProgress);
                if (fillWidth > 0) {
                    int barColor;
                    if (currentAnimatedProgress >= 0.9f) {
                        barColor = 0xFF00AA00;
                    } else if (currentAnimatedProgress >= 0.5f) {
                        barColor = 0xFFFFAA00;
                    } else {
                        barColor = 0xFFFFCC00;
                    }

                    graphics.fill(barX, y, barX + fillWidth, y + barHeight, barColor);
                    if (fillWidth > 2 && currentAnimatedProgress < 0.99f) {
                        int glowColor = 0x88FFFFFF;
                        graphics.fill(barX + fillWidth - 2, y, barX + fillWidth, y + barHeight, glowColor);
                    }
                }

                y += barHeight + 6;
            }

            y = renderEventButtons(graphics, event, y, leftMargin, rightMargin, mouseX, mouseY);
        }

        graphics.fill(leftMargin - 5, y, rightMargin, y + 1, 0x33FFFFFF);
        y += 2;
        return y;
    }

    private int renderEventButtons(GuiGraphics graphics, EventViewModel.EventData event,
                                   int y, int leftMargin, int rightMargin, int mouseX, int mouseY) {
        int buttonHeight = 18;
        int buttonSpacing = 8;
        int button1Width = 70;
        int button2Width = 80;

        ButtonState btn1 = getButton1State(event);
        renderSingleButton(graphics, leftMargin + 10, y, button1Width, buttonHeight,
                btn1.text, btn1.color, btn1.enabled, btn1.action, mouseX, mouseY);

        ButtonState btn2 = getButton2State(event);
        renderSingleButton(graphics, leftMargin + 10 + button1Width + buttonSpacing, y, button2Width, buttonHeight,
                btn2.text, btn2.color, btn2.enabled, btn2.action, mouseX, mouseY);

        return y + buttonHeight + 6;
    }


    private ButtonState getButton1State(EventViewModel.EventData event) {
        return switch (event.state) {
            case AVAILABLE -> new ButtonState(
                    "Iniciar",
                    0xFF00AA00,
                    true,
                    () -> handleStartEvent(event)
            );

            case IN_PROGRESS -> new ButtonState(
                    "En progreso",
                    0xFF444444,
                    false,
                    null
            );

            case COMPLETED -> {
                if (event.repeatable) {
                    yield new ButtonState(
                            "Reiniciar",
                            0xFFFFAA00,
                            true,
                            () -> handleStartEvent(event)
                    );
                } else {
                    yield new ButtonState(
                            "Completado",
                            0xFF444444,
                            false,
                            null
                    );
                }
            }

            case FAILED -> new ButtonState(
                    "Fallido",
                    0xFF662222,
                    false,
                    null
            );

            default -> new ButtonState(
                    "---",
                    0xFF333333,
                    false,
                    null
            );
        };
    }

    private ButtonState getButton2State(EventViewModel.EventData event) {
        if (event.state == com.eventui.api.event.EventState.IN_PROGRESS) {
            return new ButtonState(
                    "Abandonar",
                    0xFFDD0000,
                    true,
                    () -> handleAbandonEvent(event)
            );
        } else {
            return new ButtonState(
                    "Abandonar",
                    0xFF333333,
                    false,
                    null
            );
        }
    }

    private void renderSingleButton(GuiGraphics graphics, int x, int y, int width, int height,
                                    String text, int baseColor, boolean enabled, Runnable action,
                                    int mouseX, int mouseY) {

        boolean isHovered = enabled && mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
        String buttonId = x + "_" + y + "_" + text;
        float currentScale = buttonHoverScale.getOrDefault(buttonId, 1.0f);
        float targetScale = isHovered ? 1.06f : 1.0f;
        if (Math.abs(currentScale - targetScale) > 0.005f) {
            float diff = targetScale - currentScale;
            currentScale += diff * 0.3f;
            buttonHoverScale.put(buttonId, currentScale);
        } else {
            currentScale = targetScale;
            buttonHoverScale.put(buttonId, currentScale);
        }

        int scaledWidth = (int)(width * currentScale);
        int scaledHeight = (int)(height * currentScale);

        int renderX = x - (scaledWidth - width) / 2;
        int renderY = y - (scaledHeight - height) / 2;

        int bgColor;
        if (!enabled) {
            bgColor = 0xAA222222;
        } else if (isHovered) {
            bgColor = baseColor | 0xFF000000;
        } else {
            bgColor = (baseColor & 0x00FFFFFF) | 0xCC000000;
        }

        graphics.pose().pushPose();
        graphics.fill(renderX, renderY, renderX + scaledWidth, renderY + scaledHeight, bgColor);

        if (isHovered && enabled && currentScale > 1.01f) {
            int glowIntensity = (int)((currentScale - 1.0f) * 15 * 255);
            int glowColor = (Math.min(glowIntensity, 150) << 24) | 0xFFFFFF;

            graphics.fill(renderX - 1, renderY - 1, renderX + scaledWidth + 1, renderY, glowColor);
            graphics.fill(renderX - 1, renderY + scaledHeight, renderX + scaledWidth + 1, renderY + scaledHeight + 1, glowColor);
            graphics.fill(renderX - 1, renderY, renderX, renderY + scaledHeight, glowColor);
            graphics.fill(renderX + scaledWidth, renderY, renderX + scaledWidth + 1, renderY + scaledHeight, glowColor);
        }

        if (enabled && !isHovered) {
            graphics.fill(renderX, renderY, renderX + scaledWidth, renderY + 1, 0xFFFFFFFF);
            graphics.fill(renderX, renderY + scaledHeight - 1, renderX + scaledWidth, renderY + scaledHeight, 0xFF666666);
        }

        int textColor = enabled ? 0xFFFFFFFF : 0xFF666666;
        int textX = renderX + (scaledWidth / 2) - (this.font.width(text) / 2);
        int textY = renderY + (scaledHeight / 2) - 4;

        graphics.drawString(this.font, text, textX, textY, textColor, isHovered && enabled);
        graphics.pose().popPose();

        if (enabled && action != null) {
            clickableButtons.add(new ClickableButton(x, y, width, height, action));
        }
    }


    private void handleAbandonEvent(EventViewModel.EventData event) {
        if (event.repeatable) {
            executeAbandonEvent(event);
            return;
        }

        LOGGER.info("Event is not repeatable, showing confirmation dialog");

        ConfirmAbandonScreen confirmScreen = new ConfirmAbandonScreen(
                this,
                event.displayName,
                () -> executeAbandonEvent(event)
        );

        Minecraft.getInstance().setScreen(confirmScreen);
    }

    private void executeAbandonEvent(EventViewModel.EventData event) {
        assert Minecraft.getInstance().player != null;
        UUID playerId = Minecraft.getInstance().player.getUUID();

        Map<String, String> payload = Map.of(
                "button_id", "abandon-button-" + event.id,
                "action", "abandon_event:" + event.id,
                "event_id", event.id
        );

        com.eventui.api.bridge.BridgeMessage message = new BridgeMessageImpl(
                MessageType.UI_BUTTON_CLICKED,
                payload,
                playerId
        );

        ClientEventBridge.getInstance().sendMessage(message);

        LOGGER.info("Sent abandon_event action to server for: {}", event.id);

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§cAbandoning event: §f" + event.displayName),
                    true
            );
        }
    }

    private record ButtonState(String text, int color, boolean enabled, Runnable action) {
    }

    private void handleStartEvent(EventViewModel.EventData event) {
        assert Minecraft.getInstance().player != null;
        UUID playerId = Minecraft.getInstance().player.getUUID();

        Map<String, String> payload = Map.of(
                "button_id", "start-button-" + event.id,
                "screen_id", event.id,
                "action", "start_event:" + event.id,
                "event_id", event.id
        );

        com.eventui.api.bridge.BridgeMessage message = new BridgeMessageImpl(
                MessageType.UI_BUTTON_CLICKED,
                payload,
                playerId
        );

        ClientEventBridge.getInstance().sendMessage(message);
        LOGGER.info("Sent start_event action to server for: {}", event.id);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("§eStarting event: §f" + event.displayName),
                    true
            );
            Minecraft.getInstance().player.playSound(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                    1.0f,
                    1.0f
            );
        }
    }



    private void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress) {
        graphics.fill(x, y, x + width, y + height, 0xFF1A1A1A);

        int fillWidth = (int) (width * progress);
        int color = progress >= 1.0f ? 0xFF00AA00 : 0xFFFFAA00;
        graphics.fill(x, y, x + fillWidth, y + height, color);

        graphics.fill(x, y, x + width, y + 1, 0xFF666666);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF444444);
        graphics.fill(x, y, x + 1, y + height, 0xFF666666);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF666666);
    }

    @Override
    public void onClose() {
        animationManager.stopAll();

        if (viewModel != null) {
            viewModel.removeChangeListener(this::onEventsUpdated);
        }
        super.onClose();
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class ClickableButton {
        private final int x, y, width, height;
        private final Runnable onClick;

        public ClickableButton(int x, int y, int width, int height, Runnable onClick) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.onClick = onClick;
        }

        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width &&
                    mouseY >= y && mouseY <= y + height;
        }

        public void onClick() {
            this.onClick.run();
        }
    }

    private net.minecraft.world.item.ItemStack parseItemStack(String itemId) {
        try {
            if (itemId == null || !itemId.contains(":")) {
                LOGGER.warn("Invalid item ID format: {}", itemId);
                return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            }

            net.minecraft.resources.ResourceLocation location;
            try {
                location = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse ResourceLocation: {}", itemId);
                return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            }

            if (location == null) {
                LOGGER.warn("Invalid ResourceLocation: {}", itemId);
                return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
            }

            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);

            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new net.minecraft.world.item.ItemStack(item);
            }

        } catch (Exception e) {
            LOGGER.warn("Exception parsing item: {} - {}", itemId, e.getMessage());
        }

        return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
    }

    private int renderBadge(GuiGraphics graphics, int x, int y, String text, int color) {
        int padding = 4;
        int width = this.font.width(text) + padding * 2;
        int height = 11;

        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0x88FFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x88000000);
        graphics.drawString(this.font, text, x + padding, y + 2, 0xFFFFFFFF, false);
        return x + width;
    }

    private int getDifficultyColor(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 0xCC00AA00;
            case "medium" -> 0xCCFFAA00;
            case "hard" -> 0xCCDD0000;
            case "expert" -> 0xCC5500AA;
            default -> 0xCC666666;
        };
    }

    private String parseRewardsText(String rewardsJson) {
        if (rewardsJson == null || rewardsJson.isEmpty() || rewardsJson.equals("{}")) {
            return "???";
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            try {
                java.util.List<java.util.Map<String, Object>> rewardsList = gson.fromJson(
                        rewardsJson,
                        new com.google.gson.reflect.TypeToken<java.util.List<java.util.Map<String, Object>>>(){}.getType()
                );

                if (rewardsList != null && !rewardsList.isEmpty()) {
                    java.util.List<String> parts = new java.util.ArrayList<>();

                    for (java.util.Map<String, Object> reward : rewardsList) {
                        String type = (String) reward.get("type");

                        if ("item".equals(type)) {
                            String id = (String) reward.get("id");
                            int count = ((Number) reward.get("count")).intValue();
                            String itemName = id.contains(":") ?
                                    id.substring(id.indexOf(":") + 1).replace("_", " ") :
                                    id;

                            parts.add(count + "x " + itemName);
                        }
                    }

                    if (!parts.isEmpty()) {
                        return String.join(", ", parts);
                    }
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                LOGGER.warn("Failed to parse rewards as list, trying as map...");
            }

            java.util.Map<String, Object> rewards = gson.fromJson(
                    rewardsJson,
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>(){}.getType()
            );

            java.util.List<String> parts = new java.util.ArrayList<>();

            if (rewards.containsKey("xp")) {
                parts.add(rewards.get("xp") + " XP");
            }

            if (rewards.containsKey("items")) {
                java.util.List<String> items = (java.util.List<String>) rewards.get("items");
                for (String item : items) {
                    String[] split = item.split(" ");
                    if (split.length == 2) {
                        parts.add(split[1] + "x " + split[0].replace("minecraft:", ""));
                    }
                }
            }

            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to parse rewards: " + rewardsJson, e);
        }

        return "???";
    }


    private String formatElapsedTime(long millis) {
        long seconds = millis / 1000;

        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else if (seconds < 86400) {
            return (seconds / 3600) + "h";
        } else {
            return (seconds / 86400) + "d";
        }
    }

    private int renderRewardsVisual(GuiGraphics graphics, EventViewModel.EventData event, int x, int y) {
        graphics.drawString(this.font, "§6⚡ Rewards:", x, y, 0xFFFFFF);
        int iconX = x + this.font.width("⚡ Rewards:") + 8;
        int startY = y;

        if (event.rewardsJson == null || event.rewardsJson.isEmpty() || event.rewardsJson.equals("{}")) {
            graphics.drawString(this.font, "§7???", iconX, y, 0xFFFFFF);
            return startY;
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<java.util.Map<String, Object>> rewardsList = gson.fromJson(
                    event.rewardsJson,
                    new com.google.gson.reflect.TypeToken<java.util.List<java.util.Map<String, Object>>>(){}.getType()
            );

            if (rewardsList != null && !rewardsList.isEmpty()) {
                for (java.util.Map<String, Object> reward : rewardsList) {
                    String type = (String) reward.get("type");

                    if ("item".equals(type)) {
                        String itemId = (String) reward.get("id");
                        int count = ((Number) reward.get("count")).intValue();
                        net.minecraft.world.item.ItemStack itemStack = parseItemStack(itemId);

                        if (!itemStack.isEmpty()) {
                            graphics.fill(iconX - 1, y - 3, iconX + 17, y + 15, 0x44000000);
                            graphics.renderItem(itemStack, iconX, y - 2);
                            String amountText = "§f" + count + "x";
                            graphics.drawString(this.font, amountText, iconX + 18, y + 4, 0xFFFFFF, true);

                            iconX += 70;
                        }
                    }
                }

                return startY;
            }
            java.util.Map<String, Object> rewards = gson.fromJson(
                    event.rewardsJson,
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>(){}.getType()
            );
            if (rewards.containsKey("xp")) {
                int xpAmount = ((Number) rewards.get("xp")).intValue();
                net.minecraft.world.item.ItemStack xpBottle = new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.EXPERIENCE_BOTTLE
                );
                graphics.fill(iconX - 1, y - 3, iconX + 17, y + 15, 0x44000000);
                graphics.renderItem(xpBottle, iconX, y - 2);
                String xpText = "§a" + xpAmount + " XP";
                graphics.drawString(this.font, xpText, iconX + 18, y + 4, 0xFFFFFF, true);

                iconX += 90;
            }
            if (rewards.containsKey("items")) {
                java.util.List<String> items = (java.util.List<String>) rewards.get("items");

                for (String itemStr : items) {
                    try {
                        String[] parts = itemStr.trim().split("\\s+");
                        String itemId = parts[0];
                        int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        net.minecraft.world.item.ItemStack itemStack = parseItemStack(itemId);

                        if (!itemStack.isEmpty()) {
                            graphics.fill(iconX - 1, y - 3, iconX + 17, y + 15, 0x44000000);
                            graphics.renderItem(itemStack, iconX, y - 2);
                            String amountText = "§f" + amount + "x";
                            graphics.drawString(this.font, amountText, iconX + 18, y + 4, 0xFFFFFF, true);

                            iconX += 70;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to render reward item: {}", itemStr);
                    }
                }
            }
            return startY;
        } catch (Exception e) {
            LOGGER.warn("Failed to render rewards visual: " + e.getMessage());
            String rewardsText = parseRewardsText(event.rewardsJson);
            graphics.drawString(this.font, "§f" + rewardsText, iconX, y, 0xFFFFFF);
            return startY;
        }
    }

    private void renderGlobalProgress(GuiGraphics graphics) {
        completedCount = 0;
        inProgressCount = 0;
        availableCount = 0;
        int totalEvents = events.size();

        for (EventViewModel.EventData event : events) {
            switch (event.state) {
                case COMPLETED -> completedCount++;
                case IN_PROGRESS -> inProgressCount++;
                case AVAILABLE -> availableCount++;
            }
        }

        if (totalEvents == 0) return;

        float globalProgress = (float) completedCount / totalEvents;
        int centerX = this.width / 2;
        int barY = 52;
        int barWidth = 140;
        int barHeight = 8;
        int barX = centerX - (barWidth / 2);
        String label = "§7Progress:";
        int labelWidth = this.font.width(label);
        graphics.drawString(this.font, label, barX - labelWidth - 8, barY, 0xFFFFFF);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0A0A0A);

        int fillWidth = (int) (barWidth * globalProgress);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00DD00);
            graphics.fill(barX, barY, barX + fillWidth, barY + 2, 0x8800FF00);
        }

        graphics.fill(barX, barY, barX + barWidth, barY + 1, 0xFF444444);
        graphics.fill(barX, barY + barHeight - 1, barX + barWidth, barY + barHeight, 0xFF222222);
        graphics.fill(barX, barY, barX + 1, barY + barHeight, 0xFF444444);
        graphics.fill(barX + barWidth - 1, barY, barX + barWidth, barY + barHeight, 0xFF444444);

        String percentText = String.format("§a%.0f%%", globalProgress * 100);
        graphics.drawString(this.font, percentText, barX + barWidth + 8, barY, 0xFFFFFF, true);

        String statsText = String.format("§7%d/%d completed", completedCount, totalEvents);
        int statsWidth = this.font.width(statsText);
        graphics.drawString(this.font, statsText, centerX - (statsWidth / 2), barY + 12, 0xFFFFFF);
    }

    private void renderStatsPanel(GuiGraphics graphics) {
        int panelY = 25;
        int panelCenterX = this.width / 2;

        int lockedCount = 0;
        for (EventViewModel.EventData event : events) {
            if (event.isLocked) {
                lockedCount++;
            }
        }

        int panelWidth = 370;
        int panelHeight = 22;
        int panelX = panelCenterX - panelWidth / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC1A1A1A);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF333333);

        int boxWidth = 85;
        int boxSpacing = 5;
        int startX = panelX + 5;

        renderTabBox(graphics, startX, panelY + 3, boxWidth,
                "Completados", completedCount, 0xFF00AA00,
                TabFilter.COMPLETED);

        renderTabBox(graphics, startX + (boxWidth + boxSpacing), panelY + 3, boxWidth,
                "En progreso", inProgressCount, 0xFFFFAA00,
                TabFilter.IN_PROGRESS);

        renderTabBox(graphics, startX + (boxWidth + boxSpacing) * 2, panelY + 3, boxWidth,
                "Disponibles", availableCount, 0xFF666666,
                TabFilter.AVAILABLE);

        renderTabBox(graphics, startX + (boxWidth + boxSpacing) * 3, panelY + 3, boxWidth,
                "Bloqueados", lockedCount, 0xFF444444,
                TabFilter.LOCKED);
    }

    private void renderTabBox(GuiGraphics graphics, int x, int y, int width,
                              String label, int value, int color, TabFilter tabType) {
        int height = 16;

        int mouseX = (int) this.minecraft.mouseHandler.xpos() * this.width / this.minecraft.getWindow().getGuiScaledWidth();
        int mouseY = (int) this.minecraft.mouseHandler.ypos() * this.height / this.minecraft.getWindow().getGuiScaledHeight();

        boolean isHovered = mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;

        boolean isActive = this.activeTab == tabType;

        int bgColor;
        if (isActive) {
            bgColor = 0xDD000000;
        } else if (isHovered) {
            bgColor = 0xAA222222;
        } else {
            bgColor = 0x88000000;
        }

        graphics.fill(x, y, x + width, y + height, bgColor);
        if (isActive) {
            graphics.fill(x, y, x + width, y + 2, color);
            int centerX = x + width / 2;
            int indicatorY = y + height + 1;
            graphics.fill(centerX - 3, indicatorY, centerX + 3, indicatorY + 1, color);
            graphics.fill(centerX - 2, indicatorY + 1, centerX + 2, indicatorY + 2, color);
            graphics.fill(centerX - 1, indicatorY + 2, centerX + 1, indicatorY + 3, color);
        } else {
            graphics.fill(x, y, x + width, y + 1, color);
        }

        if (isHovered && !isActive) {
            graphics.fill(x - 1, y - 1, x + width + 1, y, 0x44FFFFFF);
            graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0x44FFFFFF);
            graphics.fill(x - 1, y, x, y + height, 0x44FFFFFF);
            graphics.fill(x + width, y, x + width + 1, y + height, 0x44FFFFFF);
        }


        int labelColor = isActive ? 0xFFFFFFFF : 0xFFCCCCCC;
        graphics.drawString(this.font, "§7" + label, x + 3, y + 3, labelColor, false);

        String valueStr = "§l" + value;
        int valueWidth = this.font.width(valueStr);
        int valueColor = isActive ? color : (color & 0xFFFFFF) | 0xCC000000;
        graphics.drawString(this.font, valueStr, x + width - valueWidth - 3, y + 3, valueColor, true);
    }

    private void renderStatBox(GuiGraphics graphics, int x, int y, int width, String label, int value, int color) {
        int height = 16;
        graphics.fill(x, y, x + width, y + height, 0x88000000);
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.drawString(this.font, "§7" + label, x + 3, y + 3, 0xFFFFFF, false);

        String valueStr = "§l" + value;
        int valueWidth = this.font.width(valueStr);
        graphics.drawString(this.font, valueStr, x + width - valueWidth - 3, y + 3, color, true);
    }

    private void renderGradientBox(GuiGraphics graphics, int x, int y, int width, int height, int colorTop, int colorBottom) {
        int strips = Math.min(height, 20);
        float stripHeight = (float) height / strips;

        for (int i = 0; i < strips; i++) {
            float ratio = (float) i / strips;
            int color = blendColors(colorTop, colorBottom, ratio);

            int stripY = y + (int)(i * stripHeight);
            int nextStripY = y + (int)((i + 1) * stripHeight);

            graphics.fill(x, stripY, x + width, nextStripY, color);
        }
    }

    private int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int)(a1 + (a2 - a1) * ratio);
        int r = (int)(r1 + (r2 - r1) * ratio);
        int g = (int)(g1 + (g2 - g1) * ratio);
        int b = (int)(b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void onSearchChanged(String query) {
        this.searchQuery = query.toLowerCase().trim();
        applyTabFilter();
        scrollOffset = 0;
        targetScrollOffset = 0;
    }

    private void updateFilteredEvents() {
        applyTabFilter();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchField.isFocused() && keyCode == 256) {
            this.searchField.setValue("");
            this.searchField.setFocused(false);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<String> getMissingDependencyNames(List<String> dependencies) {
        List<String> names = new ArrayList<>();
        Set<String> completedIds = new HashSet<>();
        for (EventViewModel.EventData event : events) {
            if (event.state == com.eventui.api.event.EventState.COMPLETED) {
                completedIds.add(event.id);
            }
        }

        for (String depId : dependencies) {
            if (!completedIds.contains(depId)) {
                for (EventViewModel.EventData event : events) {
                    if (event.id.equals(depId)) {
                        names.add(event.displayName);
                        break;
                    }
                }
            }
        }

        return names;
    }

    private int calculateEventHeight(EventViewModel.EventData event) {
        int height = 0;
        height += 12;

        if (event.isLocked) {
            height += 12;

            List<String> missingDeps = getMissingDependencyNames(event.dependencies);
            height += missingDeps.size() * 12;

            if (missingDeps.isEmpty()) {
                height += 12;
            }

            height += 10;
            height += 2;

            return height;
        }

        height += 12;
        if (event.currentObjectiveDescription != null &&
                (event.state == com.eventui.api.event.EventState.AVAILABLE ||
                        event.state == com.eventui.api.event.EventState.IN_PROGRESS)) {
            height += 12;
        }

        if (event.hasRewards) {
            height += 24;
        }

        if (event.state == com.eventui.api.event.EventState.IN_PROGRESS) {
            height += 12;
            height += 6;
            height += 6;
        }

        if (!event.isLocked) {
            height += 18;
            height += 6;
        }

        height += 2;

        return height;
    }


    private int getBadgeColor(String category) {
        return switch (category.toLowerCase()) {
            case "tutorial" -> 0xCC00AA00;
            case "mining" -> 0xCC666666;
            case "combat" -> 0xCCDD0000;
            case "farming" -> 0xCC00AA00;
            case "building" -> 0xCCFFAA00;
            case "exploration" -> 0xCC5555FF;
            default -> 0xCC444444;
        };
    }

}