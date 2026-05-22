package common.cn.kafei.simukraft.command;

import com.mojang.brigadier.CommandDispatcher;
import common.cn.kafei.simukraft.network.building.BuildingCacheReloadPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public final class SimuKraftCommand {
    private SimuKraftCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("simukraft")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
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
}
