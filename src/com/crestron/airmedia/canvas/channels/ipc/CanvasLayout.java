package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class CanvasLayout implements Parcelable {
    private final int contents;
    public final int id;
    public final List<CanvasSourceLayout> sources;

    public CanvasLayout() {
        this(0, null);
    }

    public CanvasLayout(int id, List<CanvasSourceLayout> sources) {
        this.contents = 0;
        this.id = id;
        this.sources = sources;
    }

    protected CanvasLayout(Parcel in) {
        this.contents = in.readInt();
        this.id = in.readInt();
        final int count = in.readInt();
        final ArrayList<CanvasSourceLayout> sources = new ArrayList<CanvasSourceLayout>(count);
        for (int i = 0; i < count; i++) {
            sources.add(i, CanvasSourceLayout.CREATOR.createFromParcel(in));
        }
        this.sources = sources;
    }

    public static final Creator<CanvasLayout> CREATOR = new Creator<CanvasLayout>() {
        @Override
        public CanvasLayout createFromParcel(Parcel in) {
            return new CanvasLayout(in);
        }

        @Override
        public CanvasLayout[] newArray(int size) {
            return new CanvasLayout[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.id);
        int count = sources != null ? sources.size() : 0;
        dest.writeInt(count);
        if (sources != null) {
            for (CanvasSourceLayout source : sources) {
                source.writeToParcel(dest, flags);
            }
        }
    }
}