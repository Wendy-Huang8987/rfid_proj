package com.example.gomore_proj;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import java.lang.ref.WeakReference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.annotation.Nullable;
import com.zebra.rfid.api3.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment {
    private static Readers readers;
    private static ArrayList<ReaderDevice> availableRFIDReaderList;
    private static ReaderDevice readerDevice;
    private static RFIDReader reader;
    private static final String TAG = "DEMO";

    private EventHandler eventHandler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tagCountTextView, uniqueTagCountTextView;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tagCountTextView = view.findViewById(R.id.tagCountTextView);
        uniqueTagCountTextView = view.findViewById(R.id.uniqueTagCountTextView);

        // 初始化 Readers
        if (readers == null) {
            readers = new Readers(getContext(), ENUM_TRANSPORT.BLUETOOTH);
        }

        // 啟動藍牙設備連接任務
        ConnectReaderTask connectReaderTask = new ConnectReaderTask(this);
        connectReaderTask.execute();  // 開始執行連接任務
    }
    private static class  ConnectReaderTask {
        private final WeakReference<FirstFragment> fragmentReference;

        ConnectReaderTask(FirstFragment fragment) {
            fragmentReference = new WeakReference<>(fragment);
        }

        void execute() {
            FirstFragment fragment = fragmentReference.get();
            if (fragment != null) {
                fragment.executorService.execute(() -> {
                    boolean isConnected = fragment.connectToReader();

                    // 在連接失敗時重試
                    if (!isConnected) {
                        fragment.mainHandler.postDelayed(() -> execute(), 3000); // 3秒後重試
                    }
                });
            }
        }
    }
    private boolean connectToReader() {
        boolean isConnected = false;

        try {
            if (readers == null) {
                readers = new Readers(getContext(), ENUM_TRANSPORT.BLUETOOTH);
            }

            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();

            if (availableRFIDReaderList != null && !availableRFIDReaderList.isEmpty()) {
                readerDevice = availableRFIDReaderList.get(0);
                reader = readerDevice.getRFIDReader();

                if (!reader.isConnected()) {
                    reader.connect();
                    configureReader();
                    isConnected = true;
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Reader Reconnected", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error connecting to reader: ", e);
        }

        return isConnected;
    }
    // Read/Status Notify handler
    public class EventHandler implements RfidEventsListener {
        private int totalTagCount = 0;
        private int uniqueTagCount = 0;
        private final ArrayList<String> uniqueTags = new ArrayList<>();

        @Override
        public void eventReadNotify(RfidReadEvents e) {
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null) {
                for (TagData tag : myTags) {
                    totalTagCount++;

                    if (!uniqueTags.contains(tag.getTagID())) {
                        uniqueTags.add(tag.getTagID());
                        uniqueTagCount++;
                    }
                }
                mainHandler.post(() -> updateTagCounts(totalTagCount, uniqueTagCount));
            }
        }

        @Override
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {//處理 RFID 讀取器的狀態事件
           //檢查觸發器事件，使用 getStatusEventType() 方法來檢查事件類型
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {// 確定是否為手持式觸發(HANDHELD_TRIGGER_EVENT)事件
                //判斷觸發器狀態，
                boolean isPressed = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED;
                executorService.execute(() -> {
                    try {
                        if (isPressed) {
                            reader.Actions.Inventory.perform();
                        } else {
                            reader.Actions.Inventory.stop();
                        }
                    } catch (InvalidUsageException | OperationFailureException e) {
                        Log.e(TAG, "Error in InventoryTask: ", e);
                    }
                });
            }
        }
    }
    @SuppressLint("SetTextI18n")
    private void updateTagCounts(int totalTagCount, int uniqueTagCount) {
        tagCountTextView.setText("Total Tags: " + totalTagCount);
        uniqueTagCountTextView.setText("Unique Tags: " + uniqueTagCount);
    }
    private void configureReader() {
        if (reader.isConnected()) {
            TriggerInfo triggerInfo = new TriggerInfo();
            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            try {
                // 設置區域
                // Get and Set regulatory configuration settings
                RegulatoryConfig regulatoryConfig = reader.Config.getRegulatoryConfig();
                RegionInfo regionInfo = reader.ReaderCapabilities.SupportedRegions.getRegionInfo(1);
                regulatoryConfig.setRegion(regionInfo.getRegionCode());
                regulatoryConfig.setIsHoppingOn(regionInfo.isHoppingConfigurable());
                regulatoryConfig.setEnabledChannels(regionInfo.getSupportedChannels());
                reader.Config.setRegulatoryConfig(regulatoryConfig);

                // 設定天線功率
                Antennas.AntennaRfConfig antennaRfConfig = reader.Config.Antennas.getAntennaRfConfig(1);
                antennaRfConfig.setTransmitPowerIndex(270); // 27 dBm
                reader.Config.Antennas.setAntennaRfConfig(1, antennaRfConfig);

                // 設定 Link Profile
                antennaRfConfig.setrfModeTableIndex(2); // 使用適合台灣的配置
                reader.Config.Antennas.setAntennaRfConfig(1, antennaRfConfig);

                // receive events from reader
                if (eventHandler == null)
                    eventHandler = new EventHandler();
                reader.Events.addEventsListener(eventHandler);
                // HH event
                reader.Events.setHandheldEvent(true);
                // tag event with tag data
                reader.Events.setTagReadEvent(true);
                // application will collect tag using getReadTags API
                reader.Events.setAttachTagDataWithReadEvent(false);
                // set trigger mode as rfid so scanner beam will not come
                reader.Config.setTriggerMode(ENUM_TRIGGER_MODE.RFID_MODE, true);
                // set start and stop triggers
                reader.Config.setStartTrigger(triggerInfo.StartTrigger);
                reader.Config.setStopTrigger(triggerInfo.StopTrigger);
            } catch (InvalidUsageException e) {
                e.printStackTrace();
            } catch (OperationFailureException e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (reader != null) {
                reader.Events.removeEventsListener(new EventHandler());
                reader.disconnect();
                Toast.makeText(getContext(), "Disconnecting reader", Toast.LENGTH_LONG).show();
                reader = null;
                readers.Dispose();
                readers = null;
            }
        } catch (InvalidUsageException | OperationFailureException e) {
            Log.e(TAG, "Error disconnecting reader: ", e);
        }
    }


}