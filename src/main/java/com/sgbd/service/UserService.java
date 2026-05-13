package com.sgbd.service;

import com.sgbd.model.Comment;
import com.sgbd.model.User;
import com.sgbd.model.Vote;
import com.sgbd.util.DatabaseConnection;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserService {

    public User login(String username, String password) throws SQLException {
        String hash = sha256(password);
        String sql = "SELECT id, username, reputation, created_at FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getInt("id"));
                    u.setUsername(rs.getString("username"));
                    u.setReputation(rs.getDouble("reputation"));
                    u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return u;
                }
            }
        }
        return null;
    }

    public User register(String username, String password) throws SQLException {
        String hash = sha256(password);
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?) RETURNING id, reputation, created_at";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, hash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getInt("id"));
                    u.setUsername(username);
                    u.setReputation(rs.getDouble("reputation"));
                    u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    return u;
                }
            }
        }
        return null;
    }

    public void updateReputation(int userId) throws SQLException {
        String sql = "UPDATE users SET reputation = sp_user_reputation(?) WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }

    public double getReputation(int userId) throws SQLException {
        String sql = "SELECT sp_user_reputation(?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        }
        return 0;
    }

    public Vote addVote(int userId, int forecastId, boolean isAccurate) throws SQLException {
        String sql = "INSERT INTO votes (user_id, forecast_id, is_accurate) VALUES (?, ?, ?) " +
                     "ON CONFLICT (user_id, forecast_id) DO UPDATE SET is_accurate = ? RETURNING id, created_at";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, forecastId);
            stmt.setBoolean(3, isAccurate);
            stmt.setBoolean(4, isAccurate);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Vote v = new Vote();
                    v.setId(rs.getInt("id"));
                    v.setUserId(userId);
                    v.setForecastId(forecastId);
                    v.setAccurate(isAccurate);
                    v.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    updateReputation(userId);
                    return v;
                }
            }
        }
        return null;
    }

    public Comment addComment(int userId, int forecastId, String text) throws SQLException {
        return addCommentInternal(userId, forecastId, null, text);
    }

    public Comment addReply(int userId, int forecastId, int parentCommentId, String text) throws SQLException {
        return addCommentInternal(userId, forecastId, parentCommentId, text);
    }

    private Comment addCommentInternal(int userId, Integer forecastId, Integer parentId, String text) throws SQLException {
        String sql = "INSERT INTO comments (user_id, forecast_id, parent_comment_id, comment_text) " +
                     "VALUES (?, ?, ?, ?) RETURNING id, created_at";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            if (forecastId != null) stmt.setInt(2, forecastId);
            else stmt.setNull(2, Types.INTEGER);
            if (parentId != null) stmt.setInt(3, parentId);
            else stmt.setNull(3, Types.INTEGER);
            stmt.setString(4, text);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Comment c = new Comment();
                    c.setId(rs.getInt("id"));
                    c.setUserId(userId);
                    c.setForecastId(forecastId);
                    c.setParentCommentId(parentId);
                    c.setCommentText(text);
                    c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    updateReputation(userId);
                    return c;
                }
            }
        }
        return null;
    }

    public List<Comment> getComments(int forecastId) throws SQLException {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT c.id, c.user_id, u.username, c.forecast_id, c.parent_comment_id, " +
                     "c.comment_text, c.created_at FROM comments c " +
                     "JOIN users u ON c.user_id = u.id " +
                     "WHERE c.forecast_id = ? ORDER BY c.created_at";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, forecastId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Comment c = new Comment();
                    c.setId(rs.getInt("id"));
                    c.setUserId(rs.getInt("user_id"));
                    c.setUsername(rs.getString("username"));
                    c.setForecastId(rs.getInt("forecast_id"));
                    Object parentId = rs.getObject("parent_comment_id");
                    if (parentId != null) c.setParentCommentId((Integer) parentId);
                    c.setCommentText(rs.getString("comment_text"));
                    c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(c);
                }
            }
        }
        return list;
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
