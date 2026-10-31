package io.github.prakarshsrivastav.jobengine.domain;

public enum JobStatus {
    PENDING, QUEUED, RUNNING, SUCCESS, FAILED, RETRYING, DEAD, CANCELLED;

    public boolean isTerminal() {
        return this == SUCCESS || this == DEAD || this == CANCELLED;
    }
}