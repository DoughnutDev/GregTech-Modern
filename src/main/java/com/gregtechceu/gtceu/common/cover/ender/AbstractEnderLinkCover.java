package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.ColorBlockWidget;
import com.gregtechceu.gtceu.api.gui.widget.ConfirmTextInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualTank;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.machine.owner.IMachineOwner;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractEnderLinkCover<T extends VirtualEntry> extends CoverBehavior
                                            implements IUICover, IControllable {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(AbstractEnderLinkCover.class,
            CoverBehavior.MANAGED_FIELD_HOLDER);
    public static final Pattern COLOR_INPUT_PATTERN = Pattern.compile("[0-9a-fA-F]*");

    protected final ConditionalSubscriptionHandler subscriptionHandler;

    @Persisted
    @DescSynced
    protected String colorStr = VirtualEntry.DEFAULT_COLOR;
    @Getter
    @Persisted
    @DescSynced
    protected Permissions permission = Permissions.PUBLIC;
    @Persisted
    @Getter
    protected boolean isWorkingEnabled = true;
    @Getter
    @Persisted
    @DescSynced
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected IO io = IO.OUT;
    protected VirtualEntryWidget virtualEntryWidget;
    @DescSynced
    boolean isAnyChanged = false;
    @Persisted
    @DescSynced
    private IMachineOwner owner;

    public AbstractEnderLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, this::isSubscriptionActive);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public abstract boolean canAttach();

    @Override
    public void onAttached(@NotNull ItemStack itemStack, @NotNull ServerPlayer player) {
        super.onAttached(itemStack, player);
        if (coverHolder instanceof MachineCoverContainer mcc) {
            var owner = mcc.getMachine().getHolder().getOwner();
            if (owner != null) this.owner = owner;
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        subscriptionHandler.unsubscribe();
        if (!isRemote()) {
            VirtualEnderRegistry.getInstance()
                    .deleteEntryIf(getOwner(), getEntryType(), getChannelName(), VirtualEntry::canRemove);
        }
    }

    @Override
    public void onUIClosed() {
        virtualEntryWidget = null;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            subscriptionHandler.updateSubscription();
        }
    }

    @Override
    public Widget createUIWidget() {
        virtualEntryWidget = new VirtualEntryWidget(this);
        return virtualEntryWidget;
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            this.io = io;
            subscriptionHandler.updateSubscription();
        }
    }

    public UUID getOwner() {
        return permission == Permissions.PRIVATE ? owner.getPlayerUUID() : null;
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled();
    }

    protected abstract String identifier();

    protected abstract VirtualEntry getEntry();

    protected abstract void setEntry(VirtualEntry entry);

    protected final String getChannelName() {
        return identifier() + this.colorStr;
    }

    protected void setChannelName(String name) {
        if (isRemote()) return;
        VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(),
                VirtualEntry::canRemove);
        this.colorStr = VirtualEntry.formatColorString(name);
        setVirtualEntry();
    }

    protected final String getChannelName(VirtualEntry entry) {
        return identifier() + entry.getColorStr();
    }

    protected void setPermission(Permissions permission) {
        if (isRemote()) return;
        VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(),
                VirtualEntry::canRemove);
        this.permission = permission;
        setVirtualEntry();
    }

    protected void setVirtualEntry() {
        setEntry(VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(), getChannelName()));
        getEntry().setColor(this.colorStr);
        this.isAnyChanged = true;
        subscriptionHandler.updateSubscription();
    }

    protected abstract EntryTypes<T> getEntryType();

    protected void update() {
        long timer = coverHolder.getOffsetTimer();
        if (timer % 5 != 0) return;
        if (isWorkingEnabled() && !isRemote()) {
            if (!VirtualEnderRegistry.getInstance().hasEntry(getOwner(), getEntryType(), getChannelName())) {
                var entry = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(),
                        getChannelName());
                entry.setColor(this.colorStr);
            }
            var entry = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(),
                    getChannelName());
            if (!entry.equals(getEntry())) {
                setEntry(entry);
            }
            transfer();
        }

        if (isAnyChanged) {
            if (virtualEntryWidget != null) virtualEntryWidget.update();
            isAnyChanged = false;
        }
        subscriptionHandler.updateSubscription();
    }

    protected abstract void transfer();

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        subscriptionHandler.updateSubscription();
    }

    @Nullable
    protected FilterHandler<?, ?> getFilterHandler() {
        return null;
    }

    protected abstract Widget addVirtualEntryWidget(VirtualEntry entry, int x, int y, int width, int height);

    protected abstract String getUITitle();

    protected int getColorStr() {
        return VirtualEntry.parseColor(this.colorStr);
    }

    protected static class VirtualEntryWidget extends WidgetGroup {

        private static final int WIDGET_BOARD = 20;
        private static final int GROUP_WIDTH = 176;
        private static final int TOTAL_WIDTH = 156;
        private static final int BUTTON_SIZE = 16;
        private final AbstractEnderLinkCover<?> cover;
        private final MutableBoolean showChannels;
        private final WidgetGroup mainGroup;
        private final WidgetGroup mainChannelGroup;
        private final DraggableScrollableWidgetGroup channelsGroup; // client only

        VirtualEntryWidget(AbstractEnderLinkCover<?> cover) {
            super(0, 0, GROUP_WIDTH, 137);
            this.cover = cover;
            this.showChannels = new MutableBoolean(false);
            mainGroup = new WidgetGroup(0, 0, GROUP_WIDTH, 137);
            channelsGroup = new DraggableScrollableWidgetGroup(0, 20, 170, 110)
                    .setYScrollBarWidth(2).setYBarStyle(null, ColorPattern.T_WHITE.rectTexture().setRadius(1));
            mainChannelGroup = new WidgetGroup(10, 20, 156, 20);
            initWidgets();
        }

        public void update() {
            if (isRemote()) return;
            widgets.clear();
            mainGroup.widgets.clear();
            channelsGroup.widgets.clear();
            mainChannelGroup.widgets.clear();
            initWidgets();
            this.detectAndSendChanges();
        }

        private void initWidgets() {
            int currentX = 0;

            this.addWidget(new LabelWidget(10, 5, cover.getUITitle()));
            this.addWidget(createToggleButton());
            this.addWidget(mainGroup);
            this.addWidget(channelsGroup.setVisible(false));

            var toggleButtonWidget = createToggleButtonForPrivacy(currentX);
            mainChannelGroup.addWidget(toggleButtonWidget);
            currentX += WIDGET_BOARD + 2;
            mainChannelGroup.addWidget(createColorBlockWidget(currentX));
            currentX += WIDGET_BOARD + 2;
            mainChannelGroup.addWidget(createConfirmTextInputWidget(currentX));

            mainChannelGroup.addWidget(new ConfirmTextInputWidget(0, WIDGET_BOARD + 2, GROUP_WIDTH - WIDGET_BOARD,
                    WIDGET_BOARD, cover.getEntry().getDescription(), cover.getEntry()::setDescription, t -> t));

            mainGroup.addWidget(mainChannelGroup);
            mainGroup.addWidget(createWorkingEnabledButton());
            addEnumSelectorWidgets();
            mainGroup.addWidget(
                    cover.addVirtualEntryWidget(cover.getEntry(), 146, WIDGET_BOARD, WIDGET_BOARD, WIDGET_BOARD));

            if (cover.getFilterHandler() != null) {
                mainGroup.addWidget(cover.getFilterHandler().createFilterSlotUI(117, 108));
                mainGroup.addWidget(cover.getFilterHandler().createFilterConfigUI(10, 72, 156, 60));
            }
        }

        @Contract(" -> new")
        private @NotNull ToggleButtonWidget createToggleButton() {
            return (ToggleButtonWidget) new ToggleButtonWidget(156, 5, 12, 12, showChannels::getValue, cd -> {
                showChannels.setValue(!showChannels.getValue());
                mainGroup.setVisible(showChannels.isFalse());
                channelsGroup.setVisible(showChannels.isTrue());
                sendChannelList();
            }).setTexture(
                    new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                            GuiTextures.BUTTON_LIST),
                    new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                            GuiTextures.BUTTON_LIST));
        }

        @Contract("_ -> new")
        private @NotNull Widget createToggleButtonForPrivacy(int currentX) {
            return new EnumSelectorWidget<>(currentX, 0,
                    WIDGET_BOARD, WIDGET_BOARD, Permissions.values(), cover.permission, cover::setPermission);
        }

        private ColorBlockWidget createColorBlockWidget(int currentX) {
            return new ColorBlockWidget(currentX, 0, WIDGET_BOARD, WIDGET_BOARD).setColorSupplier(cover::getColorStr);
        }

        private ConfirmTextInputWidget createConfirmTextInputWidget(int currentX) {
            int GROUP_X = 10;
            int textInputWidth = (GROUP_WIDTH - GROUP_X * 2) - currentX - WIDGET_BOARD - 2;
            return new ConfirmTextInputWidget(currentX, 0, textInputWidth, WIDGET_BOARD, cover.colorStr,
                    cover::setChannelName, text -> {
                        if (text == null || !COLOR_INPUT_PATTERN.matcher(text).matches() || text.length() > 8) {
                            return VirtualTank.DEFAULT_COLOR;
                        }
                        return text;
                    });
        }

        @Contract(" -> new")
        private @NotNull ToggleButtonWidget createWorkingEnabledButton() {
            return new ToggleButtonWidget(116, 82, WIDGET_BOARD, WIDGET_BOARD, GuiTextures.BUTTON_POWER,
                    cover::isWorkingEnabled, cover::setWorkingEnabled);
        }

        private void addEnumSelectorWidgets() {
            mainGroup.addWidget(new EnumSelectorWidget<>(146, 82, WIDGET_BOARD, WIDGET_BOARD, List.of(IO.IN, IO.OUT),
                    cover.io, cover::setIo));
            mainGroup.addWidget(new EnumSelectorWidget<>(146, 107, WIDGET_BOARD, WIDGET_BOARD, ManualIOMode.VALUES,
                    cover.manualIOMode, cover::setManualIOMode)
                    .setHoverTooltips("cover.universal.manual_import_export.mode.description"));
        }

        private void addChannelWidgets(List<? extends VirtualEntry> entries) {
            channelsGroup.clearAllWidgets();
            int y = 1;
            SelectableWidgetGroup selectedWidget = null;
            for (var entry : entries.stream().sorted(Comparator.comparing(VirtualEntry::getColorStr)).toList()) {
                SelectableWidgetGroup channelWidget = createChannelWidget(entry, 10, y);
                if (cover.getChannelName(entry).equals(cover.getChannelName())) {
                    selectedWidget = channelWidget;
                }
                channelsGroup.addWidget(channelWidget);
                y += 22;
            }
            channelsGroup.setSelected(selectedWidget);
            if (selectedWidget != null) selectedWidget.onSelected();
            channelsGroup.setClientSideWidget();
        }

        private @NotNull SelectableWidgetGroup createChannelWidget(@NotNull VirtualEntry entry, int x, int y) {
            int currentX = 0;
            int MARGIN = 2;
            int availableWidth = TOTAL_WIDTH - (BUTTON_SIZE + MARGIN) * 2;

            var des = entry.getDescription();
            TextBoxWidget textBoxWidget = new TextBoxWidget(BUTTON_SIZE + MARGIN,
                    !des.isEmpty() ? 0 : 4, availableWidth, List.of(entry.getColorStr())).setCenter(true);
            SelectableWidgetGroup channelGroup = new SelectableWidgetGroup(x, y, TOTAL_WIDTH, BUTTON_SIZE)
                    .setOnSelected(group -> {
                        if (cover.getChannelName().equals(cover.getChannelName(entry))) return;
                        writeClientAction(0, buffer -> {
                            // send new channel name to server
                            String newChannelColorStr = entry.getColorStr();
                            buffer.writeUtf(newChannelColorStr);
                        });
                        playButtonClickSound();
                    }).setSelectedTexture(1, -1);

            // Color block
            ColorBlockWidget colorBlockWidget = new ColorBlockWidget(currentX, 0, BUTTON_SIZE, BUTTON_SIZE)
                    .setCurrentColor(entry.getColor());
            channelGroup.addWidget(colorBlockWidget);
            currentX += BUTTON_SIZE + MARGIN;

            // Text box
            channelGroup.addWidget(textBoxWidget);
            currentX += availableWidth + MARGIN;
            if (!des.isEmpty()) {
                var desText = new TextTexture(ChatFormatting.DARK_GRAY + des).setDropShadow(false);
                desText.setType(TextTexture.TextType.ROLL).setRollSpeed(0.7f);
                channelGroup.addWidget(new ImageWidget(BUTTON_SIZE + MARGIN, 10, availableWidth, 8, desText));
            }

            // Slot
            Widget slotWidget = cover.addVirtualEntryWidget(entry, currentX, 0, BUTTON_SIZE, BUTTON_SIZE);
            channelGroup.addWidget(slotWidget);

            return channelGroup;
        }

        private void sendChannelList() {
            writeClientAction(100, buffer -> buffer.writeBoolean(showChannels.isTrue()));
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            super.handleClientAction(id, buffer);
            if (id == 0) {
                String newChannelColorStr = buffer.readUtf();
                cover.setChannelName(newChannelColorStr);
            } else if (id == 100) {
                if (!buffer.readBoolean()) return;
                var entries = VirtualEnderRegistry.getInstance().getEntryNames(cover.getOwner(), EntryTypes.ENDER_FLUID)
                        .stream().map(name -> VirtualEnderRegistry.getInstance().getEntry(cover.getOwner(),
                                EntryTypes.ENDER_FLUID, name))
                        .sorted(Comparator.comparing(VirtualEntry::getColorStr));
                writeUpdateInfo(101, buf -> {
                    var list = entries.toList();
                    buf.writeVarInt(list.size());
                    for (var entry : list) {
                        buf.writeNbt(entry.serializeNBT());
                    }
                });
            }
        }

        @Override
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            super.readUpdateInfo(id, buffer);
            if (id == 101) {
                int size = buffer.readVarInt();
                List<VirtualEntry> entries = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    VirtualEntry entry = cover.getEntryType().createInstance();
                    entry.deserializeNBT(Objects.requireNonNull(buffer.readNbt()));
                    entries.add(entry);
                }
                addChannelWidgets(entries);
            }
        }
    }

    protected enum Permissions implements EnumSelectorWidget.SelectableEnum {

        PUBLIC,
        PRIVATE;

        @Override
        public @NotNull String getTooltip() {
            return switch (this) {
                case PUBLIC -> "cover.ender_fluid_link.private.tooltip.enabled";
                case PRIVATE -> "cover.ender_fluid_link.private.tooltip.disabled";
            };
        }

        @Override
        public @NotNull IGuiTexture getIcon() {
            return switch (this) {
                case PUBLIC -> GuiTextures.BUTTON_PUBLIC_PRIVATE.getSubTexture(0, 0, 1, 0.5);
                case PRIVATE -> GuiTextures.BUTTON_PUBLIC_PRIVATE.getSubTexture(0, 0.5, 1, 0.5);
            };
        }
    }
}
