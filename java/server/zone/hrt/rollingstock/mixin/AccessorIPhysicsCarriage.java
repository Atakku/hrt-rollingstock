package zone.hrt.rollingstock.mixin;

import org.jetbrains.annotations.Nullable;

public interface AccessorIPhysicsCarriage {
    @Nullable Integer railways$getMass();
    void railways$setMass(int mass);
    @Nullable Integer trainphys$getEngineCount();
    void trainphys$setEngineCount(int engineCount);
}
