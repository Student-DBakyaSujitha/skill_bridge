package com.skillbridge;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class Database {
    private final Path databasePath;

    public Database(@Value("${skillbridge.db.path:instance/skillbridge.db}") String path) {
        this.databasePath = Path.of(path).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE,
                            password_hash TEXT NOT NULL, department TEXT NOT NULL, year INTEGER NOT NULL,
                            career_goal TEXT NOT NULL, skills TEXT NOT NULL DEFAULT '',
                            created_at TEXT DEFAULT CURRENT_TIMESTAMP)
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS roadmap_progress (
                            user_id INTEGER NOT NULL REFERENCES users(id),
                            career TEXT NOT NULL, skill TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0,
                            updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (user_id, career, skill))
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS saved_items (
                            user_id INTEGER NOT NULL REFERENCES users(id),
                            item_type TEXT NOT NULL, item_key TEXT NOT NULL, title TEXT NOT NULL,
                            details TEXT NOT NULL DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (user_id, item_type, item_key))
                        """);
            }
        } catch (SQLException | java.io.IOException exception) {
            throw new IllegalStateException("Could not initialize SkillBridge database.", exception);
        }
    }

    public Optional<Map<String, Object>> findUser(long id) {
        return queryOne("SELECT * FROM users WHERE id=?", id);
    }

    public Optional<Map<String, Object>> findUserByEmail(String email) {
        return queryOne("SELECT * FROM users WHERE email=?", email);
    }

    public Optional<Map<String, Object>> findUserCredentials(String email) {
        return queryOne("SELECT id, password_hash FROM users WHERE email=?", email);
    }

    public List<Map<String, Object>> query(String sql, Object... parameters) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columns = metadata.getColumnCount();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= columns; column++) {
                        row.put(metadata.getColumnLabel(column), resultSet.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read SkillBridge database.", exception);
        }
    }

    public Optional<Map<String, Object>> queryOne(String sql, Object... parameters) {
        List<Map<String, Object>> rows = query(sql, parameters);
        return rows.stream().findFirst();
    }

    public int update(String sql, Object... parameters) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update SkillBridge database.", exception);
        }
    }

    public long insert(String sql, Object... parameters) {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, parameters);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            try (Statement query = connection.createStatement();
                 ResultSet result = query.executeQuery("SELECT last_insert_rowid()")) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
            throw new SQLException("SQLite did not return an inserted row ID.");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not insert into SkillBridge database.", exception);
        }
    }

    public long createUser(String name, String email, String passwordHash, String department,
                           int year, String careerGoal) {
        try {
            return insert(
                    "INSERT INTO users (name,email,password_hash,department,year,career_goal) VALUES (?,?,?,?,?,?)",
                    name, email, passwordHash, department, year, careerGoal);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 19
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains("users.email")) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return connection;
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    public static final class DuplicateEmailException extends RuntimeException {}
}
