package zone.moddev.mc.poweradvantage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;
import zone.moddev.mc.poweradvantage.menu.MachineMenu;

public final class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 200;
        imageHeight = 100;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blitSprite(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mmdlib", "panel"), leftPos, topPos, imageWidth, imageHeight);
        graphics.blitSprite(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mmdlib", "compact_frame"), leftPos + 12, topPos + 45, 176, 17);
        int width = menu.capacity() == 0 ? 0 : (int) (174L * menu.amount() / menu.capacity());
        graphics.fill(leftPos + 13, topPos + 46, leftPos + 13 + width, topPos + 61, menu.isEnergy() ? 0xffdca92b : 0xff467ab7);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 10, 0x404040, false);
        Component resource = menu.isEnergy() ? Component.translatable("screen.poweradvantage.energy")
            : menu.amount() == 0 ? Component.translatable("tooltip.poweradvantage.empty")
            : new FluidStack(BuiltInRegistries.FLUID.byId(menu.fluidId()), 1).getHoverName();
        graphics.drawString(font, resource, 12, 29, 0x404040, false);
        graphics.drawString(font, menu.amount() + " / " + menu.capacity() + (menu.isEnergy() ? " FE" : " mB"), 12, 70, 0x404040, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
