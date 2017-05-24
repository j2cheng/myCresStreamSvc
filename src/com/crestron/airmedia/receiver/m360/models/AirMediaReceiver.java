package com.crestron.airmedia.receiver.m360.models;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.crestron.airmedia.receiver.m360.IAirMediaReceiver;
import com.crestron.airmedia.receiver.m360.IAirMediaReceiverObserver;
import com.crestron.airmedia.receiver.m360.IAirMediaSession;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManager;
import com.crestron.airmedia.receiver.m360.IAirMediaSessionManagerObserver;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverLoadedState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverMirroringAssist;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverResolutionMode;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaReceiverState;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPosition;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSessionScreenPositionLayout;
import com.crestron.airmedia.receiver.m360.ipc.AirMediaSize;
import com.crestron.airmedia.utilities.Common;
import com.crestron.airmedia.utilities.TaskScheduler;
import com.crestron.airmedia.utilities.delegates.MulticastChangedDelegate;
import com.crestron.airmedia.utilities.delegates.MulticastMessageDelegate;
import com.crestron.airmedia.utilities.delegates.Observer;
import com.crestron.airmedia.utilities.TimeSpan;


public class AirMediaReceiver extends AirMediaBase {
    public static final String TAG = "airmedia.receiver";
    public static final String AIRMEDIA_SERVICE_PACKAGE = "com.crestron.airmedia.receiver.m360";
    public static final String AIRMEDIA_SERVICE_BIND = "com.crestron.airmedia.receiver.m360.BIND";

    public AirMediaReceiver(IAirMediaReceiver receiver) {
        super(new TaskScheduler(TAG));
        receiver_ = receiver;
        try {
            receiver_.register(observer_);
            sessionManager_ = new AirMediaSessionManager(receiver.sessionManager(), scheduler());
            loaded_ = receiver.getLoadedState();
            version_ = receiver.getVersion();
            product_ = receiver.getProduct();
            state_ = receiver.getState();
            adapterAddress_ = receiver.getAdapterAddress();
            serverVersion_ = receiver.getServerVersion();
            serverName_ = receiver.getServerName();
            serverPassword_ = receiver.getServerPassword();
            bonjour_ = receiver.getBonjour();
            displayResolution_ = receiver.getDisplayResolution();
            maxResolution_ = receiver.getMaxResolution();
            forceCompatibility_ = receiver.getForceCompatibility();
            debugMode_ = receiver.getDebugMode();
            mirroringAssist_ = receiver.getMirroringAssist();
        } catch (RemoteException e) {
            Log.e(TAG, "");
        }
    }

    private AirMediaReceiver self() {
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// DEFINITIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final IAirMediaReceiverObserver observer_ = new IAirMediaReceiverObserver.Stub() {
        @Override
        public void onLoadedChanged(AirMediaReceiverLoadedState from, AirMediaReceiverLoadedState to) throws RemoteException {
            loaded(from, to);
        }

        @Override
        public void onProductChanged(int from, int to) throws RemoteException {
            product_ = to;
            scheduler().raise(productChanged(), self(), from, to);
        }

        @Override
        public void onStateChanged(AirMediaReceiverState from, AirMediaReceiverState to) throws RemoteException {
            state_ = to;
            scheduler().raise(stateChanged(), self(), from, to);
        }

        @Override
        public void onMirroringAssistChanged(AirMediaReceiverMirroringAssist from, AirMediaReceiverMirroringAssist to) throws RemoteException {
            mirroringAssist_ = to;
            scheduler().raise(mirroringAssistChanged(), self(), from, to);
        }

        @Override
        public void onAdapterAddressChanged(String from, String to) throws RemoteException {
            adapterAddress_ = to;
            scheduler().raise(adapterAddressChanged(), self(), from, to);
        }

        @Override
        public void onServerNameChanged(String from, String to) throws RemoteException {
            serverName_ = to;
            scheduler().raise(serverNameChanged(), self(), from, to);
        }

        @Override
        public void onServerPasswordChanged(String from, String to) throws RemoteException {
            serverPassword_ = to;
            scheduler().raise(serverPasswordChanged(), self(), from, to);
        }

        @Override
        public void onBonjourChanged(boolean to) throws RemoteException {
            bonjour_ = to;
            scheduler().raise(bonjourChanged(), self(), to);
        }

        @Override
        public void onDisplayResolutionChanged(AirMediaSize from, AirMediaSize to) {
            displayResolution_ = to;
            scheduler().raise(displayResolutionChanged(), self(), from, to);
        }

        @Override
        public void onMaxResolutionChanged(AirMediaReceiverResolutionMode from, AirMediaReceiverResolutionMode to) throws RemoteException {
            maxResolution_ = to;
            scheduler().raise(maxResolutionChanged(), self(), from, to);
        }

        @Override
        public void onForceCompatibilityChanged(boolean to) throws RemoteException {
            forceCompatibility_ = to;
            scheduler().raise(forceCompatibilityChanged(), self(), to);
        }

        @Override
        public void onDebugModeChanged(boolean to) throws RemoteException {
            debugMode_ = to;
            scheduler().raise(debugModeChanged(), self(), to);
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// FIELDS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private IAirMediaReceiver receiver_;

    private String version_;
    private int product_ = 0;
    private AirMediaReceiverLoadedState loaded_ = AirMediaReceiverLoadedState.Unloaded;

    private AirMediaReceiverState state_ = AirMediaReceiverState.Stopped;

    private String adapterAddress_ = null;
    private String serverVersion_ = "0.0.0.0";
    private String serverName_ = "";
    private String serverPassword_ = "";
    private boolean bonjour_ = true;
    private AirMediaSize displayResolution_ = AirMediaSize.Zero;
    private AirMediaReceiverResolutionMode maxResolution_ = AirMediaReceiverResolutionMode.Max1080P;
    private boolean forceCompatibility_ = false;
    private boolean debugMode_ = false;
    private AirMediaReceiverMirroringAssist mirroringAssist_ = new AirMediaReceiverMirroringAssist();

    private AirMediaSessionManager sessionManager_;

    private final MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverLoadedState> loadedChanged_ = new MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverLoadedState>();
    private final MulticastChangedDelegate<AirMediaReceiver, Integer> productChanged_ = new MulticastChangedDelegate<AirMediaReceiver, Integer>();
    private final MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverState> stateChanged_ = new MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverState>();
    private final MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverMirroringAssist> mirroringAssistChanged_ = new MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverMirroringAssist>();
    private final MulticastChangedDelegate<AirMediaReceiver, String> adapterAddressChanged_ = new MulticastChangedDelegate<AirMediaReceiver, String>();
    private final MulticastChangedDelegate<AirMediaReceiver, String> serverNameChanged_ = new MulticastChangedDelegate<AirMediaReceiver, String>();
    private final MulticastChangedDelegate<AirMediaReceiver, String> serverPasswordChanged_ = new MulticastChangedDelegate<AirMediaReceiver, String>();
    private final MulticastMessageDelegate<AirMediaReceiver, Boolean> bonjourChanged_ = new MulticastMessageDelegate<AirMediaReceiver, Boolean>();
    private final MulticastChangedDelegate<AirMediaReceiver, AirMediaSize> displayResolutionChanged_ = new MulticastChangedDelegate<AirMediaReceiver, AirMediaSize>();
    private final MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverResolutionMode> maxResolutionChanged_ = new MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverResolutionMode>();
    private final MulticastMessageDelegate<AirMediaReceiver, Boolean> forceCompatibilityChanged_ = new MulticastMessageDelegate<AirMediaReceiver, Boolean>();
    private final MulticastMessageDelegate<AirMediaReceiver, Boolean> debugModeChanged_ = new MulticastMessageDelegate<AirMediaReceiver, Boolean>();

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// PROPERTIES
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public AirMediaReceiverLoadedState loaded() { return loaded_; }

    /// PRODUCT

    public int product() { return product_; }

    public void product(int value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<Integer>() { @Override public void update(Integer v) { updateProduct(v); } }, value);
    }

    /// APP VERSION

    public String version() { return version_; }

    /// SESSION MANAGER

    public AirMediaSessionManager sessionManager() { return sessionManager_; }

    /// STATE

    public AirMediaReceiverState state() { return state_; }

    /// ADAPTER ADDRESS TO BIND TO

    public String adapterAddress() { return adapterAddress_; }

    public void adapterAddress(String value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<String>() { @Override public void update(String v) { updateAdapterAddress(v); } }, value);
    }

    /// SERVER VERSION

    public String serverVersion() { return serverVersion_; }

    /// SERVER NAME

    public String serverName() { return serverName_; }

    public void serverName(String value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<String>() { @Override public void update(String v) { updateServerName(v); } }, value);
    }

    /// SERVER PASSWORD

    public String serverPassword() { return serverPassword_; }

    public void serverPassword(String value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<String>() { @Override public void update(String v) { updateServerPassword(v); } }, value);
    }

    /// BONJOUR

    public boolean bonjour() { return bonjour_; }

    public void bonjour(boolean value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<Boolean>() { @Override public void update(Boolean v) { updateBonjour(v); } }, value);
    }

    /// DISPLAY RESOLUTION

    public AirMediaSize displayResolution() { return displayResolution_; }

    public void displayResolution(AirMediaSize value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<AirMediaSize>() { @Override public void update(AirMediaSize v) { updateDisplayResolution(v); } }, value);
    }

    /// MAX RESOLUTION

    public AirMediaReceiverResolutionMode maxResolution() { return maxResolution_; }

    public void maxResolution(AirMediaReceiverResolutionMode value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<AirMediaReceiverResolutionMode>() { @Override public void update(AirMediaReceiverResolutionMode v) { updateMaxResolution(v); } }, value);
    }

    /// FORCE COMPATIBILITY

    public boolean forceCompatibility() { return forceCompatibility_; }

    public void forceCompatibility(boolean value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<Boolean>() { @Override public void update(Boolean v) { updateForceCompatibility(v); } }, value);
    }

    /// DEBUG MODE

    public boolean debugMode() { return debugMode_; }

    public void debugMode(boolean value) {
        scheduler().update(new TaskScheduler.PropertyUpdater<Boolean>() { @Override public void update(Boolean v) { updateDebugMode(v); } }, value);
    }

    /// MIRRORING ASSIST

    public AirMediaReceiverMirroringAssist mirroringAssist() { return mirroringAssist_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// EVENTS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverLoadedState> loadedChanged() { return loadedChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, Integer> productChanged() { return productChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverState> stateChanged() { return stateChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverMirroringAssist> mirroringAssistChanged() { return mirroringAssistChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, String> adapterAddressChanged() { return adapterAddressChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, String> serverNameChanged() { return serverNameChanged_; }
    public MulticastChangedDelegate<AirMediaReceiver, String> serverPasswordChanged() { return serverPasswordChanged_; }

    public MulticastMessageDelegate<AirMediaReceiver, Boolean> bonjourChanged() { return bonjourChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, AirMediaSize> displayResolutionChanged() { return displayResolutionChanged_; }

    public MulticastChangedDelegate<AirMediaReceiver, AirMediaReceiverResolutionMode> maxResolutionChanged() { return maxResolutionChanged_; }

    public MulticastMessageDelegate<AirMediaReceiver, Boolean> forceCompatibilityChanged() { return forceCompatibilityChanged_; }

    public MulticastMessageDelegate<AirMediaReceiver, Boolean> debugModeChanged() { return debugModeChanged_; }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// METHODS
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void initialize() { initialize(DefaultTimeout); }

    public void initialize(TimeSpan timeout) {
        queue("initialize", timeout, new Runnable() { @Override public void run() { initializeTask(null); } });
    }

    public void initialize(Observer<AirMediaReceiver> observer) {
        queue(this, "initialize", observer, new TaskScheduler.ObservableTask<AirMediaReceiver>() { @Override public void run(Observer<AirMediaReceiver> value) { initializeTask(value); } });
    }

    private void initializeTask(Observer<AirMediaReceiver> observer) {
        try {
            receiver_.initialize();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.initialize  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.initialize  EXCEPTION  " + e);
        }
    }

    public void close() { close(DefaultTimeout); }

    public void close(TimeSpan timeout) {
        queue("close", timeout, new Runnable() { @Override public void run() { closeTask(null); } });
    }

    public void close(Observer<AirMediaReceiver> observer) {
        queue(this, "close", observer, new TaskScheduler.ObservableTask<AirMediaReceiver>() { @Override public void run(Observer<AirMediaReceiver> value) { closeTask(value); } });
    }

    private void closeTask(Observer<AirMediaReceiver> observer) {
        try {
            receiver_.close();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.close  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.close  EXCEPTION  " + e);
        }
    }

    public void start() { start(DefaultTimeout); }

    public void start(TimeSpan timeout) {
        queue("start", timeout, new Runnable() { @Override public void run() { startTask(null); } });
    }

    public void start(Observer<AirMediaReceiver> observer) {
        queue(this, "start", observer, new TaskScheduler.ObservableTask<AirMediaReceiver>() { @Override public void run(Observer<AirMediaReceiver> value) { startTask(value); } });
    }

    private void startTask(Observer<AirMediaReceiver> observer) {
        try {
            receiver_.start();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.start  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.start  EXCEPTION  " + e);
        }
    }

    public void stop() { stop(DefaultTimeout); }

    public void stop(TimeSpan timeout) {
        queue("stop", timeout, new Runnable() { @Override public void run() { stopTask(null); } });
    }

    public void stop(Observer<AirMediaReceiver> observer) {
        queue(this, "stop", observer, new TaskScheduler.ObservableTask<AirMediaReceiver>() { @Override public void run(Observer<AirMediaReceiver> value) { stopTask(value); } });
    }

    private void stopTask(Observer<AirMediaReceiver> observer) {
        try {
            receiver_.stop();
            scheduler().raise(observer, this);
        } catch (RemoteException e) {
            handleRemoteException();
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.stop  REMOTE EXCEPTION  " + e);
        } catch (Exception e) {
            scheduler().raiseError(observer, this, "AirMedia.Receiver", -1006, "task.stop  EXCEPTION  " + e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    /// INTERNAL
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private void loaded(AirMediaReceiverLoadedState from, AirMediaReceiverLoadedState to) {
        IAirMediaReceiver receiver = receiver_;
        try {
            loaded_ = to;
            if (to == AirMediaReceiverLoadedState.Loaded) {
                version_ = receiver.getVersion();
                product_ = receiver.getProduct();
                state_ = receiver.getState();
                adapterAddress_ = receiver.getAdapterAddress();
                serverVersion_ = receiver.getServerVersion();
                serverName_ = receiver.getServerName();
                serverPassword_ = receiver.getServerPassword();
                bonjour_ = receiver.getBonjour();
                maxResolution_ = receiver.getMaxResolution();
                forceCompatibility_ = receiver.getForceCompatibility();
                debugMode_ = receiver.getDebugMode();
                mirroringAssist_ = receiver.getMirroringAssist();
            }
            scheduler().raise(loadedChanged(), self(), from, to);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.loaded  " + from + "  ==>  " + to + "  EXCEPTION  " + e);
        }
    }

    private void updateProduct(int value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setProduct(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.product  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateAdapterAddress(String value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setAdapterAddress(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.adapter-address  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateServerName(String value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setServerName(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.server-name  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateServerPassword(String value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setServerPassword(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.server-password  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateBonjour(boolean value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setBonjour(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.bonjour  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateDisplayResolution(AirMediaSize value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setDisplayResolution(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.resolution-display  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateMaxResolution(AirMediaReceiverResolutionMode value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setMaxResolution(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.resolution-max  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateForceCompatibility(boolean value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setForceCompatibility(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.force-compatibility  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    private void updateDebugMode(boolean value) {
        try {
            IAirMediaReceiver receiver = receiver_;
            if (receiver == null) return;
            receiver.setDebugMode(value);
        } catch (RemoteException e) {
            Log.e(TAG, "receiver.debug-mode  value= " + value + "  EXCEPTION  " + e);
            handleRemoteException();
        }
    }

    /// REMOTE CONNECTION

    private void handleRemoteException() {
        receiver_ = null;
    }
}
