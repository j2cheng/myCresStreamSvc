package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.view.Surface;

public class CanvasSurfaceAcquireResponse extends CanvasResponse {
    public final Surface surface;

    @Override
    public boolean isSucceeded() { return surface != null && super.isSucceeded(); }

    public CanvasSurfaceAcquireResponse(Surface surface) {
        this.surface = surface;
    }

    public CanvasSurfaceAcquireResponse(int code, String message) {
        super(code, message);
        this.surface = null;
    }

    private CanvasSurfaceAcquireResponse(Parcel in) {
        super(in);
        final boolean surfaceExists = in.readInt() != 0;
        this.surface = surfaceExists ? Surface.CREATOR.createFromParcel(in) : null;
    }

    public static final Creator<CanvasSurfaceAcquireResponse> CREATOR = new Creator<CanvasSurfaceAcquireResponse>() {
        @Override
        public CanvasSurfaceAcquireResponse createFromParcel(Parcel in) {
            return new CanvasSurfaceAcquireResponse(in);
        }

        @Override
        public CanvasSurfaceAcquireResponse[] newArray(int size) {
            return new CanvasSurfaceAcquireResponse[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        final boolean surfaceExists = this.surface != null;
        dest.writeInt(surfaceExists ? 1 : 0);
        if (surfaceExists) dest.writeParcelable(this.surface, flags);
    }
}
