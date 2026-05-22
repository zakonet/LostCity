package client.cn.kafei.simukraft.client.buildbox;

import common.cn.kafei.simukraft.network.npc.hire.NpcHireFirePacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateRequestPacket;
import common.cn.kafei.simukraft.network.npc.state.EmploymentStateResponsePacket;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

@SuppressWarnings("null")
public class BuildBoxScreenOpener {
    private static final ResourceLocation GDP_THEME = ResourceLocation.fromNamespaceAndPath("ldlib2", "lss/gdp.lss");
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 2;
    private static final int CATEGORY_BUTTON_WIDTH = 110;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int CATEGORY_BUTTON_SPACING = 3;

    public static void open(BlockPos buildBoxPos) {
        PacketDistributor.sendToServer(new EmploymentStateRequestPacket(buildBoxPos, "build_box"));
    }

    public static void applyEmploymentState(EmploymentStateResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(packet.sourcePos(), packet), Component.empty())));
    }

    private static ModularUI createUi(BlockPos buildBoxPos, EmploymentStateResponsePacket state) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(0xC8000000)));

        Button doneButton = new Button();
        doneButton.setText(Component.translatable("gui.button.done"));
        doneButton.setOnClick(event -> close());
        doneButton.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(10);
            layout.top(10);
            layout.width(50);
            layout.height(24);
        });
        root.addChild(doneButton);

        UIElement copyrightLabel = textElement(Component.translatable("gui.copyright"), 200, TextTexture.TextType.RIGHT, 0x666666);
        copyrightLabel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(10);
            layout.top(10);
            layout.width(200);
            layout.height(16);
        });
        root.addChild(copyrightLabel);

        UIElement centerContainer = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(0);
            layout.marginTop(-60);
        });

        centerContainer.addChild(textElement(Component.translatable("gui.build_box.title"), 360, TextTexture.TextType.NORMAL, 0xFFFFFF).layout(layout -> {
            layout.width(360);
            layout.height(20);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable(state.statusKey()), 360, TextTexture.TextType.NORMAL, 0xADD8E6).layout(layout -> {
            layout.width(360);
            layout.height(16);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable("gui.build_box.instruction"), 360, TextTexture.TextType.NORMAL, 0xF5F5A0).layout(layout -> {
            layout.width(360);
            layout.height(16);
            layout.marginBottom(44);
        }));

        UIElement row1 = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(BUTTON_SPACING);
            layout.marginBottom(BUTTON_SPACING);
        });
        row1.addChild(createButtonSlot("gui.build_box.hire_builder", () -> handleHireBuilder(buildBoxPos), !state.hasAnyEmployee()));
        row1.addChild(createButtonSlot("gui.build_box.select_building", () -> handleSelectBuilding(buildBoxPos), state.builderCitizenId() != null));
        row1.addChild(createButtonSlot("gui.build_box.fire_employee", () -> handleFireEmployee(buildBoxPos, state), state.hasAnyEmployee()));
        centerContainer.addChild(row1);

        UIElement row2 = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(BUTTON_SPACING);
        });
        row2.addChild(createButtonSlot("gui.build_box.hire_planner", () -> handleHirePlanner(buildBoxPos), !state.hasAnyEmployee()));
        row2.addChild(createButtonSlot("gui.build_box.plan_area", () -> handlePlanArea(buildBoxPos), state.plannerCitizenId() != null));
        row2.addChild(createButtonSlot("gui.build_box.employee_info", BuildBoxScreenOpener::handleEmployeeInfo, true));
        centerContainer.addChild(row2);

        root.addChild(centerContainer);

        return new ModularUI(UI.of(root, GDP_THEME))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement textElement(Component text, int width, TextTexture.TextType type, int color) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(type)
                .setColor(color)
                .setDropShadow(true)));
        return element;
    }

    private static UIElement createButtonSlot(String translationKey, Runnable action, boolean active) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
        });
        Button button = new Button();
        button.setText(Component.translatable(translationKey));
        if (active) {
            button.setOnClick(event -> action.run());
        }
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(BUTTON_WIDTH);
            layout.height(BUTTON_HEIGHT);
        });
        slot.addChild(button);
        if (!active) {
            slot.addChild(new UIElement().layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(1);
                layout.top(1);
                layout.width(BUTTON_WIDTH - 2);
                layout.height(BUTTON_HEIGHT - 2);
            }).style(style -> style.backgroundTexture(new ColorRectTexture(0x88202028))));
        }
        return slot;
    }

    private static UIElement createCategoryButtonSlot(String translationKey, BlockPos buildBoxPos) {
        UIElement slot = new UIElement().layout(layout -> {
            layout.width(CATEGORY_BUTTON_WIDTH);
            layout.height(CATEGORY_BUTTON_HEIGHT);
        });
        Button button = new Button();
        button.setText(Component.translatable(translationKey));
        button.setOnClick(event -> handleBuildingCategory(buildBoxPos, translationKey));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(CATEGORY_BUTTON_WIDTH);
            layout.height(CATEGORY_BUTTON_HEIGHT);
        });
        slot.addChild(button);
        return slot;
    }

    private static void close() {
        Minecraft.getInstance().setScreen(null);
    }

    private static void handleHireBuilder(BlockPos pos) {
        client.cn.kafei.simukraft.client.hire.NpcHireScreen.request(pos, "build_box", "builder");
    }

    private static void handleHirePlanner(BlockPos pos) {
        client.cn.kafei.simukraft.client.hire.NpcHireScreen.request(pos, "build_box", "planner");
    }

    private static void handleSelectBuilding(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createSelectBuildingUi(pos), Component.empty())));
    }

    private static ModularUI createSelectBuildingUi(BlockPos buildBoxPos) {
        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(0xC8000000)));

        Button doneButton = new Button();
        doneButton.setText(Component.translatable("gui.button.done"));
        doneButton.setOnClick(event -> close());
        doneButton.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(10);
            layout.top(10);
            layout.width(50);
            layout.height(24);
        });
        root.addChild(doneButton);

        UIElement copyrightLabel = textElement(Component.translatable("gui.copyright"), 200, TextTexture.TextType.RIGHT, 0x666666);
        copyrightLabel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.right(10);
            layout.top(10);
            layout.width(200);
            layout.height(16);
        });
        root.addChild(copyrightLabel);

        UIElement centerContainer = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(0);
            layout.marginTop(-30);
        });

        centerContainer.addChild(textElement(Component.translatable("gui.select_building.title"), 480, TextTexture.TextType.NORMAL, 0xFFFFFF).layout(layout -> {
            layout.width(480);
            layout.height(20);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable("gui.select_building.status_working"), 480, TextTexture.TextType.NORMAL, 0xADD8E6).layout(layout -> {
            layout.width(480);
            layout.height(16);
            layout.marginBottom(4);
        }));
        centerContainer.addChild(textElement(Component.translatable("gui.select_building.instruction"), 480, TextTexture.TextType.NORMAL, 0xF5F5A0).layout(layout -> {
            layout.width(480);
            layout.height(16);
            layout.marginBottom(56);
        }));

        UIElement row1 = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.width(CATEGORY_BUTTON_WIDTH * 4 + CATEGORY_BUTTON_SPACING * 3);
            layout.gapAll(CATEGORY_BUTTON_SPACING);
            layout.marginBottom(CATEGORY_BUTTON_SPACING);
        });
        row1.addChild(createCategoryButtonSlot("gui.category.residential", buildBoxPos));
        row1.addChild(createCategoryButtonSlot("gui.category.commercial", buildBoxPos));
        row1.addChild(createCategoryButtonSlot("gui.category.industrial", buildBoxPos));
        row1.addChild(createCategoryButtonSlot("gui.category.other", buildBoxPos));
        centerContainer.addChild(row1);

        UIElement row2 = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.width(CATEGORY_BUTTON_WIDTH * 4 + CATEGORY_BUTTON_SPACING * 3);
            layout.gapAll(CATEGORY_BUTTON_SPACING);
        });
        row2.addChild(createCategoryButtonSlot("gui.category.public", buildBoxPos));
        centerContainer.addChild(row2);

        root.addChild(centerContainer);

        return new ModularUI(UI.of(root, GDP_THEME))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static void handlePlanArea(BlockPos pos) {
    }

    private static void handleBuildingCategory(BlockPos buildBoxPos, String translationKey) {
        String category = switch (translationKey) {
            case "gui.category.residential" -> "residential";
            case "gui.category.commercial" -> "commercial";
            case "gui.category.industrial" -> "industry";
            case "gui.category.public" -> "public";
            case "gui.category.other" -> "other";
            default -> "other";
        };
        BuildingListScreenOpener.open(category, buildBoxPos);
    }

    private static void handleEmployeeInfo() {
    }

    private static void handleFireEmployee(BlockPos pos, EmploymentStateResponsePacket state) {
        if (state.builderCitizenId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(pos, "build_box", "builder", state.builderCitizenId()));
            close();
            return;
        }
        if (state.plannerCitizenId() != null) {
            PacketDistributor.sendToServer(new NpcHireFirePacket(pos, "build_box", "planner", state.plannerCitizenId()));
            close();
        }
    }
}
