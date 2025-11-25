package dev.somdip.containerplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogStreamingService {

    private final CloudWatchLogsClient cloudWatchLogsClient;
    
    @Value("${aws.cloudwatch.logGroup.users}")
    private String userLogGroup;
    
    public String getLatestLogs(String containerId, int limit) {
        try {
            // Use prefix to match log streams: {containerId}/app/*
            // Note: containerId is already verified to belong to the authenticated user in WebApiController
            String logStreamPrefix = containerId + "/app/";

            // Use FilterLogEvents with prefix to get logs from all matching streams
            FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamNamePrefix(logStreamPrefix)
                .limit(limit)
                .build();

            FilterLogEventsResponse response = cloudWatchLogsClient.filterLogEvents(request);

            // Format logs for display
            List<String> logLines = response.events().stream()
                .map(event -> formatLogEvent(event))
                .collect(Collectors.toList());

            if (logLines.isEmpty()) {
                return "No logs available for this container yet.";
            }

            return String.join("\n", logLines);

        } catch (ResourceNotFoundException e) {
            log.warn("Log stream not found for container: {}", containerId);
            return "No logs available for this container yet.";
        } catch (Exception e) {
            log.error("Error fetching logs for container: {}", containerId, e);
            throw new RuntimeException("Failed to fetch logs", e);
        }
    }
    
    public List<String> getLogsBetween(String containerId, Instant startTime, Instant endTime) {
        try {
            String logStreamPrefix = containerId + "/app/";

            FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamNamePrefix(logStreamPrefix)
                .startTime(startTime.toEpochMilli())
                .endTime(endTime.toEpochMilli())
                .build();

            FilterLogEventsResponse response = cloudWatchLogsClient.filterLogEvents(request);

            return response.events().stream()
                .map(event -> formatLogEvent(event))
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching logs between times for container: {}", containerId, e);
            return new ArrayList<>();
        }
    }
    
    public List<String> searchLogs(String containerId, String searchTerm, int limit) {
        try {
            String logStreamPrefix = containerId + "/app/";

            FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamNamePrefix(logStreamPrefix)
                .filterPattern(searchTerm)
                .limit(limit)
                .build();

            FilterLogEventsResponse response = cloudWatchLogsClient.filterLogEvents(request);
            
            return response.events().stream()
                .map(event -> formatLogEvent(event))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error searching logs for container: {}", containerId, e);
            return new ArrayList<>();
        }
    }
    
    private String formatLogEvent(OutputLogEvent event) {
        Instant timestamp = Instant.ofEpochMilli(event.timestamp());
        return String.format("[%s] %s", timestamp.toString(), event.message());
    }
    
    private String formatLogEvent(FilteredLogEvent event) {
        Instant timestamp = Instant.ofEpochMilli(event.timestamp());
        return String.format("[%s] %s", timestamp.toString(), event.message());
    }

}