package ace.actually.mercenary.blocks;

import ace.actually.mercenary.Mercenary;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlagBlock extends Block {
    public FlagBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if(world instanceof ServerWorld serverWorld)
        {
            if(placer!=null && placer.getScoreboardTeam()!=null)
            {
                if(serverWorld.getServer().getDataCommandStorage().get(Mercenary.STOAGE).contains(placer.getScoreboardTeam().getName()))
                {
                    NbtCompound compound = serverWorld.getServer().getDataCommandStorage().get(Mercenary.STOAGE);
                    NbtCompound teamData =compound.getCompound(placer.getScoreboardTeam().getName());
                    if(teamData.contains("notComplete"))
                    {
                        BlockPos nextLoc = NbtHelper.toBlockPos(teamData.getCompound("nextLoc")).withY(placer.getBlockY());
                        System.out.println(nextLoc.toShortString());
                        if(nextLoc.getSquaredDistance(pos)<500)
                        {
                            List<HostileEntity> host = serverWorld.getEntitiesByClass(HostileEntity.class,new Box(pos.add(-30,-10,-30),pos.add(30,30,30)),LivingEntity::isAlive);
                            if(host.size()>2)
                            {
                                world.playSound(null,pos,SoundEvents.ENTITY_VILLAGER_NO,SoundCategory.BLOCKS,1,1);
                                serverWorld.getPlayers(
                                        a->a.getScoreboardTeam().getName().equals(placer.getScoreboardTeam().getName())).forEach(
                                        a->a.sendMessage(Text.of("text.mercenary.hostiles"),false));
                                world.breakBlock(pos,true);
                                host.forEach(a->a.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,500)));
                            }
                            else
                            {
                                world.playSound(null,pos,SoundEvents.ENTITY_VILLAGER_TRADE,SoundCategory.BLOCKS,1,1);
                                serverWorld.getPlayers(
                                        a->a.getScoreboardTeam().getName().equals(placer.getScoreboardTeam().getName())).forEach(
                                        a->a.sendMessage(Text.translatable("text.mercenary.complete"),false));
                                teamData.remove("notComplete");
                                compound.put(placer.getScoreboardTeam().getName(),teamData);
                                serverWorld.getServer().getDataCommandStorage().set(Mercenary.STOAGE,compound);
                            }
                        }
                        else
                        {
                            placer.sendMessage(Text.translatable("text.mercenary.faraway"));
                            world.breakBlock(pos,true);
                        }

                    }
                    else
                    {
                        placer.sendMessage(Text.translatable("text.mercenary.faraway"));
                        world.breakBlock(pos,true);
                    }
                }
                else
                {
                    placer.sendMessage(Text.translatable("text.mercenary.no_quest"));
                    world.breakBlock(pos,true);
                }
            }
            else
            {
                placer.sendMessage(Text.translatable("text.mercenary.no_team"));
                world.breakBlock(pos,true);
            }
        }

    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {

        return Block.createCuboidShape(7,0,7,9,16,9);
    }

}
