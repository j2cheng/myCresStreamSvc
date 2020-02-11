package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;


public class CanvasSourceRequest implements Parcelable {
    private final int content;
    public final long id;
    public final List<CanvasSourceTransaction> transactions;

    public CanvasSourceRequest() {
        this(0, null);
    }

    public CanvasSourceRequest(long id, List<CanvasSourceTransaction> transactions) {
        this.content = 0;
        this.id = id;
        this.transactions = transactions;
    }

    protected CanvasSourceRequest(Parcel in) {
        this.content = in.readInt();
        this.id = in.readLong();
        final int count = in.readInt();
        final ArrayList<CanvasSourceTransaction> transactions = new ArrayList<CanvasSourceTransaction>(count);
        for (int i = 0; i < count; i++) {
            transactions.add(i, CanvasSourceTransaction.CREATOR.createFromParcel(in));
        }
        this.transactions = transactions;
    }

    public static final Creator<CanvasSourceRequest> CREATOR = new Creator<CanvasSourceRequest>() {
        @Override
        public CanvasSourceRequest createFromParcel(Parcel in) {
            return new CanvasSourceRequest(in);
        }

        @Override
        public CanvasSourceRequest[] newArray(int size) {
            return new CanvasSourceRequest[size];
        }
    };

    @Override
    public int describeContents() {
        return content;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.content);
        dest.writeLong(this.id);
        int count = transactions != null ? transactions.size() : 0;
        dest.writeInt(count);
        for (CanvasSourceTransaction transaction : transactions) {
            transaction.writeToParcel(dest, flags);
        }
    }
}
