package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.model.UserTaskAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTaskAttemptRepository extends JpaRepository<UserTaskAttempt, Long> {
    Optional<UserTaskAttempt> findFirstByUserAndTaskOrderByAttemptTimestampDesc(User user, Task task);
}
