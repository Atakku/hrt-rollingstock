package zone.hrt.rollingstock.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.hrt.rollingstock.RollingStock;
import zone.hrt.rollingstock.accessors.IPhysicsCarriage;

import java.util.List;
import java.util.UUID;

@Mixin(value = Train.class, remap = false)
public abstract class MixinTrain {
  @Shadow
  public boolean derailed;

  @Shadow
  public List<Carriage> carriages;

  @Shadow
  public TrackGraph graph;

  @Shadow
  public double speed;

  @Shadow
  public double targetSpeed;

  @Shadow
  public abstract void leaveStation();

  @Shadow
  public boolean manualTick;

  @Shadow
  public int fuelTicks;

  @Shadow
  public abstract float maxSpeed();

  @Shadow
  public UUID currentStation;

  @Shadow
  public UUID id;

  @Shadow
  public abstract void crash();

  @Inject(method = "maxSpeed", at = @At("HEAD"), cancellable = true)
  public void maxSpeed(CallbackInfoReturnable<Float> cir) {
    cir.setReturnValue(AllConfigs.server().trains.trainTopSpeed.getF() / 20);
  }

  @Inject(method = "maxTurnSpeed", at = @At("HEAD"), cancellable = true)
  public void maxTurnSpeed(CallbackInfoReturnable<Float> cir) {
    cir.setReturnValue(AllConfigs.server().trains.trainTopSpeed.getF() / 20);
  }

  @Inject(method = "acceleration", at = @At("HEAD"), cancellable = true)
  public void acceleration(CallbackInfoReturnable<Float> cir) {
    double velocity = Math.abs(speed * 20); // velocity in m/s
    double force;
    double maxPower = Math.abs(targetSpeed - speed) * phys$getMass() * (20 * 20);
    if (Math.abs(targetSpeed) > Math.abs(speed) && !Mth.equal(targetSpeed * speed, 0)) {
      force = Math.min(Math.min(phys$getPower() / velocity, phys$getMaxTractiveEffort()), maxPower);
    } else {
      force = Math.min(phys$getMaxTractiveEffort(), maxPower);
    }
    double acc = Math.max(phys$forceToAcceleration(force), 0.0001);
    cir.setReturnValue((float) acc);
  }

  @Inject(method = "tickPassiveSlowdown", at = @At("HEAD"), cancellable = true)
  public void tickPassiveSlowdown(CallbackInfo ci) {
    ci.cancel();
    if (currentStation != null)
      return;
    double gravityAcceleration = phys$getGravityAcceleration();
    double aerodynamicAcceleration = -phys$forceToAcceleration(phys$getAerodynamicDrag());
    double rollingFrictionAcceleration = -phys$forceToAcceleration(phys$getRollingFriction())
        * Math.signum(speed);
    speed += gravityAcceleration + aerodynamicAcceleration + rollingFrictionAcceleration;
  }

  @WrapMethod(method = "collideWithOtherTrains")
  public void collideWithOtherTrains(Level level, Carriage carriage, Operation<Void> original) {
    if (derailed)
      return;

    TravellingPoint trailingPoint = carriage.getTrailingPoint();
    TravellingPoint leadingPoint = carriage.getLeadingPoint();

    if (leadingPoint.node1 == null || trailingPoint.node1 == null)
      return;
    ResourceKey<Level> dimension = leadingPoint.node1.getLocation().dimension;
    if (!dimension.equals(trailingPoint.node1.getLocation().dimension))
      return;

    Vec3 start = (speed < 0 ? trailingPoint : leadingPoint).getPosition(graph);
    Vec3 end = (speed < 0 ? leadingPoint : trailingPoint).getPosition(graph);

    Pair<Carriage, Vec3> collision = phys$findCollidingCarriage(level, start, end, dimension);
    if (collision == null)
      return;

    Train train = collision.getFirst().train;

    Carriage otherCarriage = collision.getFirst();

    double yawDiff = Math.abs(phys$getCarriageYaw(carriage) - phys$getCarriageYaw(otherCarriage));

    int directionMultiplier = (yawDiff > Math.PI / 2 && yawDiff < Math.PI * 1.5) ? -1 : 1;

    double relativeSpeed = Math.abs(directionMultiplier * speed + train.speed);

    if (relativeSpeed > 6 || (yawDiff % Math.PI > Math.PI / 4 && yawDiff % Math.PI < Math.PI * 0.75)) {
      Vec3 v = collision.getSecond();
      level.explode(null, v.x, v.y, v.z, (float) Math.min(3 * relativeSpeed, 5), Level.ExplosionInteraction.NONE);
      crash();
      train.crash();
      return;
    }

    double m1 = phys$getMass();
    double m2 = train.carriages.stream().mapToDouble(this::phys$getCarriageMass).sum();

    double u1 = directionMultiplier * speed * 20;
    double u2 = train.speed * 20;

    // Coefficient of restitution
    double e = 0.5;

    double v1 = (m1 * u1 + m2 * u2 + m2 * e * (u2 - u1)) / (m1 + m2);
    double v2 = (m1 * u1 + m2 * u2 + m1 * e * (u1 - u2)) / (m1 + m2);

    speed = directionMultiplier * v1 / 20;
    train.speed = v2 / 20;

  }

  @Unique
  private double phys$getCarriageMass(Carriage carriage) {
    Double carriageMass = ((IPhysicsCarriage) carriage).phys$getMass();
    if (carriageMass == null)
      carriageMass = 1.0;
    //AtomicInteger cargoMass = new AtomicInteger();
    // CombinedInvWrapper storageItems = carriage.storage.getAllItems();
    // if(storageItems != null)
    // storageItems.(storage-> cargoMass.addAndGet((int) (storage.getAmount() *
    // 10)));
    return carriageMass * 500;// cargoMass.get();
  }

  @Unique
  private double phys$getMass() {
    double mass = 0;
    for (Carriage carriage : carriages)
      mass += phys$getCarriageMass(carriage);
    return mass;
  }

  @Unique
  private int phys$getCarriagePower(Carriage carriage) {
    // count the engines and sth
    Integer engineCount = ((IPhysicsCarriage) carriage).trainphys$getEngineCount();
    if (engineCount == null)
      engineCount = 0;
    return engineCount * 200 * 1000;
  }

  @Unique
  private int phys$getPower() {
    if (fuelTicks <= 0)
      return RollingStock.MIN_POWER;
    return Math.max(carriages.stream().mapToInt(this::phys$getCarriagePower).sum(), RollingStock.MIN_POWER);
  }

  /**
   * @see <a href=
   *      "https://en.wikipedia.org/wiki/Adhesion_railway#Effect_of_adhesion_limits">Effect
   *      of adhesion limits</a>
   */
  @Unique
  private double phys$getMaxTractiveEffort() {
    return carriages.stream().mapToDouble(carriage -> RollingStock.FRICTION * phys$getCarriageMass(carriage) * 9.81).sum();
  }


  @Unique
  private double phys$getGravityAcceleration() {
    if (derailed)
      return 0;
    Vec3 leading = carriages.getFirst().getLeadingPoint().getPosition(graph);
    Vec3 trailing = carriages.getLast().getTrailingPoint().getPosition(graph);
    double horizontalDistance = Math.sqrt(Math.pow(leading.x - trailing.x, 2) + Math.pow(leading.z - trailing.z, 2));
    double verticalDistance = leading.y - trailing.y;
    double incline = Math.atan2(verticalDistance, horizontalDistance); // (-0.5pi,0): decline, (0, 0.5pi): incline 0: no
                                                                       // incline
    double gravityPerTick = -9.81 / (20 * 20); // 1s = 20t
    double gravityMultiplier = 1;
    return gravityMultiplier * gravityPerTick * Math.sin(incline);
  }

  @Unique
  private double phys$getDragConstant() {
    double airDensity = 1.204;
    double dragCoefficient = 0.35;
    double area = 9;
    return airDensity * dragCoefficient * area;
  }

  @Unique
  private double phys$getAerodynamicDrag() {
    double velocity = speed * 20;
    return phys$getDragConstant() * 0.5 * Math.pow(velocity, 2) * (speed > 0 ? 1 : -1);
  }

  @Unique
  private double phys$getRollingFriction() {
    return (RollingStock.RESISTANCE * (phys$getMass() * 9.81));
  }

  @Unique
  double phys$forceToAcceleration(double force) {
    return force / phys$getMass() / (20 * 20);
  }

  @Unique
  public double phys$getCarriageYaw(Carriage carriage) {
    Vec3 diff = carriage.getLeadingPoint().getPosition(carriage.train.graph)
        .subtract(carriage.getTrailingPoint().getPosition(carriage.train.graph)).normalize();
    return Math.atan2(diff.x, diff.z);
  }

  @Unique
  public Pair<Carriage, Vec3> phys$findCollidingCarriage(Level level, Vec3 start, Vec3 end,
      ResourceKey<Level> dimension) {
    Vec3 diff = end.subtract(start);
    double maxDistanceSqr = Math.pow(AllConfigs.server().trains.maxAssemblyLength.get(), 2.0);

    Trains: for (Train train : Create.RAILWAYS.sided(level).trains.values()) {
      if (train.id == this.id)
        continue;
      if (train.graph != null && train.graph != graph)
        continue;

      Vec3 lastPoint = null;

      for (Carriage otherCarriage : train.carriages) {
        for (boolean betweenBits : Iterate.trueAndFalse) {
          if (betweenBits && lastPoint == null)
            continue;

          TravellingPoint otherLeading = otherCarriage.getLeadingPoint();
          TravellingPoint otherTrailing = otherCarriage.getTrailingPoint();
          if (otherLeading.edge == null || otherTrailing.edge == null)
            continue;
          ResourceKey<Level> otherDimension = otherLeading.node1.getLocation().dimension;
          if (!otherDimension.equals(otherTrailing.node1.getLocation().dimension))
            continue;
          if (!otherDimension.equals(dimension))
            continue;

          Vec3 start2 = otherLeading.getPosition(train.graph);
          Vec3 end2 = otherTrailing.getPosition(train.graph);

          if (Math.min(start2.distanceToSqr(start), end2.distanceToSqr(start)) > maxDistanceSqr)
            continue Trains;

          if (betweenBits) {
            end2 = start2;
            start2 = lastPoint;
          }

          lastPoint = end2;

          if ((end.y < end2.y - 3 || end2.y < end.y - 3)
              && (start.y < start2.y - 3 || start2.y < start.y - 3))
            continue;

          Vec3 diff2 = end2.subtract(start2);
          Vec3 normedDiff = diff.normalize();
          Vec3 normedDiff2 = diff2.normalize();
          double[] intersect = VecHelper.intersect(start, start2, normedDiff, normedDiff2, Direction.Axis.Y);

          if (intersect == null) {
            Vec3 intersectSphere = VecHelper.intersectSphere(start2, normedDiff2, start, .125f);
            if (intersectSphere == null)
              continue;
            if (!Mth.equal(normedDiff2.dot(intersectSphere.subtract(start2)
                .normalize()), 1))
              continue;
            intersect = new double[2];
            intersect[0] = intersectSphere.distanceTo(start) - .125;
            intersect[1] = intersectSphere.distanceTo(start2) - .125;
          }

          if (intersect[0] > diff.length())
            continue;
          if (intersect[1] > diff2.length())
            continue;
          if (intersect[0] < 0)
            continue;
          if (intersect[1] < 0)
            continue;

          return Pair.of(otherCarriage, start.add(normedDiff.scale(intersect[0])));
        }
      }
    }
    return null;
  }
}