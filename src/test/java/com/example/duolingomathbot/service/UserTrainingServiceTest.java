import com.example.duolingomathbot.model.Task;
import com.example.duolingomathbot.model.Topic;
import com.example.duolingomathbot.model.TopicType;
import com.example.duolingomathbot.model.User;
import com.example.duolingomathbot.repository.*;
import com.example.duolingomathbot.service.UserTrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class UserTrainingServiceTest {

    private UserRepository userRepository;
    private TopicRepository topicRepository;
    private TaskRepository taskRepository;
    private TestRepository testRepository;
    private UserTopicProgressRepository userTopicProgressRepository;
    private UserTaskAttemptRepository userTaskAttemptRepository;
    private UserTrainingService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        topicRepository = Mockito.mock(TopicRepository.class);
        taskRepository = Mockito.mock(TaskRepository.class);
        testRepository = Mockito.mock(TestRepository.class);
        userTopicProgressRepository = Mockito.mock(UserTopicProgressRepository.class);
        userTaskAttemptRepository = Mockito.mock(UserTaskAttemptRepository.class);
        service = new UserTrainingService(userRepository, topicRepository, taskRepository,
                testRepository, userTopicProgressRepository, userTaskAttemptRepository);
    }

    @Test
    void getOrCreateUserReturnsExisting() {
        User user = new User(1L, "name");
        when(userRepository.findByTelegramId(1L)).thenReturn(Optional.of(user));

        User result = service.getOrCreateUser(1L, "name");

        assertSame(user, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getOrCreateUserCreatesNewWhenMissing() {
        when(userRepository.findByTelegramId(2L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.getOrCreateUser(2L, "new");

        assertEquals(2L, result.getTelegramId());
        assertEquals("new", result.getUsername());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void isAnswerCorrectHandlesCaseAndSpaces() {
        Task task = new Task();
        task.setAnswer("Answer");
        assertTrue(service.isAnswerCorrect(task, " answer "));
        assertFalse(service.isAnswerCorrect(task, "wrong"));
    }

    @Test
    void createTopicThrowsWhenDuplicateExists() {
        Topic existing = new Topic("geom", TopicType.OGE, 1.0);
        when(topicRepository.findByNameAndType("geom", TopicType.OGE))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> service.createTopic("geom", TopicType.OGE));

        verify(topicRepository, never()).save(any());
    }

    @Test
    void createTopicSavesNewTopicWhenMissing() {
        when(topicRepository.findByNameAndType("alg", TopicType.EGE))
                .thenReturn(Optional.empty());
        when(topicRepository.save(any(Topic.class)))
                .thenAnswer(invocation -> {
                    Topic t = invocation.getArgument(0);
                    t.setId(10L);
                    return t;
                });

        Topic created = service.createTopic("alg", TopicType.EGE);

        assertEquals("alg", created.getName());
        assertEquals(TopicType.EGE, created.getType());
        assertEquals(1.0, created.getMaxDifficultyInTopic());
        assertEquals(10L, created.getId());
        verify(topicRepository).save(any(Topic.class));
    }

    @Test
    void addTaskCreatesAndSavesTask() {
        Topic topic = new Topic("topic", TopicType.OGE, 1.0);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(invocation -> {
                    Task t = invocation.getArgument(0);
                    t.setId(5L);
                    return t;
                });

        Task task = service.addTask(1L, "content", "answer");

        assertEquals(topic, task.getTopic());
        assertEquals("content", task.getContent());
        assertEquals("answer", task.getAnswer());
        assertEquals(1.0, task.getDifficulty());
        assertEquals(5L, task.getId());
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void addTaskThrowsWhenTopicMissing() {
        when(topicRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.addTask(2L, "c", "a"));
    }

    @Test
    void createTestGeneratesStartIdInRange() {
        when(testRepository.existsByStartId(anyInt())).thenReturn(false);
        when(testRepository.save(any(com.example.duolingomathbot.model.Test.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.example.duolingomathbot.model.Test test = service.createTest();

        assertTrue(test.getStartId() >= 10000 && test.getStartId() <= 99999);
        verify(testRepository).existsByStartId(test.getStartId());
        verify(testRepository).save(any(com.example.duolingomathbot.model.Test.class));
    }
}
