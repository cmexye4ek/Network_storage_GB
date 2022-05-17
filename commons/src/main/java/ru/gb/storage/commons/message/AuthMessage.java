package ru.gb.storage.commons.message;

public class AuthMessage extends Message {
    private String login;
    private String password;
    private Status status;

    public enum Status {
        AUTH_ERROR_WRONG_LOGIN,
        AUTH_ERROR_WRONG_PASSWORD,
        SUCCESS
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public Status getStatus() {
        return status;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setStatus(Status status) {
        this.status = status;
    }


    @Override
    public String toString() {
        return "Auth{" +
                "Login='" + login + '\'' +
                "Password='" + password + '\'' +
                "Status='" + status + '\'' +
                '}';
    }

}