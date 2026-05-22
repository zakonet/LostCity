package client.cn.kafei.simukraft.client.buildbox;

import common.cn.kafei.simukraft.building.BuildingStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
public final class BuildingConfirmScreen extends Screen {
    private final Screen parent;
    private final BuildingCacheService.BuildingMeta building;
    private final BlockPos buildBoxPos;
    private final BuildingStructure structure;

    public BuildingConfirmScreen(Screen parent, BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
        super(Component.translatable("gui.building_preview.title"));
        this.parent = parent;
        this.building = building;
        this.buildBoxPos = buildBoxPos;
        this.structure = structure;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int centerY = height / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.building_confirm.preview"), button -> {
            Minecraft minecraft = this.minecraft;
            if (minecraft != null) {
                minecraft.setScreen(new BuildingPreviewScreen(this, building, buildBoxPos, structure));
            }
        }).bounds(centerX - 90, centerY + 40, 80, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.button.back"), button -> {
            Minecraft minecraft = this.minecraft;
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).bounds(centerX + 10, centerY + 40, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int y = height / 2 - 60;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.title", building.name()), centerX, y, 0xFFFFFF);
        y += 18;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.size", building.size()), centerX, y, 0xAAAAAA);
        y += 12;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.price", building.amount()), centerX, y, 0xAAAAAA);
        y += 12;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.author", building.author()), centerX, y, 0xAAAAAA);
        y += 16;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.desc", building.structureFileName()), centerX, y, 0x55FFFF);
        y += 18;
        guiGraphics.drawCenteredString(font, Component.translatable("gui.building_confirm.hint"), centerX, y, 0xFFFF55);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
