package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "user_topic_progress", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "topic_id"})
})
public class UserTopicProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    @Column(name = "correctly_solved_count", nullable = false)
    private int correctlySolvedCount = 0;

    @Column(name = "correctly_solved_hard_count", nullable = false)
    private int correctlySolvedHardCount = 0;

    @Column(name = "training_stage_index", nullable = false)
    private int trainingStageIndex = 0;

    @Column(name = "next_training_number")
    private Integer nextTrainingNumber;

    public UserTopicProgress() {
    }

    public UserTopicProgress(User user, Topic topic) {
        this.user = user;
        this.topic = topic;
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

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getCorrectlySolvedCount() {
        return correctlySolvedCount;
    }

    public void setCorrectlySolvedCount(int correctlySolvedCount) {
        this.correctlySolvedCount = correctlySolvedCount;
    }

    public int getCorrectlySolvedHardCount() {
        return correctlySolvedHardCount;
    }

    public void setCorrectlySolvedHardCount(int correctlySolvedHardCount) {
        this.correctlySolvedHardCount = correctlySolvedHardCount;
    }

    public int getTrainingStageIndex() {
        return trainingStageIndex;
    }

    public void setTrainingStageIndex(int trainingStageIndex) {
        this.trainingStageIndex = trainingStageIndex;
    }

    public Integer getNextTrainingNumber() {
        return nextTrainingNumber;
    }

    public void setNextTrainingNumber(Integer nextTrainingNumber) {
        this.nextTrainingNumber = nextTrainingNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserTopicProgress that = (UserTopicProgress) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}