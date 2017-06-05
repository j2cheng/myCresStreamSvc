package com.crestron.airmedia.utilities;

import android.content.res.Configuration;

public class ConfigurationHelper {
    private static final String BITMASK_DELIMETER = "·";

    private static StringBuilder toStringConfigurationOrientation(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.orientation != cached.orientation) {
            builder.append("\norientation=  ");
            if (cached != null) {
                toStringConfigurationOrientation(builder, cached).append("  ==>  ");
            }
            toStringConfigurationOrientation(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationOrientation(StringBuilder builder, Configuration config) {
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            builder.append("landscape");
        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            builder.append("portrait");
        } else {
            builder.append("undefined(").append(config.orientation).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationHardKeyboardHidden(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.hardKeyboardHidden != cached.hardKeyboardHidden) {
            builder.append("\nhardKeyboardHidden=  ");
            if (cached != null) {
                toStringConfigurationHardKeyboardHidden(builder, cached).append("  ==>  ");
            }
            toStringConfigurationHardKeyboardHidden(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationHardKeyboardHidden(StringBuilder builder, Configuration config) {
        if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            builder.append("yes");
        } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            builder.append("no");
        } else {
            builder.append("undefined(").append(config.hardKeyboardHidden).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationKeyboard(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.keyboard != cached.keyboard) {
            builder.append("\nkeyboard=  ");
            if (cached != null) {
                toStringConfigurationKeyboard(builder, cached).append("  ==>  ");
            }
            toStringConfigurationKeyboard(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationKeyboard(StringBuilder builder, Configuration config) {
        if (config.keyboard == Configuration.KEYBOARD_NOKEYS) {
            builder.append("none");
        } else if (config.keyboard == Configuration.KEYBOARD_QWERTY) {
            builder.append("qwerty");
        } else if (config.keyboard == Configuration.KEYBOARD_12KEY) {
            builder.append("12-key");
        } else {
            builder.append("undefined(").append(config.keyboard).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationKeyboardHidden(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.keyboardHidden != cached.keyboardHidden) {
            builder.append("\nhardKeyboardHidden=  ");
            if (cached != null) {
                toStringConfigurationKeyboardHidden(builder, cached).append("  ==>  ");
            }
            toStringConfigurationKeyboardHidden(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationKeyboardHidden(StringBuilder builder, Configuration config) {
        if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_YES) {
            builder.append("yes");
        } else if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
            builder.append("no");
        } else {
            builder.append("undefined(").append(config.keyboardHidden).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationNavigation(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.navigation != cached.navigation) {
            builder.append("\nnavigation=  ");
            if (cached != null) {
                toStringConfigurationNavigation(builder, cached).append("  ==>  ");
            }
            toStringConfigurationNavigation(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationNavigation(StringBuilder builder, Configuration config) {
        if (config.navigation == Configuration.NAVIGATION_NONAV) {
            builder.append("none");
        } else if (config.navigation == Configuration.NAVIGATION_DPAD) {
            builder.append("dpad");
        } else if (config.navigation == Configuration.NAVIGATION_TRACKBALL) {
            builder.append("trackball");
        } else if (config.navigation == Configuration.NAVIGATION_WHEEL) {
            builder.append("wheel");
        } else {
            builder.append("undefined(").append(config.navigation).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationNavigationHidden(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.navigationHidden != cached.navigationHidden) {
            builder.append("\nnavigationHidden=  ");
            if (cached != null) {
                toStringConfigurationNavigationHidden(builder, cached).append("  ==>  ");
            }
            toStringConfigurationNavigationHidden(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationNavigationHidden(StringBuilder builder, Configuration config) {
        if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_YES) {
            builder.append("yes");
        } else if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_NO) {
            builder.append("no");
        } else {
            builder.append("undefined(").append(config.navigationHidden).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutSize(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        if (cached == null || ((config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) != (cached.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  size=  ");
            if (cached != null) {
                toStringConfigurationScreenLayoutSize(builder, cached).append("  ==>  ");
            }
            toStringConfigurationScreenLayoutSize(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutSize(StringBuilder builder, Configuration config) {
        int layout = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (layout == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            builder.append("x-large");
        } else if (layout == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            builder.append("large");
        } else if (layout == Configuration.SCREENLAYOUT_SIZE_NORMAL) {
            builder.append("normal");
        } else if (layout == Configuration.SCREENLAYOUT_SIZE_SMALL) {
            builder.append("small");
        } else {
            builder.append("undefined");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutLong(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        if (cached == null || ((config.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK) != (cached.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  long=  ");
            if (cached != null) {
                toStringConfigurationScreenLayoutLong(builder, cached).append("  ==>  ");
            }
            toStringConfigurationScreenLayoutLong(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutLong(StringBuilder builder, Configuration config) {
        int layout = config.screenLayout & Configuration.SCREENLAYOUT_LONG_MASK;
        if (layout == Configuration.SCREENLAYOUT_LONG_YES) {
            builder.append("yes");
        } else if (layout == Configuration.SCREENLAYOUT_LONG_NO) {
            builder.append("no");
        } else {
            builder.append("undefined");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutDir(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        if (cached == null || ((config.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) != (cached.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  dir=  ");
            if (cached != null) {
                toStringConfigurationScreenLayoutDir(builder, cached).append("  ==>  ");
            }
            toStringConfigurationScreenLayoutDir(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutDir(StringBuilder builder, Configuration config) {
        int layout = config.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK;
        if (layout == Configuration.SCREENLAYOUT_LAYOUTDIR_LTR) {
            builder.append("ltr");
        } else if (layout == Configuration.SCREENLAYOUT_LAYOUTDIR_RTL) {
            builder.append("rtl");
        } else {
            builder.append("undefined");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutRound(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        final int SCREENLAYOUT_ROUND_MASK = 768;
        if (cached == null || ((config.screenLayout & SCREENLAYOUT_ROUND_MASK) != (cached.screenLayout & SCREENLAYOUT_ROUND_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  round=  ");
            if (cached != null) {
                toStringConfigurationScreenLayoutRound(builder, cached).append("  ==>  ");
            }
            toStringConfigurationScreenLayoutRound(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayoutRound(StringBuilder builder, Configuration config) {
        final int SCREENLAYOUT_ROUND_MASK = 768;
        final int SCREENLAYOUT_ROUND_YES = 512;
        final int SCREENLAYOUT_ROUND_NO = 256;
        final int SCREENLAYOUT_ROUND_UNDEFINED = 0;

        int layout = config.screenLayout & SCREENLAYOUT_ROUND_MASK;
        if (layout == SCREENLAYOUT_ROUND_YES) {
            builder.append("yes");
        } else if (layout == SCREENLAYOUT_ROUND_NO) {
            builder.append("no");
        } else {
            builder.append("undefined");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationScreenLayout(StringBuilder builder, Configuration config, Configuration cached) {
        Boolean bitMaskDelimet = false;

        if (cached == null || config.screenLayout != cached.screenLayout) {
            builder.append("\nscreenLayout=  ");

            toStringConfigurationScreenLayoutSize(builder, config, cached, bitMaskDelimet);

            toStringConfigurationScreenLayoutLong(builder, config, cached, bitMaskDelimet);

            toStringConfigurationScreenLayoutDir(builder, config, cached, bitMaskDelimet);

            toStringConfigurationScreenLayoutRound(builder, config, cached, bitMaskDelimet);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationTouchscreen(StringBuilder builder, Configuration config, Configuration cached) {
        if (cached == null || config.touchscreen != cached.touchscreen) {
            builder.append("\ntouchscreen=  ");
            if (cached != null) {
                toStringConfigurationTouchscreen(builder, cached).append("  ==>  ");
            }
            toStringConfigurationTouchscreen(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationTouchscreen(StringBuilder builder, Configuration config) {
        if (config.touchscreen == Configuration.TOUCHSCREEN_FINGER) {
            builder.append("finger");
        } else if (config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH) {
            builder.append("no touch");
        } else {
            builder.append("undefined(").append(config.touchscreen).append(")");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationUiModeType(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        if (cached == null || ((config.uiMode & Configuration.UI_MODE_TYPE_MASK) != (cached.uiMode & Configuration.UI_MODE_TYPE_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  type=  ");
            if (cached != null) {
                toStringConfigurationUiModeType(builder, cached).append("  ==>  ");
            }
            toStringConfigurationUiModeType(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationUiModeType(StringBuilder builder, Configuration config) {
        int mode = config.uiMode & Configuration.UI_MODE_TYPE_MASK;
        if (mode == Configuration.UI_MODE_TYPE_NORMAL) {
            builder.append("normal");
        } else if (mode == Configuration.UI_MODE_TYPE_DESK) {
            builder.append("desk");
        } else if (mode == Configuration.UI_MODE_TYPE_CAR) {
            builder.append("car");
        } else if (mode == Configuration.UI_MODE_TYPE_TELEVISION) {
            builder.append("tv");
        } else if (mode == Configuration.UI_MODE_TYPE_APPLIANCE) {
            builder.append("appliance");
        //} else if (mode == Configuration.UI_MODE_TYPE_WATCH) {
        //    builder.append("watch");
        } else {
            builder.append("undefined  " + mode);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationUiModeNight(StringBuilder builder, Configuration config, Configuration cached, Boolean bitMaskDelimet) {
        if (cached == null || ((config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != (cached.uiMode & Configuration.UI_MODE_NIGHT_MASK))) {
            if (bitMaskDelimet) builder.append(BITMASK_DELIMETER);
            bitMaskDelimet = true;
            builder.append("  night=  ");
            if (cached != null) {
                toStringConfigurationUiModeNight(builder, cached).append("  ==>  ");
            }
            toStringConfigurationUiModeNight(builder, config);
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationUiModeNight(StringBuilder builder, Configuration config) {
        int mode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (mode == Configuration.UI_MODE_NIGHT_YES) {
            builder.append("yes");
        } else if (mode == Configuration.UI_MODE_NIGHT_NO) {
            builder.append("no");
        } else {
            builder.append("undefined");
        }

        return builder;
    }

    private static StringBuilder toStringConfigurationUiMode(StringBuilder builder, Configuration config, Configuration cached) {
        Boolean bitMaskDelimet = false;

        if (cached == null || config.screenLayout != cached.screenLayout) {
            builder.append("\nuiMode=  ");

            toStringConfigurationUiModeType(builder, config, cached, bitMaskDelimet);

            toStringConfigurationUiModeNight(builder, config, cached, bitMaskDelimet);
        }

        return builder;
    }

    public static String toString(Configuration config, Configuration cached) {
        final String bitMaskDelimeter = "·";

        StringBuilder builder = new StringBuilder();

        builder.append("config[");

        toStringConfigurationOrientation(builder, config, cached);

        if (cached == null || config.densityDpi != cached.densityDpi) {
            builder.append("\ndensityDpi=  ");
            if (cached != null) {
                builder.append(cached.densityDpi).append("  ==>  ");
            }
            builder.append(config.densityDpi);
        }

        if (cached == null || config.fontScale != cached.fontScale) {
            builder.append("\nfontScale=  ");
            if (cached != null) {
                builder.append(cached.fontScale).append("  ==>  ");
            }
            builder.append(config.fontScale);
        }

        toStringConfigurationHardKeyboardHidden(builder, config, cached);

        toStringConfigurationKeyboard(builder, config, cached);

        toStringConfigurationKeyboardHidden(builder, config, cached);

        if ((cached == null || config.mcc != cached.mcc) && config.mcc != 0) {
            builder.append("\nmcc(Mobile Country Code)=  ");
            if (cached != null) {
                builder.append(cached.mcc).append("  ==>  ");
            }
            builder.append(config.mcc);
        }

        if ((cached == null || config.mnc != cached.mnc) /*&& config.mnc != Configuration.MNC_ZERO*/) {
            builder.append("\nmnc(Mobile Network Code)=  ");
            if (cached != null) {
                builder.append(cached.mnc).append("  ==>  ");
            }
            builder.append(config.mnc);
        }

        toStringConfigurationNavigation(builder, config, cached);

        toStringConfigurationNavigationHidden(builder, config, cached);

        if ((cached == null || config.screenHeightDp != cached.screenHeightDp) && config.screenHeightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED) {
            builder.append("\nscreenHeightDp=  ");
            if (cached != null) {
                builder.append(cached.screenHeightDp).append("  ==>  ");
            }
            builder.append(config.screenHeightDp);
        }

        if ((cached == null || config.screenWidthDp != cached.screenWidthDp) && config.screenWidthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED) {
            builder.append("\nscreenWidthDp=  ");
            if (cached != null) {
                builder.append(cached.screenWidthDp).append("  ==>  ");
            }
            builder.append(config.screenWidthDp);
        }

        if ((cached == null || config.smallestScreenWidthDp != cached.smallestScreenWidthDp) && config.smallestScreenWidthDp != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED) {
            builder.append("\nsmallestScreenWidthDp=  ");
            if (cached != null) {
                builder.append(cached.smallestScreenWidthDp).append("  ==>  ");
            }
            builder.append(config.smallestScreenWidthDp);
        }

        toStringConfigurationScreenLayout(builder, config, cached);

        toStringConfigurationTouchscreen(builder, config, cached);

        toStringConfigurationUiMode(builder, config, cached);

        // locale

        builder.append("]");

        return builder.toString();
    }
}
