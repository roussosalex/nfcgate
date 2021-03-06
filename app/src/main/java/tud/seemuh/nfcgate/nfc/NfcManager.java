package tud.seemuh.nfcgate.nfc;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import tud.seemuh.nfcgate.R;
import tud.seemuh.nfcgate.gui.fragments.CloneFragment;
import tud.seemuh.nfcgate.network.HighLevelNetworkHandler;
import tud.seemuh.nfcgate.nfc.config.Technologies;
import tud.seemuh.nfcgate.nfc.hce.ApduService;
import tud.seemuh.nfcgate.nfc.hce.DaemonConfiguration;
import tud.seemuh.nfcgate.nfc.reader.DesfireWorkaround;
import tud.seemuh.nfcgate.nfc.reader.IsoDepReader;
import tud.seemuh.nfcgate.nfc.reader.NFCTagReader;
import tud.seemuh.nfcgate.nfc.reader.NfcAReader;
import tud.seemuh.nfcgate.nfc.reader.NfcBReader;
import tud.seemuh.nfcgate.nfc.reader.NfcFReader;
import tud.seemuh.nfcgate.nfc.reader.NfcVReader;
import tud.seemuh.nfcgate.util.NfcComm;
import tud.seemuh.nfcgate.util.Utils;
import tud.seemuh.nfcgate.util.filter.FilterManager;
import tud.seemuh.nfcgate.util.sink.SinkManager;

/**
 * The NFC Manager is responsible for all NFC Interactions.
 */
public class NfcManager {
    private final String TAG = "NfcManager";

    // NFC Objects
    private Tag mTag;
    private NFCTagReader mReader;
    private ApduService mApduService;

    // Sink Manager
    private SinkManager mSinkManager;
    private Thread mSinkManagerThread;
    private BlockingQueue<NfcComm> mSinkManagerQueue;

    // Filter Manager
    private FilterManager mFilterManager;

    // Network Handler
    private HighLevelNetworkHandler mNetworkHandler;

    // Workaround
    private DesfireWorkaround mBroadcomWorkaroundRunnable;
    private Thread mBroadcomWorkaroundThread;

    // Context
    private Context mContext;

    private static NfcManager mInstance = null;


    // Constructor
    public NfcManager () {
        mInstance = this;
    }


    public static NfcManager getInstance() {
        if (mInstance == null) mInstance = new NfcManager();
        return mInstance;
    }

    public void setContext(Context ctx) {
        mContext = ctx;
    }


    // Private helper functions
    private void notifySinkManager(NfcComm nfcdata) {
        if (mSinkManagerQueue == null) {
            Log.e(TAG, "notifySinkManager: Trying to notify, but Queue is still null. Ignoring.");
            return;
        }

        //we only want to see AntiCol data in clone mode, discard everything else
        if ( ! CloneFragment.getInstance().isCloneModeEnabled()) {
            try {
                mSinkManagerQueue.add(nfcdata);
            } catch (IllegalStateException e) {
                Log.e(TAG, "notifySinkManager: Tried to notify sm, but queue is full. Ignoring.");
            }
        } else if(nfcdata.getType().equals(NfcComm.Type.AnticolBytes)) {
            try {
                mSinkManagerQueue.add(nfcdata);
            } catch (IllegalStateException e) {
                Log.e(TAG, "notifySinkManager: Tried to notify sm, but queue is full. Ignoring.");
            }
        }
    }


    private NfcComm handleAnticolDataCommon(NfcComm nfcdata) {
        /*Log.d(TAG, "handleAnticolDataCommon: Pre-Filter: " +
                Utils.bytesToHex(nfcdata.getUid())  + " - " +
                Utils.bytesToHex(nfcdata.getAtqa()) + " - " +
                Utils.bytesToHex(nfcdata.getSak())  + " - " +
                Utils.bytesToHex(nfcdata.getHist()));*/
        if (mFilterManager != null) {
            nfcdata = mFilterManager.filterAnticolData(nfcdata);
        }

        notifySinkManager(nfcdata);

        /*Log.d(TAG, "handleAnticolDataCommon: Post-Filter: " +
                Utils.bytesToHex(nfcdata.getUid())  + " - " +
                Utils.bytesToHex(nfcdata.getAtqa()) + " - " +
                Utils.bytesToHex(nfcdata.getSak())  + " - " +
                Utils.bytesToHex(nfcdata.getHist()));*/
        return nfcdata;
    }


    private NfcComm handleHceDataCommon(NfcComm nfcdata) {
        Log.d(TAG, "handleHceDataCommon: Pre-Filter: " +
                Utils.bytesToHex(nfcdata.getData()));
        if (mFilterManager != null) {
            nfcdata = mFilterManager.filterHCEData(nfcdata);
        }

        notifySinkManager(nfcdata);

        Log.d(TAG, "handleHceDataCommon: Post-Filter: " +
                Utils.bytesToHex(nfcdata.getData()));
        return nfcdata;
    }


    private NfcComm handleCardDataCommon(NfcComm nfcdata) {
        Log.d(TAG, "handleCardDataCommon: Pre-Filter: " +
                Utils.bytesToHex(nfcdata.getData()));
        if (mFilterManager != null) {
            nfcdata = mFilterManager.filterCardData(nfcdata);
        }

        notifySinkManager(nfcdata);

        Log.d(TAG, "handleCardDataCommon: Post-Filter: " +
                Utils.bytesToHex(nfcdata.getData()));
        return nfcdata;
    }

    NFCTagReader fromTag(Tag tag) {
        List<String> technologies = Arrays.asList(tag.getTechList());

        // look for higher layer technology
        if (technologies.contains(Technologies.IsoDep)) {
            // an IsoDep tag can be backed by either NfcA or NfcB technology
            if (technologies.contains(Technologies.A))
                return new IsoDepReader(tag, NfcA.get(tag));
            else if (technologies.contains(Technologies.B))
                return new IsoDepReader(tag, NfcB.get(tag));
            else
                Log.e(TAG, "Unknown tag technology backing IsoDep" +
                        TextUtils.join(", ", technologies));
        }

        for (String tech : technologies) {
            switch (tech) {
                case Technologies.A:
                    return new NfcAReader(tag);
                case Technologies.B:
                    return new NfcBReader(tag);
                case Technologies.F:
                    return new NfcFReader(tag);
                case Technologies.V:
                    return new NfcVReader(tag);
            }
        }

        return null;
    }

    // Reference setters
    /**
     * Set the Reference to the NFC Tag
     * @param tag The NFC Tag object
     */
    public void setTag(Tag tag) {
        mTag = tag;
        mReader = fromTag(tag);

        if (mReader != null)
            Log.i(TAG, "setTag: Chose " + mReader.toString() + "technology");

        if (mReader != null && mNetworkHandler != null) {
            // Start the workaround thread, if needed
            startWorkaround();

            mNetworkHandler.sendAnticol(getAnticolData());

            // Notify partner about the newly detected card
            // This may lead to error messages if we are not already in a session
            mNetworkHandler.notifyCardFound();
        } else if (mReader != null) {
            Log.i(TAG, "setTag: Got supported tag, but no network handler is set. Doing nothing");
        } else {
            Log.e(TAG, "setTag: Tag not supported");
        }
    }


    /**
     * Set the Reference to the ApduService
     * @param apduService The ApduService object
     */
    public void setApduService(ApduService apduService) {
        mApduService = apduService;
        //we dont want the network active, when clone mode is on
        if( ! CloneFragment.getInstance().isCloneModeEnabled()) {
            mNetworkHandler.notifyReaderFound();
        }
    }


    /**
     * Called when the APDU service is disconnected
     */
    public void unsetApduService() {
        mApduService = null;

        //we dont want the network active, when clone mode is on
        if( ! CloneFragment.getInstance().isCloneModeEnabled()) {
            mNetworkHandler.notifyReaderRemoved();
        }
    }


    /**
     * Set the Reference to the SinkManager
     * @param sinkManager The SinkManager object
     * @param smq The BlockingQueue connected with the SinkManager
     */
    public void setSinkManager(SinkManager sinkManager, BlockingQueue<NfcComm> smq) {
        mSinkManager = sinkManager;
        mSinkManagerQueue = smq;
    }

    public void unsetSinkManager() {
        mSinkManager = null;
        mSinkManagerQueue = null;
    }

    public SinkManager getSinkManager() {
        return mSinkManager;
    }


    /**
     * Set the reference to the FilterManager
     * @param filterManager The FilterManager object
     */
    public void setFilterManager(FilterManager filterManager) {
        mFilterManager = filterManager;
    }


    /**
     * Set the reference to the HighLevelNetworkHandler
     * @param netHandler The HighLevelNetworkHandler object
     */
    public void setNetworkHandler(HighLevelNetworkHandler netHandler) {
        mNetworkHandler = netHandler;
    }


    // NFC Interactions
    /**
     * Send NFC data to the card
     * @param nfcdata NFcComm object containing the message for the card
     */
    public void sendToCard(NfcComm nfcdata) {
        if (mReader.isConnected()) {
            nfcdata = handleHceDataCommon(nfcdata);

            // Communicate with card
            byte[] reply = mReader.sendCmd(nfcdata.getData());
            if (reply == null) {
                mReader.closeConnection();
                mNetworkHandler.disconnectCardWorkaround();
                mNetworkHandler.notifyNFCNotConnected();
            } else {
                // Create NfcComm object and pass it through filter and sinks
                NfcComm nfcreply = new NfcComm(NfcComm.Source.CARD, reply);
                nfcreply = handleCardDataCommon(nfcreply);

                // Send message
                mNetworkHandler.sendAPDUReply(nfcreply);
            }
        } else {
            Log.e(TAG, "HandleNFCData: No NFC connection active");
            // There is no connected NFC device
            mNetworkHandler.notifyNFCNotConnected();
        }
    }


    /**
     * Send NFC data to the Reader
     * @param nfcdata NfcComm object containing the message for the Reader
     */
    public void sendToReader(NfcComm nfcdata) {
        if (mApduService != null) {
            // Pass data through sinks and filters
            nfcdata = handleCardDataCommon(nfcdata);

            // Send data to the Reader device
            mApduService.sendResponse(nfcdata.getData());
        } else {
            Log.e(TAG, "HandleNFCData: Received a message for a reader, but no APDU instance active.");
            mNetworkHandler.notifyNFCNotConnected();
        }

    }


    // HCE Handler
    /**
     * Called by the ApduService when a new APDU is received
     * @param nfcdata An NfcComm object containing the APDU
     */
    public void handleHCEData(NfcComm nfcdata) {
        Log.d(TAG, "handleHCEData: Got data from ApduService");
        nfcdata = handleHceDataCommon(nfcdata);

        //we dont want the network active, when clone mode is on
        if( ! CloneFragment.getInstance().isCloneModeEnabled()) {
            mNetworkHandler.sendAPDUMessage(nfcdata);
        }
    }


    // Anticol
    /**
     * Get the Anticollision data of the attached card
     * @return NfcComm object with anticol data
     */
    public NfcComm getAnticolData() {
        NfcComm anticol = new NfcComm(mReader.getConfig());

        // Pass NfcComm object through Filter
        anticol = handleAnticolDataCommon(anticol);

        // Return NfcComm object w/ anticol data
        return anticol;
    }


    /**
     * Set the Anticollision data in the native code patch
     * @param anticol NfcComm object containing the Anticol data
     */
    public void setAnticolData(NfcComm anticol) {
        anticol = handleAnticolDataCommon(anticol);

        // Enable the Native Code Patch
        DaemonConfiguration.getInstance().uploadConfiguration(anticol.getConfig().build());
        DaemonConfiguration.getInstance().enablePatch();

        Log.i(TAG, "setAnticolData: Patch enabled");
    }


    // Workaround Handling
    /**
     * Start workaround, if needed
     */
    private void startWorkaround() {
        // Check if the device is running a specific Broadcom chipset (used in the Nexus 4, for example)
        // The drivers for this chipset contain a bug which lead to DESFire cards being set into a specific mode
        // We want to avoid that, so we use a workaround. This starts another thread that prevents
        // the keepalive function from running and thus prevents it from setting the mode of the card
        // Other devices work fine without this workaround, so we only activate it on bugged chipsets
        // TODO Only activate for DESFire cards
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean workaroundDisabled = pref.getBoolean(mContext.getString(R.string.pref_key_workaround_off), false);
        if (DesfireWorkaround.workaroundNeeded() && !workaroundDisabled) {
            Log.i(TAG, "StartWorkaround: Problematic broadcom chip found, activate workaround");

            // Initialize a runnable object
            mBroadcomWorkaroundRunnable = new DesfireWorkaround(mTag);

            // Start up a new thread
            mBroadcomWorkaroundThread = new Thread(mBroadcomWorkaroundRunnable);
            mBroadcomWorkaroundThread.start();

            // Notify the Handler
            if (mNetworkHandler != null) mNetworkHandler.notifyCardWorkaroundConnected();
        } else if (!DesfireWorkaround.workaroundNeeded()) {
            Log.i(TAG, "StartWorkaround: No problematic broadcom chipset found, leaving workaround inactive");
        } else {
            Log.i(TAG, "StartWorkaround: Workaround disabled in settings.");
        }
    }


    /**
     * Stop the Broadcom Workaround thread, if it exists.
     */
    public void stopWorkaround() {
        Log.i(TAG, "stopWorkaround: Workaround thread stopping");
        if (mBroadcomWorkaroundThread != null) mBroadcomWorkaroundThread.interrupt();
        mBroadcomWorkaroundThread = null;
        mBroadcomWorkaroundRunnable = null;
    }


    /**
     * Shut down the NfcManager instance.
     */
    public void shutdown() {
        Log.i(TAG, "shutdown: Stopping workaround, closing connections");
        stopWorkaround();
        if (mReader != null) mReader.closeConnection();
        mReader = null;
        if (mSinkManagerThread != null) mSinkManagerThread.interrupt();
        mSinkManagerQueue = null;
        mSinkManager = null;
        mSinkManagerThread = null;
    }


    /**
     * Start up the NfcManager and related services.
     */
    public void start() {
        if(mSinkManagerThread == null) {
            Log.i(TAG, "start: Starting SinkManager Thread");
            mSinkManagerThread = new Thread(mSinkManager);
            mSinkManagerThread.start();
        } else {
            Log.i(TAG, "start: SinkManager Thread already started");
        }
    }
}