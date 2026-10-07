package com.lczme.glazuli.render;

import com.lczme.glazuli.config.GlazuliConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;

/**
 * Glass inventories: a glass panel behind every container screen, its background texture redrawn with the vanilla
 * panel grey made transparent (see {@code container_glass.fsh}), and the hotbar and creative tabs drawn as glass.
 */
public final class ContainerGlass {
    /**
     * Light frosted body shared by inventories, JEI and the hotbar, so they all have one brightness. Vanilla's container
     * art and dark labels are made for a light grey panel.
     */
    private static final int LIGHT_FILM = 0xF4F6FA;
    private static final float SURFACE_BODY = 0.34F;
    public static final GlassStyle WINDOW = frosted(SURFACE_BODY, GlassUi.CARD_RADIUS);
    public static final GlassStyle SURFACE = frosted(SURFACE_BODY, 6.0F);
    public static final GlassStyle TAB_SELECTED = SURFACE;
    public static final GlassStyle TAB_UNSELECTED = frosted(0.18F, 6.0F);
    private static final GlassStyle HOTBAR = SURFACE;
    private static final int HOTBAR_SELECTION = 0xB3FFFFFF;
    /** Background-sized blits lose their 3 px bevelled frame; the glass panel has its own edge. */
    private static final float FRAME = 4.0F;
    private static final int MIN_FRAMED_SIZE = 64;

    /**
     * Mod textures in the vanilla palette that screens assemble from several blits (a side panel from top, middle and
     * bottom strips, slot wells on top). During the background pass these are collected, joined into panels by
     * adjacency and drawn at its end: glass behind each panel outside the window, then the art cut along the joined edge.
     */
    private static final Set<ResourceLocation> PIECED = Set.of(
            ResourceLocation.fromNamespaceAndPath("curios", "textures/gui/curios/inventory.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/storage_background_9.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/storage_background_9_wider.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/storage_background_12.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/storage_background_12_wider.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/slots_background.png"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/gui_controls.png"));
    /** Pieces of a side column that overlaps the window: joined only with each other, not with the window's art. */
    private static final Set<ResourceLocation> SIDE_COLUMN = Set.of(
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/gui_controls.png"));

    private static int[] pendingPanel;
    private static int[] window;
    private static final List<Piece> pieces = new ArrayList<>();

    private ContainerGlass() {
    }

    /** Light film with an explicit body opacity, below the spec's 0.42 floor so the frosted world shows through. */
    private static GlassStyle frosted(float body, float radius) {
        return GlassStyle.film(LIGHT_FILM, (body - 0.42F) / 0.7F).withCornerRadius(radius);
    }

    public static boolean isEnabled() {
        return GlazuliConfig.SPEC.isLoaded() && GlazuliConfig.GLASS_INVENTORY.get() && !GlazuliConfig.isClassic();
    }

    /**
     * Called before a container screen draws its background: the window panel is drawn under the first background
     * texture that is actually turned into glass, so a screen retextured by a resource pack keeps the pack's art alone.
     */
    public static void beginBackground(int x, int y, int width, int height) {
        pendingPanel = isEnabled() ? new int[]{x, y, width, height} : null;
        window = pendingPanel;
        pieces.clear();
    }

    public static void endBackground(GuiGraphics guiGraphics) {
        pendingPanel = null;
        if (!pieces.isEmpty()) {
            drawPieces(guiGraphics);
            pieces.clear();
        }
        window = null;
    }

    private static void drawPieces(GuiGraphics guiGraphics) {
        ShaderInstance shader = GlazuliShaders.containerGlass();
        if (shader == null) {
            return;
        }
        int[] group = new int[pieces.size()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }
        for (int i = 0; i < group.length; i++) {
            for (int j = i + 1; j < group.length; j++) {
                if (pieces.get(i).touches(pieces.get(j))) {
                    int a = root(group, i);
                    int b = root(group, j);
                    group[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        Map<Integer, int[]> bounds = new HashMap<>();
        for (int i = 0; i < group.length; i++) {
            Piece piece = pieces.get(i);
            bounds.merge(root(group, i), new int[]{piece.x1, piece.y1, piece.x2, piece.y2}, (a, b) -> new int[]{
                    Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])});
        }
        for (int[] b : bounds.values()) {
            boolean inWindow = window != null && b[0] >= window[0] && b[1] >= window[1]
                    && b[2] <= window[0] + window[2] && b[3] <= window[1] + window[3];
            if (!inWindow) {
                GlassRenderer.drawPanel(guiGraphics, b[0], b[1], b[2] - b[0], b[3] - b[1], SURFACE);
            }
        }
        guiGraphics.flush();
        shader.safeGetUniform("ArtAlpha").set(1.0F);
        for (int i = 0; i < group.length; i++) {
            Piece piece = pieces.get(i);
            int[] b = bounds.get(root(group, i));
            boolean framed = b[2] - b[0] >= 16 && b[3] - b[1] >= 16;
            shader.safeGetUniform("BodyRect").set((float) (b[0] - piece.x1), (float) (b[1] - piece.y1),
                    (float) (b[2] - piece.x1), (float) (b[3] - piece.y1));
            shader.safeGetUniform("EdgeCut").set(framed ? FRAME : 0.0F);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().last().pose().set(piece.pose);
            drawTexture(guiGraphics, shader, piece.texture, piece.x1, piece.x2, piece.y1, piece.y2, piece.z,
                    piece.u0, piece.u1, piece.v0, piece.v1);
            guiGraphics.pose().popPose();
        }
    }

    private static int root(int[] group, int i) {
        while (group[i] != i) {
            i = group[i];
        }
        return i;
    }

    private static boolean isGlassTexture(ResourceLocation texture) {
        String path = texture.getPath();
        return path.startsWith("textures/gui/container/") || path.equals("textures/gui/recipe_book.png");
    }

    /** Replaces a blit of a container background texture; returns false to let vanilla draw it. */
    public static boolean blit(GuiGraphics guiGraphics, ResourceLocation texture, int x1, int x2, int y1, int y2, int z,
                               float u0, float u1, float v0, float v1) {
        ShaderInstance shader = GlazuliShaders.containerGlass();
        if (shader == null || !isEnabled()) {
            return false;
        }
        int width = x2 - x1;
        int height = y2 - y1;
        if (window != null && PIECED.contains(texture) && !PackOverrides.texture(texture)) {
            int[] panel = pendingPanel;
            if (panel != null && x1 < panel[0] + panel[2] && x2 > panel[0] && y1 < panel[1] + panel[3] && y2 > panel[1]) {
                pendingPanel = null;
                GlassRenderer.drawPanel(guiGraphics, panel[0], panel[1], panel[2], panel[3], WINDOW);
            }
            pieces.add(new Piece(texture, x1, x2, y1, y2, z, u0, u1, v0, v1, new Matrix4f(guiGraphics.pose().last().pose())));
            return true;
        }
        if (CreatePanels.isPanelTexture(texture)) {
            // Create's screens are separate panels, not one window.
            pendingPanel = null;
            float[] body = width >= MIN_FRAMED_SIZE && height >= CreatePanels.MIN_HEIGHT && !PackOverrides.texture(texture)
                    ? CreatePanels.body(texture, u0, u1, v0, v1) : null;
            if (body == null) {
                return false;
            }
            float bx = body[0] * width;
            float by = body[1] * height;
            GlassRenderer.drawPanel(guiGraphics, x1 + bx, y1 + by, (body[2] - body[0]) * width, (body[3] - body[1]) * height,
                    CreatePanels.PANEL);
            guiGraphics.flush();
            shader.safeGetUniform("BodyRect").set(bx, by, body[2] * width, body[3] * height);
            shader.safeGetUniform("EdgeCut").set(CreatePanels.FRAME);
            shader.safeGetUniform("ArtAlpha").set(CreatePanels.ART_ALPHA);
            drawTexture(guiGraphics, shader, texture, x1, x2, y1, y2, z, u0, u1, v0, v1);
            return true;
        }
        if (!isGlassTexture(texture)) {
            return false;
        }
        int[] panel = pendingPanel;
        pendingPanel = null;
        if (PackOverrides.texture(texture)) {
            return false;
        }
        if (panel != null) {
            GlassRenderer.drawPanel(guiGraphics, panel[0], panel[1], panel[2], panel[3], WINDOW);
        }
        boolean framed = width >= MIN_FRAMED_SIZE && height >= MIN_FRAMED_SIZE;
        if (framed && texture.getPath().equals("textures/gui/recipe_book.png")) {
            GlassRenderer.drawPanel(guiGraphics, x1, y1, width, height, WINDOW);
        }
        guiGraphics.flush();

        shader.safeGetUniform("BodyRect").set(0.0F, 0.0F, (float) width, (float) height);
        shader.safeGetUniform("EdgeCut").set(framed ? FRAME : 0.0F);
        shader.safeGetUniform("ArtAlpha").set(1.0F);
        drawTexture(guiGraphics, shader, texture, x1, x2, y1, y2, z, u0, u1, v0, v1);
        return true;
    }

    private static void drawTexture(GuiGraphics guiGraphics, ShaderInstance shader, ResourceLocation texture, int x1, int x2,
                                    int y1, int y2, int z, float u0, float u1, float v0, float v1) {
        shader.safeGetUniform("RectSize").set((float) (x2 - x1), (float) (y2 - y1));
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = guiGraphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.addVertex(pose, x1, y1, z).setUv(u0, v0).setColor(0.0F, 0.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x1, y2, z).setUv(u0, v1).setColor(0.0F, 1.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x2, y2, z).setUv(u1, v1).setColor(1.0F, 1.0F, 0.0F, 1.0F);
        builder.addVertex(pose, x2, y1, z).setUv(u1, v0).setColor(1.0F, 0.0F, 0.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** Glass replacements for HUD and creative-tab sprites; returns false to let vanilla draw the sprite. */
    public static boolean sprite(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width, int height) {
        if (!isEnabled() || !sprite.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) || !isGlassSprite(sprite.getPath())
                || PackOverrides.sprite(sprite)) {
            return false;
        }
        String path = sprite.getPath();
        switch (path) {
            case "hud/hotbar" -> GlassRenderer.drawPanel(guiGraphics, x, y, width, height, HOTBAR);
            // The selection frame overhangs the 20 px slot by 2 px on each side (1 px above the bar).
            case "hud/hotbar_selection" -> GlassUi.roundedFill(guiGraphics, x + 1, y + 1, x + width - 1, y + height - 1, 3, HOTBAR_SELECTION);
            // Offhand sprites are 29 px wide: a 22 px slot plus a gap towards the hotbar.
            case "hud/hotbar_offhand_left" -> GlassRenderer.drawPanel(guiGraphics, x, y + 1, 22, 22, HOTBAR);
            case "hud/hotbar_offhand_right" -> GlassRenderer.drawPanel(guiGraphics, x + width - 22, y + 1, 22, 22, HOTBAR);
            default -> {
                if (!path.startsWith("container/creative_inventory/tab_")) {
                    return false;
                }
                GlassRenderer.drawPanel(guiGraphics, x, y, width, height, path.contains("unselected") ? TAB_UNSELECTED : TAB_SELECTED);
            }
        }
        return true;
    }

    private static boolean isGlassSprite(String path) {
        return path.startsWith("hud/hotbar") || path.startsWith("container/creative_inventory/tab_");
    }

    private record Piece(ResourceLocation texture, int x1, int x2, int y1, int y2, int z, float u0, float u1, float v0,
                         float v1, Matrix4f pose) {
        boolean touches(Piece other) {
            return SIDE_COLUMN.contains(texture) == SIDE_COLUMN.contains(other.texture)
                    && x1 <= other.x2 && other.x1 <= x2 && y1 <= other.y2 && other.y1 <= y2;
        }
    }
}
