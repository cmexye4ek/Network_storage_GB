package ru.gb.storage.server;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class DBConnector {
    private static final Logger LOGGER = LogManager.getLogger(DBConnector.class.getName());
    private static Connection connection;

    private DBConnector() {
    }

    public static void dBCreate() throws IOException {
        File db = new File("./users.db");
        if (db.exists()) {
            LOGGER.log(Level.INFO, "Database already exist");
        } else if (db.createNewFile()) {
            LOGGER.log(Level.INFO, "New database creation successful");
        } else {
            LOGGER.log(Level.FATAL, "Database not created, no further work possible");
        }
    }

    public static Connection getConnection() throws SQLException {
        if (connection == null) {
            connection = DriverManager.getConnection("jdbc:sqlite:users.db");
        }
        return connection;
    }
}

