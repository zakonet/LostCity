package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.job.CityJobType;

public record CitizenSkillSnapshot(CityJobType skillType, int level, int xp, int maxLevel) {
    public boolean maxLevelReached() {
        return level >= maxLevel;
    }
}
