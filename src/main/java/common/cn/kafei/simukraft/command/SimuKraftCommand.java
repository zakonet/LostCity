package common.cn.kafei.simukraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.network.building.BuildingCacheReloadPacket;
import common.cn.kafei.simukraft.network.hud.HudSyncService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Optional;

@SuppressWarnings("null")
public final class SimuKraftCommand {
    private SimuKraftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simukraft")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("city")
                        .then(Commands.literal("funds")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01D))
                                                .executes(context -> addFundsToSelfCity(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "amount")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> addFundsToPlayerCity(
                                                                context.getSource(),
                                                                DoubleArgumentType.getDouble(context, "amount"),
                                                                EntityArgument.getPlayer(context, "player")))))))));
    }

    private static int reload(CommandSourceStack source) {
        int count = 0;
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, new BuildingCacheReloadPacket());
            count++;
        }
        final int reloadedPlayerCount = count;
        source.sendSuccess(() -> Component.translatable("message.simukraft.reload.success", reloadedPlayerCount), true);
        return 1;
    }

    // 给命令执行者所属城市加款；控制台必须显式指定玩家。
    private static int addFundsToSelfCity(CommandSourceStack source, double amount) {
        ServerPlayer sourcePlayer = source.getPlayer();
        if (sourcePlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        return addFundsToPlayerCity(source, amount, sourcePlayer);
    }

    // 给指定在线玩家所属城市加款，并记录财政流水。
    private static int addFundsToPlayerCity(CommandSourceStack source, double amount, ServerPlayer targetPlayer) {
        if (targetPlayer == null) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.player_required"));
            return 0;
        }
        double normalizedAmount = EconomyService.normalizeAmount(amount);
        if (normalizedAmount <= 0.0D) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.invalid_amount"));
            return 0;
        }
        ServerLevel level = targetPlayer.serverLevel();
        Optional<CityData> city = CityService.findPlayerCity(level, targetPlayer.getUUID());
        if (city.isEmpty()) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.no_city", targetPlayer.getGameProfile().getName()));
            return 0;
        }
        ServerPlayer actor = source.getPlayer();
        if (!EconomyService.depositCityFunds(level, city.get().cityId(), actor, normalizedAmount, "command_add_funds")) {
            source.sendFailure(Component.translatable("message.simukraft.command.city_funds.failed"));
            return 0;
        }
        syncCityMembersHud(level, city.get());
        double balance = EconomyService.getCityBalance(level, city.get().cityId());
        String amountText = String.format(Locale.ROOT, "%.2f", normalizedAmount);
        String balanceText = String.format(Locale.ROOT, "%.2f", balance);
        source.sendSuccess(() -> Component.translatable(
                "message.simukraft.command.city_funds.added",
                amountText,
                city.get().cityName(),
                targetPlayer.getGameProfile().getName(),
                balanceText
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    // 同一城市的在线成员都需要马上看到新余额。
    private static void syncCityMembersHud(ServerLevel level, CityData city) {
        if (level == null || city == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (city.member(player.getUUID()).isPresent()) {
                HudSyncService.syncToPlayer(player, true);
            }
        }
    }
}
