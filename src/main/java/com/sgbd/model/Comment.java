package com.sgbd.model;

import java.time.LocalDateTime;

public class Comment {
    private int id;
    private int userId;
    private String username;
    private Integer forecastId;
    private Integer parentCommentId;
    private String commentText;
    private LocalDateTime createdAt;

    public Comment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getForecastId() { return forecastId; }
    public void setForecastId(Integer forecastId) { this.forecastId = forecastId; }
    public Integer getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Integer parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
