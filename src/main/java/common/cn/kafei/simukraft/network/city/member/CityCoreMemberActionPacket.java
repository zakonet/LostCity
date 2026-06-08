package common.cn.kafei.simukraft.network.city.member;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityMemberData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.group.CityGroupMessageService;
import common.cn.kafei.simukraft.city.group.CityUserGroup;
import common.cn.kafei.simukraft.city.group.CityUserGroupService;
import common.cn.kafei.simukraft.network.city.core.CityCoreAccessValidator;
import common.cn.kafei.simukraft.network.city.core.CityCoreOpenRequestPacket;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("null")
public record CityCoreMemberActionPacket(BlockPos pos, Action action, UUID targetId, String targetName, CityPermissionLevel permissionLevel) implements CustomPacketPayload {
    public static final Type<CityCoreMemberActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SimuKraft.MOD_ID, "city_core_member_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCoreMemberActionPacket> STREAM_CODEC = StreamCodec.of(CityCoreMemberActionPacket::encode, CityCoreMemberActionPacket::decode);
    public static final UUID EMPTY_PLAYER_ID = new UUID(0L, 0L);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, CityCoreMemberActionPacket packet) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeUtf(packet.action().name(), 32);
        buffer.writeUUID(packet.targetId());
        buffer.writeUtf(packet.targetName(), 64);
        buffer.writeUtf(packet.permissionLevel().name(), 16);
    }

    public static CityCoreMemberActionPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CityCoreMemberActionPacket(buffer.readBlockPos(), Action.fromName(buffer.readUtf(32)), buffer.readUUID(), buffer.readUtf(64), CityPermissionLevel.fromName(buffer.readUtf(16)));
    }

    public static void handle(CityCoreMemberActionPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            handleAction(level, player, packet);
        }
    }

    private static void handleAction(ServerLevel level, ServerPlayer player, CityCoreMemberActionPacket packet) {
        if (!CityCoreAccessValidator.requireAccess(level, player, packet.pos())) {
            return;
        }
        Optional<CityData> city = CityService.findCityByCorePosForPlayer(level, packet.pos(), player.getUUID());
        if (city.isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.simukraft.city_core.not_found"));
            return;
        }
        CityData cityData = city.get();
        List<ServerPlayer> beforeGroup = CityUserGroupService.onlinePlayers(level, CityUserGroup.members(cityData.cityId()));
        MemberActionResult result = switch (packet.action()) {
            case ADD -> addOnlinePlayer(level, player, cityData, packet.targetId(), packet.targetName(), packet.permissionLevel());
            case REMOVE -> removePlayer(level, player, cityData, packet.targetId(), packet.targetName(), beforeGroup);
            case SET_PERMISSION -> setPermission(level, player, cityData, packet.targetId(), packet.targetName(), packet.permissionLevel());
        };
        if (result.success()) {
            CityGroupMessageService.sendResolved(result.recipients(), Component.translatable("toast.simukraft.title"), result.message(), "success", net.minecraft.world.item.ItemStack.EMPTY);
            HudSyncService.syncResolvedGroup(result.recipients(), true);
        } else {
            InfoToastService.warning(player, result.message());
        }
        CityCoreMembersRequestPacket.sendMembers(level, player, packet.pos());
        CityCoreOpenRequestPacket.openFor(level, player, packet.pos());
    }

    // addOnlinePlayer: 将在线玩家加入城市，并按权限生成城市用户组消息。
    private static MemberActionResult addOnlinePlayer(ServerLevel level, ServerPlayer operator, CityData city, UUID targetId, String rawTargetName, CityPermissionLevel permissionLevel) {
        if (permissionLevel == CityPermissionLevel.MAYOR) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        String targetName = rawTargetName == null ? "" : rawTargetName.trim();
        boolean hasTargetId = targetId != null && !EMPTY_PLAYER_ID.equals(targetId);
        if (targetName.isBlank() && !hasTargetId) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        ServerPlayer target = hasTargetId
                ? level.getServer().getPlayerList().getPlayer(targetId)
                : level.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.player_not_online", targetName));
        }
        Optional<CityData> targetCity = CityService.findPlayerCity(level, target.getUUID());
        if (targetCity.isPresent() && !targetCity.get().cityId().equals(city.cityId())) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.target_has_city", target.getGameProfile().getName()));
        }
        boolean added = CityService.addPlayer(level, city.cityId(), operator.getUUID(), target.getUUID(), target.getGameProfile().getName(), permissionLevel);
        if (!added) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        Component message = permissionLevel == CityPermissionLevel.OFFICIAL
                ? Component.translatable("message.simukraft.city_core.official_added", target.getGameProfile().getName(), city.cityName())
                : Component.translatable("message.simukraft.city_core.member_added", target.getGameProfile().getName(), city.cityName(), permissionName(permissionLevel));
        return MemberActionResult.succeeded(message, CityUserGroupService.onlinePlayers(level, CityUserGroup.members(city.cityId())));
    }

    // removePlayer: 从城市移除成员，并使用变更前用户组快照通知所有相关在线成员。
    private static MemberActionResult removePlayer(ServerLevel level, ServerPlayer operator, CityData city, UUID targetId, String targetName, List<ServerPlayer> beforeGroup) {
        Optional<CityMemberData> targetMember = city.member(targetId);
        if (targetMember.isEmpty()) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        boolean removed = CityService.removePlayer(level, city.cityId(), operator.getUUID(), targetId);
        if (!removed) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        String displayName = displayName(targetMember.get(), targetName);
        Component message = targetMember.get().permissionLevel() == CityPermissionLevel.OFFICIAL
                ? Component.translatable("message.simukraft.city_core.official_removed", displayName, city.cityName())
                : Component.translatable("message.simukraft.city_core.member_removed", displayName, city.cityName());
        return MemberActionResult.succeeded(message, beforeGroup);
    }

    // setPermission: 调整成员权限，并向城市用户组广播权限变化。
    private static MemberActionResult setPermission(ServerLevel level, ServerPlayer operator, CityData city, UUID targetId, String targetName, CityPermissionLevel permissionLevel) {
        Optional<CityMemberData> targetMember = city.member(targetId);
        if (targetMember.isEmpty() || permissionLevel == CityPermissionLevel.MAYOR) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        CityPermissionLevel oldPermission = targetMember.get().permissionLevel();
        boolean changed = CityService.setPlayerPermission(level, city.cityId(), operator.getUUID(), targetId, permissionLevel);
        if (!changed) {
            return MemberActionResult.failed(Component.translatable("message.simukraft.city_core.member_action_failed"));
        }
        String displayName = displayName(targetMember.get(), targetName);
        Component message;
        if (oldPermission != CityPermissionLevel.OFFICIAL && permissionLevel == CityPermissionLevel.OFFICIAL) {
            message = Component.translatable("message.simukraft.city_core.official_added", displayName, city.cityName());
        } else if (oldPermission == CityPermissionLevel.OFFICIAL && permissionLevel == CityPermissionLevel.CITIZEN) {
            message = Component.translatable("message.simukraft.city_core.official_removed", displayName, city.cityName());
        } else {
            message = Component.translatable("message.simukraft.city_core.member_permission_changed", displayName, city.cityName(), permissionName(permissionLevel));
        }
        return MemberActionResult.succeeded(message, CityUserGroupService.onlinePlayers(level, CityUserGroup.members(city.cityId())));
    }

    // displayName: 优先使用服务端已知成员名，缺失时使用包内名称兜底。
    private static String displayName(CityMemberData member, String fallbackName) {
        String memberName = member != null ? member.playerName() : "";
        if (memberName != null && !memberName.isBlank()) {
            return memberName;
        }
        return fallbackName != null && !fallbackName.isBlank() ? fallbackName : "Unknown";
    }

    // permissionName: 生成权限等级的本地化显示名。
    private static Component permissionName(CityPermissionLevel permissionLevel) {
        return Component.translatable("permission.simukraft." + permissionLevel.name().toLowerCase(Locale.ROOT));
    }

    private record MemberActionResult(boolean success, Component message, Collection<ServerPlayer> recipients) {
        // succeeded: 记录成功消息和目标用户组快照。
        private static MemberActionResult succeeded(Component message, Collection<ServerPlayer> recipients) {
            return new MemberActionResult(true, message, recipients);
        }

        // failed: 记录仅回给操作者的失败消息。
        private static MemberActionResult failed(Component message) {
            return new MemberActionResult(false, message, List.of());
        }
    }

    public enum Action {
        ADD,
        REMOVE,
        SET_PERMISSION;

        public static Action fromName(String name) {
            if (name == null || name.isBlank()) {
                return ADD;
            }
            for (Action action : values()) {
                if (action.name().equalsIgnoreCase(name)) {
                    return action;
                }
            }
            return ADD;
        }
    }
}
