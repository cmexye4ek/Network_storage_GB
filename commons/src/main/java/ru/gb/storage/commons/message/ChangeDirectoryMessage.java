package ru.gb.storage.commons.message;

public class ChangeDirectoryMessage extends Message {
    private String path;
    private Status status;

    public enum Status {
        ERROR,
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
