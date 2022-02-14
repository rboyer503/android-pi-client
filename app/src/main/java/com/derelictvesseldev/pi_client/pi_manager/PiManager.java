package com.derelictvesseldev.pi_client.pi_manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Handler;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class PiManager {
    private interface TokenReadyCallback {
        void onTokenReady(Result result);
    }

    private static final String PI_USER = "pi-client-user";
    private static final String TOKEN_FILE = "pi-server-token";
    private static final String TOKEN_PATH = "/tmp/" + TOKEN_FILE;
    private static final int SSH_PORT = 34600;
    private static final int MONITOR_PORT = 34601;

    private final Context context;
    private final ExecutorService executorService;
    private final Handler resultHandler;
    private final ConnectCallback connectCallback;
    private final FrameCallback frameCallback;
    private final PiCommandThread piCommandThread;
    private String host;
    private String token;
    private Future<?> monitorThreadFuture;

    public PiManager(Context context, ExecutorService executorService, Handler resultHandler,
                     ConnectCallback connectCallback, FrameCallback frameCallback) {
        this.context = context;
        this.executorService = executorService;
        this.resultHandler = resultHandler;
        this.connectCallback = connectCallback;
        this.frameCallback = frameCallback;
        piCommandThread = new PiCommandThread(this::commandConnect);
    }

    public void connect(String host, String password) {
        this.host = host;

        // Securely transmit token to pi-server.
        token = UUID.randomUUID().toString();
        executorService.execute(() -> sendSecureToken(password, token, (result) -> {
            if (result instanceof Result.Success) {
                // Next stage of connection: connect to command socket.
                piCommandThread.connect(host);
            }
            else {
                resultHandler.post(() -> connectCallback.onConnect(result));
            }
        }));
    }

    public void disconnect() {
        piCommandThread.disconnect();
        monitorThreadFuture.cancel(true);
    }

    public void sendCommand(String command) {
        piCommandThread.sendCommand(command);
    }

    private void sendSecureToken(String password, String token,
                                 TokenReadyCallback callback) {
        Result result = null;

        File file = new File(context.getFilesDir(), TOKEN_FILE);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(token.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            result = new Result.Error(e, "Cannot write token file!");
        } finally {
            try {
                if (stream != null)
                    stream.close();
            } catch (IOException e) {
                e.printStackTrace();
                result = new Result.Error(e, "Cannot close token file!");
            }
        }

        if (result == null) {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(PI_USER, host, SSH_PORT);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Channel channel = session.openChannel("sftp");
                channel.connect();
                ChannelSftp sftp = (ChannelSftp) channel;
                sftp.put(file.getPath(), TOKEN_PATH);
                channel.disconnect();
                session.disconnect();
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                result = new Result.Error(e, "Cannot send token to server!");
            }
        }

        if (result == null) {
            callback.onTokenReady(new Result.Success());
        }
        else {
            callback.onTokenReady(result);
        }
    }

    private void commandConnect(Result result) {
        if (result instanceof Result.Success) {
            // Initiate monitoring and send token for authorization.
            monitorThread();
            piCommandThread.sendCommand(token);
        }

        resultHandler.post(() -> connectCallback.onConnect(result));
    }

    private void monitorThread() {
        monitorThreadFuture = executorService.submit(() -> {
            try {
                Socket socket = new Socket(host, MONITOR_PORT);
                byte[] b = new byte[4];
                InputStream inputStream = socket.getInputStream();

                while (!Thread.currentThread().isInterrupted()) {
                    int recvBytes = inputStream.read(b, 0, 4);
                    if (recvBytes != 4) {
                        break;
                    }

                    int frameSize = fromByteArray(b, 0);
                    int offset = 0;
                    int remaining = frameSize;
                    byte[] data = new byte[frameSize];
                    while (remaining > 0) {
                        recvBytes = inputStream.read(data, offset, remaining);
                        if (recvBytes <= 0) {
                            break;
                        }

                        offset += recvBytes;
                        remaining -= recvBytes;
                    }

                    ArrayList<Bitmap> bitmaps = new ArrayList<>();
                    int bufferOffset = 0;
                    int imageDataLength;

                    for (int i = 0; i < 4; i++) {
                        imageDataLength = fromByteArray(data, bufferOffset);
                        bufferOffset += 4;
                        bitmaps.add(BitmapFactory.decodeByteArray(data, bufferOffset,
                                imageDataLength));
                        bufferOffset += imageDataLength;
                    }

                    resultHandler.post(() -> frameCallback.onFrame(combineImageIntoOne(bitmaps)));
                }

                socket.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    int fromByteArray(byte[] bytes, int offset) {
        return bytes[3 + offset] << 24 | (bytes[2 + offset] & 0xFF) << 16 |
                (bytes[1 + offset] & 0xFF) << 8 | (bytes[offset] & 0xFF);
    }

    private Bitmap combineImageIntoOne(ArrayList<Bitmap> bitmap) {
        Bitmap temp = Bitmap.createBitmap(bitmap.get(0).getWidth(),
                bitmap.get(0).getHeight() * 4, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(temp);
        int top = 0;
        for (int i = 0; i < bitmap.size(); i++) {
            canvas.drawBitmap(bitmap.get(i), 0f, top, null);
            top += bitmap.get(i).getHeight();
        }
        return temp;
    }
}