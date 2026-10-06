package com.lczme.liquidglass.cave;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Picks the menu scene; the cave behind it switches live, so the screen keeps to a strip along the bottom. */
public final class ScenePickerScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final int BUTTON_WIDTH = 116;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int FOOTER_WIDTH = 150;

    private final Screen parent;
    private int page;
    private int gridTop;
    private String marked = "";

    public ScenePickerScreen(Screen parent) {
        super(Component.translatable("liquidglass.scenes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<MenuScenes.Entry> entries = MenuScenes.list();
        int maxRows = Mth.clamp((this.height / 2 - 40) / (BUTTON_HEIGHT + GAP), 1, 4);
        int perPage = COLUMNS * maxRows;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Mth.clamp(page, 0, pages - 1);
        List<MenuScenes.Entry> shown = entries.subList(page * perPage, Math.min(entries.size(), (page + 1) * perPage));

        int rows = Math.max(1, (shown.size() + COLUMNS - 1) / COLUMNS);
        int gridWidth = COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
        int left = (this.width - gridWidth) / 2;
        int footerY = this.height - BUTTON_HEIGHT - 8;
        gridTop = footerY - 10 - rows * (BUTTON_HEIGHT + GAP) + GAP;
        String current = CaveBackground.scene();
        marked = current;
        for (int i = 0; i < shown.size(); i++) {
            MenuScenes.Entry entry = shown.get(i);
            Component label = entry.id().equals(current) ? Component.literal("● ").append(entry.name()) : entry.name();
            int x = left + (i % COLUMNS) * (BUTTON_WIDTH + GAP);
            int y = gridTop + (i / COLUMNS) * (BUTTON_HEIGHT + GAP);
            this.addRenderableWidget(Button.builder(label, button -> {
                CaveBackground.select(entry.id(), false);
                this.minecraft.tell(this::rebuildWidgets);
            }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        }
        if (pages > 1) {
            int y = gridTop + (rows - 1) * (BUTTON_HEIGHT + GAP) / 2;
            this.addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                    .bounds(left - GAP - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build()).active = page > 0;
            this.addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                    .bounds(left + gridWidth + GAP, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build()).active = page < pages - 1;
        }

        int footerLeft = this.width / 2 - FOOTER_WIDTH - GAP / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("liquidglass.scenes.folder"), button -> openFolder())
                .bounds(footerLeft, footerY, FOOTER_WIDTH, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(footerLeft + FOOTER_WIDTH + GAP, footerY, FOOTER_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void tick() {
        if (!marked.equals(CaveBackground.scene())) {
            rebuildWidgets();
        }
    }

    private void turn(int delta) {
        page += delta;
        this.minecraft.tell(this::rebuildWidgets);
    }

    private static void openFolder() {
        Path dir = MenuScenes.directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        Util.getPlatform().openFile(dir.toFile());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, gridTop - 30, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("liquidglass.scenes.hint"), this.width / 2, gridTop - 16, 0xB8C0CC);
    }

    /** Only the scene: no blur or dimming, it's what is being chosen. A soft shade keeps the text readable. */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderPanorama(guiGraphics, partialTick);
        guiGraphics.fillGradient(0, gridTop - 60, this.width, this.height, 0x00000000, 0x99000000);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
