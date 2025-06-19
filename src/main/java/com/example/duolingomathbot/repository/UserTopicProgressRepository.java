package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Topic;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.model.UserTopicProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, Long> {
    Optional<UserTopicProgress> findByUserAndTopic(User user, Topic topic);

    List<UserTopicProgress> findByUserAndCompletedTrueAndNextTrainingNumberLessThanEqual(User user, int currentTrainingCounter);

    List<UserTopicProgress> findByUserAndCompletedFalseOrderByCorrectlySolvedCountAsc(User user);
}