package com.lczme.glazuli.compat.sodium.gui.frame.tab;

import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;

import java.util.List;

record TabGroup(String id, ModOptions modOptions, List<Tab<?>> tabs) {
}
