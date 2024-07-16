package ace.actually.mercenary;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

@Environment(EnvType.CLIENT)
public class ClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (Mercenary.BOUNTY_KEY.wasPressed()) {
                PacketByteBuf byteBuf = PacketByteBufs.create();
                ClientPlayNetworking.send(Mercenary.C2S_BOUNTY_PACKET,byteBuf);
            }
        });
    }
}
