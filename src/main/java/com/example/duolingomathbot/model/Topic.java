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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(8) default 'OBA'")

    private TopicType type = TopicType.OBA;

    @Column(name = "order_index")
    private Integer orderIndex;

    public Topic() {
    }

    public Topic(String name, TopicType type, double maxDifficultyInTopic) {
        this.name = name;
        this.type = type;
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

    public TopicType getType() {
        return type;
    }

    public void setType(TopicType type) {
        this.type = type;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
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
