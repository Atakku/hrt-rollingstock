package zone.hrt.rollingstock.accessors;

import org.jetbrains.annotations.Nullable;

public interface IPhysicsCarriage {
  @Nullable
  Integer phys$getMass();

  void phys$setMass(int mass);

  @Nullable
  Integer trainphys$getEngineCount();

  void trainphys$setEngineCount(int engineCount);
}
