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
    private static final String TAG = "BluetoothActivity"; // 로그 태그
    private static final String APP_NAME = "MyBluetoothApp"; // 앱 이름
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // 블루투스 통신을 위한 UUID

    private BluetoothAdapter bluetoothAdapter; // 블루투스 어댑터
    private BluetoothSocket bluetoothSocket; // 블루투스 소켓
    private BluetoothServerSocket serverSocket; // 블루투스 서버 소켓
    private OutputStream outputStream; // 출력 스트림
    private InputStream inputStream; // 입력 스트림
    private Thread serverThread, clientThread; // 서버 및 클라이언트 스레드
    private boolean isReceiving = false; // 데이터 수신 상태 플래그
    private EditText receivedDataEditText; // 수신된 데이터를 표시하는 EditText
    private TextView pairedDeviceTextView; // 연결된 장치 이름을 표시하는 TextView
    private ArrayAdapter<String> devicesArrayAdapter; // 장치 목록 어댑터
    private ArrayList<BluetoothDevice> devicesList; // 장치 목록

    // 버튼 ID 배열
    private int[] buttonIds = {
            R.id.send_button_1,
            R.id.send_button_2,
            R.id.send_button_3,
            // R.id.send_button_4,
            // R.id.send_button_5
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        // 블루투스 어댑터 초기화
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        pairedDeviceTextView = findViewById(R.id.connected_devices_name); // 연결된 장치 이름 표시 TextView
        receivedDataEditText = findViewById(R.id.received_data_edit_text); // 수신된 데이터 표시 EditText
        receivedDataEditText.setMovementMethod(new ScrollingMovementMethod()); // 스크롤 가능하도록 설정

        // 버튼 클릭 리스너 설정
        for (int i = 0; i < buttonIds.length; i++) {
            final int index = i + 1;
            Button button = findViewById(buttonIds[i]);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendData(String.valueOf(index)); // 버튼 클릭 시 데이터 전송
                }
            });
        }

        Button sendButton = findViewById(R.id.send_button);
        Button searchButton = findViewById(R.id.search_button);
        Button resetButton = findViewById(R.id.reset_button);

        // 블루투스 연결 초기화 버튼 클릭 리스너
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetBluetoothConnection(); // 블루투스 연결 초기화
            }
        });

        // 블루투스 장치 검색 버튼 클릭 리스너
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndShowDevices(); // 권한 확인 후 장치 표시
            }
        });

        // 데이터 전송 버튼 클릭 리스너
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText data_to_send = findViewById(R.id.data_to_send);
                sendData(data_to_send.getText().toString()); // 입력된 데이터 전송
            }
        });

        devicesList = new ArrayList<>(); // 장치 목록 초기화
        devicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1); // 장치 목록 어댑터 초기화

        startServer(); // 블루투스 서버 시작
    }

    /**
     * 데이터를 연결된 블루투스 장치로 전송합니다.
     *
     * @param data 전송할 데이터
     */
    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes()); // 데이터 전송
                Toast.makeText(this, "Data " + data + " sent", Toast.LENGTH_SHORT).show();
                receivedDataEditText.append("송신:" + data + "\n"); // 송신 데이터 표시

            } catch (IOException e) {
                Toast.makeText(this, "Failed to send data. please restart", Toast.LENGTH_SHORT).show();
                pairedDeviceTextView.setText("연결된 장치 없음"); // 연결된 장치 없음 표시
            }
        } else {
            Toast.makeText(this, "No connection", Toast.LENGTH_SHORT).show(); // 연결 없음 표시
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
                        // 권한이 없을 경우 처리
                        return;
                    }
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID); // 서버 소켓 생성
                    bluetoothSocket = serverSocket.accept(); // 연결 대기

                    // 연결된 장치의 이름을 가져옴
                    BluetoothDevice remoteDevice = bluetoothSocket.getRemoteDevice();
                    final String deviceName = remoteDevice.getName();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), deviceName + "장치가 연결 요청하였습니다.", Toast.LENGTH_SHORT).show();
                            pairedDeviceTextView.setText("연결된 장치:" + deviceName); // 연결된 장치 이름 표시
                        }
                    });

                    manageConnectedSocket(bluetoothSocket); // 연결된 소켓 관리
                } catch (IOException e) {
                    Log.e(TAG, "Error in server thread", e); // 서버 스레드 오류 로그 출력
                }
            }
        });
        serverThread.start(); // 서버 스레드 시작
    }

    /**
     * 연결된 블루투스 소켓을 관리합니다.
     *
     * @param socket 연결된 블루투스 소켓
     */
    private void manageConnectedSocket(BluetoothSocket socket) {
        try {
            inputStream = socket.getInputStream(); // 입력 스트림 초기화
            outputStream = socket.getOutputStream(); // 출력 스트림 초기화
            isReceiving = true; // 데이터 수신 상태 플래그 설정
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buffer = new byte[1024];
                    int bytes;
                    while (isReceiving) {
                        try {
                            bytes = inputStream.read(buffer); // 데이터 읽기
                            String receivedMessage = new String(buffer, 0, bytes);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    receivedDataEditText.append("수신:" + receivedMessage + "\n"); // 수신 데이터 표시
                                }
                            });
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading from input stream", e); // 입력 스트림 읽기 오류 로그 출력
                            isReceiving = false; // 데이터 수신 상태 플래그 해제
                        }
                    }
                }
            }).start(); // 데이터 수신 스레드 시작
        } catch (IOException e) {
            Log.e(TAG, "Error managing socket", e); // 소켓 관리 오류 로그 출력
            resetBluetoothConnection(); // 블루투스 연결 초기화
        }
    }

    // 액티비티 종료 시 호출되는 메서드
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isReceiving = false; // 데이터 수신 상태 플래그 해제
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close(); // 블루투스 소켓 종료
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e); // 소켓 종료 오류 로그 출력
        }
        unregisterReceiver(receiver); // BroadcastReceiver 해제
        pairedDeviceTextView.setText("연결된 장치 없음"); // 연결된 장치 없음 표시
    }

    // 필요한 권한을 요청
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showDevices(); // 권한이 승인된 경우 장치 표시
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
                        1); // 권한 요청
            } else {
                showDevices(); // 권한이 있는 경우 장치 표시
            }
        } else {
            showDevices();
        }
    }

    /**
     * 사용 가능한 블루투스 장치를 표시합니다.
     */
    private void showDevices() {
        devicesArrayAdapter.clear(); // 장치 목록 초기화
        devicesList.clear(); // 장치 목록 초기화

        // 페어링된 장치를 추가
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "showDevices() 권한 오류", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                devicesArrayAdapter.add("Paired: " + device.getName() + "\n" + device.getAddress());
                devicesList.add(device); // 페어링된 장치 목록에 추가
            }
        } else {
            devicesArrayAdapter.add("No paired devices found"); // 페어링된 장치가 없는 경우
        }

        // 블루투스 검색을 시작
        startDiscovery();

        final ListView listView = new ListView(this);
        listView.setAdapter(devicesArrayAdapter); // 장치 목록 어댑터 설정

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("연결할 장치를 선택하세요.")
                .setView(listView)
                .setNegativeButton("닫기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        bluetoothAdapter.cancelDiscovery(); // 블루투스 검색 중지
                    }
                })
                .create();
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            BluetoothDevice device = devicesList.get(position);
            connectToSelectedDevice(device, dialog); // 선택한 장치에 연결
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
            bluetoothAdapter.cancelDiscovery(); // 현재 검색 중이면 중단
        } else {
            if (bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.startDiscovery(); // 블루투스 검색 시작

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(receiver, filter); // BroadcastReceiver 등록

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
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "BroadcastReceiver 권한 오류", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    devicesArrayAdapter.add("New: " + device.getName() + "\n" + device.getAddress());
                    if (!devicesList.contains(device)) {
                        devicesList.add(device); // 장치 목록에 추가
                        devicesArrayAdapter.notifyDataSetChanged(); // 어댑터 갱신
                    }
                }
            }
        }
    };

    /**
     * 선택한 블루투스 장치에 연결합니다.
     *
     * @param device 선택한 블루투스 장치
     * @param dialog 장치 목록을 표시하는 다이얼로그
     */
    private void connectToSelectedDevice(BluetoothDevice device, AlertDialog dialog) {
        clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String device_name = null;
                try {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(getApplicationContext(), "connectToSelectedDevice 권한 오류", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    device_name = device.getName();
                    String finalDevice_name = device_name;
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect(); // 블루투스 소켓 연결

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pairedDeviceTextView.setText("연결된 장치:" + finalDevice_name); // 연결된 장치 이름 표시
                            Toast.makeText(getApplicationContext(), finalDevice_name + "에 연결합니다.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss(); // 다이얼로그 닫기
                        }
                    });
                    manageConnectedSocket(bluetoothSocket); // 연결된 소켓 관리
                } catch (IOException e) {
                    pairedDeviceTextView.setText("Error in client thread. 상대방이 연결 가능한 상태인지 확인하세요");
                    Log.e(TAG, "Error in client thread. 상대방이 연결 가능한 상태인지 확인하세요", e);
                    try {
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close(); // 블루투스 소켓 종료
                        }
                    } catch (IOException closeException) {
                        Log.e(TAG, "Error closing socket", closeException);
                    }
                }
            }
        });
        clientThread.start(); // 클라이언트 스레드 시작
    }

    /**
     * 블루투스 연결을 초기화합니다.
     */
    private void resetBluetoothConnection() {
        isReceiving = false; // 데이터 수신 상태 플래그 해제
        try {
            if (inputStream != null) {
                inputStream.close(); // 입력 스트림 종료
            }
            if (outputStream != null) {
                outputStream.close(); // 출력 스트림 종료
            }
            if (bluetoothSocket != null) {
                bluetoothSocket.close(); // 블루투스 소켓 종료
            }
        } catch (IOException e) {
            Log.e(TAG, "Error resetting Bluetooth connection", e); // 블루투스 연결 초기화 오류 로그 출력
        }
        pairedDeviceTextView.setText("연결된 장치 없음"); // 연결된 장치 없음 표시
    }
}