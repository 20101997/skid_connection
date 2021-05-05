package com.example.connect_rfid;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;


import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.caen.BLEPort.BLEPortEvent;
import com.caen.RFIDLibrary.CAENRFIDBLEConnectionEventListener;
import com.caen.RFIDLibrary.CAENRFIDException;
import com.caen.RFIDLibrary.CAENRFIDPort;
import com.caen.RFIDLibrary.CAENRFIDReader;
import com.caen.RFIDLibrary.CAENRFIDReaderInfo;
import com.caen.VCPSerialPort.VCPSerialPort;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.Semaphore;


public class MainActivity extends AppCompatActivity implements CAENRFIDBLEConnectionEventListener  {



    protected static final int ADD_READER_BT = 1;

    protected static final int DO_INVENTORY = 3;

    protected static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    protected static boolean STARTED = true;
    protected static boolean DESTROYED = false;
    protected static boolean CONNECTION_SUCCESSFUL = false;

    private final ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();
    private SimpleAdapter adapter;

    public static boolean returnFromActivity = false;

    private static boolean exitFromApp = false;

    public static Vector<DemoReader> Readers;

    public static int Selected_Reader;

    private ProgressDialog tcpBtWaitProgressDialog;

    public static VCPSerialPort mVCPPort;


    private final BroadcastReceiver mReceiverUUIDCached = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DESTROYED)
                return;
            String action = intent.getAction();
            assert action != null;
            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                CONNECTION_SUCCESSFUL = true;
            }
        }
    };

    ////////////////////////Bluetooth /////////////////////////////


    private final BroadcastReceiver mReceiverBTDisconnect = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DESTROYED)
                return;
            String action = intent.getAction();
            assert action != null;
            if ((action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) && (!BluetoothAdapter
                    .getDefaultAdapter().isEnabled()))
                    || (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED))) {
                int pos = 0;
                Vector<Integer> toRemove = new Vector<Integer>();
                for (DemoReader demoReader : Readers) {
                    try {
                        if (demoReader.getConnectionType().equals(
                                CAENRFIDPort.CAENRFID_BT)) {
                            data.remove(pos);
                            adapter.notifyDataSetChanged();
                            demoReader.getReader().Disconnect();
                            toRemove.add(pos);
                        }
                    } catch (CAENRFIDException e) {
                        e.printStackTrace();
                    }
                    pos++;
                }
                for (int i = 0; i < toRemove.size(); i++) {
                    Readers.remove(toRemove.get(i).intValue());
                }
                if (!toRemove.isEmpty()) {
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth device disconnected!",
                            Toast.LENGTH_SHORT).show();
                }
                toRemove = null;
            }
        }
    };

/*    private static final String ACTION_USB_PERMISSION =
            "com.example.connect_rfid.USB_PERMISSION";

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mUsbPermissionSemaphore.release();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "permission denied for USB device " + device,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };*/



    @Override
    public void onBLEConnectionEvent(final CAENRFIDReader caenrfidReader, final BLEPortEvent blePortEvent) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {}
        });

    }



    private class BTConnector extends AsyncTask<Object, Boolean, Boolean> {

        private BluetoothSocket sock;
        private CAENRFIDReaderInfo info;
        private String fwrel;

        protected Boolean doInBackground(Object... pars) {
            boolean secure = true;
            boolean no_connection = true;
            CAENRFIDReader r =null;
            //wait if discovery cancelling isn't finished yet (it should not be happen!)
            while(BluetoothAdapter.getDefaultAdapter().isDiscovering()){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (no_connection) {
                try {
                    if (!secure)
                        sock = ((BluetoothDevice) pars[0])
                                .createRfcommSocketToServiceRecord(MY_UUID);
                    else
                        sock = ((BluetoothDevice) pars[0])
                                .createInsecureRfcommSocketToServiceRecord(MY_UUID);
                } catch (IOException e1) {
                    return false;
                }

                r = new CAENRFIDReader();
                try {
                    r.Connect(sock);
                    while (!CONNECTION_SUCCESSFUL) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    CONNECTION_SUCCESSFUL = false;
                    //
                    Log.d("VOOOORRR","\n\nas1 secure:" + (secure?"TRUE":"FALSE") +"\n");
                    try {
                        Thread.sleep(8000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d("VOOOORRR","\n\nas2\n");
                    if(!sock.isConnected()){
                        return false;
                    }
                    try {
                        int byteTodiscard = sock.getInputStream().available();
                        if(byteTodiscard > 0){
                            sock.getInputStream().skip(byteTodiscard);
                            Log.d("VOOOORRR","\n\nas3 bytes discard:"+ Integer.toString(byteTodiscard)+"\n");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //
                    no_connection = false;
                    int state = ((BluetoothDevice) pars[0]).getBondState();
                    while (state != BluetoothDevice.BOND_BONDED) {
                        state = ((BluetoothDevice) pars[0]).getBondState();
                    }
                } catch (CAENRFIDException e) {
                    try {
                        r.Disconnect();
                    } catch (CAENRFIDException ioException) {
                        Log.e(MainActivity.class.getSimpleName(),"Error closing BT Socket");
                    }
                    if (!secure)
                        return false;
                    else{
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                        secure = false;
                    }
                }
            }
            try {
                // r.UnblockReader();
                info = r.GetReaderInfo();
                fwrel = r.GetFirmwareRelease();
                Log.d("VOOOORRR","\n\nas4   GOO!!  \n");
            } catch (CAENRFIDException e) {
                //maybe reader is blocked on a continuous inventory?
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tcpBtWaitProgressDialog.setMessage("Reader seems to be blocked. Trying unlock");
                    }
                });
                try {
                    final boolean wasBlocked = r.ForceAbort(3000);
                    if (wasBlocked) {
                        info = r.GetReaderInfo();
                        fwrel = r.GetFirmwareRelease();
                    } else {
                        r.Disconnect();
                        return false;
                    }
                } catch (CAENRFIDException ignored) {
                    //
                    return false;
                }
            }
            DemoReader dr = new DemoReader(r, info.GetModel(),
                    info.GetSerialNumber(), fwrel, CAENRFIDPort.CAENRFID_BT);
            Readers.add(dr);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                Toast.makeText(getApplicationContext(),
                        "Error during connection...", Toast.LENGTH_SHORT)
                        .show();
            }
            updateReadersList();
            tcpBtWaitProgressDialog.dismiss();
        }
    }
    public void updateReadersList() {
        if (Readers != null) {

            ((ListView) findViewById(R.id.reader_list)).setAdapter(null);
            data.clear();

            for (int i = 0; i < Readers.size(); i++) {
                DemoReader r = Readers.get(i);

                HashMap<String, Object> readerMap = new HashMap<>();

                readerMap
                        .put("image", R.drawable.ic_bt_reader);
                readerMap.put("name", r.getReaderName());
                readerMap.put("info", "Serial: " + r.getSerialNumber()
                        + "\nFirmware: " + r.getFirmwareRelease()
                        + "\nRegulation: " + r.getRegulation());
                data.add(readerMap);
            }
        }
        String[] from = { "image", "name", "info" };
        int[] to = { R.id.reader_image, R.id.reader_name, R.id.reader_info };

        adapter = new SimpleAdapter(getApplicationContext(), data,
                R.layout.list_reader, from, to);
        adapter.notifyDataSetChanged();

        ((ListView) findViewById(R.id.reader_list)).setAdapter(adapter);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

       // Toolbar toolbar = findViewById(R.id.toolbar);
        //toolbar.setTitle(R.string.banner); // ("Easy Controller"); // set Title for Toolbar
        //toolbar.setLogo(R.drawable.logo_s); // set logo for Toolbar
      //  setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("");

        if (!MainActivity.returnFromActivity) {
            Readers = new Vector<>();
        } else
            MainActivity.returnFromActivity = false;

        IntentFilter disc_filt = new IntentFilter( BluetoothDevice.ACTION_ACL_DISCONNECTED );
        this.registerReceiver(mReceiverBTDisconnect, disc_filt);


        IntentFilter disc_filt3 = new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(mReceiverBTDisconnect, disc_filt3);

        IntentFilter disc_filt4 = new IntentFilter(
                BluetoothDevice.ACTION_ACL_CONNECTED);
        this.registerReceiver(mReceiverUUIDCached, disc_filt4);


    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onPostResume() {

        super.onPostResume();
    }

    @Override
    protected void onRestart() {

        super.onRestart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        STARTED = true;
        DESTROYED = false;
        ListView lv = this.findViewById(R.id.reader_list);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Selected_Reader = position;
                Intent do_inventory = new Intent(getApplicationContext(),
                        InventoryActivity.class);
                startActivityForResult(do_inventory, DO_INVENTORY);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        DESTROYED = true;
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (MainActivity.exitFromApp) {
            for (DemoReader demoReader : Readers) {
                try {

                    if ((demoReader.getConnectionType().equals(
                            CAENRFIDPort.CAENRFID_BT) && BluetoothAdapter.getDefaultAdapter().isEnabled())
                  )
                    {
                        demoReader.getReader().Disconnect();
                        demoReader.getReader().removeCAENRFIDBLEConnectionEventListener(MainActivity.this);
                    }
                } catch (CAENRFIDException e) {
                    e.printStackTrace();
                }
            }
            Readers = null;
        }
        this.unregisterReceiver(mReceiverBTDisconnect);
        this.unregisterReceiver(mReceiverUUIDCached);

        exitFromApp = false;
        returnFromActivity = false;
    }


    public Activity getActivity() {
        return this;
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        if (id == 1) {
            final CharSequence[] items = {"Bluetooth"};

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Connection Type");
            builder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                   if (item == 0) {
                        if (BluetoothAdapter.getDefaultAdapter() == null) {
                            Toast.makeText(getApplicationContext(), "No Bluetooth adapter found!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(getApplicationContext(), items[item],
                                Toast.LENGTH_SHORT).show();
                        Intent addReader = new Intent(getApplicationContext(),
                                BTSelection.class);
                        getActivity().startActivityForResult(addReader,
                                ADD_READER_BT);
                    }
                }
            });
            dialog = builder.create();
        } else {
            dialog = null;
        }
        return dialog;
    }
    public void addNewReaderActivity(View v) {

        if (BluetoothAdapter.getDefaultAdapter() == null) {
            Toast.makeText(getApplicationContext(), "No Bluetooth adapter found!", Toast.LENGTH_SHORT).show();
            return;
        }
     /*   Toast.makeText(getApplicationContext(), items[item],
                Toast.LENGTH_SHORT).show();*/
        Intent addReader = new Intent(getApplicationContext(),
                BTSelection.class);
        getActivity().startActivityForResult(addReader,
                ADD_READER_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String ip;
        BluetoothDevice dev;
        switch (requestCode) {

            case MainActivity.ADD_READER_BT:
                if (resultCode == RESULT_OK) {
                    dev = data.getParcelableExtra("BT_DEVICE");
                    assert dev != null;
                    tcpBtWaitProgressDialog = ProgressDialog.show(this,
                            "Connection ", "Connecting to " + dev.getName(), true,
                            true);
                    new BTConnector().execute(dev);
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        exitFromApp = true;
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }



}