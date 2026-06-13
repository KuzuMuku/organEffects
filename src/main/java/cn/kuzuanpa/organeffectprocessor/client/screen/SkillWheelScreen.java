package cn.kuzuanpa.organeffectprocessor.client.screen;

import cn.kuzuanpa.organeffectprocessor.common.network.CastSkillC2SPacket;
import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillDefinition;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class SkillWheelScreen extends Screen {
    private final List<SkillDefinition> skills;
    private int selectedIndex;

    public SkillWheelScreen(LocalPlayer player) {
        super(Component.translatable("screen.organeffectprocessor.skill_wheel"));
        this.skills = SkillManager.getAvailableSkills(player);
        this.selectedIndex = resolveInitialIndex(player);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int centerY = height / 2;
        guiGraphics.fill(0, 0, width, height, 0x66000000);
        guiGraphics.drawCenteredString(font, title, centerX, centerY - 10, 0xFFFFFF);
        if (skills.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("message.organeffectprocessor.skill.none"), centerX, centerY + 10, 0xFF8080);
            return;
        }

        updateSelection(mouseX, mouseY, centerX, centerY);
        int radius = 70;
        for (int i = 0; i < skills.size(); i++) {
            double angle = -Math.PI / 2 + (Math.PI * 2 * i / skills.size());
            int x = centerX + (int) (Math.cos(angle) * radius);
            int y = centerY + (int) (Math.sin(angle) * radius);
            int color = i == selectedIndex ? 0xFFFF55 : 0xFFFFFF;
            guiGraphics.drawCenteredString(font, Component.translatable(skills.get(i).nameKey()), x, y, color);
        }

        SkillDefinition selected = skills.get(selectedIndex);
        guiGraphics.drawCenteredString(font, Component.translatable(selected.nameKey()), centerX, centerY + 18, 0x55FF55);
        guiGraphics.drawCenteredString(font, Component.translatable(selected.descriptionKey()), centerX, centerY + 32, 0xAAAAAA);
    }

    public void confirmSelection() {
        if (!skills.isEmpty()) {
            OepNetwork.sendToServer(new CastSkillC2SPacket(skills.get(selectedIndex).id()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    private int resolveInitialIndex(LocalPlayer player) {
        String selectedSkillId = SkillManager.getSelectedSkillId(player);
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).id().equals(selectedSkillId)) {
                return i;
            }
        }
        return 0;
    }

    private void updateSelection(int mouseX, int mouseY, int centerX, int centerY) {
        if (skills.isEmpty()) {
            return;
        }
        double angle = Math.atan2(mouseY - centerY, mouseX - centerX) + Math.PI / 2;
        if (angle < 0) {
            angle += Math.PI * 2;
        }
        selectedIndex = Math.floorMod((int) Math.round(angle / (Math.PI * 2) * skills.size()), skills.size());
    }
}
