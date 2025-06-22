package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.util.Objects;

import com.example.duolingomathbot.model.TopicType;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type")
    private TopicType exam;

    @Column(name = "marathon", nullable = false)
    private boolean marathon = false;

    @Column(name = "weekly_points", nullable = false)
    private int weeklyPoints = 0;

    @Column(name = "monthly_points", nullable = false)
    private int monthlyPoints = 0;

    @Column(name = "all_points", nullable = false)
    private int allPoints = 0;

    @Column(name = "train_notification", nullable = false)
    private boolean trainNotification = true;

    @Column(name = "streak", nullable = false)
    private int streak = 0;

    @Column(name = "last_training_date")
    private java.time.LocalDate lastTrainingDate;

    public User() {
    }

    public User(Long telegramId, String username) {
        this.telegramId = telegramId;
        this.username = username;
        this.trainingCounter = 0;
        this.exam = null;
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

    public TopicType getExam() {
        return exam;
    }

    public void setExam(TopicType exam) {
        this.exam = exam;
    }

    public boolean isMarathon() {
        return marathon;
    }

    public void setMarathon(boolean marathon) {
        this.marathon = marathon;
    }

    public int getWeeklyPoints() {
        return weeklyPoints;
    }

    public void setWeeklyPoints(int weeklyPoints) {
        this.weeklyPoints = weeklyPoints;
    }

    public int getMonthlyPoints() {
        return monthlyPoints;
    }

    public void setMonthlyPoints(int monthlyPoints) {
        this.monthlyPoints = monthlyPoints;
    }

    public int getAllPoints() {
        return allPoints;
    }

    public void setAllPoints(int allPoints) {
        this.allPoints = allPoints;
    }

    public boolean isTrainNotification() {
        return trainNotification;
    }

    public void setTrainNotification(boolean trainNotification) {
        this.trainNotification = trainNotification;
    }

    public int getStreak() {
        return streak;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }

    public java.time.LocalDate getLastTrainingDate() {
        return lastTrainingDate;
    }

    public void setLastTrainingDate(java.time.LocalDate lastTrainingDate) {
        this.lastTrainingDate = lastTrainingDate;
    }

    public void addPoints(int pts) {
        this.weeklyPoints += pts;
        this.monthlyPoints += pts;
        this.allPoints += pts;
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
