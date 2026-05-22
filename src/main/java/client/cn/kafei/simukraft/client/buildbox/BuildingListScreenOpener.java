package client.cn.kafei.simukraft.client.buildbox;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;
import common.cn.kafei.simukraft.building.BuildingStructure;
import common.cn.kafei.simukraft.building.BuildingStructureService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
@EventBusSubscriber(value = Dist.CLIENT)
public final class BuildingListScreenOpener {
    private static final ResourceLocation GDP_THEME = ResourceLocation.fromNamespaceAndPath("ldlib2", "lss/gdp.lss");
    private static final int BUILDINGS_PER_PAGE = 6;
    private static final int COLUMNS = 3;
    private static final int CARD_GAP = 10;
    private static final int TOP_MARGIN = 80;
    private static final int BOTTOM_MARGIN = 50;
    private static final int PREFERRED_CARD_WIDTH = 180;
    private static final int MIN_CARD_WIDTH = 160;
    private static final int PREFERRED_CARD_HEIGHT = 80;
    private static final int MIN_CARD_HEIGHT = 76;
    private static int currentPage;
    private static String currentCategory;
    private static BlockPos currentBuildBoxPos;
    private static String selectedBuildingFileName;
    private static PendingPreview pendingPreview;

    private BuildingListScreenOpener() {
    }

    public static void open(String category, BlockPos buildBoxPos) {
        currentCategory = category;
        currentBuildBoxPos = buildBoxPos;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(category, buildBoxPos), Component.empty())));
    }

    private static void reopen() {
        if (currentCategory != null && currentBuildBoxPos != null) {
            open(currentCategory, currentBuildBoxPos);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (pendingPreview == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            pendingPreview = null;
            return;
        }
        if (minecraft.screen == null || minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen) {
            minecraft.setScreen(new BuildingConfirmScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(currentCategory, currentBuildBoxPos), Component.empty()), pendingPreview.building(), pendingPreview.buildBoxPos(), pendingPreview.structure()));
            pendingPreview = null;
        }
    }

    private static ModularUI createUi(String category, BlockPos buildBoxPos) {
        int screenWidth = Math.max(320, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int screenHeight = Math.max(240, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        List<BuildingCacheService.BuildingMeta> buildings = BuildingCacheService.getBuildings(category);
        int pageCount = totalPages(buildings);
        currentPage = Math.max(0, Math.min(currentPage, pageCount - 1));

        UIElement root = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
        }).style(style -> style.backgroundTexture(new ColorRectTexture(0x80000000)));

        Button backButton = new Button();
        backButton.setText(Component.translatable("gui.button.back"));
        backButton.setOnClick(event -> BuildBoxScreenOpener.open(buildBoxPos));
        backButton.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(5);
            layout.top(5);
            layout.width(45);
            layout.height(20);
        });
        root.addChild(backButton);

        root.addChild(textElement(Component.translatable("gui.building_list.title", categoryName(category)), screenWidth, 0xFFFFFF, TextTexture.TextType.NORMAL).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(25);
            layout.width(screenWidth);
            layout.height(14);
        }));

        Component status = buildings.isEmpty()
                ? Component.translatable("gui.building_list.empty_dir", BuildingCacheService.categoryDirectory(category).toString())
                : Component.translatable("gui.building_list.status", buildings.size());
        int statusColor = buildings.isEmpty() ? 0xFF5555 : 0x55FF55;
        root.addChild(textElement(status, screenWidth, statusColor, TextTexture.TextType.NORMAL).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(45);
            layout.width(screenWidth);
            layout.height(14);
        }));

        List<BuildingCacheService.BuildingMeta> pageBuildings = pageBuildings(buildings, currentPage);
        int availableWidth = screenWidth - 40;
        int availableHeight = screenHeight - TOP_MARGIN - BOTTOM_MARGIN - 50;
        int maxCardWidth = (availableWidth - (COLUMNS - 1) * CARD_GAP) / COLUMNS;
        int rows = (int) Math.ceil((double) Math.max(1, pageBuildings.size()) / COLUMNS);
        int actualCardWidth = Math.max(MIN_CARD_WIDTH, Math.min(PREFERRED_CARD_WIDTH, maxCardWidth));
        int maxCardHeight = rows > 0 ? (availableHeight - (rows - 1) * CARD_GAP) / rows : PREFERRED_CARD_HEIGHT;
        int actualCardHeight = Math.max(MIN_CARD_HEIGHT, Math.min(PREFERRED_CARD_HEIGHT, maxCardHeight));
        int totalWidth = COLUMNS * actualCardWidth + (COLUMNS - 1) * CARD_GAP;
        int totalHeight = rows * actualCardHeight + (rows - 1) * CARD_GAP;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = Math.max(TOP_MARGIN, TOP_MARGIN + (availableHeight - totalHeight) / 2);

        for (int i = 0; i < pageBuildings.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int x = startX + col * (actualCardWidth + CARD_GAP);
            int y = startY + row * (actualCardHeight + CARD_GAP);
            root.addChild(createBuildingCard(pageBuildings.get(i), x, y, actualCardWidth, actualCardHeight, categoryColor(category)));
        }

        if (pageCount > 1) {
            root.addChild(textElement(Component.translatable("gui.pagination.info", currentPage + 1, pageCount), screenWidth, 0xAAAAAA, TextTexture.TextType.NORMAL).layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(screenHeight - 40);
                layout.width(screenWidth);
                layout.height(12);
            }));
        }

        if (selectedBuildingFileName != null) {
            root.addChild(textElement(Component.translatable("gui.building_list.selected", selectedBuildingFileName), screenWidth, 0xFFFF00, TextTexture.TextType.NORMAL).layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(screenHeight - 60);
                layout.width(screenWidth);
                layout.height(12);
            }));
        }

        Button prevButton = new Button();
        prevButton.setText(Component.translatable("gui.pagination.previous"));
        prevButton.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(screenWidth / 2 - 100);
            layout.top(screenHeight - 30);
            layout.width(80);
            layout.height(20);
        });
        if (currentPage > 0) {
            prevButton.setOnClick(event -> {
                currentPage--;
                reopen();
            });
        } else {
            prevButton.style(style -> style.backgroundTexture(new ColorRectTexture(0x66000000)));
        }
        root.addChild(prevButton);

        Button nextButton = new Button();
        nextButton.setText(Component.translatable("gui.pagination.next"));
        nextButton.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(screenWidth / 2 + 20);
            layout.top(screenHeight - 30);
            layout.width(80);
            layout.height(20);
        });
        if (currentPage < pageCount - 1) {
            nextButton.setOnClick(event -> {
                currentPage++;
                reopen();
            });
        } else {
            nextButton.style(style -> style.backgroundTexture(new ColorRectTexture(0x66000000)));
        }
        root.addChild(nextButton);

        return new ModularUI(UI.of(root, GDP_THEME)).shouldCloseOnEsc(true).shouldCloseOnKeyInventory(false);
    }

    private static UIElement createBuildingCard(BuildingCacheService.BuildingMeta building, int x, int y, int width, int height, int accentColor) {
        boolean selected = building.structureFileName().equals(selectedBuildingFileName);
        UIElement card = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(width);
            layout.height(height);
            layout.paddingAll(6);
        }).style(style -> style.backgroundTexture(selected
                ? new GuiTextureGroup(
                Sprites.BORDER1_RT1,
                new ColorRectTexture(0x66000000),
                new ColorRectTexture(0x66000000)
        )
                : new GuiTextureGroup(
                Sprites.BORDER1_RT1,
                new ColorRectTexture(0x66000000)
        )));
        card.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, event -> {
            if (event.button != 0) {
                return;
            }
            selectedBuildingFileName = building.structureFileName();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                var loadedStructure = BuildingStructureLoader.load(building);
                Component message = loadedStructure.isPresent()
                        ? Component.translatable("message.simukraft.building_list.loaded", building.name(), loadedStructure.get().format().name() + " " + loadedStructure.get().size().toShortString())
                        : Component.translatable("message.simukraft.building_list.load_failed", building.name(), building.structureFileName());
                minecraft.player.displayClientMessage(message, true);
                Optional<BuildingStructure> structure = BuildingStructureService.loadStructure(building);
                if (structure.isPresent()) {
                    pendingPreview = new PendingPreview(building, currentBuildBoxPos, structure.get());
                    minecraft.setScreen(null);
                } else {
                    minecraft.player.displayClientMessage(Component.translatable("message.simukraft.building_preview.open_failed", building.name()), false);
                }
            }
            event.stopPropagation();
        });

        card.addChild(new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(8);
            layout.top(3);
            layout.width(width - 16);
            layout.height(2);
        }).style(style -> style.backgroundTexture(new GuiTextureGroup(
                new ColorRectTexture(0x66000000),
                new ColorRectTexture(accentColor)
        ))));

        card.addChild(textElement(Component.literal(trim(building.name(), Math.max(10, width / 10))), width, 0xFFFFFF, TextTexture.TextType.NORMAL).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(8);
            layout.width(width);
            layout.height(12);
        }));

        int infoWidth = width - 8;
        int infoY = 24;
        card.addChild(infoLine(Component.translatable("gui.building.size", building.size()), infoWidth, 0xAAAAAA).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(4);
            layout.top(infoY);
            layout.width(infoWidth);
            layout.height(10);
        }));
        card.addChild(infoLine(Component.translatable("gui.building.price", building.amount()), infoWidth, 0xAAAAAA).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(4);
            layout.top(infoY + 12);
            layout.width(infoWidth);
            layout.height(10);
        }));
        card.addChild(infoLine(Component.translatable("gui.building.author", building.author()), infoWidth, 0xAAAAAA).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(4);
            layout.top(infoY + 24);
            layout.width(infoWidth);
            layout.height(10);
        }));

        return card;
    }

    private static UIElement infoLine(Component text, int width, int color) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(TextTexture.TextType.LEFT)
                .setColor(color)
                .setDropShadow(true)));
        return element;
    }

    private static UIElement textElement(Component text, int width, int color, TextTexture.TextType type) {
        UIElement element = new UIElement();
        element.style(style -> style.backgroundTexture(new TextTexture(text.getString())
                .setWidth(width)
                .setType(type)
                .setColor(color)
                .setDropShadow(true)));
        return element;
    }

    private static List<BuildingCacheService.BuildingMeta> pageBuildings(List<BuildingCacheService.BuildingMeta> buildings, int page) {
        int start = Math.max(0, page) * BUILDINGS_PER_PAGE;
        int end = Math.min(start + BUILDINGS_PER_PAGE, buildings.size());
        if (start >= end) {
            return new ArrayList<>();
        }
        return new ArrayList<>(buildings.subList(start, end));
    }

    private static int totalPages(List<BuildingCacheService.BuildingMeta> buildings) {
        return Math.max(1, (int) Math.ceil(Math.max(1, buildings.size()) / 6.0D));
    }

    private static String categoryName(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "residential" -> Component.translatable("gui.category.residential").getString();
            case "commercial" -> Component.translatable("gui.category.commercial").getString();
            case "industry" -> Component.translatable("gui.category.industrial").getString();
            case "public" -> Component.translatable("gui.category.public").getString();
            case "other" -> Component.translatable("gui.category.other").getString();
            default -> category;
        };
    }

    private static int categoryColor(String category) {
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "residential" -> 0xFF90EE90;
            case "commercial" -> 0xFFADD8E6;
            case "industry" -> 0xFFC8A260;
            case "public" -> 0xFFB39DDB;
            case "other" -> 0xFFFFFFFF;
            default -> 0xFFC8A260;
        };
    }

    private static String trim(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars - 2)) + "..";
    }

    private record PendingPreview(BuildingCacheService.BuildingMeta building, BlockPos buildBoxPos, BuildingStructure structure) {
    }
}
