package dev.zeorez.industry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;

final class DockBlock extends MachineBlock {
    DockBlock() {
        super(Kind.RAILWAY_LOADING_DOCK);
        registerDefaultState(defaultBlockState().setValue(UNLOAD,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(UNLOAD);
    }
}
