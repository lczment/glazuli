package com.lczme.glazuli.cave;

import com.lczme.glazuli.anim.AnimClock;
import com.lczme.glazuli.anim.Easing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private static final float TURN_SECONDS = 0.38F;
    /** Room around the grid for the buttons' hover growth, inside the clip the pages slide in. */
    private static final int CLIP_MARGIN = 4;

    private final Screen parent;
    private int page = -1;
    private int gridTop;
    private int gridLeft;
    private int gridBottom;
    private String marked = "";
    /** The grid's buttons are drawn here (sliding during a page turn), not with the other widgets. */
    private final List<Button> pageButtons = new ArrayList<>();
    private List<Button> outgoing = List.of();
    private int outgoingTop;
    private double turnStart = Double.NaN;
    private int turnDirection;

    public ScenePickerScreen(Screen parent) {
        super(Component.translatable("glazuli.scenes.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<MenuScenes.Entry> entries = MenuScenes.list();
        int maxRows = Mth.clamp((this.height / 2 - 40) / (BUTTON_HEIGHT + GAP), 1, 4);
        int perPage = COLUMNS * maxRows;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        String current = CaveBackground.scene();
        if (page < 0) {
            // Opens on the page with the scene that is showing.
            int index = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id().equals(current)) {
                    index = i;
                }
            }
            page = index / perPage;
        }
        page = Mth.clamp(page, 0, pages - 1);
        List<MenuScenes.Entry> shown = entries.subList(page * perPage, Math.min(entries.size(), (page + 1) * perPage));

        int rows = Math.max(1, (shown.size() + COLUMNS - 1) / COLUMNS);
        int gridWidth = COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
        gridLeft = (this.width - gridWidth) / 2;
        int footerY = this.height - BUTTON_HEIGHT - 8;
        gridTop = footerY - 10 - rows * (BUTTON_HEIGHT + GAP) + GAP;
        gridBottom = gridTop + rows * (BUTTON_HEIGHT + GAP) - GAP;
        marked = current;
        pageButtons.clear();
        for (int i = 0; i < shown.size(); i++) {
            MenuScenes.Entry entry = shown.get(i);
            Component label = entry.id().equals(current) ? Component.literal("● ").append(entry.name()) : entry.name();
            int x = gridLeft + (i % COLUMNS) * (BUTTON_WIDTH + GAP);
            int y = gridTop + (i / COLUMNS) * (BUTTON_HEIGHT + GAP);
            Button button = Button.builder(label, b -> {
                CaveBackground.select(entry.id(), false);
                this.minecraft.tell(this::rebuildWidgets);
            }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
            pageButtons.add(this.addWidget(button));
        }
        if (pages > 1) {
            int y = gridTop + (rows - 1) * (BUTTON_HEIGHT + GAP) / 2;
            this.addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                    .bounds(gridLeft - GAP - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build()).active = page > 0;
            this.addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                    .bounds(gridLeft + gridWidth + GAP, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build()).active = page < pages - 1;
        }

        int footerLeft = this.width / 2 - FOOTER_WIDTH - GAP / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("glazuli.scenes.folder"), button -> openFolder())
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
        this.minecraft.tell(() -> {
            outgoing = List.copyOf(pageButtons);
            outgoingTop = gridTop;
            turnDirection = delta;
            turnStart = AnimClock.now();
            page += delta;
            rebuildWidgets();
        });
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
        guiGraphics.drawCenteredString(this.font, Component.translatable("glazuli.scenes.hint"), this.width / 2, gridTop - 16, 0xB8C0CC);
        renderPages(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** The page turn: the old page slides out to one side and fades while the new one slides in from the other. */
    private void renderPages(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float t = Double.isNaN(turnStart) ? 1.0F : Mth.clamp((float) ((AnimClock.now() - turnStart) / TURN_SECONDS), 0.0F, 1.0F);
        if (t >= 1.0F) {
            turnStart = Double.NaN;
            outgoing = List.of();
            for (Button button : pageButtons) {
                button.setAlpha(1.0F);
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        float e = Easing.SPEC_EASE.apply(t);
        float distance = COLUMNS * (BUTTON_WIDTH + GAP) * 0.6F;
        int gridWidth = COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
        guiGraphics.enableScissor(gridLeft - CLIP_MARGIN, Math.min(gridTop, outgoingTop) - CLIP_MARGIN,
                gridLeft + gridWidth + CLIP_MARGIN, gridBottom + CLIP_MARGIN);
        drawSliding(guiGraphics, outgoing, -turnDirection * distance * e, 1.0F - Mth.clamp(e * 1.6F, 0.0F, 1.0F), partialTick);
        drawSliding(guiGraphics, pageButtons, turnDirection * distance * (1.0F - e), Mth.clamp(e * 1.4F, 0.0F, 1.0F), partialTick);
        guiGraphics.disableScissor();
    }

    private static void drawSliding(GuiGraphics guiGraphics, List<Button> buttons, float offset, float alpha, float partialTick) {
        if (alpha <= 0.02F) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(offset, 0.0F, 0.0F);
        for (Button button : buttons) {
            button.setAlpha(alpha);
            // No hover while it moves under the cursor.
            button.render(guiGraphics, -1, -1, partialTick);
        }
        guiGraphics.pose().popPose();
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
