package com.derelictvesseldev.pi_client.pi_manager;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import java.io.OutputStream;
import java.net.Socket;

interface PiCommandConnectCallback {
    void onConnect(Result result);
}

public class PiCommandThread {
    private static final int COMMAND_PORT = 34602;

    private final HandlerThread ht = new HandlerThread("PiCommandThread");
    private final Handler handler;
    private Socket socket;

    public PiCommandThread(PiCommandConnectCallback piCommandConnectCallback) {
        ht.start();
        handler = new Handler(ht.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        try {
                            String host = (String)msg.obj;
                            socket = new Socket(host, COMMAND_PORT);
                            piCommandConnectCallback.onConnect(new Result.Success());
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            piCommandConnectCallback.onConnect(new Result.Error(e,
                                    "Server not running!"));
                        }
                        break;

                    case 2:
                        try {
                            socket.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;

                    case 3:
                        if (socket == null || !socket.isConnected())
                            break;

                        try {
                            String command = (String)msg.obj;
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(command.getBytes());
                            outputStream.flush();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };
    }

    public void connect(String host) {
        Message message = Message.obtain(handler, 1, host);
        message.sendToTarget();
    }

    public void disconnect() {
        Message message = Message.obtain(handler, 2);
        message.sendToTarget();
    }

    public void sendCommand(String command) {
        Message message = Message.obtain(handler, 3, command);
        message.sendToTarget();
    }
}