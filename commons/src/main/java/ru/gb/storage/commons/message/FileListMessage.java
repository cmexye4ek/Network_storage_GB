package ru.gb.storage.commons.message;

import java.io.File;

public class FileListMessage extends Message{
    private File path;
    private File [] fileList;


    public File getPath() {
        return path;
    }

    public void setPath(File path) {
        this.path = path;
    }

    public File [] getFileList() {
        return fileList;
    }

    public void setFileList(File [] fileList) {
        this.fileList = fileList;
    }
}
