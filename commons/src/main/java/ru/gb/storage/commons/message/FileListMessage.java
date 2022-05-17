package ru.gb.storage.commons.message;

import java.io.File;

public class FileListMessage extends Message {
    private File path;
    private File[] fileList;
    private long space;
    private Status status;

    public enum Status {
        ERROR,
        ERROR_WRONG_PATH,
        SUCCESS
    }

    public String getPath() {
        return path.getPath();
    }

    public void setPath(File path) {
        this.path = path;
    }

    public File[] getFileList() {
        return fileList;
    }

    public void setFileList(File[] fileList) {
        this.fileList = fileList;
    }

    public long getSpace() {
        return space;
    }

    public void setSpace(long space) {
        this.space = space;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
