package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "topics")
public class Topic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "max_difficulty_in_topic", nullable = false)
    private double maxDifficultyInTopic = 1.0;

    public Topic() {
    }

    public Topic(String name, double maxDifficultyInTopic) {
        this.name = name;
        this.maxDifficultyInTopic = maxDifficultyInTopic;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getMaxDifficultyInTopic() {
        return maxDifficultyInTopic;
    }

    public void setMaxDifficultyInTopic(double maxDifficultyInTopic) {
        this.maxDifficultyInTopic = maxDifficultyInTopic;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Topic topic = (Topic) o;
        return Objects.equals(id, topic.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}