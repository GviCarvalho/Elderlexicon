package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.block.SpellReading;
import com.elderlexicon.mod.spelling.data.SpellingRepertoire;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The rune repertoire (docs/repertorio-design.md): every rune of the language on one page, the families gathered apart
 * like constellations (Fontes, Funções, Filtros, Fatos), with no hierarchy among them, and below, the nine slots the
 * mage recites in trance. A rune the mage has not unlocked is a dark block that shows neither its glyph, its name nor
 * what it does (once understood, its name shows in the secret letters, waiting for its dream); in creative, every rune
 * is unlocked. Drag a rune to a slot to recite it; right-click a slot to empty it.
 * The page is dragged to look around and the wheel zooms in and out; the button in the corner opens it to the whole
 * screen, with nothing around it.
 */
@SuppressWarnings("null")
public final class SpellingRepertoireScreen extends Screen {

    private static final ParserDictionary DICTIONARY = ParserDictionary.load();
    private static final Component TITLE = Component.translatable("screen.elderlexicon.spelling_repertoire.title");
    private static final ResourceLocation RUNE_SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("elderlexicon", "textures/gui/rune_square.png");
    private static final ResourceLocation STONE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png");
    private static final int RUNE_TEXTURE_WIDTH = 90;
    private static final int RUNE_TEXTURE_HEIGHT = 87;

    /** The families, in the order they are laid out, and the word classes each gathers. */
    private enum Family {
        FONTS("fonts", ParserDictionary.RuneType.SOURCE),
        FUNCTIONS("functions", ParserDictionary.RuneType.FUNCTION),
        FILTERS("filters", ParserDictionary.RuneType.FILTER),
        FACTS("facts", ParserDictionary.RuneType.CONDITION, ParserDictionary.RuneType.REFERENT);

        final String key;
        final List<ParserDictionary.RuneType> types;

        Family(String key, ParserDictionary.RuneType... types) {
            this.key = key;
            this.types = List.of(types);
        }

        Component label() {
            return Component.translatable("screen.elderlexicon.spelling_repertoire.family." + key);
        }
    }

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 220;
    private static final int FULL_MARGIN = 8;
    private static final int TITLE_BAR = 18;
    private static final int NODE_VISUAL_SIZE = 20;
    /** Room each rune takes along its family's ring. */
    private static final int RING_STEP = 38;
    private static final int MIN_RADIUS = 36;
    /** Empty sky between two families. */
    private static final int FAMILY_GAP = 90;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_GAP = 2;
    private static final int TOGGLE_SIZE = 11;
    private static final float LABEL_SCALE = 0.75F;
    private static final double MIN_ZOOM = 0.4D;
    private static final double MAX_ZOOM = 2.5D;
    private static final double ZOOM_STEP = 1.15D;

    private static final int FRAME_EDGE = 0xFF05070A;
    private static final int FRAME = 0xFF1A1F27;
    private static final int FRAME_LIGHT = 0xFF2D3644;
    private static final int LOCKED = 0xFF0B0D12;
    private static final int LOCKED_EDGE = 0xFF20252F;
    private static final int RING = 0x302D3F5D;
    private static final int FAMILY_TEXT = 0xFF8EA6C8;

    /** Whether the repertoire opens to the whole screen; kept while the game runs. */
    private static boolean fullScreen;

    private final ClientSpellingController controller;
    private final List<String> workingSlots = new ArrayList<>(SpellingRepertoire.SLOT_COUNT);

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int canvasLeft;
    private int canvasTop;
    private int canvasWidth;
    private int canvasHeight;
    private int hotbarLeft;
    private int hotbarTop;

    private final List<RuneNode> runeNodes = new ArrayList<>();
    private final List<Cluster> clusters = new ArrayList<>();
    private double scrollX;
    private double scrollY;
    private double minScrollX;
    private double maxScrollX;
    private double minScrollY;
    private double maxScrollY;
    private boolean scrollInitialized;
    /** How near the sky is: 1 as drawn, more to come closer. */
    private double zoom = 1.0D;
    /** The edges of the sky, in page units, with room for the runes' names. */
    private int contentMinX;
    private int contentMaxX;
    private int contentMinY;
    private int contentMaxY;

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
        if (fullScreen) {
            // The whole window is sky: the title, the button and the slots float over it.
            panelLeft = 0;
            panelTop = 0;
            panelWidth = width;
            panelHeight = height;
            canvasLeft = 0;
            canvasTop = 0;
            canvasWidth = width;
            canvasHeight = height;
        } else {
            panelWidth = Math.min(PANEL_WIDTH, width - FULL_MARGIN * 2);
            panelHeight = Math.min(PANEL_HEIGHT, height - FULL_MARGIN * 2);
            panelLeft = (width - panelWidth) / 2;
            panelTop = (height - panelHeight) / 2;
            canvasLeft = panelLeft + 6;
            canvasTop = panelTop + TITLE_BAR;
            canvasWidth = panelWidth - 12;
        }
        hotbarLeft = panelLeft + (panelWidth - hotbarWidth()) / 2;
        hotbarTop = panelTop + panelHeight - HOTBAR_SLOT_SIZE - 8;
        if (!fullScreen) {
            canvasHeight = Math.max(32, hotbarTop - canvasTop - 10);
        }
        selectedSlot = Mth.clamp(selectedSlot, 0, SpellingRepertoire.SLOT_COUNT - 1);
        rebuildRuneLayout(true);
    }

    /** Each family a ring of its runes, the rings side by side with empty sky between them. */
    private void rebuildRuneLayout(boolean resetScroll) {
        runeNodes.clear();
        clusters.clear();
        List<List<ParserDictionary.RuneDefinition>> members = new ArrayList<>();
        for (Family family : Family.values()) {
            List<ParserDictionary.RuneDefinition> runes = new ArrayList<>();
            for (ParserDictionary.RuneDefinition definition : DICTIONARY.entries().values()) {
                if (family.types.contains(definition.type()) && RuneSgaMapper.glyphForRune(definition.id()).isPresent()) {
                    runes.add(definition);
                }
            }
            members.add(runes);
        }
        int x = 0;
        int minX = 0;
        int maxX = 0;
        int maxRadius = MIN_RADIUS;
        boolean first = true;
        for (int f = 0; f < Family.values().length; f++) {
            List<ParserDictionary.RuneDefinition> runes = members.get(f);
            if (runes.isEmpty()) {
                continue;
            }
            int radius = Math.max(MIN_RADIUS, (int) Math.ceil(runes.size() * RING_STEP / (2.0D * Math.PI)));
            x += first ? 0 : radius;
            if (first) {
                minX = -radius;
            }
            clusters.add(new Cluster(Family.values()[f], x, 0, radius));
            for (int i = 0; i < runes.size(); i++) {
                double angle = -Math.PI / 2.0D + 2.0D * Math.PI * i / runes.size();
                runeNodes.add(new RuneNode(runes.get(i), x + (int) Math.round(Math.cos(angle) * radius),
                        (int) Math.round(Math.sin(angle) * radius)));
            }
            maxX = x + radius;
            maxRadius = Math.max(maxRadius, radius);
            x += radius + FAMILY_GAP;
            first = false;
        }
        // Centre the sky on its middle.
        int middle = (minX + maxX) / 2;
        runeNodes.replaceAll(node -> new RuneNode(node.definition(), node.graphX() - middle, node.graphY()));
        clusters.replaceAll(cluster -> new Cluster(cluster.family(), cluster.x() - middle, cluster.y(), cluster.radius()));
        int margin = NODE_VISUAL_SIZE + 6;
        contentMinX = minX - middle - margin;
        contentMaxX = maxX - middle + margin;
        contentMinY = -maxRadius - margin;
        contentMaxY = maxRadius + margin;
        updateScrollBounds(resetScroll);
    }

    /** How far the sky can be dragged at this zoom: its edges to the canvas's edges, or centred when it fits. */
    private void updateScrollBounds(boolean resetScroll) {
        double minX = contentMinX * zoom;
        double maxX = contentMaxX * zoom;
        double minY = contentMinY * zoom;
        double maxY = contentMaxY * zoom;
        double halfWidth = canvasWidth / 2.0D;
        double halfHeight = canvasHeight / 2.0D;
        minScrollX = Math.min(0.0D, halfWidth - maxX);
        maxScrollX = Math.max(0.0D, -halfWidth - minX);
        minScrollY = Math.min(0.0D, halfHeight - maxY);
        maxScrollY = Math.max(0.0D, -halfHeight - minY);
        if (maxX - minX <= canvasWidth) {
            minScrollX = maxScrollX = 0.0D;
        }
        if (maxY - minY <= canvasHeight) {
            minScrollY = maxScrollY = 0.0D;
        }
        if (resetScroll || !scrollInitialized) {
            scrollX = (minScrollX + maxScrollX) / 2.0D;
            scrollY = (minScrollY + maxScrollY) / 2.0D;
            scrollInitialized = true;
        } else {
            clampScroll();
        }
    }

    private void clampScroll() {
        scrollX = Mth.clamp(scrollX, minScrollX, maxScrollX);
        scrollY = Mth.clamp(scrollY, minScrollY, maxScrollY);
    }

    private boolean unlocked(String runeId) {
        return controller.isUnlocked(runeId);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && (minecraft.level == null || minecraft.player == null)) {
            onClose();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawFrame(graphics, mouseX, mouseY);
        renderRuneCanvas(graphics, mouseX, mouseY);
        renderHotbar(graphics, mouseX, mouseY);
        drawToggle(graphics, mouseX, mouseY);
        graphics.drawCenteredString(font, TITLE, panelLeft + panelWidth / 2, panelTop + 5, 0xFFFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredNode != null) {
            renderRuneTooltip(graphics, hoveredNode.definition().id(), mouseX, mouseY);
        } else if (hoveredSlot >= 0) {
            renderRuneTooltip(graphics, slotRune(hoveredSlot), mouseX, mouseY);
        } else if (overToggle(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable(fullScreen
                    ? "screen.elderlexicon.spelling_repertoire.windowed"
                    : "screen.elderlexicon.spelling_repertoire.full_screen"), mouseX, mouseY);
        }
        if (draggingRuneId != null) {
            renderDragGhost(graphics);
        }
    }

    /** The frame: dark, with a title bar, and the button that opens the page to the whole screen. */
    private void drawFrame(GuiGraphics graphics, int mouseX, int mouseY) {
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        if (!fullScreen) {
            graphics.fill(panelLeft - 1, panelTop - 1, right + 1, bottom + 1, FRAME_EDGE);
            graphics.fill(panelLeft, panelTop, right, bottom, FRAME);
            graphics.fill(panelLeft, panelTop, right, panelTop + 1, FRAME_LIGHT);
            graphics.fill(panelLeft, panelTop, panelLeft + 1, bottom, FRAME_LIGHT);
        }
    }

    /** The full-screen button, drawn over the sky. */
    private void drawToggle(GuiGraphics graphics, int mouseX, int mouseY) {
        // The full-screen button: four corners pointing out, or in when the page already fills the screen.
        int x = toggleLeft();
        int y = toggleTop();
        int color = overToggle(mouseX, mouseY) ? 0xFFB7DBFF : 0xFF6D84A6;
        int s = TOGGLE_SIZE - 1;
        if (!fullScreen) {
            corner(graphics, x, y, 1, 1, color);
            corner(graphics, x + s, y, -1, 1, color);
            corner(graphics, x, y + s, 1, -1, color);
            corner(graphics, x + s, y + s, -1, -1, color);
        } else {
            corner(graphics, x + 3, y + 3, -1, -1, color);
            corner(graphics, x + s - 3, y + 3, 1, -1, color);
            corner(graphics, x + 3, y + s - 3, -1, 1, color);
            corner(graphics, x + s - 3, y + s - 3, 1, 1, color);
        }
    }

    /** A corner of three pixels each way from {@code (x, y)}, along {@code dx} and {@code dy}. */
    private static void corner(GuiGraphics graphics, int x, int y, int dx, int dy, int color) {
        for (int i = 0; i < 3; i++) {
            graphics.fill(x + i * dx, y, x + i * dx + 1, y + 1, color);
            graphics.fill(x, y + i * dy, x + 1, y + i * dy + 1, color);
        }
    }

    private int toggleLeft() {
        return panelLeft + panelWidth - TOGGLE_SIZE - 4;
    }

    private int toggleTop() {
        return panelTop + 4;
    }

    private boolean overToggle(double mouseX, double mouseY) {
        return mouseX >= toggleLeft() && mouseX < toggleLeft() + TOGGLE_SIZE
                && mouseY >= toggleTop() && mouseY < toggleTop() + TOGGLE_SIZE;
    }

    private void renderRuneCanvas(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredNode = null;
        drawCanvasBackground(graphics);
        graphics.enableScissor(canvasLeft, canvasTop, canvasLeft + canvasWidth, canvasTop + canvasHeight);
        for (Cluster cluster : clusters) {
            drawCluster(graphics, cluster);
        }
        String assignedRune = slotRune(selectedSlot);
        boolean overCanvas = isInsideCanvas(mouseX, mouseY);
        int size = nodeSize();
        for (RuneNode node : runeNodes) {
            int centerX = graphToScreenX(node.graphX());
            int centerY = graphToScreenY(node.graphY());
            int left = centerX - size / 2;
            int top = centerY - size / 2;
            if (!intersects(left, top, size, size + (int) (12 * zoom), canvasLeft, canvasTop, canvasWidth,
                    canvasHeight)) {
                continue;
            }
            int right = left + size;
            int bottom = top + size;
            String id = node.definition().id();
            if (!unlocked(id)) {
                // A rune not yet learned: a dark block, and nothing of it to see. Once it is understood, and only
                // its dream keeps it, its name shows in the secret letters.
                graphics.fill(left, top, right, bottom, LOCKED_EDGE);
                graphics.fill(left + 1, top + 1, right - 1, bottom - 1, LOCKED);
                if (controller.isUnderstood(id)) {
                    renderScaledGlyph(graphics, centerX, bottom + 3 * (float) zoom, LABEL_SCALE * (float) zoom,
                            Component.literal(id).withStyle(ChatFormatting.OBFUSCATED), 0xFF9A7AC8);
                }
                continue;
            }
            boolean hovered = overCanvas && mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
            if (hovered) {
                hoveredNode = node;
            }
            boolean selected = assignedRune != null && assignedRune.equalsIgnoreCase(id);
            graphics.blit(RUNE_SLOT_TEXTURE, left, top, size, size, 0, 0, RUNE_TEXTURE_WIDTH,
                    RUNE_TEXTURE_HEIGHT, RUNE_TEXTURE_WIDTH, RUNE_TEXTURE_HEIGHT);
            if (hovered) {
                graphics.fill(left, top, right, bottom, 0x25263A5E);
            }
            if (selected) {
                graphics.renderOutline(left, top, size, size, 0xFF8CC8FF);
            } else if (hovered) {
                graphics.renderOutline(left, top, size, size, 0xFF4C658B);
            }
            float scale = (float) zoom;
            renderScaledGlyph(graphics, centerX, centerY - 3.5F * scale, scale, RuneSgaMapper.runeComponent(id),
                    0xFFF2F7FF);
            renderScaledGlyph(graphics, centerX, bottom + 3 * scale, LABEL_SCALE * scale, Component.literal(id),
                    0xFFE1E5F2);
        }
        graphics.disableScissor();
    }

    /** A family: a faint ring around its runes, and its name in the middle. */
    private void drawCluster(GuiGraphics graphics, Cluster cluster) {
        int cx = graphToScreenX(cluster.x());
        int cy = graphToScreenY(cluster.y());
        int r = (int) Math.round((cluster.radius() + NODE_VISUAL_SIZE / 2 + 8) * zoom);
        int steps = Math.max(48, r * 2);
        for (int i = 0; i < steps; i += 2) {
            double angle = 2.0D * Math.PI * i / steps;
            int px = cx + (int) Math.round(Math.cos(angle) * r);
            int py = cy + (int) Math.round(Math.sin(angle) * r);
            graphics.fill(px, py, px + 1, py + 1, RING);
        }
        float scale = (float) zoom;
        renderScaledGlyph(graphics, cx, cy - 4 * scale, scale, cluster.family().label(), FAMILY_TEXT);
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
        graphics.fill(canvasLeft, canvasTop, canvasRight, canvasBottom, 0xCC03070B);
        if (!fullScreen) {
            graphics.renderOutline(canvasLeft - 1, canvasTop - 1, canvasWidth + 2, canvasHeight + 2, FRAME_EDGE);
        }
    }

    private void renderHotbar(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredSlot = -1;
        int totalWidth = hotbarWidth();
        int frameLeft = hotbarLeft - 4;
        int frameRight = hotbarLeft + totalWidth + 4;
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
            String rune = slotRune(slot);
            if (!rune.isBlank()) {
                renderScaledGlyph(graphics, slotX + HOTBAR_SLOT_SIZE / 2.0F, slotY + 6, 1.2F,
                        RuneSgaMapper.runeComponent(rune), unlocked(rune) ? 0xFFFDFDFD : 0xFF5A5A5A);
            }
        }
    }

    private void renderScaledGlyph(GuiGraphics graphics, float centerX, float top, float scale, Component text, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, top, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        pose.popPose();
    }

    /** A rune's name, its glyph's meaning and what it does; nothing for a rune not yet learned. */
    private void renderRuneTooltip(GuiGraphics graphics, String runeId, int mouseX, int mouseY) {
        if (runeId == null || runeId.isBlank() || !unlocked(runeId)) {
            return;
        }
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(runeId).withStyle(ChatFormatting.AQUA).getVisualOrderText());
        DICTIONARY.lookup(runeId).ifPresent(definition -> {
            tooltip.add(Component.literal(definition.translation()).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            String lore = SpellReading.lore(runeId);
            if (lore != null && !lore.isBlank()) {
                tooltip.addAll(font.split(Component.literal(lore).withStyle(ChatFormatting.DARK_GRAY), 200));
            }
            if (definition.requiresTarget()) {
                tooltip.add(Component.translatable("screen.elderlexicon.spelling_repertoire.requires_target")
                        .withStyle(ChatFormatting.DARK_RED).getVisualOrderText());
            }
        });
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderDragGhost(GuiGraphics graphics) {
        Component glyph = RuneSgaMapper.runeComponent(draggingRuneId);
        Component label = Component.literal(draggingRuneId);
        int cardWidth = Math.max(font.width(glyph), font.width(label)) + 16;
        int cardHeight = font.lineHeight * 2 + 10;
        int left = (int) draggingMouseX - cardWidth / 2;
        int top = (int) draggingMouseY - cardHeight - 6;
        graphics.fill(left, top, left + cardWidth, top + cardHeight, 0xDD111724);
        graphics.renderOutline(left, top, cardWidth, cardHeight, 0xFF5F82BE);
        graphics.drawCenteredString(font, glyph, left + cardWidth / 2, top + 4, 0xFFFFFFFF);
        graphics.drawCenteredString(font, label, left + cardWidth / 2, top + 4 + font.lineHeight + 2, 0xFFBFDAFF);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overToggle(mouseX, mouseY)) {
            fullScreen = !fullScreen;
            init(minecraft, width, height);
            return true;
        }
        // The slots first: in full screen they float over the sky.
        int slotIndex = slotIndexAt(mouseX, mouseY);
        if (slotIndex >= 0) {
            if (button == 0) {
                selectedSlot = slotIndex;
                return true;
            }
            if (button == 1) {
                assignSlot(slotIndex, "");
                return true;
            }
        }
        if (isInsideCanvas(mouseX, mouseY)) {
            RuneNode node = findNodeAt(mouseX, mouseY);
            if (button == 0 && node != null && unlocked(node.definition().id())) {
                beginDrag(node.definition().id(), mouseX, mouseY);
                return true;
            }
            if (button == 0 || button == 1) {
                panningCanvas = true;
                lastPanMouseX = mouseX;
                lastPanMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void beginDrag(String runeId, double mouseX, double mouseY) {
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

    /** The wheel zooms in and out, keeping what is under the pointer where it is. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isInsideCanvas(mouseX, mouseY) && delta != 0.0D) {
            double next = Mth.clamp(delta > 0 ? zoom * ZOOM_STEP : zoom / ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
            double centerX = canvasLeft + canvasWidth / 2.0D;
            double centerY = canvasTop + canvasHeight / 2.0D;
            double pageX = (mouseX - centerX - scrollX) / zoom;
            double pageY = (mouseY - centerY - scrollY) / zoom;
            zoom = next;
            scrollX = mouseX - centerX - pageX * zoom;
            scrollY = mouseY - centerY - pageY * zoom;
            updateScrollBounds(false);
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

    private RuneNode findNodeAt(double mouseX, double mouseY) {
        int size = nodeSize();
        for (RuneNode node : runeNodes) {
            int left = graphToScreenX(node.graphX()) - size / 2;
            int top = graphToScreenY(node.graphY()) - size / 2;
            if (mouseX >= left && mouseX <= left + size && mouseY >= top && mouseY <= top + size) {
                return node;
            }
        }
        return null;
    }

    private boolean isInsideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasLeft && mouseX <= canvasLeft + canvasWidth
                && mouseY >= canvasTop && mouseY <= canvasTop + canvasHeight;
    }

    private static boolean intersects(int x, int y, int width, int height, int otherX, int otherY, int otherWidth,
                                      int otherHeight) {
        return x + width > otherX && x < otherX + otherWidth && y + height > otherY && y < otherY + otherHeight;
    }

    private int slotIndexAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < SpellingRepertoire.SLOT_COUNT; slot++) {
            int slotX = hotbarLeft + slot * (HOTBAR_SLOT_SIZE + HOTBAR_GAP);
            if (mouseX >= slotX && mouseX <= slotX + HOTBAR_SLOT_SIZE
                    && mouseY >= hotbarTop && mouseY <= hotbarTop + HOTBAR_SLOT_SIZE) {
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

    private int nodeSize() {
        return Math.max(6, (int) Math.round(NODE_VISUAL_SIZE * zoom));
    }

    private int graphToScreenX(int graphX) {
        return canvasLeft + canvasWidth / 2 + (int) Math.round(graphX * zoom + scrollX);
    }

    private int graphToScreenY(int graphY) {
        return canvasTop + canvasHeight / 2 + (int) Math.round(graphY * zoom + scrollY);
    }

    private record RuneNode(ParserDictionary.RuneDefinition definition, int graphX, int graphY) {
    }

    /** A family's ring: its centre on the page and its radius. */
    private record Cluster(Family family, int x, int y, int radius) {
    }
}
