package com.elderlexicon.mod.spelling.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Declares all key mappings used by the Spelling mechanic.
 */
@SuppressWarnings("null")
public final class SpellingKeyMappings {

	public static final String CATEGORY = "key.categories.elderlexicon";

	public static final KeyMapping SPELLING_KEY = new KeyMapping(
			"key.elderlexicon.spelling",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_R,
			CATEGORY
	);

	public static final KeyMapping REPERTOIRE_KEY = new KeyMapping(
			"key.elderlexicon.spelling_repertoire",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_Y,
			CATEGORY
	);

	private SpellingKeyMappings() {
	}

	public static void register(RegisterKeyMappingsEvent event) {
		event.register(SPELLING_KEY);
		event.register(REPERTOIRE_KEY);
	}
}
