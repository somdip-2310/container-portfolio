package dev.somdip.containerplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.somdip.containerplatform.dto.container.CreateContainerRequest;
import dev.somdip.containerplatform.model.Container;
import dev.somdip.containerplatform.repository.DeploymentRepository;
import dev.somdip.containerplatform.service.ContainerService;
import dev.somdip.containerplatform.service.LogStreamingService;
import dev.somdip.containerplatform.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContainerController.class)
class ContainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContainerService containerService;

    @MockBean
    private DeploymentRepository deploymentRepository;

    @MockBean
    private LogStreamingService logStreamingService;

    @MockBean
    private MetricsService metricsService;

    private Container testContainer;

    @BeforeEach
    void setUp() {
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
    @WithMockUser(username = "user-123")
    void createContainer_Success() throws Exception {
        // Arrange
        CreateContainerRequest request = new CreateContainerRequest();
        request.setName("test-container");
        request.setImage("nginx");
        request.setImageTag("latest");
        request.setPort(80);

        when(containerService.createContainer(anyString(), anyString(), anyString(), anyString(), anyInt()))
            .thenReturn(testContainer);

        // Act & Assert
        mockMvc.perform(post("/api/containers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.containerId").value("container-123"))
            .andExpect(jsonPath("$.name").value("test-container"));

        verify(containerService, times(1))
            .createContainer("user-123", "test-container", "nginx", "latest", 80);
    }

    @Test
    @WithMockUser(username = "user-123")
    void listContainers_Success() throws Exception {
        // Arrange
        when(containerService.listUserContainers("user-123"))
            .thenReturn(Arrays.asList(testContainer));

        // Act & Assert
        mockMvc.perform(get("/api/containers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].containerId").value("container-123"))
            .andExpect(jsonPath("$[0].name").value("test-container"));

        verify(containerService, times(1)).listUserContainers("user-123");
    }

    @Test
    @WithMockUser(username = "user-123")
    void getContainer_Success() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);

        // Act & Assert
        mockMvc.perform(get("/api/containers/container-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.containerId").value("container-123"))
            .andExpect(jsonPath("$.name").value("test-container"));

        verify(containerService, times(1)).getContainer("container-123");
    }

    @Test
    @WithMockUser(username = "different-user")
    void getContainer_Forbidden_WhenNotOwner() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);

        // Act & Assert
        mockMvc.perform(get("/api/containers/container-123"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user-123")
    void getContainer_NotFound() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123"))
            .thenThrow(new IllegalArgumentException("Container not found"));

        // Act & Assert
        mockMvc.perform(get("/api/containers/container-123"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user-123")
    void deployContainer_Success() throws Exception {
        // Arrange
        testContainer.setStatus(Container.ContainerStatus.RUNNING);
        when(containerService.getContainer("container-123")).thenReturn(testContainer);
        when(containerService.deployContainer("container-123")).thenReturn(testContainer);
        when(deploymentRepository.findLatestByContainerId("container-123"))
            .thenReturn(java.util.Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/api/containers/container-123/deploy")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.container.containerId").value("container-123"));

        verify(containerService, times(1)).deployContainer("container-123");
    }

    @Test
    @WithMockUser(username = "user-123")
    void stopContainer_Success() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);
        when(containerService.stopContainer("container-123")).thenReturn(testContainer);

        // Act & Assert
        mockMvc.perform(post("/api/containers/container-123/stop")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.containerId").value("container-123"));

        verify(containerService, times(1)).stopContainer("container-123");
    }

    @Test
    @WithMockUser(username = "user-123")
    void deleteContainer_Success() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);
        doNothing().when(containerService).deleteContainer("container-123");

        // Act & Assert
        mockMvc.perform(delete("/api/containers/container-123")
                .with(csrf()))
            .andExpect(status().isNoContent());

        verify(containerService, times(1)).deleteContainer("container-123");
    }

    @Test
    @WithMockUser(username = "user-123")
    void getContainerLogs_Success() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);
        when(logStreamingService.getLatestLogs("container-123", 100))
            .thenReturn("Log line 1\nLog line 2");

        // Act & Assert
        mockMvc.perform(get("/api/containers/container-123/logs")
                .param("lines", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.containerId").value("container-123"))
            .andExpect(jsonPath("$.logs").exists());

        verify(logStreamingService, times(1)).getLatestLogs("container-123", 100);
    }

    @Test
    @WithMockUser(username = "user-123")
    void getContainerMetrics_Success() throws Exception {
        // Arrange
        when(containerService.getContainer("container-123")).thenReturn(testContainer);
        when(metricsService.getContainerMetrics(anyList()))
            .thenReturn(Map.of("container-123", Collections.emptyMap()));

        // Act & Assert
        mockMvc.perform(get("/api/containers/container-123/metrics")
                .param("period", "1h"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.containerId").value("container-123"))
            .andExpect(jsonPath("$.metrics").exists());

        verify(metricsService, times(1)).getContainerMetrics(anyList());
    }

    @Test
    void createContainer_Unauthorized_WithoutAuth() throws Exception {
        // Arrange
        CreateContainerRequest request = new CreateContainerRequest();
        request.setName("test-container");
        request.setImage("nginx");

        // Act & Assert
        mockMvc.perform(post("/api/containers")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }
}
