package ru.gb.storage.commons.message;

public class AuthMessage extends  Message {
    private String login;
    private String password;

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Auth{" +
                "Login='" + login + '\'' +
                "Password='" + password + '\'' +
                '}';
    }

}
