package dev.somdip.containerplatform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogStreamingServiceTest {

    @Mock
    private CloudWatchLogsClient cloudWatchLogsClient;

    @InjectMocks
    private LogStreamingService logStreamingService;

    private static final String TEST_CONTAINER_ID = "container-123";
    private static final String TEST_LOG_GROUP = "/ecs/user-containers";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(logStreamingService, "userLogGroup", TEST_LOG_GROUP);
    }

    @Test
    void getLatestLogs_Success() {
        // Arrange
        OutputLogEvent logEvent1 = OutputLogEvent.builder()
            .timestamp(Instant.now().toEpochMilli())
            .message("Log message 1")
            .build();

        OutputLogEvent logEvent2 = OutputLogEvent.builder()
            .timestamp(Instant.now().toEpochMilli())
            .message("Log message 2")
            .build();

        GetLogEventsResponse response = GetLogEventsResponse.builder()
            .events(Arrays.asList(logEvent1, logEvent2))
            .build();

        when(cloudWatchLogsClient.getLogEvents(any(GetLogEventsRequest.class)))
            .thenReturn(response);

        // Act
        String result = logStreamingService.getLatestLogs(TEST_CONTAINER_ID, 100);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("Log message 1"));
        assertTrue(result.contains("Log message 2"));
        verify(cloudWatchLogsClient, times(1)).getLogEvents(any(GetLogEventsRequest.class));
    }

    @Test
    void getLatestLogs_ResourceNotFound_ReturnsMessage() {
        // Arrange
        when(cloudWatchLogsClient.getLogEvents(any(GetLogEventsRequest.class)))
            .thenThrow(ResourceNotFoundException.builder()
                .message("Log stream not found")
                .build());

        // Act
        String result = logStreamingService.getLatestLogs(TEST_CONTAINER_ID, 100);

        // Assert
        assertNotNull(result);
        assertEquals("No logs available for this container yet.", result);
    }

    @Test
    void getLatestLogs_Exception_ThrowsRuntimeException() {
        // Arrange
        when(cloudWatchLogsClient.getLogEvents(any(GetLogEventsRequest.class)))
            .thenThrow(new RuntimeException("CloudWatch error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            logStreamingService.getLatestLogs(TEST_CONTAINER_ID, 100)
        );
    }

    @Test
    void getLogsBetween_Success() {
        // Arrange
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant endTime = Instant.now();

        FilteredLogEvent logEvent = FilteredLogEvent.builder()
            .timestamp(Instant.now().toEpochMilli())
            .message("Filtered log message")
            .build();

        FilterLogEventsResponse response = FilterLogEventsResponse.builder()
            .events(Arrays.asList(logEvent))
            .build();

        when(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
            .thenReturn(response);

        // Act
        List<String> result = logStreamingService.getLogsBetween(TEST_CONTAINER_ID, startTime, endTime);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("Filtered log message"));
    }

    @Test
    void searchLogs_Success() {
        // Arrange
        String searchTerm = "ERROR";

        FilteredLogEvent logEvent = FilteredLogEvent.builder()
            .timestamp(Instant.now().toEpochMilli())
            .message("ERROR: Something went wrong")
            .build();

        FilterLogEventsResponse response = FilterLogEventsResponse.builder()
            .events(Arrays.asList(logEvent))
            .build();

        when(cloudWatchLogsClient.filterLogEvents(any(FilterLogEventsRequest.class)))
            .thenReturn(response);

        // Act
        List<String> result = logStreamingService.searchLogs(TEST_CONTAINER_ID, searchTerm, 100);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).contains("ERROR"));
    }
}
