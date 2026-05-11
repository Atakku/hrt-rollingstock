package zone.hrt.rollingstock.accessors;

import org.jetbrains.annotations.Nullable;

public interface IPhysicsCarriage {
  @Nullable
  Double phys$getMass();

  void phys$setMass(double mass);

  @Nullable
  Integer trainphys$getEngineCount();

  void trainphys$setEngineCount(int engineCount);
}
