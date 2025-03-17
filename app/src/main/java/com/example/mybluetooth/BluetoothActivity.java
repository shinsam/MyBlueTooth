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
import android.widget.TextView;
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

public class BluetoothActivity extends Activity {
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
    private TextView pairedDeviceTextView;
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
        pairedDeviceTextView = findViewById(R.id.paired_devices_name);
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

        Button sendButton = findViewById(R.id.send_button);
        Button searchButton = findViewById(R.id.search_button);
        Button resetButton = findViewById(R.id.reset_button);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetBluetoothConnection();
            }
        });

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndShowDevices();
            }
        });
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText data_to_send = findViewById(R.id.data_to_send);
                 sendData(data_to_send.getText().toString());
            }
        });


        devicesList = new ArrayList<>();
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        startServer();
    }



    /**
     * 데이터를 연결된 블루투스 장치로 전송합니다.
     * @param data 전송할 데이터
     */
    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                Toast.makeText(this, "Data " + data + " sent", Toast.LENGTH_SHORT).show();
                receivedDataEditText.append("송신:" + data+ "\n");

            } catch (IOException e) {
                Toast.makeText(this, "Failed to send data. please restart", Toast.LENGTH_SHORT).show();
                pairedDeviceTextView.setText("연결된 장치 없음");
            }
        } else {
            Toast.makeText(this, "No connection", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 블루투스 서버를 시작하여 들어오는 연결을 대기합니다.
     */
    private void startServer() {
        serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(getApplicationContext(), "startServer 권한 오류", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(getApplicationContext(), deviceName + "장치가 연결 요청하였습니다.", Toast.LENGTH_SHORT).show();
                            pairedDeviceTextView.setText("연결장치:" + deviceName);
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

    /**
     * 연결된 블루투스 소켓을 관리합니다.
     * @param socket 연결된 블루투스 소켓
     */
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
                                    receivedDataEditText.append("수신:" + receivedMessage + "\n");
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
            resetBluetoothConnection();
        }
    }


    //연결이 끊어질때
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
        pairedDeviceTextView.setText("연결된 장치 없음");
    }

    //필요한 권한을 요청
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


    /**
     * 필요한 권한을 확인하고 블루투스 장치를 표시합니다.
     */
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

    /**
     * 사용 가능한 블루투스 장치를 표시합니다.
     */
    private void showDevices() {
        devicesArrayAdapter.clear();
        devicesList.clear();

        // 페어링된 장치를 추가
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "showDevices() 권한 오류", Toast.LENGTH_SHORT).show();
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
                .setTitle("연결할 장치를 선택하세요.")
                .setView(listView)
                .setNegativeButton("닫기", new DialogInterface.OnClickListener() {
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

    /**
     * 블루투스 장치 검색을 시작합니다.
     */
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

    /**
     * 블루투스 장치 검색 중 발견된 장치를 처리하는 BroadcastReceiver입니다.
     */
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Toast.makeText(context, "새로운 장치를 찾고있습니다.", Toast.LENGTH_SHORT).show();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "BroadcastReceiver 권한 오류", Toast.LENGTH_SHORT).show();
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

    /**
     * 선택한 블루투스 장치에 연결합니다.
     * @param device 선택한 블루투스 장치
     * @param dialog 장치 목록을 표시하는 다이얼로그
     */
    private void connectToSelectedDevice(BluetoothDevice device, AlertDialog dialog) {
        clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String device_name=null;
                try {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(getApplicationContext(), "connectToSelectedDevice 권한 오류", Toast.LENGTH_SHORT).show();
                          return;
                    }

                    device_name = device.getName();
                    String finalDevice_name = device_name;
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();


                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pairedDeviceTextView.setText("연결장치:" + finalDevice_name);
                            Toast.makeText(getApplicationContext(), finalDevice_name + "에 연결합니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    });
                    manageConnectedSocket(bluetoothSocket);
                } catch (IOException e) {
                    pairedDeviceTextView.setText("Error in client thread. 상대방이 연결 가능한 상태인지 확인하세요");
                    Log.e(TAG, "Error in client thread. 상대방이 연결 가능한 상태인지 확인하세요", e);
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

    /**
     * 블루투스 연결을 초기화합니다.
     */
    private void resetBluetoothConnection() {
        isReceiving = false;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error resetting Bluetooth connection", e);
        }
        pairedDeviceTextView.setText("연결된 장치 없음");

    }
}