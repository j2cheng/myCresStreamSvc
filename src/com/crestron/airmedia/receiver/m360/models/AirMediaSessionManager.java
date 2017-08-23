package com.crestron.airmedia.receiver.m360.models;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.crestron.airmedia.receiver.m360.IAirMediaSession;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManagerObserver;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionObserver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionConnectionState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionStreamingState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class AirMediaSessionManager extends AirMediaBase {
    private static final String TAG = "airmedia.manager";

    AirMediaSessionManager(IAirMediaSessionManager manager, TaskScheduler scheduler) {
        super(scheduler);
        manager_ = manager;
        try {
            manager.register(observer_);
            occupied_ = AirMediaSessionScreenPosition.set(manager.getOccupied());
            layout_ = manager.getLayout();
        } catch (RemoteException e) {

        }
    }

    private AirMediaSessionManager self() {
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final IAirMediaSessionManagerObserver observer_ = new IAirMediaSessionManagerObserver.Stub() {
        @Override
        public void onLayoutChanged(AirMediaSessionScreenPositionLayout from, AirMediaSessionScreenPositionLayout to) throws RemoteException {
            Log.w(TAG, "IAirMediaSessionManagerObserver.onLayoutChanged  " + from + "  ==>  " + to);
            scheduler().raise(layoutChanged(), self(), from, to);
        }

        @Override
        public void onOccupiedChanged(int from, int to) throws RemoteException {
            Log.w(TAG, "IAirMediaSessionManagerObserver.onOccupiedChanged  " + AirMediaSessionScreenPosition.set(from) + "  ==>  " + AirMediaSessionScreenPosition.set(to));
            occupied_ = AirMediaSessionScreenPosition.set(to);
            scheduler().raise(occupiedChanged(), self(), AirMediaSessionScreenPosition.set(from), AirMediaSessionScreenPosition.set(to));
        }

        @Override
        public void onAdded(IAirMediaSession session) throws RemoteException {
            Log.w(TAG, "IAirMediaSessionManagerObserver.onAdded  " + session);
            scheduler().update(new TaskScheduler.PropertyUpdater<IAirMediaSession>() { @Override public void update(IAirMediaSession v) { add(v); } }, session);
        }

        @Override
        public void onRemoved(IAirMediaSession session) throws RemoteException {
            Log.w(TAG, "IAirMediaSessionManagerObserver.onRemoved  " + session);
            scheduler().update(new TaskScheduler.PropertyUpdater<IAirMediaSession>() { @Override public void update(IAirMediaSession v) { remove(v); } }, session);
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final Object lock_ = new Object();
    private final List<AirMediaSession> sessions_ = new LinkedList<AirMediaSession>();

    private IAirMediaSessionManager manager_;

    private EnumSet<AirMediaSessionScreenPosition> occupied_ = EnumSet.noneOf(AirMediaSessionScreenPosition.class);
    private AirMediaSessionScreenPositionLayout layout_ = AirMediaSessionScreenPositionLayout.None;

    private final MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> added_ = new MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession>();
    private final MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> removed_ = new MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession>();
    private final MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenPositionLayout> layoutChanged_ = new MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenPositionLayout>();
    private final MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChanged_ = new MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>>();


    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// LAYOUT

    public AirMediaSessionScreenPositionLayout layout() {
        return layout_;
    }

    /// OCCUPIED

    public EnumSet<AirMediaSessionScreenPosition> occupied() {
        return occupied_.clone();
    }

    /// SESSIONS

    public boolean isEmpty() { return count() > 0; }

    public boolean isNotEmpty() { return !isEmpty(); }

    public int count() {
        return sessions_.size();
    }

    public Collection<AirMediaSession> sessions() {
        Collection<AirMediaSession> list = new LinkedList<AirMediaSession>();
        synchronized (sessions_) {
            for (AirMediaSession session : sessions_)
                list.add(session);
        }
        return list;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> added() { return added_; }

    public MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> removed() { return removed_; }

    public MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenPositionLayout> layoutChanged() { return layoutChanged_; }

    public MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChanged() { return occupiedChanged_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void clear() { clear(DefaultTimeout); }

    public void clear(TimeSpan timeout) {
        queue("clear", timeout, new Runnable() { @Override public void run() { clearTask(null); } });
    }

    public void clear(Observer<AirMediaSessionManager> observer) {
        queue(this, "clear", observer, new TaskScheduler.ObservableTask<AirMediaSessionManager>() { @Override public void run(Observer<AirMediaSessionManager> value) { clearTask(value); } });
    }

    private void clearTask(Observer<AirMediaSessionManager> observer) {
        try {
            manager_.clear();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.clear  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.clear  EXCEPTION  " + e);
        }
    }

    private void add(IAirMediaSession aidlSession) {
        AirMediaSession session;
        synchronized (sessions_) {
            for (AirMediaSession entry : sessions_) {
                if (AirMediaSession.isEqual(entry, aidlSession)) return;
            }
            session = new AirMediaSession(aidlSession, scheduler());
            sessions_.add(session);
        }
        scheduler().raise(added(), self(), session);
    }

    private void remove(IAirMediaSession aidlSession) {
        AirMediaSession session = null;
        synchronized (sessions_) {
            for (AirMediaSession entry : sessions_) {
                if (!AirMediaSession.isEqual(entry, aidlSession)) continue;
                session = entry;
                break;
            }
            if (session == null) return;
            sessions_.remove(session);
        }
        scheduler().raise(removed(), self(), session);
    }

    /// REMOTE CONNECTION

    private void handleRemoteException() {
        manager_ = null;
    }
}
