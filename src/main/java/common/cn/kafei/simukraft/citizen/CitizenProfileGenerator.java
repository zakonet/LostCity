package common.cn.kafei.simukraft.citizen;

import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class CitizenProfileGenerator {
    private static final String[] FAMILY_NAMES = {"赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "刘", "杨", "黄", "林", "何", "高"};
    private static final String[] MALE_NAMES = {"明", "强", "磊", "军", "杰", "涛", "超", "伟", "峰", "航", "宇", "辰"};
    private static final String[] FEMALE_NAMES = {"芳", "娜", "敏", "静", "丽", "婷", "雪", "欣", "雅", "琪", "萱", "琳"};
    private static final int MALE_SKIN_COUNT = 60;
    private static final int FEMALE_SKIN_COUNT = 60;

    private CitizenProfileGenerator() {
    }

    public static void fillMissingProfile(CitizenData data, RandomSource random, long gameDay) {
        if (isMissingGender(data.gender())) {
            data.setGender(random.nextDouble() < 0.5D ? "male" : "female");
        }
        if (data.name().isBlank()) {
            data.setName(createName(data.gender(), random));
        }
        if (data.skinPath().isBlank()) {
            data.setSkinPath(createSkinPath(data.gender(), data.uuid()));
        }
        if (data.age() <= 0) {
            data.setAge(18 + random.nextInt(18));
        }
        if (data.lifespan() <= 0) {
            data.setLifespan(70 + random.nextInt(21));
        }
        if (data.bornDay() <= 0L) {
            data.setBornDay(gameDay - data.age() * 365L);
        }
    }

    private static boolean isMissingGender(String gender) {
        return gender == null || gender.isBlank() || "unknown".equalsIgnoreCase(gender);
    }

    private static String createName(String gender, RandomSource random) {
        String familyName = FAMILY_NAMES[random.nextInt(FAMILY_NAMES.length)];
        String[] names = "female".equals(gender) ? FEMALE_NAMES : MALE_NAMES;
        return familyName + names[random.nextInt(names.length)];
    }

    private static String createSkinPath(String gender, UUID uuid) {
        int skinCount = "female".equals(gender) ? FEMALE_SKIN_COUNT : MALE_SKIN_COUNT;
        int index = Math.floorMod(uuid.hashCode(), skinCount);
        String prefix = "female".equals(gender) ? "custom_female_entity_" : "custom_male_entity_";
        String folder = "female".equals(gender) ? "female" : "male";
        return "simukraft:textures/entity/" + folder + "/" + prefix + index + ".png";
    }
}
