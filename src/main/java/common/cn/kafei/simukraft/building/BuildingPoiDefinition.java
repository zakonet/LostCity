package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.poi.CityPoiType;

public record BuildingPoiDefinition(String id, CityPoiType poiType, int capacity) {
}
