package com.crestron.txrxservice.wc.ipc;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class WC_Connection implements Parcelable {
    private final int contents;
    public final int sessionId;
    public final List<String> urlList;	
    public final String certificate;

    public WC_Connection() {
        this(0, null, null);
    }

    public WC_Connection(int sessionId, List<String> urlList, String cert) {
    	this.contents = 0;
        this.sessionId = sessionId;
        this.urlList = urlList;
		this.certificate = cert;
    }

    protected WC_Connection(Parcel in) {
        this.contents = in.readInt();
        this.sessionId = in.readInt();
        int count = in.readInt();
        final ArrayList<String> urlList = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            urlList.add(i, in.readString());
        }
        this.urlList = urlList;
		this.certificate = in.readString();
    }

    public static final Creator<WC_Connection> CREATOR = new Creator<WC_Connection>() {
        @Override
        public WC_Connection createFromParcel(Parcel in) {
            return new WC_Connection(in);
        }

        @Override
        public WC_Connection[] newArray(int size) {
            return new WC_Connection[size];
        }
    };

    @Override
    public int describeContents() {
        return contents;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.contents);
        dest.writeInt(this.sessionId);
        dest.writeInt(this.urlList.size());
        for (int i=0; i < this.urlList.size(); i++)
        {
           dest.writeString(this.urlList.get(i));
        }
		dest.writeString(this.certificate);
    }

    public String toString() {
        return "\tsessionId="+sessionId+"\n\tURL="+((urlList!=null)?urlList:"null")+"\n\tCertificate="+certificate;
    }
    
    public String toStringNoCert() {
        return "\tsessionId="+sessionId+"\n\tURL="+((urlList!=null)?urlList:"null")+"\n\tCertificate="+((certificate==null)?"null":certificate.length());
    }

    public String toStringMaskURL() {
        return "\tsessionId="+sessionId+"\n\tURL= *Masked*"+"\n\tCertificate="+((certificate==null)?"null":certificate.length());
    }
}
