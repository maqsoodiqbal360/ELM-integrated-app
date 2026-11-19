package com.maqsood.cartechlog.obd2;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.BridgeActivity;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends BridgeActivity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothSocket btSocket = null;
    private InputStream inStream = null;
    private OutputStream outStream = null;

    @Override
    public void onStart() {
        super.onStart();
        if (this.bridge != null && this.bridge.getWebView() != null) {
            WebView wv = this.bridge.getWebView();
            wv.addJavascriptInterface(new ElmBluetoothBridge(), "AndroidOBD");
        }
    }

    public class ElmBluetoothBridge {
        @JavascriptInterface
        public String requestPermissions() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{ Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN }, 101);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{ Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION }, 101);
            }
            return "REQUESTED";
        }

        @JavascriptInterface
        public String getPairedDevices() {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) return "[]";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                    return "[]";
                }
                Set<BluetoothDevice> paired = adapter.getBondedDevices();
                JSONArray arr = new JSONArray();
                for (BluetoothDevice dev : paired) {
                    JSONObject o = new JSONObject();
                    o.put("name", dev.getName() != null ? dev.getName() : "Unknown");
                    o.put("address", dev.getAddress());
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String connect(String address) {
            disconnect();
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return "ERR:No Bluetooth adapter";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return "ERR:Bluetooth permission required. Allow and tap Connect again.";
            }
            adapter.cancelDiscovery();
            BluetoothDevice device = adapter.getRemoteDevice(address);

            try {
                btSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();
                inStream = btSocket.getInputStream();
                outStream = btSocket.getOutputStream();
                return "OK";
            } catch (Exception e1) {
                try {
                    btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    btSocket.connect();
                    inStream = btSocket.getInputStream();
                    outStream = btSocket.getOutputStream();
                    return "OK";
                } catch (Exception e2) {
                    try {
                        btSocket = (BluetoothSocket) device.getClass().getMethod("createInsecureRfcommSocket", new Class[]{int.class}).invoke(device, 1);
                        btSocket.connect();
                        inStream = btSocket.getInputStream();
                        outStream = btSocket.getOutputStream();
                        return "OK";
                    } catch (Exception e3) {
                        return "ERR:" + e3.getMessage();
                    }
                }
            }
        }

        @JavascriptInterface
        public synchronized String sendCommand(String cmd, int timeoutMs) {
            try {
                if (btSocket == null || !btSocket.isConnected() || outStream == null || inStream == null) {
                    return "ERR:Not connected";
                }
                while (inStream.available() > 0) {
                    inStream.read();
                }
                String payload = cmd.trim() + "\r";
                outStream.write(payload.getBytes("US-ASCII"));
                outStream.flush();

                StringBuilder sb = new StringBuilder();
                long start = System.currentTimeMillis();
                while ((System.currentTimeMillis() - start) < timeoutMs) {
                    if (inStream.available() > 0) {
                        int b = inStream.read();
                        if (b == -1) break;
                        char c = (char) b;
                        if (c == '>') break;
                        sb.append(c);
                    } else {
                        Thread.sleep(12);
                    }
                }
                return sb.toString().replace("\r", " ").replace("\n", " ").trim();
            } catch (Exception e) {
                return "ERR:" + e.getMessage();
            }
        }

        @JavascriptInterface
        public boolean isConnected() {
            return btSocket != null && btSocket.isConnected();
        }

        @JavascriptInterface
        public void disconnect() {
            try { if (inStream != null) inStream.close(); } catch (Exception ignored) {}
            try { if (outStream != null) outStream.close(); } catch (Exception ignored) {}
            try { if (btSocket != null) btSocket.close(); } catch (Exception ignored) {}
            inStream = null; outStream = null; btSocket = null;
        }
    }
}
