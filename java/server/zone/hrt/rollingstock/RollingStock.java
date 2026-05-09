// Copyright 2026 Atakku <https://atakku.dev>
//
// This project is dual licensed under MIT and Apache.

package zone.hrt.rollingstock;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RollingStock.MOD_ID)
public class RollingStock {
  public static final String MOD_ID = "hrt_rollingstock";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  public static final double FRICTION = 0.4; // frictionCoefficient
  public static final double RESISTANCE = 0.001; // rollingResistanceCoefficient

  public static final float MAX_SPEED = 360 / 72f; // 360 km/h / 3.6 = 100 m/s / 20t = 5 m/t

  public static final TagKey<Block> TAG_ENGINE = TagKey.create(
      Registries.BLOCK,
      ResourceLocation.fromNamespaceAndPath(MOD_ID, "train_engine"));
}
