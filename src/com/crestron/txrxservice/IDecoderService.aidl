package com.crestron.txrxservice;

import android.view.Surface;

interface IDecoderService {
    int attachSurface(in int id, in Surface surface);
    int detachSurface(in int id, in Surface surface);
    int masterStartStream(in int id, in String url);
    int masterStopStream(in int id);
}
