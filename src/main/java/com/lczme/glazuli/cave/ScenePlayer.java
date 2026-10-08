package com.lczme.glazuli.cave;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.joml.Vector3f;

/**
 * A person in a menu scene: a player model in the scene level, which nothing ticks, so {@link #pose} sets it up every
 * frame. Working figures swing their tool and turn round to face the camera when it comes up to them.
 */
final class ScenePlayer extends RemotePlayer {
    private static final PlayerTeam NO_NAME_TAG = noNameTag();
    private static final float NOTICE_DISTANCE = 4.5F;
    private static final float TURN_SECONDS = 0.55F;
    @Nullable
    private static CompletableFuture<PlayerSkin> ownSkin;

    private final CaveWorld.Figure figure;
    private final PlayerSkin fallback;
    private float facing;
    private double lastPose = Double.NaN;
    ScenePlayer(ClientLevel level, CaveWorld.Figure figure) {
        super(level, profile(figure));
        this.figure = figure;
        this.fallback = DefaultPlayerSkin.get(getUUID());
        entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        setItemSlot(EquipmentSlot.MAINHAND, figure.mainHand().copy());
        setItemSlot(EquipmentSlot.OFFHAND, figure.offHand().copy());
        moveTo(figure.x(), figure.y(), figure.z(), figure.yaw(), 0.0F);
        if (figure.self()) {
            ownSkin();
        }
    }

    private static GameProfile profile(CaveWorld.Figure figure) {
        if (figure.self()) {
            User user = Minecraft.getInstance().getUser();
            return new GameProfile(user.getProfileId(), user.getName());
        }
        return new GameProfile(new UUID(0L, figure.skin()), "miner" + figure.skin());
    }

    private static PlayerTeam noNameTag() {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), "glazuli_scene");
        team.setNameTagVisibility(Team.Visibility.NEVER);
        return team;
    }

    /** The player's own skin, fetched once off the render thread (the profile lookup can wait on the network). */
    private static CompletableFuture<PlayerSkin> ownSkin() {
        if (ownSkin == null) {
            Minecraft minecraft = Minecraft.getInstance();
            ownSkin = CompletableFuture.supplyAsync(minecraft::getGameProfile, Util.backgroundExecutor())
                    .thenComposeAsync(profile -> minecraft.getSkinManager().getOrLoad(profile), minecraft)
                    .exceptionally(e -> null);
        }
        return ownSkin;
    }

    @Override
    public PlayerSkin getSkin() {
        if (figure.self()) {
            PlayerSkin skin = ownSkin().getNow(null);
            if (skin != null) {
                return skin;
            }
        }
        return fallback;
    }

    @Override
    public PlayerTeam getTeam() {
        return NO_NAME_TAG;
    }

    /** Sets this frame's pose for a camera at {@code eye}. */
    void pose(Vector3f eye, double now) {
        float dt = Double.isNaN(lastPose) ? 0.0F : (float) Math.min(0.1, now - lastPose);
        lastPose = now;
        float dx = eye.x - (float) getX();
        float dz = eye.z - (float) getZ();
        float distance = Mth.sqrt(dx * dx + dz * dz);
        boolean noticed = figure.swings() && distance < NOTICE_DISTANCE;
        facing = Mth.clamp(facing + (noticed ? dt : -dt) / TURN_SECONDS, 0.0F, 1.0F);
        float turn = facing * facing * (3.0F - 2.0F * facing);
        float toCamera = (float) Math.toDegrees(Mth.atan2(-dx, dz));
        float yaw = figure.yaw() + Mth.wrapDegrees(toCamera - figure.yaw()) * turn;
        float lookUp = (float) -Math.toDegrees(Mth.atan2(eye.y - (float) getEyeY(), Math.max(distance, 0.5F)));
        float pitch = Mth.lerp(turn, figure.swings() ? 25.0F : 18.0F, lookUp);
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
        setXRot(pitch);
        xRotO = pitch;
        tickCount = (int) (now * 20.0) + figure.skin() * 7;        float swing = 0.0F;
        if (figure.swings() && turn < 0.02F) {
            float cycle = (float) ((now * 1.1 + figure.skin() * 0.37) % 1.0);
            swing = cycle < 0.5F ? cycle * 2.0F : 0.0F;
        }
        swinging = swing > 0.0F;
        swingingArm = InteractionHand.MAIN_HAND;
        attackAnim = swing;
        oAttackAnim = swing;
    }
}
