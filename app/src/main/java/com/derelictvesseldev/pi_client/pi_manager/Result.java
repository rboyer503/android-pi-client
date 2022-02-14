package com.derelictvesseldev.pi_client.pi_manager;

public abstract class Result {
    private Result() {}

    public static final class Success extends Result {
        public Success() {}
    }

    public static final class Error extends Result {
        public Exception exception;
        public String userMessage;

        public Error(Exception exception, String userMessage) {
            this.exception = exception;
            this.userMessage = userMessage;
        }
    }
}