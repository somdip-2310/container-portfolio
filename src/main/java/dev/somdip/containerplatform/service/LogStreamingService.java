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
            String logStreamName = "container/" + containerId;
            
            // Get the latest log events
            GetLogEventsRequest request = GetLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamName(logStreamName)
                .limit(limit)
                .startFromHead(false) // Get latest logs
                .build();
                
            GetLogEventsResponse response = cloudWatchLogsClient.getLogEvents(request);
            
            // Format logs for display
            List<String> logLines = response.events().stream()
                .map(event -> formatLogEvent(event))
                .collect(Collectors.toList());
                
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
            String logStreamName = "container/" + containerId;
            
            FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamNames(logStreamName)
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
            String logStreamName = "container/" + containerId;
            
            FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(userLogGroup)
                .logStreamNames(logStreamName)
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