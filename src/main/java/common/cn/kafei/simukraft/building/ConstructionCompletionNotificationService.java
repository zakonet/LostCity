package common.cn.kafei.simukraft.building;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.city.group.CityUserGroup;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.registry.ModSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

@SuppressWarnings("null")
public final class ConstructionCompletionNotificationService {
    private ConstructionCompletionNotificationService() {
    }

    // notifyCompleted：建筑任务完工后向城市成员弹窗，并按旧版逻辑给市长播放完成音效。
    public static void notifyCompleted(ServerLevel level, CitizenData citizen, BuildingTaskData task) {
        if (level == null || citizen == null || task == null || task.cityId() == null) {
            return;
        }
        Optional<CityData> city = CityService.findCity(level, task.cityId());
        if (city.isEmpty()) {
            return;
        }
        Component message = Component.translatable("message.simukraft.construction.completed", citizen.name(), task.displayName());
        CityGroupMessageService.send(level, CityUserGroup.members(task.cityId()),
                Component.translatable("toast.simukraft.construction_title"), message, "success", ItemStack.EMPTY);
        findMayor(city.get()).ifPresent(member -> playMayorSound(level, member));
    }

    private static Optional<CityMemberData> findMayor(CityData city) {
        return city.members().stream()
                .filter(member -> member.permissionLevel() == CityPermissionLevel.MAYOR)
                .findFirst();
    }

    private static void playMayorSound(ServerLevel level, CityMemberData member) {
        ServerPlayer mayor = level.getServer().getPlayerList().getPlayer(member.playerId());
        if (mayor != null) {
            mayor.playNotifySound(ModSoundEvents.CONSTRUCTION_COMPLETE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
