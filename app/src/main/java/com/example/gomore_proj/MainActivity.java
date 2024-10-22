package com.example.gomore_proj;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import android.view.View;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.zebra.rfid.api3.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    private static Readers readers;
    private static ArrayList<ReaderDevice> availableRFIDReaderList;
    private static ReaderDevice readerDevice;
    private static RFIDReader reader;
    private static String TAG = "DEMO";
    TextView textView;
    private EventHandler eventHandler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        //.setAnchorView(R.id.fab)
                        .setAction("Action", null).show();
            }
        });
        //
        //UI
        textView = (TextView) findViewById(R.id.TagText);
        // SDK
        if (readers == null) {
            readers = new Readers(this, ENUM_TRANSPORT.SERVICE_SERIAL);
        }

        new ConnectReaderTask(this).execute();
    }
    private static class ConnectReaderTask {
        private final WeakReference<MainActivity> activityReference;

        ConnectReaderTask(MainActivity activity) {
            activityReference = new WeakReference<>(activity);
        }

        void execute() {
            MainActivity activity = activityReference.get();
            if (activity != null) {
                ExecutorService executorService = activity.executorService;
                Handler mainHandler = activity.mainHandler;

                executorService.execute(() -> {
                    boolean isConnected = false;

                    try {
                        // Check if readers are initialized
                        if (readers != null && readers.GetAvailableRFIDReaderList() != null) {
                            availableRFIDReaderList = readers.GetAvailableRFIDReaderList();

                            // Check if list is not empty
                            if (!availableRFIDReaderList.isEmpty()) {
                                readerDevice = (ReaderDevice) availableRFIDReaderList.get(0);
                                reader = readerDevice.getRFIDReader();

                                if (!reader.isConnected()) {
                                    reader.connect();
                                    activity.ConfigureReader();
                                    isConnected = true;
                                }
                            }
                        }
                    } catch (InvalidUsageException | OperationFailureException e) {
                        Log.e(TAG, "Error connecting reader: ", e);
                    }

                    final boolean finalIsConnected = isConnected;
                    mainHandler.post(() -> {
                        MainActivity activityInner = activityReference.get();
                        if (activityInner != null) {
                            if (finalIsConnected) {
                                Toast.makeText(activityInner.getApplicationContext(), "Reader Connected", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(activityInner.getApplicationContext(), "Reader Connection Failed", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                });
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    private void ConfigureReader() {
        if (reader.isConnected()) {
            TriggerInfo triggerInfo = new TriggerInfo();
            triggerInfo.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
            triggerInfo.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
            try {
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
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (reader != null) {
                reader.Events.removeEventsListener(eventHandler);
                reader.disconnect();
                Toast.makeText(getApplicationContext(), "Disconnecting reader", Toast.LENGTH_LONG).show();
                reader = null;
                readers.Dispose();
                readers = null;
            }
        } catch (InvalidUsageException e) {
            e.printStackTrace();
        } catch (OperationFailureException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Read/Status Notify handler
    // Implement the RfidEventsListener class to receive event notifications
    public class EventHandler implements RfidEventsListener {
        // Read Event Notification
        public void eventReadNotify(RfidReadEvents e) {
            TagData[] myTags = reader.Actions.getReadTags(100);
            if (myTags != null) {
                for (int index = 0; index < myTags.length; index++) {
                    Log.d(TAG, "Tag ID " + myTags[index].getTagID());
                }
            }
        }

        @Override
        public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
            Log.d(TAG, "Status Notification: " + rfidStatusEvents.StatusEventData.getStatusEventType());
            if (rfidStatusEvents.StatusEventData.getStatusEventType() == STATUS_EVENT_TYPE.HANDHELD_TRIGGER_EVENT) {
                boolean isPressed = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent() == HANDHELD_TRIGGER_EVENT_TYPE.HANDHELD_TRIGGER_PRESSED;
                executorService.execute(new InventoryTask(isPressed));
            }
        }
    }
    private static class InventoryTask implements Runnable {
        private final boolean start;

        InventoryTask(boolean start) {
            this.start = start;
        }

        @Override
        public void run() {
            try {
                if (start) {
                    reader.Actions.Inventory.perform();
                } else {
                    reader.Actions.Inventory.stop();
                }
            } catch (InvalidUsageException | OperationFailureException e) {
                Log.e(TAG, "Error in InventoryTask: ", e);
            }
        }
    }

}