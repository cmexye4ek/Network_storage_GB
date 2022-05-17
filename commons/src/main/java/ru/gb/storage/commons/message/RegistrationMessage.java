package ru.gb.storage.commons.message;

public class RegistrationMessage extends Message {
    private String login;
    private String password;
    private Status status;

    public enum Status {
        REGISTRATION_ERROR_LOGIN_EXIST,
        REGISTRATION_ERROR_USERFOLDER_CREATION,
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
        return "Registration{" +
                "Login='" + login + '\'' +
                "Password='" + password + '\'' +
                "Status='" + status + '\'' +
                '}';
    }

}
