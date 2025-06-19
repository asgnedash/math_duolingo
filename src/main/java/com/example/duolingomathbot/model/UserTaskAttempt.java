package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_task_attempts")
public class UserTaskAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "attempt_timestamp", nullable = false)
    private LocalDateTime attemptTimestamp;

    @Column(name = "attempted_at_training_counter", nullable = false)
    private int attemptedAtTrainingCounter;

    @Column(name = "next_review_at_training")
    private Integer nextReviewAtTraining;


    public UserTaskAttempt() {
    }

    public UserTaskAttempt(User user, Task task, boolean correct, int attemptedAtTrainingCounter) {
        this.user = user;
        this.task = task;
        this.correct = correct;
        this.attemptTimestamp = LocalDateTime.now();
        this.attemptedAtTrainingCounter = attemptedAtTrainingCounter;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public LocalDateTime getAttemptTimestamp() {
        return attemptTimestamp;
    }

    public void setAttemptTimestamp(LocalDateTime attemptTimestamp) {
        this.attemptTimestamp = attemptTimestamp;
    }

    public int getAttemptedAtTrainingCounter() {
        return attemptedAtTrainingCounter;
    }

    public void setAttemptedAtTrainingCounter(int attemptedAtTrainingCounter) {
        this.attemptedAtTrainingCounter = attemptedAtTrainingCounter;
    }

    public Integer getNextReviewAtTraining() {
        return nextReviewAtTraining;
    }

    public void setNextReviewAtTraining(Integer nextReviewAtTraining) {
        this.nextReviewAtTraining = nextReviewAtTraining;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserTaskAttempt that = (UserTaskAttempt) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}