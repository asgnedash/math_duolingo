package com.example.duolingomathbot.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "magnets")
public class Magnet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_id", unique = true, nullable = false)
    private Integer startId;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getStartId() {
        return startId;
    }

    public void setStartId(Integer startId) {
        this.startId = startId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Magnet magnet = (Magnet) o;
        return Objects.equals(id, magnet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
