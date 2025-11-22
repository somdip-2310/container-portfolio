// WebSocket Log Streaming
class LogViewer {
    constructor(containerId) {
        this.containerId = containerId;
        this.stompClient = null;
        this.isConnected = false;
        this.logs = [];
        this.autoScroll = true;
        this.maxLines = 1000;
        this.init();
    }

    init() {
        console.log('Initializing log viewer for container:', this.containerId);
        this.setupUI();
        this.loadInitialLogs();
        this.connectWebSocket();
        this.setupEventListeners();
    }

    setupUI() {
        const logContainer = document.getElementById('log-container');
        if (!logContainer) {
            console.error('Log container not found');
            return;
        }

        logContainer.innerHTML = `
            <div class="log-viewer">
                <div class="log-header d-flex justify-content-between align-items-center p-3 bg-dark text-white">
                    <div>
                        <h5 class="mb-0">
                            <i class="bi bi-file-text"></i>
                            Container Logs
                            <span id="connection-status" class="badge bg-secondary ms-2">Disconnected</span>
                        </h5>
                        <small id="container-name" class="text-muted"></small>
                    </div>
                    <div class="btn-group" role="group">
                        <button class="btn btn-sm btn-outline-light" id="clear-logs" title="Clear logs">
                            <i class="bi bi-trash"></i> Clear
                        </button>
                        <button class="btn btn-sm btn-outline-light" id="download-logs" title="Download logs">
                            <i class="bi bi-download"></i> Download
                        </button>
                        <button class="btn btn-sm btn-outline-light" id="toggle-autoscroll" title="Toggle auto-scroll">
                            <i class="bi bi-arrow-down-circle-fill"></i> Auto-scroll
                        </button>
                        <button class="btn btn-sm btn-outline-light" id="refresh-logs" title="Refresh logs">
                            <i class="bi bi-arrow-clockwise"></i> Refresh
                        </button>
                    </div>
                </div>
                <div class="log-controls p-2 bg-light border-bottom">
                    <div class="row g-2">
                        <div class="col-md-4">
                            <input type="text" id="log-search" class="form-control form-control-sm"
                                   placeholder="Search logs...">
                        </div>
                        <div class="col-md-3">
                            <select id="log-level-filter" class="form-select form-control-sm">
                                <option value="">All Levels</option>
                                <option value="ERROR">ERROR</option>
                                <option value="WARN">WARN</option>
                                <option value="INFO">INFO</option>
                                <option value="DEBUG">DEBUG</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <select id="log-lines" class="form-select form-control-sm">
                                <option value="100">Last 100 lines</option>
                                <option value="500">Last 500 lines</option>
                                <option value="1000" selected>Last 1000 lines</option>
                            </select>
                        </div>
                        <div class="col-md-2 text-end">
                            <span id="log-count" class="badge bg-secondary">0 lines</span>
                        </div>
                    </div>
                </div>
                <div class="log-content" id="log-output">
                    <div class="text-center py-5 text-muted">
                        <div class="spinner-border" role="status">
                            <span class="visually-hidden">Loading...</span>
                        </div>
                        <p class="mt-2">Loading logs...</p>
                    </div>
                </div>
            </div>
        `;
    }

    async loadInitialLogs() {
        try {
            const lines = document.getElementById('log-lines').value;
            const response = await fetch(`/api/containers/${this.containerId}/logs?lines=${lines}`, {
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load logs');
            }

            const data = await response.json();
            this.displayLogs(data.logs || 'No logs available');

            // Update container name
            document.getElementById('container-name').textContent = data.containerName || '';
        } catch (error) {
            console.error('Error loading initial logs:', error);
            this.displayError('Failed to load logs: ' + error.message);
        }
    }

    connectWebSocket() {
        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);

            this.stompClient.connect({
                Authorization: `Bearer ${this.getAuthToken()}`
            },
            (frame) => {
                this.onConnected(frame);
            },
            (error) => {
                this.onError(error);
            });
        } catch (error) {
            console.error('WebSocket connection error:', error);
            this.updateConnectionStatus(false);
        }
    }

    onConnected(frame) {
        console.log('WebSocket connected:', frame);
        this.isConnected = true;
        this.updateConnectionStatus(true);

        // Subscribe to log stream for this container
        this.stompClient.subscribe(`/topic/logs/${this.containerId}`, (message) => {
            this.handleLogMessage(message);
        });

        // Request log streaming
        this.stompClient.send(`/app/logs/${this.containerId}/stream`, {}, JSON.stringify({
            containerId: this.containerId
        }));
    }

    onError(error) {
        console.error('WebSocket error:', error);
        this.isConnected = false;
        this.updateConnectionStatus(false);

        // Attempt to reconnect after 5 seconds
        setTimeout(() => {
            if (!this.isConnected) {
                console.log('Attempting to reconnect...');
                this.connectWebSocket();
            }
        }, 5000);
    }

    handleLogMessage(message) {
        try {
            const logData = JSON.parse(message.body);
            this.appendLog(logData.message || logData);
        } catch (error) {
            console.error('Error parsing log message:', error);
            this.appendLog(message.body);
        }
    }

    displayLogs(logsText) {
        const logOutput = document.getElementById('log-output');
        logOutput.innerHTML = '';

        if (typeof logsText === 'string') {
            this.logs = logsText.split('\n').filter(line => line.trim());
        } else {
            this.logs = Array.isArray(logsText) ? logsText : [];
        }

        this.logs.forEach(log => this.appendLog(log, false));
        this.updateLogCount();
        this.scrollToBottom();
    }

    appendLog(logLine, shouldScroll = true) {
        if (!logLine || !logLine.trim()) return;

        // Keep only last maxLines
        if (this.logs.length >= this.maxLines) {
            this.logs.shift();
        }

        this.logs.push(logLine);

        const logOutput = document.getElementById('log-output');
        const logElement = document.createElement('div');
        logElement.className = 'log-line';

        // Parse log level for color coding
        const logLevel = this.parseLogLevel(logLine);
        if (logLevel) {
            logElement.classList.add(`log-${logLevel.toLowerCase()}`);
        }

        logElement.textContent = logLine;
        logOutput.appendChild(logElement);

        this.updateLogCount();

        if (shouldScroll && this.autoScroll) {
            this.scrollToBottom();
        }
    }

    parseLogLevel(logLine) {
        if (logLine.includes('ERROR') || logLine.includes('FATAL')) return 'ERROR';
        if (logLine.includes('WARN')) return 'WARN';
        if (logLine.includes('INFO')) return 'INFO';
        if (logLine.includes('DEBUG')) return 'DEBUG';
        return null;
    }

    clearLogs() {
        this.logs = [];
        document.getElementById('log-output').innerHTML = `
            <div class="text-center py-3 text-muted">
                <p>Logs cleared. New logs will appear here.</p>
            </div>
        `;
        this.updateLogCount();
    }

    downloadLogs() {
        const logsText = this.logs.join('\n');
        const blob = new Blob([logsText], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `container-${this.containerId}-logs-${new Date().toISOString()}.txt`;
        a.click();
        URL.revokeObjectURL(url);
    }

    searchLogs(searchTerm) {
        const filtered = this.logs.filter(log =>
            log.toLowerCase().includes(searchTerm.toLowerCase())
        );

        const logOutput = document.getElementById('log-output');
        logOutput.innerHTML = '';

        filtered.forEach(log => {
            const logElement = document.createElement('div');
            logElement.className = 'log-line';

            // Highlight search term
            const regex = new RegExp(`(${searchTerm})`, 'gi');
            logElement.innerHTML = log.replace(regex, '<mark>$1</mark>');

            logOutput.appendChild(logElement);
        });

        this.updateLogCount(filtered.length);
    }

    filterByLevel(level) {
        if (!level) {
            this.displayLogs(this.logs);
            return;
        }

        const filtered = this.logs.filter(log =>
            log.toUpperCase().includes(level.toUpperCase())
        );

        const logOutput = document.getElementById('log-output');
        logOutput.innerHTML = '';

        filtered.forEach(log => this.appendLog(log, false));
        this.updateLogCount(filtered.length);
    }

    scrollToBottom() {
        const logOutput = document.getElementById('log-output');
        logOutput.scrollTop = logOutput.scrollHeight;
    }

    toggleAutoScroll() {
        this.autoScroll = !this.autoScroll;
        const btn = document.getElementById('toggle-autoscroll');
        btn.classList.toggle('active', this.autoScroll);

        if (this.autoScroll) {
            this.scrollToBottom();
        }
    }

    updateConnectionStatus(connected) {
        const statusBadge = document.getElementById('connection-status');
        if (statusBadge) {
            statusBadge.className = `badge ms-2 ${connected ? 'bg-success' : 'bg-danger'}`;
            statusBadge.textContent = connected ? 'Connected' : 'Disconnected';
        }
    }

    updateLogCount(count) {
        const logCount = document.getElementById('log-count');
        if (logCount) {
            const displayCount = count !== undefined ? count : this.logs.length;
            logCount.textContent = `${displayCount} line${displayCount !== 1 ? 's' : ''}`;
        }
    }

    displayError(message) {
        const logOutput = document.getElementById('log-output');
        logOutput.innerHTML = `
            <div class="alert alert-danger m-3" role="alert">
                <i class="bi bi-exclamation-triangle"></i> ${message}
            </div>
        `;
    }

    setupEventListeners() {
        // Clear logs button
        document.getElementById('clear-logs')?.addEventListener('click', () => {
            if (confirm('Are you sure you want to clear all logs?')) {
                this.clearLogs();
            }
        });

        // Download logs button
        document.getElementById('download-logs')?.addEventListener('click', () => {
            this.downloadLogs();
        });

        // Auto-scroll toggle
        document.getElementById('toggle-autoscroll')?.addEventListener('click', () => {
            this.toggleAutoScroll();
        });

        // Refresh logs button
        document.getElementById('refresh-logs')?.addEventListener('click', () => {
            this.loadInitialLogs();
        });

        // Search
        document.getElementById('log-search')?.addEventListener('input', (e) => {
            const searchTerm = e.target.value;
            if (searchTerm) {
                this.searchLogs(searchTerm);
            } else {
                this.displayLogs(this.logs);
            }
        });

        // Level filter
        document.getElementById('log-level-filter')?.addEventListener('change', (e) => {
            this.filterByLevel(e.target.value);
        });

        // Lines dropdown
        document.getElementById('log-lines')?.addEventListener('change', () => {
            this.loadInitialLogs();
        });
    }

    getAuthToken() {
        return localStorage.getItem('authToken') || '';
    }

    disconnect() {
        if (this.stompClient && this.isConnected) {
            this.stompClient.disconnect();
            this.updateConnectionStatus(false);
        }
    }
}

// Initialize log viewer when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const containerId = params.get('containerId');

    if (containerId) {
        window.logViewer = new LogViewer(containerId);
    } else {
        console.error('No container ID provided');
        document.getElementById('log-container').innerHTML = `
            <div class="alert alert-warning m-4" role="alert">
                <i class="bi bi-exclamation-triangle"></i> No container specified
            </div>
        `;
    }
});

// Clean up on page unload
window.addEventListener('beforeunload', () => {
    if (window.logViewer) {
        window.logViewer.disconnect();
    }
});
