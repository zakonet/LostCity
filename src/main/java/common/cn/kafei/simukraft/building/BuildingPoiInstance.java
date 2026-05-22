package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.core.BlockPos;

public record BuildingPoiInstance(String key, CityPoiType poiType, int capacity, BlockPos worldPos) {
}
