package com.derelictvesseldev.pi_client;

public class ConnectionParams {
    String host = "";
    String password = "";

    public ConnectionParams() {}

    public ConnectionParams(String host, String password) {
        this.host = host;
        this.password = password;
    }

    public boolean isValid() {
        return (!host.isEmpty() && !password.isEmpty());
    }
}