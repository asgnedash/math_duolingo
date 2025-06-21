package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    @Query("SELECT t FROM Topic t WHERE t.id NOT IN (SELECT utp.topic.id FROM UserTopicProgress utp WHERE utp.user.id = :userId) ORDER BY t.id")
    List<Topic> findUnstartedTopicsForUser(@Param("userId") Long userId);

    Optional<Topic> findByName(String name);
}
