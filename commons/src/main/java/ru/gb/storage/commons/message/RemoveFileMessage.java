package ru.gb.storage.commons.message;

public class RemoveFileMessage extends Message {
    private String path;
    private Status status;

    public enum Status {
        ERROR,
        CONFIRMATION_REQUEST,
        CONFIRMED,
        SUCCESS
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
