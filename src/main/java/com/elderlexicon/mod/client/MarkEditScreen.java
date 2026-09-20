package com.elderlexicon.mod.client;

import com.elderlexicon.mod.mark.MarkTarget;
import com.elderlexicon.mod.mark.network.MarkNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Simple text prompt for applying Marks via the Elder Brush.
 */
@OnlyIn(Dist.CLIENT)
public final class MarkEditScreen extends Screen {

    private static final int MAX_LENGTH = 32;

    private final MarkTarget target;
    private EditBox input;
    private boolean sent;

    private MarkEditScreen(MarkTarget target) {
        super(Component.translatable("screen.elderlexicon.mark.title"));
        this.target = target;
    }

    public static void open(MarkTarget target) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || target == null) {
            return;
        }
        minecraft.setScreen(new MarkEditScreen(target));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        input = new EditBox(this.font, centerX - 100, centerY - 20, 200, 20, Component.empty());
        input.setMaxLength(MAX_LENGTH);
        input.setResponder(value -> {
        });
        input.setValue("");
        input.setHint(Component.translatable("screen.elderlexicon.mark.placeholder"));
        addRenderableWidget(input);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> submit())
                .bounds(centerX - 100, centerY + 8, 95, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 5, centerY + 8, 95, 20)
                .build());

        setInitialFocus(input);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (input.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (input.canConsumeInput()) {
            return true;
        }
        if (keyCode == 257 || keyCode == 335) { // Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private void submit() {
        if (sent) {
            return;
        }
        String value = input.getValue();
        if (StringUtil.isNullOrEmpty(value)) {
            value = "";
        }
        MarkNetwork.sendSetMark(target, value);
        sent = true;
        onClose();
    }
}
