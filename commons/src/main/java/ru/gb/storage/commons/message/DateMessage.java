package ru.gb.storage.commons.message;

import java.util.Date;

public class DateMessage extends Message {
    private Date date;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "DateMessage{" +
                "date=" + date +
                '}';
    }
}