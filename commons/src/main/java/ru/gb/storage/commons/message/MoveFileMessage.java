package ru.gb.storage.commons.message;

public class MoveFileMessage extends Message{
    private String sourcePath;
    private String destinationPath;
    private Status status;

    public enum Status {
        ERROR,
        ERROR_WRONG_SOURCE,
        ERROR_WRONG_DESTINATION,
        SUCCESS
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
