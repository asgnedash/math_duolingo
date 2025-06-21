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
}
