package zone.hrt.rollingstock.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackGraph;
import dev.ryanhcode.sable.mixinterface.block_properties.BlockStateExtension;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyTypes;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.hrt.rollingstock.RollingStock;
import zone.hrt.rollingstock.accessors.IPhysicsCarriage;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = Carriage.class, remap = false)
public abstract class MixinCarriage implements IPhysicsCarriage {
  @Shadow
  public abstract CarriageContraptionEntity anyAvailableEntity();

  @Unique
  private @Nullable Double phys$mass = null;
  @Unique

  private @Nullable Integer trainphys$engineCount = null;

  @Inject(method = "write", at = @At("RETURN"))
  private void writeMassAndEngineCount(DimensionPalette dimensions, HolderLookup.Provider registries,
      CallbackInfoReturnable<CompoundTag> cir) {
    CompoundTag tag = cir.getReturnValue();
    Double mass = phys$getMass();
    if (mass != null)
      tag.putDouble("mass", mass);
    Integer engineCount = trainphys$getEngineCount();
    if (engineCount != null)
      tag.putInt("engineCount", engineCount);
  }

  @Inject(method = "read", at = @At("RETURN"))
  private static void readMassAndEngineCount(CompoundTag tag, HolderLookup.Provider registries, TrackGraph graph,
      DimensionPalette dimensions, CallbackInfoReturnable<Carriage> cir) {
    IPhysicsCarriage carriage = (IPhysicsCarriage) cir.getReturnValue();

    if (tag.contains("mass", CompoundTag.TAG_DOUBLE))
      carriage.phys$setMass(tag.getDouble("mass"));
    if (tag.contains("engineCount", CompoundTag.TAG_INT))
      carriage.trainphys$setEngineCount(tag.getInt("engineCount"));
  }

  @Override
  public @Nullable Double phys$getMass() {
    System.out.println("MASS " + phys$mass);
    if (phys$mass == null || phys$mass == 0) {
      CarriageContraptionEntity entity = anyAvailableEntity();
      if (entity != null)
        phys$mass = entity.getContraption().getBlocks().values().stream()
            .mapToDouble(a -> ((BlockStateExtension) a.state()).sable$getProperty(PhysicsBlockPropertyTypes.MASS.get()))
            .sum();
    }
    return phys$mass;
  }

  @Override
  public void phys$setMass(double mass) {
    phys$mass = mass;
  }

  @Override
  public @Nullable Integer trainphys$getEngineCount() {
    if (trainphys$engineCount == null || trainphys$engineCount == 0) {
      CarriageContraptionEntity entity = anyAvailableEntity();
      if (entity != null) {
        AtomicInteger engineCount = new AtomicInteger();

        entity.getContraption().getBlocks().forEach((p, b) -> {
          if (b.state().getBlock().defaultBlockState().is(RollingStock.TAG_ENGINE)) {
            engineCount.getAndIncrement();
          }
        });
        trainphys$setEngineCount(engineCount.get());
      }
    }
    return trainphys$engineCount;
  }

  @Override
  public void trainphys$setEngineCount(int engineCount) {
    trainphys$engineCount = engineCount;
  }
}
