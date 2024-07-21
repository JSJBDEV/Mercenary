package ace.actually.mercenary;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class ClientInit implements ClientModInitializer {

    public static final KeyBinding BOUNTY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mercenary.bounty", // The translation key of the keybinding's name
            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
            GLFW.GLFW_KEY_BACKSLASH, // The keycode of the key
            "category.mercenary" // The translation key of the keybinding's category.
    ));
    public static final KeyBinding REMOVE_QUEST_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mercenary.remove_quest",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            "category.mercenary"
    ));
    public static final KeyBinding COUNT_EMERALDS_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mercenary.count_emeralds",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            "category.mercenary"
    ));


    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (BOUNTY_KEY.wasPressed()) {
                PacketByteBuf byteBuf = PacketByteBufs.create();
                ClientPlayNetworking.send(Mercenary.C2S_BOUNTY_PACKET,byteBuf);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (REMOVE_QUEST_KEY.wasPressed()) {
                PacketByteBuf byteBuf = PacketByteBufs.create();
                ClientPlayNetworking.send(Mercenary.C2S_REMOVE_QUEST_PACKET,byteBuf);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (COUNT_EMERALDS_KEY.wasPressed()) {
                PacketByteBuf byteBuf = PacketByteBufs.create();
                ClientPlayNetworking.send(Mercenary.C2S_COUNT_EMERALDS_PACKET,byteBuf);
            }
        });
    }
}
