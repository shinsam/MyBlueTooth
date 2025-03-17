package com.example.mybluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class BluetoothActivity_v9 extends Activity {
    private static final String TAG = "BluetoothActivity";
    private static final String APP_NAME = "MyBluetoothApp";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private BluetoothServerSocket serverSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread serverThread, clientThread;
    private boolean isReceiving = false;
    private EditText receivedDataEditText;
    private ArrayAdapter<String> devicesArrayAdapter;
    private ArrayList<BluetoothDevice> devicesList;

    private int[] buttonIds = {
            R.id.send_button_1,
            R.id.send_button_2,
            R.id.send_button_3,
//        R.id.send_button_4,
//        R.id.send_button_5
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        receivedDataEditText = findViewById(R.id.received_data_edit_text);
        receivedDataEditText.setMovementMethod(new ScrollingMovementMethod());

        for (int i = 0; i < buttonIds.length; i++) {
            final int index = i + 1;
            Button button = findViewById(buttonIds[i]);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendData(String.valueOf(index));
                }
            });
        }

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndShowDevices();
            }
        });

        devicesList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        startServer();
        connectToServer();
    }

    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                Toast.makeText(this, "Data " + data + " sent", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Failed to send data", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No connection", Toast.LENGTH_SHORT).show();
        }
    }

    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(BluetoothActivity_v9.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
                    bluetoothSocket = serverSocket.accept();

                    // 연결된 장치의 이름을 가져옴
                    BluetoothDevice remoteDevice = bluetoothSocket.getRemoteDevice();
                    final String deviceName = remoteDevice.getName();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Client connected: " + deviceName, Toast.LENGTH_SHORT).show();
                        }
                    });

                    manageConnectedSocket(bluetoothSocket);
                } catch (IOException e) {
                    Log.e(TAG, "Error in server thread", e);
                }
            }
        });
        serverThread.start();
    }

    private void connectToServer() {
        clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice("98:D3:31:F7:0E:00"); // 원격 장치 주소
                try {
                    if (ActivityCompat.checkSelfPermission(BluetoothActivity_v9.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Connected to server", Toast.LENGTH_SHORT).show();
                        }
                    });
                    manageConnectedSocket(bluetoothSocket);
                } catch (IOException e) {
                    Log.e(TAG, "Error in client thread", e);
                    try {
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close();
                        }
                    } catch (IOException closeException) {
                        Log.e(TAG, "Error closing socket", closeException);
                    }
                }
            }
        });
        clientThread.start();
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            isReceiving = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;
                    while (isReceiving) {
                        try {
                            bytes = inputStream.read(buffer);
                            String receivedMessage = new String(buffer, 0, bytes);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    receivedDataEditText.append(receivedMessage + "\n");
                                }
                            });
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading from input stream", e);
                            isReceiving = false;
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            Log.e(TAG, "Error managing socket", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isReceiving = false;
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
        unregisterReceiver(receiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showDevices();
            } else {
                Toast.makeText(this, "Bluetooth connect permission is required to find devices", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void checkPermissionsAndShowDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        1);
            } else {
                showDevices();
            }
        } else {
            showDevices();
        }
    }

    private void showDevices() {
        devicesArrayAdapter.clear();
        devicesList.clear();

        // 페어링된 장치를 추가
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                devicesArrayAdapter.add("Paired: " + device.getName() + "\n" + device.getAddress());
                devicesList.add(device);
            }
        } else {
            devicesArrayAdapter.add("No paired devices found");
        }

        // 블루투스 검색을 시작
        startDiscovery();

        final ListView listView = new ListView(this);
        listView.setAdapter(devicesArrayAdapter);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select a device to connect")
                .setView(listView)
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        bluetoothAdapter.cancelDiscovery();
                    }
                })
                .create();
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            BluetoothDevice device = devicesList.get(position);
            connectToSelectedDevice(device, dialog);
            return true;
        });
        dialog.show();
    }

    private void startDiscovery() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "startDiscovery 권한 오류", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery(); //현재 검색중이면 중단하기
        } else {
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.startDiscovery();

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(receiver, filter);

            } else {
                Toast.makeText(getApplicationContext(), "bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Toast.makeText(context, "BroadcastReceiver!!!", Toast.LENGTH_SHORT).show();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "BroadcastReceiver", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    devicesArrayAdapter.add("New: " + device.getName() + "\n" + device.getAddress());
                    devicesList.add(device);
                    devicesArrayAdapter.notifyDataSetChanged();
                }

            }
        }
    };

    private void connectToSelectedDevice(BluetoothDevice device, AlertDialog dialog) {
        clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(BluetoothActivity_v9.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    String device_name = device.getName();

                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BluetoothActivity_v9.this, "Connected to " + device_name, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    });
                    manageConnectedSocket(bluetoothSocket);
                } catch (IOException e) {
                    Log.e(TAG, "Error in client thread", e);
                    try {
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close();
                        }
                    } catch (IOException closeException) {
                        Log.e(TAG, "Error closing socket", closeException);
                    }
                }
            }
        });
        clientThread.start();
    }
}