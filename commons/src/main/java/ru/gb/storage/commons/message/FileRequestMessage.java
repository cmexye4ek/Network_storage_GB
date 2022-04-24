package ru.gb.storage.commons.message;

public class FileRequestMessage extends Message {
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
