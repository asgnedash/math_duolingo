package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TestRepository extends JpaRepository<Test, Long> {
    Optional<Test> findByStartId(int startId);

    @Query("select max(t.startId) from Test t")
    Integer findMaxStartId();
}
