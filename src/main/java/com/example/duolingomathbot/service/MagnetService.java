package com.example.duolingomathbot.service;

import com.example.duolingomathbot.model.Magnet;
import com.example.duolingomathbot.repository.MagnetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;

@Service
public class MagnetService {

    private final MagnetRepository magnetRepository;
    private final Random random = new Random();

    @Autowired
    public MagnetService(MagnetRepository magnetRepository) {
        this.magnetRepository = magnetRepository;
    }

    @Transactional
    public Magnet createMagnet(String fileId, String message) {
        int startId;
        do {
            startId = 100000 + random.nextInt(900000);
        } while (magnetRepository.existsByStartId(startId));

        Magnet magnet = new Magnet();
        magnet.setStartId(startId);
        magnet.setFileId(fileId);
        magnet.setMessage(message);
        return magnetRepository.save(magnet);
    }

    @Transactional(readOnly = true)
    public Optional<Magnet> getByStartId(int startId) {
        return magnetRepository.findByStartId(startId);
    }
}
