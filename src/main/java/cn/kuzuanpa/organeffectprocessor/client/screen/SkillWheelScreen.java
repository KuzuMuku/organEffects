package cn.kuzuanpa.organeffectprocessor.client.screen;

import cn.kuzuanpa.organeffectprocessor.common.network.OepNetwork;
import cn.kuzuanpa.organeffectprocessor.common.network.SelectSkillC2SPacket;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillDefinition;
import cn.kuzuanpa.organeffectprocessor.common.skill.SkillManager;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class SkillWheelScreen {
    private final List<SkillDefinition> skills;
    private final LocalPlayer player;
    private final Component title = Component.translatable("screen.organeffectprocessor.skill_wheel");
    private int selectedIndex;

    public SkillWheelScreen(LocalPlayer player) {
        this.player = player;
        this.skills = SkillManager.getAvailableSkills(player);
        this.selectedIndex = resolveInitialIndex(player);
    }

    public boolean isEmpty() {
        return skills.isEmpty();
    }

    public void render(GuiGraphics guiGraphics, int width, int height, int mouseX, int mouseY) {
        int centerX = width / 2;
        int centerY = height / 2;
        Font font = Minecraft.getInstance().font;
        int panelWidth = 320;
        int panelHeight = 220;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;
        int wheelCenterY = panelY + 82;
        guiGraphics.fill(0, 0, width, height, 0x22000000);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x88101418);
        guiGraphics.flush();
        if (skills.isEmpty()) {
            drawCenteredLabel(guiGraphics, font, Component.translatable("message.organeffectprocessor.skill.none"), centerX, centerY + 10, 0xFF8080);
            return;
        }

        updateSelection(mouseX, mouseY, centerX, wheelCenterY);
        int radius = 72;
        for (int i = 0; i < skills.size(); i++) {
            SkillDefinition skill = skills.get(i);
            double angle = -Math.PI / 2 + (Math.PI * 2 * i / skills.size());
            int x = centerX + (int) (Math.cos(angle) * radius);
            int y = wheelCenterY + (int) (Math.sin(angle) * radius);
            long cooldown = SkillManager.getRemainingCooldownTicks(player, skill.id());
            int color = cooldown > 0L ? 0xFF8080 : (i == selectedIndex ? 0xFFFF55 : 0xFFFFFF);
            Component label = getSkillLabel(skill, cooldown);
            int labelWidth = font.width(label);
            guiGraphics.fill(x - labelWidth / 2 - 4, y - 3, x + labelWidth / 2 + 4, y + 10, i == selectedIndex ? 0x90304048 : 0x70101820);
            drawCenteredLabel(guiGraphics, font, label, x, y, color);
        }

        SkillDefinition selected = skills.get(selectedIndex);
        long selectedCooldown = SkillManager.getRemainingCooldownTicks(player, selected.id());
        int detailY = panelY + 150;
        drawCenteredLabel(guiGraphics, font, Component.translatable(selected.nameKey()), centerX, detailY, 0x55FF55);
        guiGraphics.drawWordWrap(font, Component.translatable(selected.descriptionKey()), panelX + 24, detailY + 18, panelWidth - 48, 0xC8C8C8);
        drawCenteredLabel(guiGraphics, font, getCooldownStatus(selectedCooldown), centerX, panelY + panelHeight - 18, selectedCooldown > 0L ? 0xFF8080 : 0x80FF80);
    }

    public void applySelection() {
        if (!skills.isEmpty()) {
            String skillId = skills.get(selectedIndex).id();
            SkillManager.setSelectedSkillId(player, skillId);
            OepNetwork.sendToServer(new SelectSkillC2SPacket(skillId));
        }
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

    private Component getSkillLabel(SkillDefinition skill, long cooldownTicks) {
        if (cooldownTicks <= 0L) {
            return Component.translatable(skill.nameKey());
        }
        return Component.literal(Component.translatable(skill.nameKey()).getString() + " (" + formatSeconds(cooldownTicks) + "s)");
    }

    private Component getCooldownStatus(long cooldownTicks) {
        if (cooldownTicks <= 0L) {
            return Component.translatable("screen.organeffectprocessor.skill_wheel.ready");
        }
        return Component.translatable("message.organeffectprocessor.skill.cooldown", formatSeconds(cooldownTicks));
    }

    private String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private void drawCenteredLabel(GuiGraphics guiGraphics, Font font, Component text, int centerX, int y, int color) {
        guiGraphics.drawCenteredString(font, text, centerX, y, color);
    }
}
