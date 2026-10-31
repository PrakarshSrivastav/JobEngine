package io.github.prakarshsrivastav.jobengine.exception;

import java.util.Set;

public class UnknownJobTypeException extends RuntimeException {
    public UnknownJobTypeException(String type, Set<String> supported) {
        super("Unknown job type '" + type + "'. Supported types: " + supported);
    }
}
