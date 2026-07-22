package common.cn.kafei.simukraft.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcGrowthServiceTest {
    @Test
    void ageAdvancesOncePerFourGameDays() {
        assertEquals(0L, NpcGrowthService.completedYears(10L, 13L));
        assertEquals(1L, NpcGrowthService.completedYears(10L, 14L));
        assertEquals(3L, NpcGrowthService.completedYears(10L, 22L));
    }

    @Test
    void invalidOrRewoundDatesDoNotAdvanceAge() {
        assertEquals(0L, NpcGrowthService.completedYears(-1L, 20L));
        assertEquals(0L, NpcGrowthService.completedYears(20L, 19L));
    }
}
