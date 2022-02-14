package com.derelictvesseldev.pi_client;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.derelictvesseldev.pi_client.databinding.ActivityMainBinding;
import com.derelictvesseldev.pi_client.pi_manager.PiManager;
import com.derelictvesseldev.pi_client.pi_manager.Result;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'pi_client' library on application startup.
    static {
        System.loadLibrary("pi_client");
    }

    private interface ConnectionParamCallback {
        void onConnectionParam(ConnectionParams connectionParams);
    }

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private PiManager piManager;
    private final CryptoUtils cryptoUtils = new CryptoUtils();
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.derelictvesseldev.pi_client.databinding.ActivityMainBinding binding =
                ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final Button buttonConnect = findViewById(R.id.connect_button);
        final ImageView imageView = findViewById(R.id.imageView);

        piManager = new PiManager(getApplicationContext(), executorService, mainThreadHandler,
                (result) -> {
            if (result instanceof Result.Success) {
                buttonConnect.setText(R.string.disconnect_button);
                isConnected = true;
            }
            else {
                Result.Error errorResult = (Result.Error)result;
                Toast.makeText(getApplicationContext(), errorResult.userMessage, Toast.LENGTH_SHORT)
                        .show();
            }

        }, imageView::setImageBitmap);

        buttonConnect.setOnClickListener(l -> {
            if (!isConnected) {
                getConnectionParams(connectionParams -> {
                    if (connectionParams.isValid()) {
                        piManager.connect(connectionParams.host, connectionParams.password);
                    }
                });
            }
            else {
                piManager.disconnect();
                buttonConnect.setText(R.string.connect_button);
                isConnected = false;
            }
        });

        buttonConnect.setOnLongClickListener(v -> {
            resetConnectionParams();
            return true;
        });

        final Button buttonMode = findViewById(R.id.mode_button);
        buttonMode.setOnClickListener(l -> piManager.sendCommand("mode"));
    }

    private void getConnectionParams(ConnectionParamCallback callback) {
        ConnectionParams connectionParams = new ConnectionParams();

        SharedPreferences sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);

        if (sharedPref.contains("host")) {
            connectionParams.host = sharedPref.getString("host", "");
        }

        if (sharedPref.contains("password")) {
            String ciphertext = sharedPref.getString("password", "");
            connectionParams.password = cryptoUtils.decryptString(ciphertext);
        }

        if (connectionParams.isValid()) {
            callback.onConnectionParam(connectionParams);
        }
        else {
            LayoutInflater factory = LayoutInflater.from(this);
            final View connParamView = factory.inflate(R.layout.connection_param_input, null);

            final EditText hostEditText = connParamView.findViewById(R.id.editTextHost);
            final EditText passwordEditText = connParamView.findViewById(R.id.editTextPassword);

            hostEditText.setText(connectionParams.host);
            passwordEditText.setText(connectionParams.password);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Connection Parameters");
            builder.setView(connParamView);

            builder.setPositiveButton("OK", (dialog, which) -> {
                ConnectionParams newConnectionParams =
                        new ConnectionParams(hostEditText.getText().toString(),
                                passwordEditText.getText().toString());

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("host", newConnectionParams.host);
                editor.putString("password", cryptoUtils.encryptString(
                        newConnectionParams.password));
                editor.apply();

                callback.onConnectionParam(newConnectionParams);
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.cancel();
                callback.onConnectionParam(new ConnectionParams());
            });

            builder.show();
        }
    }

    private void resetConnectionParams() {
        SharedPreferences sharedPref = this.getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove("host");
        editor.remove("password");
        editor.apply();
    }
}