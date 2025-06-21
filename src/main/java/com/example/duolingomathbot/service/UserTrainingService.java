package com.example.duolingomathbot.service;

import com.example.duolingomathbot.config.SrsConfig;
import com.example.duolingomathbot.model.*;
import com.example.duolingomathbot.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class UserTrainingService {

    private static final Logger logger = LoggerFactory.getLogger(UserTrainingService.class);

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final TaskRepository taskRepository;
    private final UserTopicProgressRepository userTopicProgressRepository;
    private final UserTaskAttemptRepository userTaskAttemptRepository;
    private final Random random = new Random();


    @Autowired
    public UserTrainingService(UserRepository userRepository,
                               TopicRepository topicRepository,
                               TaskRepository taskRepository,
                               UserTopicProgressRepository userTopicProgressRepository,
                               UserTaskAttemptRepository userTaskAttemptRepository) {
        this.userRepository = userRepository;
        this.topicRepository = topicRepository;
        this.taskRepository = taskRepository;
        this.userTopicProgressRepository = userTopicProgressRepository;
        this.userTaskAttemptRepository = userTaskAttemptRepository;
    }

    @Transactional
    public User getOrCreateUser(Long telegramId, String username) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId, username)));
    }

    @Transactional
    public Optional<Task> getNextTaskForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        int currentTrainingCounter = user.getTrainingCounter();

        // 1. Проверяем темы на интервальном повторении (SRS)
        List<UserTopicProgress> dueSrsProgresses = userTopicProgressRepository
                .findByUserAndCompletedTrueAndNextTrainingNumberLessThanEqual(user, currentTrainingCounter);

        if (!dueSrsProgresses.isEmpty()) {
            dueSrsProgresses.sort((p1, p2) -> {
                if (p1.getNextTrainingNumber() == null && p2.getNextTrainingNumber() == null) return 0;
                if (p1.getNextTrainingNumber() == null) return 1;
                if (p2.getNextTrainingNumber() == null) return -1;
                return p1.getNextTrainingNumber().compareTo(p2.getNextTrainingNumber());
            });

            for (UserTopicProgress srsProgress : dueSrsProgresses) {
                Topic topic = srsProgress.getTopic();
                List<Task> tasksForReview = taskRepository.findTasksForReviewInTopic(
                        user.getId(), topic, currentTrainingCounter, PageRequest.of(0, 1));
                if (!tasksForReview.isEmpty()) {
                    logger.info("Serving task {} from topic {} (SRS - specific task review)", tasksForReview.get(0).getId(), topic.getName());
                    return Optional.of(tasksForReview.get(0));
                }

                Optional<Task> randomTask = getRandomTaskFromTopic(topic);
                if (randomTask.isPresent()) {
                    logger.info("Serving random task {} from topic {} (SRS - general topic review)", randomTask.get().getId(), topic.getName());
                    return randomTask;
                }
            }
        }

        // 2. Проверяем темы, которые ещё не пройдены
        List<UserTopicProgress> learningProgresses = userTopicProgressRepository
                .findByUserAndCompletedFalseOrderByCorrectlySolvedCountAsc(user);

        for (UserTopicProgress learningProgress : learningProgresses) {
            Optional<Task> task = findTaskInLearningTopic(user, learningProgress.getTopic());
            if (task.isPresent()) {
                logger.info("Serving task {} from learning topic {}", task.get().getId(), learningProgress.getTopic().getName());
                return task;
            }
        }

        List<Topic> unstartedTopics = topicRepository.findUnstartedTopicsForUser(user.getId());
        if (!unstartedTopics.isEmpty()) {
            Topic newTopic = unstartedTopics.get(0); // predetermined order
            UserTopicProgress newProgress = new UserTopicProgress(user, newTopic);
            userTopicProgressRepository.save(newProgress);

            Optional<Task> task = findTaskInLearningTopic(user, newTopic);
            if (task.isPresent()) {
                logger.info("Serving task {} from newly started topic {}", task.get().getId(), newTopic.getName());
                return task;
            }
        }

        logger.info("No suitable tasks found for user {}", userId);
        return Optional.empty();
    }

    private Optional<Task> findTaskInLearningTopic(User user, Topic topic) {
        List<Task> unattemptedTasks = taskRepository.findUnattemptedTasksInTopicForUser(topic, user.getId(), PageRequest.of(0, 10));
        if (!unattemptedTasks.isEmpty()) {
            return Optional.of(unattemptedTasks.get(random.nextInt(unattemptedTasks.size())));
        }
        return getRandomTaskFromTopic(topic);
    }

    private Optional<Task> getRandomTaskFromTopic(Topic topic) {
        List<Task> tasks = taskRepository.findByTopic(topic);
        if (tasks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(tasks.get(random.nextInt(tasks.size())));
    }

    /**
     * Processes a textual answer from a user. The provided answer is compared
     * with the correct answer stored for the task and then delegated to the
     * main processing routine.
     *
     * @param userId     id of the user
     * @param taskId     id of the task
     * @param userAnswer answer typed by user
     * @return {@code true} if the answer is correct
     */
    @Transactional
    public boolean processAnswer(Long userId, Long taskId, String userAnswer) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
        boolean isCorrect = false;
        if (task.getAnswer() != null) {
            isCorrect = task.getAnswer().trim().equalsIgnoreCase(userAnswer.trim());
        }
        processAnswer(userId, taskId, isCorrect);
        return isCorrect;
    }


    @Transactional
    public void processAnswer(Long userId, Long taskId, boolean isCorrect) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found with ID: " + taskId));
        Topic topic = task.getTopic();

        UserTopicProgress progress = userTopicProgressRepository.findByUserAndTopic(user, topic)
                .orElseGet(() -> {
                    logger.warn("UserTopicProgress not found for user {} and topic {}. Creating new.", userId, topic.getName());
                    UserTopicProgress newProgress = new UserTopicProgress(user, topic);
                    return userTopicProgressRepository.save(newProgress);
                });

        UserTaskAttempt attempt = new UserTaskAttempt(user, task, isCorrect, user.getTrainingCounter());

        if (isCorrect) {
            if (progress.isCompleted()) {
                int currentStageIndex = progress.getTrainingStageIndex();
                int nextStageIndex = Math.min(currentStageIndex + 1, SrsConfig.SRS_STAGES_INTERVALS.size() - 1);
                progress.setTrainingStageIndex(nextStageIndex);
                int interval = SrsConfig.SRS_STAGES_INTERVALS.get(nextStageIndex);
                progress.setNextTrainingNumber(user.getTrainingCounter() + interval);
                attempt.setNextReviewAtTraining(user.getTrainingCounter() + interval);
                logger.info("User {} correct on SRS topic {}. Next review in {} trainings (at counter {}). Task specific review at {}",
                        user.getId(), topic.getName(), interval, progress.getNextTrainingNumber(), attempt.getNextReviewAtTraining());

            } else {
                progress.setCorrectlySolvedCount(progress.getCorrectlySolvedCount() + 1);
                boolean isHardTask = task.getDifficulty() >= (SrsConfig.HARD_TASK_DIFFICULTY_THRESHOLD_FACTOR * topic.getMaxDifficultyInTopic());
                if (isHardTask) {
                    progress.setCorrectlySolvedHardCount(progress.getCorrectlySolvedHardCount() + 1);
                }
                logger.info("User {} correct on learning topic {}. Total correct: {}, Hard correct: {}. IsHard: {}",
                        user.getId(), topic.getName(), progress.getCorrectlySolvedCount(), progress.getCorrectlySolvedHardCount(), isHardTask);

                if (progress.getCorrectlySolvedHardCount() >= SrsConfig.MIN_HARD_TASKS_FOR_TOPIC_COMPLETION) {
                    progress.setCompleted(true);
                    progress.setTrainingStageIndex(0);
                    int interval = SrsConfig.SRS_STAGES_INTERVALS.get(0);
                    progress.setNextTrainingNumber(user.getTrainingCounter() + interval);
                    progress.setCorrectlySolvedCount(0);
                    progress.setCorrectlySolvedHardCount(0);
                    logger.info("Topic {} completed for user {}. Moving to SRS. Next review in {} trainings (at counter {}).",
                            topic.getName(), user.getId(), interval, progress.getNextTrainingNumber());
                }
            }
        } else {
            if (progress.isCompleted()) {
                progress.setTrainingStageIndex(0);
                int interval = SrsConfig.SRS_STAGES_INTERVALS.get(0);
                progress.setNextTrainingNumber(user.getTrainingCounter() + interval);
                logger.info("User {} incorrect on SRS topic {}. Resetting SRS stage. Next review in {} trainings (at counter {}).",
                        user.getId(), topic.getName(), interval, progress.getNextTrainingNumber());
            } else {
                logger.info("User {} incorrect on learning topic {}.", user.getId(), topic.getName());
            }
        }

        userTaskAttemptRepository.save(attempt);
        userTopicProgressRepository.save(progress);
        user.incrementTrainingCounter();
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Topic> getAllTopics() {
        return topicRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Topic> getTopic(Long id) {
        return topicRepository.findById(id);
    }

    @Transactional
    public Task addTask(Long topicId, String content, String answer) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found with ID: " + topicId));
        Task task = new Task(topic, content, answer, 1.0);
        return taskRepository.save(task);
    }

    @Transactional
    public Topic createTopic(String name, TopicType type) {
        if (topicRepository.findByNameAndType(name, type).isPresent()) {
            throw new IllegalArgumentException("Topic already exists: " + name + " " + type);
        }
        Topic topic = new Topic();
        topic.setName(name);
        topic.setType(type);
        topic.setMaxDifficultyInTopic(1.0);
        return topicRepository.save(topic);
    }

    @Transactional
    public void updateTopicOrder(List<Long> orderedIds, TopicType type) {
        List<Topic> topicsOfType = topicRepository.findByType(type);
        for (Topic t : topicsOfType) {
            int index = orderedIds.indexOf(t.getId());
            if (index >= 0) {
                t.setOrderIndex(index);
            } else {
                t.setOrderIndex(null);
            }
        }
        topicRepository.saveAll(topicsOfType);
    }

    @Transactional(readOnly = true)
    public List<Topic> getOrderedTopics(TopicType type) {
        return topicRepository.findAllByTypeAndOrderIndexNotNullOrderByOrderIndexAsc(type);
    }

    @Transactional(readOnly = true)
    public List<Topic> getUnorderedTopics(TopicType type) {
        return topicRepository.findByTypeAndOrderIndexIsNullOrderByNameAsc(type);
    }
}
