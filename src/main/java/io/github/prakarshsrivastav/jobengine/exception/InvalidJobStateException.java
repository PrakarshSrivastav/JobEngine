package io.github.prakarshsrivastav.jobengine.exception;

public class InvalidJobStateException extends RuntimeException {
    public InvalidJobStateException(String message) {
        super(message);
    }
}
