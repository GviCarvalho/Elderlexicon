package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * UI screen allowing the player to curate the nine-slot spelling repertoire.
 */
@SuppressWarnings("null")
public final class SpellingRepertoireScreen extends Screen {

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();
    private static final Component TITLE = Component.translatable("screen.elderlexicon.spelling_repertoire.title");
    private static final ParserDictionary.RuneType[] COLUMN_ORDER = {
            ParserDictionary.RuneType.SOURCE,
            ParserDictionary.RuneType.FUNCTION,
            ParserDictionary.RuneType.SHAPE
    };

    private static final ResourceLocation TAB_TEXTURE = ResourceLocation.fromNamespaceAndPath("elderlexicon", "textures/gui/runic_lexicon_tab.png");
    private static final ResourceLocation RUNE_SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath("elderlexicon", "textures/gui/rune_square.png");
    private static final ResourceLocation STONE_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png");
    private static final int TAB_TEXTURE_WIDTH = 896;
    private static final int TAB_TEXTURE_HEIGHT = 560;
    private static final int RUNE_TEXTURE_WIDTH = 70;
    private static final int RUNE_TEXTURE_HEIGHT = 70;

    private static final int PANEL_WIDTH = 210;
    private static final int PANEL_HEIGHT = 166;
    private static final int NODE_VISUAL_SIZE = 20;
    private static final int COLUMN_SPACING = 70;
    private static final int ROW_SPACING = 40;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_GAP = 2;

    private final ClientSpellingController controller;
    private final List<String> workingSlots = new ArrayList<>(SpellingRepertoire.SLOT_COUNT);

    private int panelLeft;
    private int panelTop;
    private int canvasLeft;
    private int canvasTop;
    private int canvasWidth;
    private int canvasHeight;
    private int hotbarLeft;
    private int hotbarTop;

    private final List<RuneNode> runeNodes = new ArrayList<>();
    private double scrollX;
    private double scrollY;
    private double minScrollX;
    private double maxScrollX;
    private double minScrollY;
    private double maxScrollY;
    private boolean scrollInitialized;

    private boolean panningCanvas;
    private double lastPanMouseX;
    private double lastPanMouseY;

    private RuneNode hoveredNode;
    private int hoveredSlot = -1;
    private int selectedSlot = 0;

    private String draggingRuneId;
    private double draggingMouseX;
    private double draggingMouseY;

    public SpellingRepertoireScreen(ClientSpellingController controller) {
        super(TITLE);
        this.controller = Objects.requireNonNull(controller);
        workingSlots.addAll(controller.repertoireSlots());
        while (workingSlots.size() < SpellingRepertoire.SLOT_COUNT) {
            workingSlots.add("");
        }
    }

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - PANEL_HEIGHT) / 2;
        canvasLeft = panelLeft + 8;
        canvasTop = panelTop + 20;
        canvasWidth = PANEL_WIDTH - 16;
        hotbarLeft = panelLeft + (PANEL_WIDTH - hotbarWidth()) / 2;
        hotbarTop = panelTop + PANEL_HEIGHT - HOTBAR_SLOT_SIZE - 12;
        if (hotbarTop < canvasTop + 24) {
            hotbarTop = canvasTop + 24;
        }
        canvasHeight = Math.max(32, hotbarTop - canvasTop - 8);

        selectedSlot = Mth.clamp(selectedSlot, 0, SpellingRepertoire.SLOT_COUNT - 1);

        rebuildRuneLayout(true);
    }

    private void rebuildRuneLayout(boolean resetScroll) {
        runeNodes.clear();

        Map<ParserDictionary.RuneType, List<ParserDictionary.RuneDefinition>> grouped = new EnumMap<>(ParserDictionary.RuneType.class);
        for (ParserDictionary.RuneDefinition definition : DICTIONARY.entries().values()) {
            if (RuneSgaMapper.glyphForRune(definition.id()).isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(definition.type(), key -> new ArrayList<>()).add(definition);
        }
        grouped.values().forEach(list -> list.sort((a, b) -> a.id().compareToIgnoreCase(b.id())));

        int baseColumnX = -COLUMN_SPACING * (COLUMN_ORDER.length - 1) / 2;
        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        boolean firstNode = true;

        for (int columnIndex = 0; columnIndex < COLUMN_ORDER.length; columnIndex++) {
            ParserDictionary.RuneType type = COLUMN_ORDER[columnIndex];
            List<ParserDictionary.RuneDefinition> entries = grouped.getOrDefault(type, List.of());
            int columnX = baseColumnX + columnIndex * COLUMN_SPACING;
            if (entries.isEmpty()) {
                continue;
            }
            int totalHeight = (entries.size() - 1) * ROW_SPACING;
            int startY = -totalHeight / 2;
            for (int row = 0; row < entries.size(); row++) {
                int graphY = startY + row * ROW_SPACING;
                RuneNode node = new RuneNode(entries.get(row), columnX, graphY);
                runeNodes.add(node);
                if (firstNode) {
                    minX = maxX = columnX;
                    minY = maxY = graphY;
                    firstNode = false;
                } else {
                    minX = Math.min(minX, columnX);
                    maxX = Math.max(maxX, columnX);
                    minY = Math.min(minY, graphY);
                    maxY = Math.max(maxY, graphY);
                }
            }
        }

        if (runeNodes.isEmpty()) {
            minX = maxX = minY = maxY = 0;
        }

        updateScrollBounds(minX, maxX, minY, maxY, resetScroll);
    }

    private void updateScrollBounds(int minX, int maxX, int minY, int maxY, boolean resetScroll) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            return;
        }
        int centerX = canvasLeft + canvasWidth / 2;
        int centerY = canvasTop + canvasHeight / 2;
        int leftBound = canvasLeft + 12;
        int rightBound = canvasLeft + canvasWidth - 12;
        int topBound = canvasTop + 12;
        int bottomBound = canvasTop + canvasHeight - 12;

        double leftLimit = leftBound - centerX - minX;
        double rightLimit = rightBound - centerX - maxX;
        double topLimit = topBound - centerY - minY;
        double bottomLimit = bottomBound - centerY - maxY;

        minScrollX = Math.min(leftLimit, rightLimit);
        maxScrollX = Math.max(leftLimit, rightLimit);
        minScrollY = Math.min(topLimit, bottomLimit);
        maxScrollY = Math.max(topLimit, bottomLimit);

        if (resetScroll || !scrollInitialized) {
            scrollX = (minScrollX + maxScrollX) / 2.0D;
            scrollY = (minScrollY + maxScrollY) / 2.0D;
            scrollInitialized = true;
        } else {
            clampScroll();
        }
    }

    private void clampScroll() {
        scrollX = Math.min(Math.max(scrollX, minScrollX), maxScrollX);
        scrollY = Math.min(Math.max(scrollY, minScrollY), maxScrollY);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        renderFrameBackdrop(graphics);

        renderRuneCanvas(graphics, mouseX, mouseY);
        renderHotbar(graphics, mouseX, mouseY);

        renderFrameOverlay(graphics);
        graphics.drawCenteredString(font, TITLE, panelLeft + PANEL_WIDTH / 2, panelTop + 8, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hoveredNode != null) {
            renderRuneTooltip(graphics, hoveredNode.definition.id(), mouseX, mouseY);
        } else if (hoveredSlot >= 0) {
            renderRuneTooltip(graphics, slotRune(hoveredSlot), mouseX, mouseY);
        }

        if (draggingRuneId != null) {
            renderDragGhost(graphics);
        }
    }

    private void renderFrameBackdrop(GuiGraphics graphics) {
        drawStoneBackdrop(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0x55000000);
    }

    private void renderFrameOverlay(GuiGraphics graphics) {
        graphics.blit(TAB_TEXTURE, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0, 0, TAB_TEXTURE_WIDTH, TAB_TEXTURE_HEIGHT, TAB_TEXTURE_WIDTH, TAB_TEXTURE_HEIGHT);
    }

    private void drawStoneBackdrop(GuiGraphics graphics) {
        int frameRight = panelLeft + PANEL_WIDTH;
        int frameBottom = panelTop + PANEL_HEIGHT;
        for (int y = panelTop; y < frameBottom; y += 16) {
            int tileHeight = Math.min(16, frameBottom - y);
            for (int x = panelLeft; x < frameRight; x += 16) {
                int tileWidth = Math.min(16, frameRight - x);
                graphics.blit(STONE_TEXTURE, x, y, tileWidth, tileHeight, 0, 0, 16, 16, 16, 16);
            }
        }
    }

    private void renderRuneCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredNode = null;
        drawCanvasBackground(graphics);

        graphics.enableScissor(canvasLeft, canvasTop, canvasLeft + canvasWidth, canvasTop + canvasHeight);

        String assignedRune = slotRune(selectedSlot);
        for (RuneNode node : runeNodes) {
            int centerX = graphToScreenX(node.graphX);
            int centerY = graphToScreenY(node.graphY);
            int squareSize = NODE_VISUAL_SIZE;
            int squareLeft = centerX - squareSize / 2;
            int squareTop = centerY - squareSize / 2;
            if (!intersects(squareLeft, squareTop, squareSize, squareSize, canvasLeft, canvasTop, canvasWidth, canvasHeight)) {
                continue;
            }
            int squareRight = squareLeft + squareSize;
            int squareBottom = squareTop + squareSize;
            boolean hovered = mouseX >= squareLeft && mouseX <= squareRight && mouseY >= squareTop && mouseY <= squareBottom;
            if (hovered) {
                hoveredNode = node;
            }
            boolean selected = assignedRune != null && assignedRune.equalsIgnoreCase(node.definition.id());
            graphics.blit(RUNE_SLOT_TEXTURE, squareLeft, squareTop, squareSize, squareSize, 0, 0, RUNE_TEXTURE_WIDTH, RUNE_TEXTURE_HEIGHT, RUNE_TEXTURE_WIDTH, RUNE_TEXTURE_HEIGHT);
            if (hovered) {
                graphics.fill(squareLeft, squareTop, squareRight, squareBottom, 0x25263A5E);
            }
            if (selected) {
                graphics.renderOutline(squareLeft, squareTop, squareSize, squareSize, 0xFF8CC8FF);
            } else if (hovered) {
                graphics.renderOutline(squareLeft, squareTop, squareSize, squareSize, 0xFF4C658B);
            }
            Component glyph = RuneSgaMapper.runeComponent(node.definition.id());
            renderScaledGlyph(graphics, centerX, centerY - 3, 1.0F, glyph, 0xFFF2F7FF);
            Component label = Component.literal(node.definition.id());
            graphics.drawCenteredString(font, label, centerX, squareBottom + 4, 0xFFE1E5F2);
        }

        graphics.disableScissor();
    }

    private void drawCanvasBackground(GuiGraphics graphics) {
        int canvasRight = canvasLeft + canvasWidth;
        int canvasBottom = canvasTop + canvasHeight;
        for (int y = canvasTop; y < canvasBottom; y += 16) {
            int tileHeight = Math.min(16, canvasBottom - y);
            for (int x = canvasLeft; x < canvasRight; x += 16) {
                int tileWidth = Math.min(16, canvasRight - x);
                graphics.blit(STONE_TEXTURE, x, y, tileWidth, tileHeight, 0, 0, tileWidth, tileHeight, 16, 16);
            }
        }
        graphics.fill(canvasLeft, canvasTop, canvasRight, canvasBottom, 0x7703070B);
    }

    private void renderHotbar(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredSlot = -1;
        int totalWidth = hotbarWidth();
        int frameLeft = Math.max(panelLeft + 3, hotbarLeft - 4);
        int frameRight = Math.min(panelLeft + PANEL_WIDTH - 3, hotbarLeft + totalWidth + 4);
        graphics.fill(frameLeft, hotbarTop - 4, frameRight, hotbarTop + HOTBAR_SLOT_SIZE + 4, 0x6606070C);
        graphics.renderOutline(frameLeft, hotbarTop - 4, frameRight - frameLeft, HOTBAR_SLOT_SIZE + 8, 0x662D3F5D);

        for (int slot = 0; slot < SpellingRepertoire.SLOT_COUNT; slot++) {
            int slotX = hotbarLeft + slot * (HOTBAR_SLOT_SIZE + HOTBAR_GAP);
            int slotY = hotbarTop;
            int slotRight = slotX + HOTBAR_SLOT_SIZE;
            boolean hovered = mouseX >= slotX && mouseX <= slotRight && mouseY >= slotY && mouseY <= slotY + HOTBAR_SLOT_SIZE;
            if (hovered) {
                hoveredSlot = slot;
            }
            boolean selected = slot == selectedSlot;
            graphics.fill(slotX, slotY, slotRight, slotY + HOTBAR_SLOT_SIZE, 0xFF06080B);
            int slotColor = selected ? 0xFF263852 : 0xFF151B25;
            if (hovered) {
                slotColor = 0xFF334967;
            }
            graphics.fill(slotX + 1, slotY + 1, slotRight - 1, slotY + HOTBAR_SLOT_SIZE - 1, slotColor);
            int borderColor = selected ? 0xFF8EC0FF : 0xFF4B5C75;
            if (hovered) {
                borderColor = 0xFFB7DBFF;
            }
            graphics.renderOutline(slotX, slotY, HOTBAR_SLOT_SIZE, HOTBAR_SLOT_SIZE, borderColor);

            Component glyph = RuneSgaMapper.runeComponent(slotRune(slot));
            renderScaledGlyph(graphics, slotX + HOTBAR_SLOT_SIZE / 2, slotY + 8, 1.2F, glyph, 0xFFFDFDFD);
        }
    }

    private void renderScaledGlyph(GuiGraphics graphics, float centerX, float centerY, float scale, Component glyph, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-centerX, -centerY, 0.0F);
        graphics.drawCenteredString(font, glyph, (int) centerX, (int) centerY, color);
        pose.popPose();
    }

    private void renderRuneTooltip(GuiGraphics graphics, String runeId, int mouseX, int mouseY) {
        if (runeId == null || runeId.isBlank()) {
            return;
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(runeId).withStyle(ChatFormatting.AQUA));
        Optional<ParserDictionary.RuneDefinition> definition = DICTIONARY.lookup(runeId);
        definition.ifPresent(def -> {
            tooltip.add(Component.literal(def.translation()).withStyle(ChatFormatting.GRAY));
            if (def.requiresTarget()) {
                tooltip.add(Component.translatable("screen.elderlexicon.spelling_repertoire.requires_target").withStyle(ChatFormatting.DARK_RED));
            }
        });
        graphics.renderTooltip(font, tooltip, Optional.empty(), mouseX, mouseY);
    }

    private void renderDragGhost(GuiGraphics graphics) {
        Component glyph = RuneSgaMapper.runeComponent(draggingRuneId);
        Component label = Component.literal(draggingRuneId);
        int glyphWidth = font.width(glyph);
        int labelWidth = font.width(label);
        int cardWidth = Math.max(glyphWidth, labelWidth) + 16;
        int cardHeight = font.lineHeight * 2 + 10;
        int left = (int) draggingMouseX - cardWidth / 2;
        int top = (int) draggingMouseY - cardHeight - 6;
        graphics.fill(left, top, left + cardWidth, top + cardHeight, 0xDD111724);
        graphics.renderOutline(left, top, cardWidth, cardHeight, 0xFF5F82BE);
        graphics.drawCenteredString(font, glyph, left + cardWidth / 2, top + 4, 0xFFFFFFFF);
        graphics.drawCenteredString(font, label, left + cardWidth / 2, top + 4 + font.lineHeight + 2, 0xFFBFDAFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsideCanvas(mouseX, mouseY)) {
            RuneNode node = findNodeAt(mouseX, mouseY);
            if (button == 0 && node != null) {
                beginDrag(node.definition.id(), mouseX, mouseY);
                return true;
            }
            if (button == 0 || button == 1) {
                panningCanvas = true;
                lastPanMouseX = mouseX;
                lastPanMouseY = mouseY;
                return true;
            }
        }

        int slotIndex = slotIndexAt(mouseX, mouseY);
        if (slotIndex >= 0) {
            if (button == 0) {
                selectedSlot = slotIndex;
                return true;
            }
            if (button == 1) {
                clearSlot(slotIndex);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void beginDrag(String runeId, double mouseX, double mouseY) {
        if (runeId == null || runeId.isBlank()) {
            return;
        }
        draggingRuneId = runeId;
        draggingMouseX = mouseX;
        draggingMouseY = mouseY;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingRuneId != null && button == 0) {
            int slotIndex = slotIndexAt(mouseX, mouseY);
            if (slotIndex >= 0) {
                assignSlot(slotIndex, draggingRuneId);
                selectedSlot = slotIndex;
            }
            draggingRuneId = null;
            return true;
        }
        if (panningCanvas && (button == 0 || button == 1)) {
            panningCanvas = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingRuneId != null && button == 0) {
            draggingMouseX = mouseX;
            draggingMouseY = mouseY;
            return true;
        }
        if (panningCanvas && (button == 0 || button == 1)) {
            scrollX += mouseX - lastPanMouseX;
            scrollY += mouseY - lastPanMouseY;
            lastPanMouseX = mouseX;
            lastPanMouseY = mouseY;
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInsideCanvas(mouseX, mouseY)) {
            scrollY -= delta * 12.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        controller.requestRepertoireUpdate(List.copyOf(workingSlots));
        super.onClose();
        controller.notifyEditorClosed();
    }

    private void assignSlot(int slotIndex, String runeId) {
        if (slotIndex < 0 || slotIndex >= workingSlots.size()) {
            return;
        }
        workingSlots.set(slotIndex, runeId == null ? "" : runeId);
    }

    private void clearSlot(int slotIndex) {
        assignSlot(slotIndex, "");
    }

    private RuneNode findNodeAt(double mouseX, double mouseY) {
        for (RuneNode node : runeNodes) {
            int centerX = graphToScreenX(node.graphX);
            int centerY = graphToScreenY(node.graphY);
            int squareSize = NODE_VISUAL_SIZE;
            int left = centerX - squareSize / 2;
            int top = centerY - squareSize / 2;
            if (mouseX >= left && mouseX <= left + squareSize && mouseY >= top && mouseY <= top + squareSize) {
                return node;
            }
        }
        return null;
    }

    private boolean isInsideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasLeft && mouseX <= canvasLeft + canvasWidth
                && mouseY >= canvasTop && mouseY <= canvasTop + canvasHeight;
    }

    private boolean intersects(int x, int y, int width, int height, int otherX, int otherY, int otherWidth, int otherHeight) {
        return x + width > otherX && x < otherX + otherWidth && y + height > otherY && y < otherY + otherHeight;
    }

    private int slotIndexAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < SpellingRepertoire.SLOT_COUNT; slot++) {
            int slotX = hotbarLeft + slot * (HOTBAR_SLOT_SIZE + HOTBAR_GAP);
            int slotY = hotbarTop;
            if (mouseX >= slotX && mouseX <= slotX + HOTBAR_SLOT_SIZE
                    && mouseY >= slotY && mouseY <= slotY + HOTBAR_SLOT_SIZE) {
                return slot;
            }
        }
        return -1;
    }

    private String slotRune(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= workingSlots.size()) {
            return "";
        }
        String value = workingSlots.get(slotIndex);
        return value == null ? "" : value;
    }

    private int hotbarWidth() {
        return SpellingRepertoire.SLOT_COUNT * HOTBAR_SLOT_SIZE + (SpellingRepertoire.SLOT_COUNT - 1) * HOTBAR_GAP;
    }

    private int graphToScreenX(int graphX) {
        return canvasLeft + canvasWidth / 2 + (int) Math.round(graphX + scrollX);
    }

    private int graphToScreenY(int graphY) {
        return canvasTop + canvasHeight / 2 + (int) Math.round(graphY + scrollY);
    }

    private record RuneNode(ParserDictionary.RuneDefinition definition, int graphX, int graphY) {
    }
}
