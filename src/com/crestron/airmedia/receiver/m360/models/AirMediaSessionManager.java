package com.crestron.airmedia.receiver.m360.models;

import android.os.RemoteException;

import com.crestron.airmedia.receiver.m360.IAirMediaSession;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManagerObserver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.TimeSpan;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class AirMediaSessionManager extends AirMediaBase {
    private static final String TAG = "airmedia.manager";
    public static final boolean USE_SYNCHRONOUS_ADD_SESSION_EVENT = true;

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
        public void onLayoutChanged(AirMediaSessionScreenLayout from, AirMediaSessionScreenLayout to) throws RemoteException {
            Common.Logging.d(TAG, "IAirMediaSessionManagerObserver.onLayoutChanged  " + from + "  ==>  " + to);
            scheduler().raise(layoutChanged(), self(), from, to);
        }

        @Override
        public void onOccupiedChanged(int from, int to) throws RemoteException {
            Common.Logging.d(TAG, "IAirMediaSessionManagerObserver.onOccupiedChanged  " + AirMediaSessionScreenPosition.set(from) + "  ==>  " + AirMediaSessionScreenPosition.set(to));
            occupied_ = AirMediaSessionScreenPosition.set(to);
            scheduler().raise(occupiedChanged(), self(), AirMediaSessionScreenPosition.set(from), AirMediaSessionScreenPosition.set(to));
        }

        @Override
        public void onAdded(IAirMediaSession session) throws RemoteException {
            Common.Logging.d(TAG, "IAirMediaSessionManagerObserver.onAdded  " + session);
            // need to add immediately, to allow systems down-path to register incoming events
            if (USE_SYNCHRONOUS_ADD_SESSION_EVENT) {
                add(session);
            } else {
                scheduler().update(new TaskScheduler.PropertyUpdater<IAirMediaSession>() { @Override public void update(IAirMediaSession v) { add(v); } }, session);
            }
        }

        @Override
        public void onRemoved(IAirMediaSession session) throws RemoteException {
            Common.Logging.d(TAG, "IAirMediaSessionManagerObserver.onRemoved  " + session);
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
    private AirMediaSessionScreenLayout layout_ = AirMediaSessionScreenLayout.None;

    private final MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> added_ = new MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession>();
    private final MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession> removed_ = new MulticastMessageDelegate<AirMediaSessionManager, AirMediaSession>();
    private final MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenLayout> layoutChanged_ = new MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenLayout>();
    private final MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChanged_ = new MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /// LAYOUT

    public AirMediaSessionScreenLayout layout() {
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

    public MulticastChangedDelegate<AirMediaSessionManager, AirMediaSessionScreenLayout> layoutChanged() { return layoutChanged_; }

    public MulticastChangedDelegate<AirMediaSessionManager, EnumSet<AirMediaSessionScreenPosition>> occupiedChanged() { return occupiedChanged_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void close() { close(DefaultTimeout); }

    public void close(TimeSpan timeout) {
        queue("close", timeout, new Runnable() { @Override public void run() { closeTask(null); } });
    }

    public void close(Observer<AirMediaSessionManager> observer) {
        queue(this, "close", observer, new TaskScheduler.ObservableTask<AirMediaSessionManager>() { @Override public void run(Observer<AirMediaSessionManager> value) { closeTask(value); } });
    }

    private void closeTask(Observer<AirMediaSessionManager> observer) {
        try {
            manager_.close();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.close  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.close  EXCEPTION  " + e);
        }
    }

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

    public void stop() { stop(DefaultTimeout); }

    public void stop(TimeSpan timeout) {
        queue("stop", timeout, new Runnable() { @Override public void run() { stopTask(null); } });
    }

    public void stop(Observer<AirMediaSessionManager> observer) {
        queue(this, "stop", observer, new TaskScheduler.ObservableTask<AirMediaSessionManager>() { @Override public void run(Observer<AirMediaSessionManager> value) { stopTask(value); } });
    }

    private void stopTask(Observer<AirMediaSessionManager> observer) {
        try {
            manager_.stop();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.stop  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Manager", -1006, "task.stop  EXCEPTION  " + e);
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
        if (USE_SYNCHRONOUS_ADD_SESSION_EVENT) {
            added().raise(this, session);
        } else {
            scheduler().raise(added(), self(), session);
        }
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
