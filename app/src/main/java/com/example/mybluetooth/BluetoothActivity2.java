package com.example.mybluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import java.util.Set;
import java.util.UUID;

public class BluetoothActivity2 extends Activity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final int REQUEST_BLUETOOTH_CONNECT = 3;
    private static final int REQUEST_BLUETOOTH_SCAN = 4;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> pairedDevicesArrayAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread dataReceiveThread;
    private boolean isReceiving = false;
    private EditText receivedDataEditText;
    private Thread serverThread, clientThread;
    private BluetoothServerSocket serverSocket;
    private static final String APP_NAME = "MyBluetoothApp";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "BluetoothActivity";

    private int[] buttonIds = {
            R.id.send_button_1,
            R.id.send_button_2,
            R.id.send_button_3,
//            R.id.send_button_4,
//            R.id.send_button_5
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

        Button searchButton = findViewById(R.id.search_button);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndSearch();
            }
        });

        Button sendButton = findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData("Hello");
            }
        });

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

        receivedDataEditText = findViewById(R.id.received_data_edit_text);
        receivedDataEditText.setEnabled(false); // 사용자 입력을 막기 위해 비활성화

        startServer();

        pairedDevicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView pairedDevicesListView = findViewById(R.id.paired_devices_list);
        pairedDevicesListView.setAdapter(pairedDevicesArrayAdapter);

        // 리스트 항목을 롱클릭했을 때 connectToDevice 호출
        pairedDevicesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String item = (String) parent.getItemAtPosition(position);
                String deviceAddress = item.substring(item.length() - 17);
                connectToDevice(deviceAddress);
                return true;
            }
        });
    }

    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
                            Toast.makeText(getApplicationContext(), "accept : " + deviceName, Toast.LENGTH_SHORT).show();
                        }
                    });
                    manageConnectedSocket(bluetoothSocket);
                } catch (IOException e) {
                    Log.e("StartServer", "Error in server thread", e);
                }
            }
        });
        serverThread.start();
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
    private void checkPermissionsAndSearch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                        REQUEST_BLUETOOTH_CONNECT);
            } else {
                searchForDevices();
            }
        } else {
            searchForDevices();
        }
    }

    private void searchForDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BLUETOOTH_CONNECT);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            pairedDevicesArrayAdapter.clear();  // 리스트를 리셋
            showPairedDevices();
        }
    }

    private void showPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();


        if (pairedDevices.size() > 0) {
            if (pairedDevices.size() > 0) {
                final ArrayList<String> deviceNames = new ArrayList<>();
                final ArrayList<BluetoothDevice> devices = new ArrayList<>();
                for (BluetoothDevice device : pairedDevices) {
                    deviceNames.add(device.getName());
                    devices.add(device);
                }
                final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
                final ListView listView = new ListView(this);
                listView.setAdapter(adapter);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("Select a device to connect")
                        .setView(listView)
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .create();
                listView.setOnItemLongClickListener((parent, view, position, id) -> {
                    BluetoothDevice device = devices.get(position);
                    connectToSelectedDevice(device, dialog);
                    return true;
                });
                dialog.show();
            } else {
                Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show();
            }
            ///startDiscovery();
        }
    }


    private void connectToSelectedDevice(BluetoothDevice device, AlertDialog dialog) {
       // BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_CONNECT);
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")); // SPP UUID 사용
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            startDataReceive();
            Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();

            // 소켓을 닫아 문제가 발생하지 않도록 한다
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException closeException) {
                closeException.printStackTrace();
            }
        }
    }


    private void connectToDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_CONNECT);
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")); // SPP UUID 사용
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream = bluetoothSocket.getInputStream();
            startDataReceive();
            Toast.makeText(this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();

            // 소켓을 닫아 문제가 발생하지 않도록 한다
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException closeException) {
                closeException.printStackTrace();
            }
        }
    }

    private void sendData(String msg) {
        if (outputStream != null) {
            try {
                outputStream.write(msg.getBytes());
                Toast.makeText(this, "Data sent" + msg, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Failed to send data" + msg, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No connection", Toast.LENGTH_SHORT).show();
        }
    }
    private void startDataReceive() {
        isReceiving = true;
        dataReceiveThread = new Thread(new Runnable() {
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
                        e.printStackTrace();
                        isReceiving = false;
                    }
                }
            }
        });
        dataReceiveThread.start();
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
            e.printStackTrace();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            pairedDevicesArrayAdapter.clear();  // 리스트를 리셋
            showPairedDevices();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                searchForDevices();
            } else {
                Toast.makeText(this, "Bluetooth connect permission is required to find devices", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}