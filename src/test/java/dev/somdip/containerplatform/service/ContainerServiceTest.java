package dev.somdip.containerplatform.service;

import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.model.User;
import dev.somdip.containerplatform.repository.ContainerRepository;
import dev.somdip.containerplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EcsService ecsService;

    @InjectMocks
    private ContainerService containerService;

    private User testUser;
    private Container testContainer;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId("user-123");
        testUser.setEmail("test@example.com");
        testUser.setTier(User.UserTier.FREE);

        testContainer = new Container();
        testContainer.setContainerId("container-123");
        testContainer.setUserId("user-123");
        testContainer.setName("test-container");
        testContainer.setImage("nginx");
        testContainer.setImageTag("latest");
        testContainer.setPort(80);
        testContainer.setStatus(Container.ContainerStatus.STOPPED);
        testContainer.setCpu(256);
        testContainer.setMemory(512);
    }

    @Test
    void createContainer_Success() {
        // Arrange
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(containerRepository.findByUserId("user-123")).thenReturn(Arrays.asList());
        when(containerRepository.save(any(Container.class))).thenReturn(testContainer);

        // Act
        Container result = containerService.createContainer(
            "user-123",
            "test-container",
            "nginx",
            "latest",
            80
        );

        // Assert
        assertNotNull(result);
        assertEquals("test-container", result.getName());
        assertEquals("nginx", result.getImage());
        verify(containerRepository, times(1)).save(any(Container.class));
    }

    @Test
    void createContainer_UserNotFound_ThrowsException() {
        // Arrange
        when(userRepository.findById("user-123")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            containerService.createContainer("user-123", "test", "nginx", "latest", 80)
        );
        verify(containerRepository, never()).save(any(Container.class));
    }

    @Test
    void createContainer_ContainerLimitReached_ThrowsException() {
        // Arrange
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(containerRepository.findByUserId("user-123"))
            .thenReturn(Arrays.asList(testContainer)); // Already 1 container (FREE tier limit)

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
            containerService.createContainer("user-123", "test2", "nginx", "latest", 80)
        );
        verify(containerRepository, never()).save(any(Container.class));
    }

    @Test
    void listUserContainers_Success() {
        // Arrange
        List<Container> containers = Arrays.asList(testContainer);
        when(containerRepository.findByUserId("user-123")).thenReturn(containers);

        // Act
        List<Container> result = containerService.listUserContainers("user-123");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test-container", result.get(0).getName());
        verify(containerRepository, times(1)).findByUserId("user-123");
    }

    @Test
    void getContainer_Success() {
        // Arrange
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));

        // Act
        Container result = containerService.getContainer("container-123");

        // Assert
        assertNotNull(result);
        assertEquals("container-123", result.getContainerId());
        assertEquals("test-container", result.getName());
    }

    @Test
    void getContainer_NotFound_ThrowsException() {
        // Arrange
        when(containerRepository.findById("container-123")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
            containerService.getContainer("container-123")
        );
    }

    @Test
    void deployContainer_Success() {
        // Arrange
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));
        when(ecsService.deployContainer(any(Container.class), anyString())).thenReturn(any());
        when(containerRepository.save(any(Container.class))).thenReturn(testContainer);

        // Act
        Container result = containerService.deployContainer("container-123");

        // Assert
        assertNotNull(result);
        verify(ecsService, times(1)).deployContainer(any(Container.class), anyString());
        verify(containerRepository, times(1)).save(any(Container.class));
    }

    @Test
    void deployContainer_AlreadyRunning_ThrowsException() {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.RUNNING);
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
            containerService.deployContainer("container-123")
        );
        verify(ecsService, never()).deployContainer(any(Container.class), anyString());
    }

    @Test
    void stopContainer_Success() {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.RUNNING);
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));
        when(containerRepository.save(any(Container.class))).thenReturn(testContainer);

        // Act
        Container result = containerService.stopContainer("container-123");

        // Assert
        assertNotNull(result);
        verify(ecsService, times(1)).stopContainer(any(Container.class));
        verify(containerRepository, times(1)).save(any(Container.class));
    }

    @Test
    void stopContainer_NotRunning_ThrowsException() {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.STOPPED);
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
            containerService.stopContainer("container-123")
        );
        verify(ecsService, never()).stopContainer(any(Container.class));
    }

    @Test
    void updateContainer_Success() {
        // Arrange
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));
        when(containerRepository.save(any(Container.class))).thenReturn(testContainer);

        // Act
        Container result = containerService.updateContainer(
            "container-123",
            512,
            1024,
            null
        );

        // Assert
        assertNotNull(result);
        verify(containerRepository, times(1)).save(any(Container.class));
    }

    @Test
    void deleteContainer_Success() {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.STOPPED);
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));

        // Act
        containerService.deleteContainer("container-123");

        // Assert
        verify(containerRepository, times(1)).delete(testContainer);
    }

    @Test
    void deleteContainer_Running_ThrowsException() {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.RUNNING);
        when(containerRepository.findById("container-123")).thenReturn(Optional.of(testContainer));

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
            containerService.deleteContainer("container-123")
        );
        verify(containerRepository, never()).delete(any(Container.class));
    }
}
