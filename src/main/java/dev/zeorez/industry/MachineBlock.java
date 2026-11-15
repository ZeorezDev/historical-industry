package dev.zeorez.industry;

import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import javax.annotation.Nullable;

class MachineBlock extends BaseEntityBlock {
    static final BooleanProperty UNLOAD = BooleanProperty.create("unload");
    final Kind kind;
    static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    MachineBlock(Kind kind) {
        super(Properties.of().strength(4, 8).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(ACTIVE,false).setValue(FACING,Direction.NORTH));
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(ACTIVE,FACING); }
    @Nullable public BlockState getStateForPlacement(BlockPlaceContext c) {
        if (c.getLevel() instanceof ServerLevel world && (!(c.getPlayer() instanceof ServerPlayer) || Land.site(world,c.getClickedPos()) == null)) {
            if (c.getPlayer() != null) c.getPlayer().displayClientMessage(Component.translatable("industry.land"),true);
            return null;
        }
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
    public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new Machine(p,s); }
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide || kind.logistics() ? null : createTickerHelper(t,Industry.MACHINE.get(),Machine::tick);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if (!old.is(this) && !level.isClientSide && scheduled()) level.scheduleTick(pos,this,8);
    }
    private boolean scheduled() { return kind==Kind.INDUSTRIAL_CONVEYOR || kind==Kind.RAILWAY_LOADING_DOCK; }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!scheduled()) return;
        if (level.getBlockEntity(pos) instanceof Machine m) Logistics.step(level,m,state);
        level.scheduleTick(pos,this,kind==Kind.INDUSTRIAL_CONVEYOR ? 8 : 20);
    }
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity who, ItemStack stack) {
        if (who instanceof ServerPlayer player && l.getBlockEntity(p) instanceof Machine m) m.bind(player);
    }
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player player, InteractionHand h, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && l.getBlockEntity(p) instanceof Machine m) {
            if (Land.mayUse(sp,m)) {
                if (kind==Kind.INDUSTRIAL_CONVEYOR)
                    l.setBlockAndUpdate(p,s.setValue(FACING,s.getValue(FACING).getClockWise()));
                else if (kind==Kind.RAILWAY_LOADING_DOCK) {
                    var next=s.cycle(UNLOAD);
                    l.setBlockAndUpdate(p,next);
                    sp.displayClientMessage(Component.translatable(next.getValue(UNLOAD) ? "industry.unload" : "industry.load"),true);
                } else NetworkHooks.openScreen(sp,m,p);
            }
            else sp.displayClientMessage(Component.translatable("industry.denied"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock()) && !l.isClientSide && l.getBlockEntity(p) instanceof Machine m) m.drop();
        super.onRemove(s,l,p,next,moving);
    }
    public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        if (s.getValue(ACTIVE) && (kind == Kind.COAL_GENERATOR || kind == Kind.INDUSTRIAL_FURNACE))
            l.addParticle(ParticleTypes.SMOKE,p.getX()+0.5,p.getY()+1.02,p.getZ()+0.5,0,0.04,0);
    }
}
