package com.lczme.glazuli.mixin;

import com.lczme.glazuli.title.WorldDataLoad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** "Create New World" loads the default datapacks off the render thread; see {@link WorldDataLoad}. */
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {
    @WrapOperation(method = "openFresh", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/WorldLoader;load("
            + "Lnet/minecraft/server/WorldLoader$InitConfig;Lnet/minecraft/server/WorldLoader$WorldDataSupplier;"
            + "Lnet/minecraft/server/WorldLoader$ResultFactory;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;)"
            + "Ljava/util/concurrent/CompletableFuture;"))
    private static <D, R> CompletableFuture<R> glazuli$loadOffThread(WorldLoader.InitConfig config, WorldLoader.WorldDataSupplier<D> data,
            WorldLoader.ResultFactory<D, R> result, Executor background, Executor sync, Operation<CompletableFuture<R>> original) {
        return WorldDataLoad.load(config, data, result);
    }

    @WrapOperation(method = "openFresh", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;managedBlock(Ljava/util/function/BooleanSupplier;)V"))
    private static void glazuli$awaitDrawing(Minecraft minecraft, BooleanSupplier done, Operation<Void> original) {
        WorldDataLoad.await(minecraft, done);
    }
}
