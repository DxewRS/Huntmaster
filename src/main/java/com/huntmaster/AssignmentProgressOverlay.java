package com.huntmaster;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

final class AssignmentProgressOverlay extends OverlayPanel
{
    private final AssignmentDashboardState state;
    private final BosscapeSettings config;
    private final Client client;
    AssignmentProgressOverlay(HuntmasterPlugin plugin,Client client,AssignmentDashboardState state,BosscapeSettings config)
    { super(plugin);this.client=client;this.state=state;this.config=config;setPosition(OverlayPosition.TOP_LEFT); }
    @Override public Dimension render(Graphics2D graphics)
    {
        if(client.getGameState()!=GameState.LOGGED_IN)return null;
        String text=state.overlay(System.currentTimeMillis(),config.showAssignmentProgressOverlay());
        if(text==null)return null;
        panelComponent.getChildren().add(LineComponent.builder().left(text).build());
        return super.render(graphics);
    }
}
