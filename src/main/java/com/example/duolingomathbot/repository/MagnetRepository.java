package com.example.duolingomathbot.repository;

import com.example.duolingomathbot.model.Magnet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MagnetRepository extends JpaRepository<Magnet, Long> {
    Optional<Magnet> findByStartId(Integer startId);
    boolean existsByStartId(Integer startId);
}
