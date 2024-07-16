package ace.actually.mercenary;

import ace.actually.mercenary.blocks.FlagBlock;
import brightspark.asynclocator.AsyncLocator;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.gen.structure.Structure;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mercenary implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("mercenary");
	public static final Identifier STOAGE = Identifier.of("mercenary","storage");
	public static final TagKey<Structure> ROGUES = TagKey.of(RegistryKeys.STRUCTURE,Identifier.of("mercenary","rogues"));
	public static final KeyBinding BOUNTY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.mercenary.bounty", // The translation key of the keybinding's name
			InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
			GLFW.GLFW_KEY_BACKSLASH, // The keycode of the key
			"category.mercenary" // The translation key of the keybinding's category.
	));
	public static final Identifier C2S_BOUNTY_PACKET =  Identifier.of("mercenary","bounty_packet");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		registerBlocks();
		registerItems();

        ServerPlayNetworking.registerGlobalReceiver(C2S_BOUNTY_PACKET,((server, player, handler, buf, responseSender) -> server.execute(()-> sortBounties(player))));
		LOGGER.info("Hello Fabric world!");
	}

	public static final FlagBlock FLAG_BLOCK = new FlagBlock(AbstractBlock.Settings.create());
	public static final FlagBlock CRATE = new FlagBlock(AbstractBlock.Settings.create());
	private void registerBlocks()
	{
		Registry.register(Registries.BLOCK,Identifier.of("mercenary","flag"),FLAG_BLOCK);
		Registry.register(Registries.BLOCK,Identifier.of("mercenary","crate"),CRATE);
	}

	private void registerItems()
	{
		Registry.register(Registries.ITEM,Identifier.of("mercenary","flag"),new BlockItem(FLAG_BLOCK,new Item.Settings()));
		Registry.register(Registries.ITEM,Identifier.of("mercenary","crate"),new BlockItem(CRATE,new Item.Settings()));
	}


	private void sortBounties(ServerPlayerEntity spe)
	{
		if(!spe.getServerWorld().getEntitiesByClass(VillagerEntity.class,new Box(spe.getBlockPos().add(-20,-20,-20),spe.getBlockPos().add(20,20,20)), LivingEntity::isAlive).isEmpty())
		{
			NbtCompound compound = spe.getServer().getDataCommandStorage().get(Mercenary.STOAGE);
			if(spe.getScoreboardTeam()!=null)
			{
				if(!spe.getServer().getDataCommandStorage().get(Mercenary.STOAGE).contains(spe.getScoreboardTeam().getName()))
				{
					spe.getServerWorld().getPlayers(
							a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
							a->a.sendMessage(Text.translatable("text.mercenary.quest"),true));
					NbtCompound teamData = new NbtCompound();
					if(spe.getRandom().nextBoolean())
					{
						AsyncLocator.locate(spe.getServerWorld(), StructureTags.VILLAGE,spe.getBlockPos(),10000,true).thenOnServerThread(vPos ->
						{
							spe.getServerWorld().playSound(null, spe.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS,1,1);
							spe.giveItemStack(new ItemStack(Mercenary.CRATE));
							spe.getServerWorld().getPlayers(
									a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
									a->a.sendMessage(Text.translatable("text.mercenary.package").append(vPos.toShortString()),false));
							teamData.put("nextLoc", NbtHelper.fromBlockPos(vPos));
							teamData.putBoolean("notComplete",true);
							compound.put(spe.getScoreboardTeam().getName(),teamData);
							spe.getServer().getDataCommandStorage().set(Mercenary.STOAGE,compound);
						});
					}
					else
					{
						AsyncLocator.locate(spe.getServerWorld(), Mercenary.ROGUES,spe.getBlockPos(),10000,true).thenOnServerThread(vPos ->
						{
							spe.getServerWorld().playSound(null, spe.getBlockPos(), SoundEvents.ENTITY_PILLAGER_CELEBRATE, SoundCategory.BLOCKS,1,1);
							spe.giveItemStack(new ItemStack(Mercenary.FLAG_BLOCK));
							spe.getServerWorld().getPlayers(
									a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
									a->a.sendMessage(Text.translatable("text.mercenary.rogues").append(vPos.toShortString()),false));
							teamData.put("nextLoc", NbtHelper.fromBlockPos(vPos));
							teamData.putBoolean("notComplete",true);
							compound.put(spe.getScoreboardTeam().getName(),teamData);
							spe.getServer().getDataCommandStorage().set(Mercenary.STOAGE,compound);
						});

					}
				}
				else
				{
					if(!compound.getCompound(spe.getScoreboardTeam().getName()).contains("notComplete"))
					{
						spe.giveItemStack(new ItemStack(Items.EMERALD,10));
						spe.getServerWorld().playSound(null, spe.getBlockPos(),SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS,1,1);
						spe.getServerWorld().getServer().getDataCommandStorage().get(Mercenary.STOAGE).remove(spe.getScoreboardTeam().getName());
					}
				}
			}
			else
			{
				spe.sendMessage(Text.translatable("text.mercenary.no_team"));
			}
		}
		else
		{
			spe.sendMessage(Text.translatable("text.mercenary.no_village"));
		}

	}
}