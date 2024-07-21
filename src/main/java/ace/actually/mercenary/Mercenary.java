package ace.actually.mercenary;

import ace.actually.mercenary.blocks.FlagBlock;
import brightspark.asynclocator.AsyncLocator;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
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
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Mercenary implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("mercenary");
	public static final Identifier STOAGE = Identifier.of("mercenary","storage");
	public static final TagKey<Structure> ROGUES = TagKey.of(RegistryKeys.STRUCTURE,Identifier.of("mercenary","rogues"));

	public static final Identifier C2S_BOUNTY_PACKET =  Identifier.of("mercenary","bounty_packet");
	public static final Identifier C2S_REMOVE_QUEST_PACKET =  Identifier.of("mercenary","remove_quest_packet");
	public static final Identifier C2S_COUNT_EMERALDS_PACKET =  Identifier.of("mercenary","count_emeralds_packet");

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.
		registerBlocks();
		registerItems();

        ServerPlayNetworking.registerGlobalReceiver(C2S_BOUNTY_PACKET,((server, player, handler, buf, responseSender) -> server.execute(()-> sortBounties(player))));
		ServerPlayNetworking.registerGlobalReceiver(C2S_REMOVE_QUEST_PACKET,((server, player, handler, buf, responseSender) -> server.execute(()-> removeQuest(player))));
		ServerPlayNetworking.registerGlobalReceiver(C2S_COUNT_EMERALDS_PACKET,((server, player, handler, buf, responseSender) -> server.execute(()-> countEmeralds(player))));

		CommandRegistrationCallback.EVENT.register((dispatcher, phase, registrationEnvironment) -> dispatcher.register(

				literal("mercenary")
						.requires(source -> source.hasPermissionLevel(0))
						.then(literal("quest").executes(context -> {
							sortBounties(context.getSource().getPlayer());
							return 1;
						}))
						.then(literal("count").executes(context -> {
							countEmeralds(context.getSource().getPlayer());
							return 1;
						}))
						.then(literal("remove").executes(context -> {
							removeQuest(context.getSource().getPlayer());
							return 1;
						}))
		));


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

	private void dumpStructuresToConfig(MinecraftServer server)
	{
		Set<Identifier> ids = server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds();
		Set<String> mapped = ids.stream().map(a->a+",").collect(Collectors.toSet());
        try {
            FileUtils.writeLines(FabricLoader.getInstance().getConfigDir().resolve("mercenary/structures.txt").toFile(),mapped);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	private void removeQuest(ServerPlayerEntity spe)
	{
		if(spe.getScoreboardTeam()!=null)
		{
			if(spe.getServer().getDataCommandStorage().get(Mercenary.STOAGE).contains(spe.getScoreboardTeam().getName()))
			{
				NbtCompound cmpd = spe.getServerWorld().getServer().getDataCommandStorage().get(Mercenary.STOAGE);
				cmpd.remove(spe.getScoreboardTeam().getName());
				spe.getServerWorld().getServer().getDataCommandStorage().set(Mercenary.STOAGE,cmpd);

				spe.getServerWorld().getPlayers(
						a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
						a->a.sendMessage(Text.translatable("text.mercenary.quest_removed"),true));

				spe.getServerWorld().playSound(null, spe.getBlockPos(),SoundEvents.BLOCK_ANVIL_BREAK, SoundCategory.PLAYERS,1,1);

			}
			else
			{
				spe.sendMessage(Text.translatable("text.mercenary.no_quest"));
			}
		}
		else
		{
			spe.sendMessage(Text.translatable("text.mercenary.no_team"));
		}
	}

	private void countEmeralds(ServerPlayerEntity spe)
	{
		if(spe.getScoreboardTeam()!=null)
		{
			int added = 0;
			ItemStack stack = spe.getMainHandStack();
			if(stack.isOf(Items.EMERALD))
			{
				added+=stack.getCount();
				stack.decrement(64);
				spe.setStackInHand(Hand.MAIN_HAND,stack);
			}

			if(spe.getServerWorld().getScoreboard().getObjective("merc_score")==null)
			{
				ScoreboardObjective objcv = spe.getServerWorld().getScoreboard().addObjective("merc_score", ScoreboardCriterion.DUMMY,Text.of("[M] Team Scores"), ScoreboardCriterion.RenderType.INTEGER);
				spe.getServerWorld().getScoreboard().getPlayerScore(spe.getScoreboardTeam().getName(),objcv).incrementScore(added);
			}
			else
			{
				ScoreboardObjective objcv = spe.getServerWorld().getScoreboard().getObjective("merc_score");
				spe.getServerWorld().getScoreboard().getPlayerScore(spe.getScoreboardTeam().getName(),objcv).incrementScore(added);
			}

			if(added>0)
			{
				spe.getServerWorld().playSound(null, spe.getBlockPos(),SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS,1,1);
				int finalAdded = added;
				spe.getServerWorld().getPlayers(
						a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
						a->a.sendMessage(spe.getName().copy().append(" ").append(Text.translatable("text.mercenary.added_emeralds").append(" ["+ finalAdded +"]")),true));
			}
		}
		else
		{
			spe.sendMessage(Text.translatable("text.mercenary.no_team"));
		}

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
						int v = spe.getServerWorld().random.nextBetween(1,64);
						ItemStack stack = new ItemStack(Items.EMERALD,v);
						spe.giveItemStack(stack);

						spe.getServerWorld().playSound(null, spe.getBlockPos(),SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS,1,1);

						NbtCompound cmpd = spe.getServerWorld().getServer().getDataCommandStorage().get(Mercenary.STOAGE);
						cmpd.remove(spe.getScoreboardTeam().getName());
						spe.getServerWorld().getServer().getDataCommandStorage().set(Mercenary.STOAGE,cmpd);
					}
					else
					{
						BlockPos vPos = NbtHelper.toBlockPos(compound.getCompound(spe.getScoreboardTeam().getName()).getCompound("nextLoc"));

						spe.getServerWorld().getPlayers(
								a->a.getScoreboardTeam().getName().equals(spe.getScoreboardTeam().getName())).forEach(
								a->a.sendMessage(Text.translatable("text.mercenary.location").append(vPos.toShortString()),false));
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