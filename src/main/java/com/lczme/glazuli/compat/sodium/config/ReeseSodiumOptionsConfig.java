package com.lczme.glazuli.compat.sodium.config;

/** Layout and behaviour defaults of Reese's Sodium Options; the glass port has no settings page of its own. */
public final class ReeseSodiumOptionsConfig {
    private static final ConfigData CONFIG = new ConfigData();

    private ReeseSodiumOptionsConfig() {
    }

    public static ConfigData config() {
        return CONFIG;
    }

    public static final class ConfigData {
        public boolean isEnabled() {
            return true;
        }

        public boolean isTabHeaderIcons() {
            return true;
        }

        public boolean isTabHeaderVersionLabels() {
            return true;
        }

        public TabHeaderCollapseMode getTabHeaderCollapseMode() {
            return TabHeaderCollapseMode.ALL_EXPANDED;
        }

        public boolean isTabHeaders() {
            return true;
        }

        public boolean isCollapseSinglePageGroups() {
            return true;
        }

        public boolean isCollapsibleGroups() {
            return true;
        }

        public int getTooltipDelayMs() {
            return 500;
        }

        public boolean isTooltipOptionIds() {
            return false;
        }

        public boolean isColorThemes() {
            return true;
        }

        public boolean isThemedHeadersAndLabels() {
            return true;
        }

        public boolean isThemedTooltipBorders() {
            return true;
        }

        public boolean isReducedMotion() {
            return false;
        }

        public boolean isReverseCyclingControls() {
            return true;
        }

        public boolean isShiftScrollSliderAdjustments() {
            return true;
        }

        public int getSearchResultLimit() {
            return 15;
        }

        public boolean isHideNonMatchingOptions() {
            return true;
        }

        public boolean isHideNonMatchingTabs() {
            return true;
        }

        public DisabledOptionVisibility getDisabledOptionVisibility() {
            return DisabledOptionVisibility.SHOWN;
        }

        public FocusBorderMode getFocusBorderMode() {
            return FocusBorderMode.KEYBOARD;
        }

        public boolean isControllerGuides() {
            return false;
        }

        public boolean isResetButtonOverlay() {
            return true;
        }

        public boolean isUndoButtonOverlay() {
            return true;
        }

        public boolean isAlwaysShowActionButtons() {
            return false;
        }
    }

    public enum TabHeaderCollapseMode {
        SELECTED_GROUP,
        ALL_EXPANDED,
        MANUAL
    }

    public enum DisabledOptionVisibility {
        SHOWN,
        HIDDEN
    }

    public enum FocusBorderMode {
        KEYBOARD,
        ALWAYS,
        NEVER
    }
}
