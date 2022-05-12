package ru.gb.storage.commons.message;

import java.io.File;

public class FileListMessage extends Message {
    private File path;
    private File[] fileList;
    private long space;

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
        return path.getUsableSpace();
    }
}
