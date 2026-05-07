package com.btaudio.controller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothA2dp bluetoothA2dp;
    private HttpServer httpServer;
    private TextView statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private BluetoothProfile.ServiceListener a2dpListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = (BluetoothA2dp) proxy;
            }
        }
        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) {
                bluetoothA2dp = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        statusText = new TextView(this);
        statusText.setPadding(40, 40, 40, 40);
        statusText.setTextSize(18);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setBackgroundColor(0xFF000000);
        statusText.setText("BT Audio Controller\nStarting...");
        setContentView(statusText);
        
        // Set text immediately to verify view is working
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {}
        
        updateStatus("Initializing...");
        
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                updateStatus("Bluetooth NOT supported");
                return;
            }
            
            updateStatus("Bluetooth found");
            
            // Check and request permissions
            boolean hasPermission = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            updateStatus(hasPermission ? "Permissions OK" : "Requesting permissions...");
            
            if (!hasPermission) {
                requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                }, 1);
                // Wait for callback - don't continue until permissions granted
                return;
            }
            
            setupBluetooth();
            
        } catch (Exception e) {
            updateStatus("Fatal error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            updateStatus("Permissions " + (granted ? "granted" : "denied"));
            if (granted) {
                setupBluetooth();
            }
        }
    }
    
    private void setupBluetooth() {
        try {
            updateStatus("Setting up Bluetooth...");
            
            if (!bluetoothAdapter.isEnabled()) {
                try {
                    bluetoothAdapter.enable();
                    updateStatus("Enabling Bluetooth...");
                } catch (Exception e) {
                    updateStatus("Cannot enable: " + e.getMessage());
                    return;
                }
            }
            
            try {
                bluetoothAdapter.getProfileProxy(this, a2dpListener, BluetoothProfile.A2DP);
            } catch (Exception e) {
                updateStatus("A2DP error: " + e.getMessage());
            }
            
            updateStatus("Starting web server...");
            
            // Start HTTP server
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000); // Wait for Bluetooth to initialize
                        startHttpServer();
                    } catch (Exception e) {
                        updateStatusAsync("Server error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    }
                }
            }).start();
        } catch (Exception e) {
            updateStatus("Setup error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    
    private void updateStatus(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.append("\n" + text);
            }
        });
        try { Thread.sleep(100); } catch (InterruptedException e) {}
    }
    
    private void updateStatusAsync(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.append("\n" + text);
            }
        });
    }
    
    private void startHttpServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    updateStatusAsync("Creating server...");
                    httpServer = new HttpServer(5000);
                    updateStatusAsync("Binding to port...");
                    httpServer.start();
                } catch (Exception e) {
                    updateStatusAsync("Server failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {}
                final String ip = getLocalIpAddress();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.append("\n\n========================================\n");
                        statusText.append("SERVER RUNNING!\n");
                        statusText.append("Open: http://" + ip + ":5000\n");
                        statusText.append("Bluetooth: " + (bluetoothAdapter.isEnabled() ? "ON" : "OFF") + "\n");
                        statusText.append("Devices paired: " + getPairedCount() + "\n");
                    }
                });
            }
        }).start();
    }
    
    private String getLocalIpAddress() {
        try {
            java.net.NetworkInterface nif = java.net.NetworkInterface.getByName("wlan0");
            if (nif == null) nif = java.net.NetworkInterface.getByName("eth0");
            if (nif == null) nif = java.net.NetworkInterface.getByName("wlan1");
            if (nif != null) {
                java.util.Enumeration<java.net.InetAddress> e = nif.getInetAddresses();
                while (e.hasMoreElements()) {
                    java.net.InetAddress addr = e.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {}
        return "localhost";
    }
    
    private int getPairedCount() {
        try {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                return bluetoothAdapter.getBondedDevices().size();
            }
        } catch (Exception e) {}
        return 0;
    }
    
    class HttpServer {
        private final int port;
        private ServerSocket serverSocket;
        private volatile boolean running = true;
        
        public HttpServer(int port) {
            this.port = port;
        }
        
        public void start() throws IOException {
            serverSocket = new ServerSocket(port);
            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleRequest(client));
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        }
        
        public void stop() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        }
        
        private void handleRequest(Socket socket) {
            try {
                InputStream in = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                
                String requestLine = reader.readLine();
                if (requestLine == null) { socket.close(); return; }
                
                String[] parts = requestLine.split(" ");
                String method = parts[0];
                String path = parts[1];
                
                // Read headers
                String contentType = "text/html";
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                
                String response;
                if ("GET".equals(method) && "/".equals(path)) {
                    response = getIndexPage();
                    contentType = "text/html";
                } else if ("GET".equals(method) && "/api/devices".equals(path)) {
                    response = getDevicesJson();
                    contentType = "application/json";
                } else if ("POST".equals(method) && path.startsWith("/api/connect")) {
                    response = connectDevice(path);
                    contentType = "application/json";
                } else if ("POST".equals(method) && path.startsWith("/api/disconnect")) {
                    response = disconnectDevice(path);
                    contentType = "application/json";
                } else {
                    response = "{\"error\":\"Not found\"}";
                    contentType = "application/json";
                }
                
                byte[] body = response.getBytes("UTF-8");
                OutputStream out = socket.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out);
                
                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: " + contentType + "; charset=utf-8\r\n");
                writer.write("Content-Length: " + body.length + "\r\n");
                writer.write("Access-Control-Allow-Origin: *\r\n");
                writer.write("\r\n");
                writer.flush();
                out.write(body);
                out.flush();
                
                socket.close();
            } catch (Exception e) {
                try { socket.close(); } catch (IOException ex) {}
            }
        }
        
        private String getDevicesJson() {
            try {
                JSONArray devices = new JSONArray();
                
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return "{\"error\":\"Permission denied\",\"devices\":[]}";
                }
                
                Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
                for (BluetoothDevice device : paired) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", device.getName() != null ? device.getName() : "Unknown");
                    obj.put("mac", device.getAddress());
                    obj.put("paired", true);
                    
                    boolean connected = false;
                    int state = -1;
                    try {
                        if (bluetoothA2dp != null) {
                            state = bluetoothA2dp.getConnectionState(device);
                            connected = state == BluetoothProfile.STATE_CONNECTED;
                        }
                    } catch (Exception e) {}
                    obj.put("connected", connected);
                    obj.put("a2dp_state", state);
                    devices.put(obj);
                }
                
                return devices.toString(2);
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage() + "\",\"devices\":[]}";
            }
        }
        
        private String connectDevice(String path) {
            try {
                String mac = "";
                if (path.contains("?")) {
                    String query = path.split("\\?")[1];
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("mac=")) {
                            mac = param.substring(4);
                        }
                    }
                }
                
                if (mac.isEmpty()) {
                    return "{\"ok\":false,\"message\":\"No MAC provided\"}";
                }
                
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return "{\"ok\":false,\"message\":\"Permission denied\"}";
                }
                
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);
                
                if (bluetoothA2dp != null) {
                    int state = bluetoothA2dp.getConnectionState(device);
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        return "{\"ok\":true,\"message\":\"Already connected to " + device.getName() + "\"}";
                    }
                    
                    try {
                        java.lang.reflect.Method connectMethod = bluetoothA2dp.getClass().getMethod("connect", BluetoothDevice.class);
                        connectMethod.invoke(bluetoothA2dp, device);
                        return "{\"ok\":true,\"message\":\"Connecting to " + device.getName() + " (A2DP)\"}";
                    } catch (Exception e) {
                        // Try RFCOMM as fallback
                    }
                }
                
                try {
                    UUID uuid = UUID.fromString("0000110D-0000-1000-8000-00805F9B34FB");
                    android.bluetooth.BluetoothSocket socket = device.createRfcommSocketToServiceRecord(uuid);
                    socket.connect();
                    socket.close();
                    return "{\"ok\":true,\"message\":\"Connected to " + device.getName() + "\"}";
                } catch (Exception e) {
                    return "{\"ok\":false,\"message\":\"Connection failed: " + e.getMessage() + "\"}";
                }
                
            } catch (Exception e) {
                return "{\"ok\":false,\"message\":\"Error: " + e.getMessage() + "\"}";
            }
        }
        
        private String disconnectDevice(String path) {
            try {
                String mac = "";
                if (path.contains("?")) {
                    String query = path.split("\\?")[1];
                    String[] params = query.split("&");
                    for (String param : params) {
                        if (param.startsWith("mac=")) {
                            mac = param.substring(4);
                        }
                    }
                }
                
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return "{\"ok\":false,\"message\":\"Permission denied\"}";
                }
                
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);
                if (bluetoothA2dp != null) {
                    try {
                        java.lang.reflect.Method disconnectMethod = bluetoothA2dp.getClass().getMethod("disconnect", BluetoothDevice.class);
                        disconnectMethod.invoke(bluetoothA2dp, device);
                        return "{\"ok\":true,\"message\":\"Disconnected\"}";
                    } catch (Exception e) {
                        return "{\"ok\":false,\"message\":\"Disconnect failed\"}";
                    }
                }
                return "{\"ok\":true,\"message\":\"Disconnected\"}";
            } catch (Exception e) {
                return "{\"ok\":false,\"message\":\"Error: " + e.getMessage() + "\"}";
            }
        }
        
        private String getIndexPage() {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>BT Audio Controller</title>");
            sb.append("<style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,Roboto,sans-serif;background:#0a0a0f;color:#e8e8f0;min-height:100vh;padding:16px}");
            sb.append("h1{text-align:center;font-size:1.4rem;margin:10px 0 4px;background:linear-gradient(135deg,#6c63ff,#a78bfa);-webkit-background-clip:text;-webkit-text-fill-color:transparent}");
            sb.append(".sub{text-align:center;color:#8888a0;font-size:.8rem;margin-bottom:16px}");
            sb.append(".section{background:#16161e;border-radius:14px;padding:16px;margin-bottom:14px}");
            sb.append(".btn{display:inline-flex;align-items:center;justify-content:center;padding:12px 20px;border:none;border-radius:12px;font-size:.9rem;font-weight:600;cursor:pointer;width:100%}");
            sb.append(".btn:active{transform:scale(.97)}.btn-primary{background:#6c63ff;color:#fff}");
            sb.append(".btn-green{background:rgba(0,230,118,.15);color:#00e676}.btn-red{background:rgba(255,82,82,.15);color:#ff5252}");
            sb.append(".btn-sm{padding:8px 12px;font-size:.8rem;width:auto}");
            sb.append(".device-list{max-height:40vh;overflow-y:auto}");
            sb.append(".device-item{display:flex;align-items:center;gap:12px;padding:14px;background:#1e1e2a;border-radius:12px;margin-bottom:8px}");
            sb.append(".device-icon{width:42px;height:42px;border-radius:10px;background:rgba(108,99,255,.15);display:flex;align-items:center;justify-content:center;font-size:1.3rem;flex-shrink:0}");
            sb.append(".device-info{flex:1;min-width:0}.device-name{font-weight:600;font-size:.95rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}");
            sb.append(".device-mac{font-size:.75rem;color:#8888a0;font-family:monospace}");
            sb.append(".badge{font-size:.65rem;padding:2px 6px;border-radius:6px;font-weight:600;text-transform:uppercase}");
            sb.append(".badge-connected{background:rgba(0,230,118,.2);color:#00e676}.badge-paired{background:rgba(255,215,64,.2);color:#ffd740}");
            sb.append(".device-actions{display:flex;flex-direction:column;gap:6px}");
            sb.append(".toast{position:fixed;bottom:20px;left:16px;right:16px;padding:14px 18px;border-radius:12px;font-size:.85rem;font-weight:500;z-index:100;transform:translateY(100px);opacity:0;transition:all .3s;text-align:center}");
            sb.append(".toast.show{transform:translateY(0);opacity:1}.toast-success{background:rgba(0,230,118,.2);color:#00e676}.toast-error{background:rgba(255,82,82,.2);color:#ff5252}.toast-info{background:rgba(108,99,255,.2);color:#6c63ff}");
            sb.append(".spinner{display:inline-block;width:18px;height:18px;border:2px solid rgba(255,255,255,.3);border-top-color:#fff;border-radius:50%;animation:spin .7s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}");
            sb.append("</style></head><body>");
            sb.append("<h1>🎧 BT Audio</h1><p class=\"sub\">Connect &amp; play via Bluetooth</p>");
            sb.append("<div class=\"section\"><button class=\"btn btn-primary\" id=\"scanBtn\" onclick=\"loadDevices()\">🔄 Refresh Devices</button></div>");
            sb.append("<div id=\"deviceList\" class=\"device-list\"></div>");
            sb.append("<div id=\"toast\" class=\"toast\"></div>");
            sb.append("<script>");
            sb.append("function toast(m,t='success'){var e=document.getElementById('toast');e.textContent=m;e.className='toast toast-'+t+' show';setTimeout(function(){e.classList.remove('show')},3000)}");
            sb.append("function loadDevices(){var btn=document.getElementById('scanBtn');btn.innerHTML='<span class=spinner></span> Loading...';fetch('/api/devices').then(function(r){return r.json()}).then(function(d){renderDevices(d.devices||[]);btn.innerHTML='🔄 Refresh Devices'}).catch(function(e){toast('Error: '+e.message,'error')})}");
            sb.append("function renderDevices(devs){var el=document.getElementById('deviceList');if(!devs.length){el.innerHTML='<div class=section><p style=text-align:center;color:#8888a0;padding:30px>No paired devices found.<br><br>Go to Android Settings → Bluetooth and pair your device first.</p></div>';return}");
            sb.append("el.innerHTML=devs.map(function(d){var h='<div class=device-item><div class=device-icon>🎧</div><div class=device-info><div class=device-name>'+d.name+'</div><div class=device-mac>'+d.mac+'</div><div style=display:flex;flex-direction:row;gap:6px;margin-top:6px>'");
            sb.append("if(d.connected){h+='<span class=badge badge-connected>● Connected</span>'}else{h+='<button class=btn btn-green btn-sm onclick=connect(\\''+d.mac+'\\')>Connect</button>'}");
            sb.append("h+='<span class=badge badge-paired>Paired</span></div></div></div>';return h}).join('')}");
            sb.append("function connect(mac){toast('Connecting...','info');fetch('/api/connect?mac='+mac,{method:'POST'}).then(function(r){return r.json()}).then(function(d){toast(d.message,d.ok?'success':'error');setTimeout(loadDevices,2000)})}");
            sb.append("loadDevices();");
            sb.append("</script></body></html>");
            return sb.toString();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (httpServer != null) httpServer.stop();
        if (bluetoothAdapter != null && bluetoothA2dp != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp);
        }
    }
}
