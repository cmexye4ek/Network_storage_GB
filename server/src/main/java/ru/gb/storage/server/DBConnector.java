package ru.gb.storage.server;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DBConnector {

    private static Connection connection;

    private DBConnector() {
    }

    public static void dBCreate() throws IOException {
        File db = new File("." + File.separator + "users.db");
        if (db.exists()) {
            System.out.println("Database already exist");
        } else if (db.createNewFile()) {
            System.out.println("Database creation successful");
        } else {
            System.out.println("Database cannot be created");
        }
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection("jdbc:sqlite:users.db");
        }
        return connection;
    }
}

