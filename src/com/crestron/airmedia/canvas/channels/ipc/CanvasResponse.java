package com.crestron.airmedia.canvas.channels.ipc;

import android.os.Parcel;
import android.os.Parcelable;

public class CanvasResponse implements Parcelable {
    public static class ErrorCodes {
        public static final int OK = 0;
        public static final int NotImplemented = -1;
        public static final int NoSourceManager = -2;
        public static final int InternalError = -3;
        public static final int TimedOut = -4;
        public static final int FailedToAssignView = -5;
        public static final int NoAssignedView = -6;
        public static final int NoCanvasService = -7;
        public static final int NoSessionExists = -8;
        public static final int UnsupportedAction = -9;
        public static final int NotReady = -10;
        public static final int Obsolete = -11;
    }

    private final int content;
    private int errorCode;
    private String errorMessage;

    public boolean isSucceeded() { return errorCode == ErrorCodes.OK; }

    public boolean isFailed() { return !isSucceeded(); }

    public int getErrorCode() { return errorCode; }

    public String getErrorMessage() { return errorMessage; }

    protected void setErrorCode(int code) { errorCode = code; }

    protected void setErrorMessage(String message) { errorMessage = message; }

    protected void setError(int code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
    }

    protected void clearError() {
        this.errorCode = ErrorCodes.OK;
        this.errorMessage = null;
    }
    
    public CanvasResponse() {
        this.content = 0;
        this.errorCode = ErrorCodes.OK;
        this.errorMessage = null;
    }

    public CanvasResponse(int code, String message) {
        this.content = 0;
        this.errorCode = code;
        this.errorMessage = message;
    }

    protected CanvasResponse(Parcel in) {
        this.content = in.readInt();
        this.errorCode = in.readInt();
        this.errorMessage = in.readString();
    }

    public static final Creator<CanvasResponse> CREATOR = new Creator<CanvasResponse>() {
        @Override
        public CanvasResponse createFromParcel(Parcel in) {
            return new CanvasResponse(in);
        }

        @Override
        public CanvasResponse[] newArray(int size) {
            return new CanvasResponse[size];
        }
    };

    @Override
    public int describeContents() {
        return content;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.content);
        dest.writeInt(this.errorCode);
        dest.writeString(this.errorMessage);
    }
    
    @Override
    public String toString() {
        return isSucceeded()
                ? "canvas.response.success"
                : "canvas.response.failed  " + getErrorCode() + "  " + getErrorMessage();
    }
}
