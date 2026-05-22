package client.cn.kafei.simukraft.client.citizen;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import common.cn.kafei.simukraft.network.citizen.info.CitizenInfoResponsePacket;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("null")
public final class CitizenScreenOpener {
    private static final ResourceLocation CITIZEN_THEME = ResourceLocation.fromNamespaceAndPath("ldlib2", "lss/gdp.lss");

    private CitizenScreenOpener() {
    }

    public static void open(CitizenInfoResponsePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(() -> minecraft.setScreen(new com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen(createUi(packet), Component.empty())));
    }

    private static ModularUI createUi(CitizenInfoResponsePacket packet) {
        EditorWindow root = new EditorWindow(() -> new CitizenInfoEditor(packet));
        return new ModularUI(UI.of(root, CITIZEN_THEME))
                .shouldCloseOnEsc(true)
                .shouldCloseOnKeyInventory(false);
    }

    private static UIElement workspace(CitizenInfoResponsePacket packet, CitizenInfoEditor editor) {
        UIElement body = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(8);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
        });
        body.addChild(editor.rightTabs);
        return body;
    }

    private static ScrollerView scrollable(UIElement child) {
        ScrollerView scroller = new ScrollerView();
        scroller.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL));
        scroller.layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.flex(1);
        });
        scroller.addScrollViewChild(child);
        return scroller;
    }

    private static UIElement identityPanel(CitizenInfoResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.title", packet.name())));
        panel.addChild(contentSpacerSmall());
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.name", packet.name())));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.gender", Component.translatable("gui.npc.gender." + genderKey(packet.gender())).getString())));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.age", packet.age(), packet.lifespan())));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.health", healthText(packet))));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.hunger", hungerText(packet.hunger()))));
        return panel;
    }

    private static UIElement workPanel(CitizenInfoResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.menu.work")));
        panel.addChild(contentSpacerSmall());
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.work_status", workStatusText(packet))));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.job", jobText(packet))));
        return panel;
    }

    private static UIElement residencePanel(CitizenInfoResponsePacket packet) {
        UIElement panel = basePanel();
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.menu.residence")));
        panel.addChild(contentSpacerSmall());
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.city", blankFallback(packet.cityName()))));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.home", blankFallback(packet.homeName()))));
        panel.addChild(line(Component.translatable("screen.simukraft.citizen_info.workplace", blankFallback(packet.workplaceName()))));
        return panel;
    }

    private static UIElement basePanel() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(10);
            layout.gapAll(5);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.STRETCH);
        });
    }

    private static UIElement contentSpacerSmall() {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(6);
        });
    }

    private static UIElement line(Component component) {
        UIElement wrapper = new UIElement();
        wrapper.layout(layout -> {
            layout.height(13);
            layout.widthPercent(100);
        });
        wrapper.style(style -> style.backgroundTexture(new TextTexture(component.getString())
                .setType(TextTexture.TextType.LEFT)
                .setWidth(200)));
        return wrapper;
    }

    private static TextTexture windowTitleTexture(CitizenInfoResponsePacket packet) {
        return new TextTexture(Component.translatable("screen.simukraft.citizen_info.title", packet.name()).getString())
                .setType(TextTexture.TextType.LEFT)
                .setWidth(150);
    }

    private static String genderKey(String gender) {
        return "female".equalsIgnoreCase(gender) ? "female" : "male";
    }

    private static String blankFallback(String value) {
        return value == null || value.isBlank() ? Component.translatable("screen.simukraft.citizen_info.none").getString() : value;
    }

    private static String workStatusText(CitizenInfoResponsePacket packet) {
        return Component.translatable(packet.workStatus()).getString();
    }

    private static String jobText(CitizenInfoResponsePacket packet) {
        return Component.translatable(common.cn.kafei.simukraft.job.CityJobType.fromName(packet.jobType()).translationKey()).getString();
    }

    private static String healthText(CitizenInfoResponsePacket packet) {
        String health = String.format(Locale.ROOT, "%.1f/20.0", packet.health());
        return packet.sick() ? health + " " + Component.translatable("gui.npc_interaction.sick").getString() : health + " " + Component.translatable("gui.npc_interaction.healthy").getString();
    }

    private static String hungerText(double hunger) {
        String key;
        if (hunger <= 4.0D) {
            key = "gui.npc.hunger.level.starving";
        } else if (hunger <= 8.0D) {
            key = "gui.npc.hunger.level.very_hungry";
        } else if (hunger <= 14.0D) {
            key = "gui.npc.hunger.level.bit_hungry";
        } else {
            key = "gui.npc.hunger.level.full";
        }
        return String.format(Locale.ROOT, "%.1f/20.0 %s", hunger, Component.translatable(key).getString());
    }

    private static final class CitizenInfoEditor extends Editor {
        private final CitizenInfoResponsePacket packet;
        private final ViewContainer rightTabs = new ViewContainer();
        private final Map<String, View> openedTabs = new ConcurrentHashMap<>();

        private CitizenInfoEditor(CitizenInfoResponsePacket packet) {
            this.packet = packet;
            icon.setDisplay(false);
            menuContainer.setDisplay(false);
            topPlaceholder.layout(layout -> {
                layout.flex(1);
                layout.paddingLeft(8);
                layout.paddingTop(0);
            }).style(style -> style.backgroundTexture(windowTitleTexture(packet)));
            rootWindow.setViewContainer(new ViewContainer());
            rootWindow.getLeftTop().tabView.tabHeaderContainer.setDisplay(false);
            rightTabs.layout(layout -> {
                layout.flex(1);
                layout.heightPercent(100);
                layout.widthPercent(100);
            });
            View workspaceView = new View("screen.simukraft.citizen_info.title", IGuiTexture.EMPTY);
            workspaceView.addChild(workspace(packet, this));
            rootWindow.getLeftTop().addView(workspaceView);
            openDefaultTabs();
        }

        private void openDefaultTabs() {
            openTab("identity", "screen.simukraft.citizen_info.menu.identity", scrollable(identityPanel(packet)));
            openTab("work", "screen.simukraft.citizen_info.menu.work", scrollable(workPanel(packet)));
            openTab("residence", "screen.simukraft.citizen_info.menu.residence", scrollable(residencePanel(packet)));
            View identity = openedTabs.get("identity");
            if (identity != null) {
                rightTabs.selectView(identity);
            }
        }

        private void openTab(String id, String titleKey, UIElement content) {
            View existing = openedTabs.get(id);
            if (existing != null && rightTabs.hasView(existing)) {
                rightTabs.selectView(existing);
                return;
            }
            View view = new StaticTabView(titleKey);
            view.addChild(content);
            openedTabs.put(id, view);
            rightTabs.addView(view);
        }

        @Override
        protected @Nonnull Editor createNewEditorInstance() {
            return new CitizenInfoEditor(packet);
        }

        @Override
        protected void initMenus() {
        }

        @Override
        protected void onPrepareInspectorView() {
        }

        @Override
        protected void onPrepareHistoryView() {
        }

        @Override
        protected void onPrepareResourceView() {
        }
    }

    private static final class StaticTabView extends View {
        private StaticTabView(String name) {
            super(name, IGuiTexture.EMPTY);
            setCanRemove(false);
        }

        @Override
        public Tab createTab() {
            Tab tab = new Tab();
            tab.setText(Component.translatable(getName()));
            return tab;
        }
    }
}
