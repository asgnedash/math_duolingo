package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.Topic;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByTopic(Topic topic);

    @Query("SELECT t FROM Task t WHERE t.topic = :topic AND t.id NOT IN " +
            "(SELECT uta.task.id FROM UserTaskAttempt uta WHERE uta.user.id = :userId AND uta.task.topic = :topic)")
    List<Task> findUnattemptedTasksInTopicForUser(@Param("topic") Topic topic, @Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT * FROM tasks WHERE topic_id = :topicId ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Task> findRandomTaskInTopic(@Param("topicId") Long topicId);

    @Query("SELECT uta.task FROM UserTaskAttempt uta " +
            "WHERE uta.user.id = :userId AND uta.task.topic = :topic " +
            "AND uta.nextReviewAtTraining = :trainingCounter " +
            "ORDER BY uta.attemptTimestamp DESC")
    List<Task> findTasksForReviewInTopic(@Param("userId") Long userId, @Param("topic") Topic topic, @Param("trainingCounter") int trainingCounter, Pageable pageable);
}