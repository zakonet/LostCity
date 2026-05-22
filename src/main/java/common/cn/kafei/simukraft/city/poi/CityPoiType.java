package common.cn.kafei.simukraft.city.poi;

public enum CityPoiType {
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,
    OTHER,
    GATHERING,
    STORAGE,
    LOGISTICS,
    FARMLAND,
    DEFENSE;

    public static CityPoiType fromName(String name) {
        for (CityPoiType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return OTHER;
    }
}
