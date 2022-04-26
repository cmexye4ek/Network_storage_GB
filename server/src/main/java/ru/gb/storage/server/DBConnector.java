package ru.gb.storage.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DBConnector {

    private static Connection connection;

    private DBConnector() {
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection("jdbc:sqlite:users.db");
        }
        return connection;
    }
}

