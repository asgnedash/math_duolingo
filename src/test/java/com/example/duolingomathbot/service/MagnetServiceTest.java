import com.example.duolingomathbot.model.Magnet;
import com.example.duolingomathbot.repository.MagnetRepository;
import com.example.duolingomathbot.service.MagnetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class MagnetServiceTest {

    private MagnetRepository magnetRepository;
    private MagnetService magnetService;

    @BeforeEach
    void setUp() {
        magnetRepository = Mockito.mock(MagnetRepository.class);
        magnetService = new MagnetService(magnetRepository);
    }

    @Test
    void createMagnetGeneratesAndSavesMagnet() {
        when(magnetRepository.existsByStartId(anyInt())).thenReturn(false);
        when(magnetRepository.save(any(Magnet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Magnet result = magnetService.createMagnet("fileId", "message");

        assertEquals("fileId", result.getFileId());
        assertEquals("message", result.getMessage());
        assertNotNull(result.getStartId());
        verify(magnetRepository).save(any(Magnet.class));
    }

    @Test
    void getByStartIdDelegatesToRepository() {
        Magnet magnet = new Magnet();
        when(magnetRepository.findByStartId(123)).thenReturn(Optional.of(magnet));

        Optional<Magnet> result = magnetService.getByStartId(123);

        assertTrue(result.isPresent());
        assertSame(magnet, result.get());
        verify(magnetRepository).findByStartId(123);
    }
}
