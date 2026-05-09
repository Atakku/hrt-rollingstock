package zone.hrt.rollingstock.mixin;

import org.jetbrains.annotations.Nullable;

public interface AccessorIPhysicsCarriage {
  @Nullable
  Integer phys$getMass();

  void phys$setMass(int mass);

  @Nullable
  Integer trainphys$getEngineCount();

  void trainphys$setEngineCount(int engineCount);
}
