package ru.gb.storage.commons.message;

public class AuthMessage extends Message {
    private String login;
    private String password;
    private String status;

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setStatus(String status) {
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
