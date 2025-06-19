package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "users") // "user" is often a reserved keyword in SQL
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Column(name = "training_counter", nullable = false)
    private int trainingCounter = 0;

    public User() {
    }

    public User(Long telegramId, String username) {
        this.telegramId = telegramId;
        this.username = username;
        this.trainingCounter = 0;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTrainingCounter() {
        return trainingCounter;
    }

    public void setTrainingCounter(int trainingCounter) {
        this.trainingCounter = trainingCounter;
    }

    public void incrementTrainingCounter() {
        this.trainingCounter++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}