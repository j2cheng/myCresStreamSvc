package com.crestron.airmedia.receiver.m360.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.EnumSet;

public enum AirMediaSessionScreenPosition implements Parcelable {
    None(0, false), Auto(1 << 0, false), Fullscreen(1 << 1),
    FourUpperLeft(1 << 2), FourUpperRight(1 << 3), FourLowerLeft(1 << 4), FourLowerRight(1 << 5),
    SixUpperLeft(1 << 6), SixUpperRight(1 << 7), SixCenterRight(1 << 8), SixLowerLeft(1 << 9), SixLowerCenter(1 << 10), SixLowerRight(1 << 11),
    NineUpperLeft(1 << 12), NineUpperCenter(1 << 13), NineUpperRight(1 << 14), NineCenterLeft(1 << 15), NineCenter(1 << 16), NineCenterRight(1 << 17), NineLowerLeft(1 << 18), NineLowerCenter(1 << 19), NineLowerRight(1 << 20);
    public final int value;
    public final boolean valid;
    public static final EnumSet<AirMediaSessionScreenPosition> allScreens = EnumSet.range(Fullscreen, NineLowerRight);
    public static final EnumSet<AirMediaSessionScreenPosition> fullScreen = EnumSet.of(Fullscreen);
    public static final EnumSet<AirMediaSessionScreenPosition> fourScreens = EnumSet.range(FourUpperLeft, FourLowerRight);
    public static final EnumSet<AirMediaSessionScreenPosition> sixScreens = EnumSet.range(SixUpperLeft, SixLowerRight);
    public static final EnumSet<AirMediaSessionScreenPosition> nineScreens = EnumSet.range(NineUpperLeft, NineLowerRight);

    AirMediaSessionScreenPosition(int v) {
        this(v, true);
    }

    AirMediaSessionScreenPosition(int v, boolean i) { value = v; valid = i; }

    @Override
    public void writeToParcel(Parcel dest, int flags) { dest.writeInt(value); }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<AirMediaSessionScreenPosition> CREATOR = new Creator<AirMediaSessionScreenPosition>() {
        @Override
        public AirMediaSessionScreenPosition createFromParcel(Parcel in) { return AirMediaSessionScreenPosition.from(in); }

        @Override
        public AirMediaSessionScreenPosition[] newArray(int size) { return new AirMediaSessionScreenPosition[size]; }
    };

    public static AirMediaSessionScreenPosition from(Parcel in) { return from(in.readInt()); }

    public static AirMediaSessionScreenPosition from(final int v) {
        switch (v) {
            case 0:
                return None;
            case 1 << 0:
                return Auto;
            case 1 << 1:
                return Fullscreen;
            case 1 << 2:
                return FourUpperLeft;
            case 1 << 3:
                return FourUpperRight;
            case 1 << 4:
                return FourLowerLeft;
            case 1 << 5:
                return FourLowerRight;
            case 1 << 6:
                return SixUpperLeft;
            case 1 << 7:
                return SixUpperRight;
            case 1 << 8:
                return SixCenterRight;
            case 1 << 9:
                return SixLowerLeft;
            case 1 << 10:
                return SixLowerCenter;
            case 1 << 11:
                return SixLowerRight;
            case 1 << 12:
                return NineUpperLeft;
            case 1 << 13:
                return NineUpperCenter;
            case 1 << 14:
                return NineUpperRight;
            case 1 << 15:
                return NineCenterLeft;
            case 1 << 16:
                return NineCenter;
            case 1 << 17:
                return NineCenterRight;
            case 1 << 18:
                return NineLowerLeft;
            case 1 << 19:
                return NineLowerCenter;
            case 1 << 20:
                return NineLowerRight;
        }
        return None;
    }

    public static EnumSet<AirMediaSessionScreenPosition> set(final int v) {
        EnumSet<AirMediaSessionScreenPosition> positions = EnumSet.noneOf(AirMediaSessionScreenPosition.class);
        for (AirMediaSessionScreenPosition position : AirMediaSessionScreenPosition.values()) {
            if ((position.value & v) != 0) positions.add(position);
        }
        return positions;
    }

    public static int toValue(EnumSet<AirMediaSessionScreenPosition> positions) {
        int value = 0;
        for (AirMediaSessionScreenPosition position : positions) {
            value |= position.value;
        }
        return value;
    }

    public static EnumSet<AirMediaSessionScreenPosition> union(EnumSet<AirMediaSessionScreenPosition> lhs, EnumSet<AirMediaSessionScreenPosition> rhs) {
        EnumSet<AirMediaSessionScreenPosition> set = lhs.clone();
        for (AirMediaSessionScreenPosition p : rhs) {
            if (!set.contains(p)) set.add(p);
        }
        return set;
    }

    public static EnumSet<AirMediaSessionScreenPosition> intersect(EnumSet<AirMediaSessionScreenPosition> lhs, EnumSet<AirMediaSessionScreenPosition> rhs) {
        EnumSet<AirMediaSessionScreenPosition> set = lhs.clone();
        set.retainAll(rhs);
        return set;
    }

    public static EnumSet<AirMediaSessionScreenPosition> difference(EnumSet<AirMediaSessionScreenPosition> lhs, EnumSet<AirMediaSessionScreenPosition> rhs) {
        EnumSet<AirMediaSessionScreenPosition> set = lhs.clone();
        set.removeAll(rhs);
        return set;
    }

    public static AirMediaSessionScreenPositionLayout layout(EnumSet<AirMediaSessionScreenPosition> set) {
        if (set.isEmpty()) return AirMediaSessionScreenPositionLayout.None;
        if (isFullscreenOnly(set)) return AirMediaSessionScreenPositionLayout.Fullscreen;
        if (isFourScreenOnly(set)) return AirMediaSessionScreenPositionLayout.FourScreen;
        if (isSixScreenOnly(set)) return AirMediaSessionScreenPositionLayout.SixScreen;
        if (isNineScreenOnly(set)) return AirMediaSessionScreenPositionLayout.NineScreen;
        return AirMediaSessionScreenPositionLayout.Mixed;
    }

    private static boolean isFullscreenOnly(EnumSet<AirMediaSessionScreenPosition> set) { return isFullscreen(set) && !isOtherThanFullscreen((set)); }

    private static boolean isFourScreenOnly(EnumSet<AirMediaSessionScreenPosition> set) { return isFourScreen(set) && !isOtherThanFourScreen(set); }

    private static boolean isSixScreenOnly(EnumSet<AirMediaSessionScreenPosition> set) { return isSixScreen(set) && !isOtherThanSixScreen(set); }

    private static boolean isNineScreenOnly(EnumSet<AirMediaSessionScreenPosition> set) { return isNineScreen(set) && !isOtherThanNineScreen(set); }

    private static boolean isOtherThanFullscreen(EnumSet<AirMediaSessionScreenPosition> set) { return isFourScreen(set) || isSixScreen(set) || isNineScreen(set); }

    private static boolean isOtherThanFourScreen(EnumSet<AirMediaSessionScreenPosition> set) { return isFullscreen(set) || isSixScreen(set) || isNineScreen(set); }

    private static boolean isOtherThanSixScreen(EnumSet<AirMediaSessionScreenPosition> set) { return isFullscreen(set) || isFourScreen(set) || isNineScreen(set); }

    private static boolean isOtherThanNineScreen(EnumSet<AirMediaSessionScreenPosition> set) { return isFullscreen(set) || isFourScreen(set) || isSixScreen(set); }

    private static boolean isFullscreen(EnumSet<AirMediaSessionScreenPosition> set) { return !Collections.disjoint(set, fullScreen); }

    private static boolean isFourScreen(EnumSet<AirMediaSessionScreenPosition> set) { return !Collections.disjoint(set, fourScreens); }

    private static boolean isSixScreen(EnumSet<AirMediaSessionScreenPosition> set) { return !Collections.disjoint(set, sixScreens); }

    private static boolean isNineScreen(EnumSet<AirMediaSessionScreenPosition> set) { return !Collections.disjoint(set, nineScreens); }
}
