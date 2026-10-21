package com.huntmaster;

/** Presentation preference only. Server-confirmed Hunt progress remains authoritative. */
public enum HuntOverlayVisibility
{
    AFTER_CREDITED_KILL("After credited kill"),
    WHILE_HUNT_ACTIVE("While Hunt active");

    private final String label;
    HuntOverlayVisibility(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
