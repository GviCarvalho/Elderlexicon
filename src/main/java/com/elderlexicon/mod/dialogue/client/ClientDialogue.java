package com.elderlexicon.mod.dialogue.client;

import com.elderlexicon.mod.dialogue.DialogueNetwork;
import net.minecraft.client.Minecraft;

/** The client's side of a talk: shows each line on the dialogue screen, and closes it when the talk ends. */
public final class ClientDialogue {

    private ClientDialogue() {
    }

    public static void show(DialogueNetwork.LinePacket line) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen screen && screen.speaker() == line.speaker()) {
            screen.say(line);
        } else {
            minecraft.setScreen(new DialogueScreen(line));
        }
    }

    public static void end() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen screen) {
            screen.ended();
        }
    }
}
