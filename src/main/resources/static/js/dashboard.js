// Dashboard Real-time Updates
class Dashboard {
    constructor() {
        this.refreshInterval = 30000; // 30 seconds
        this.metricsChart = null;
        this.init();
    }

    init() {
        console.log('Initializing dashboard...');
        this.loadDashboardStats();
        this.loadRecentActivity();
        this.startAutoRefresh();
        this.setupEventListeners();
    }

    async loadDashboardStats() {
        try {
            const response = await fetch('/api/dashboard/stats', {
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load dashboard stats');
            }

            const stats = await response.json();
            this.updateStats(stats);
            this.updateMetricsChart(stats);
        } catch (error) {
            console.error('Error loading dashboard stats:', error);
            this.showError('Failed to load dashboard statistics');
        }
    }

    updateStats(stats) {
        // Update container counts
        document.getElementById('total-containers').textContent = stats.totalContainers || 0;
        document.getElementById('running-containers').textContent = stats.runningContainers || 0;
        document.getElementById('stopped-containers').textContent =
            (stats.totalContainers - stats.runningContainers) || 0;

        // Update resource usage
        const cpuUsage = stats.avgCpuUsage || 0;
        const memoryUsage = stats.avgMemoryUsage || 0;

        document.getElementById('cpu-usage').textContent = `${cpuUsage.toFixed(1)}%`;
        document.getElementById('memory-usage').textContent = `${memoryUsage.toFixed(1)}%`;

        // Update progress bars
        this.updateProgressBar('cpu-progress', cpuUsage);
        this.updateProgressBar('memory-progress', memoryUsage);

        // Update status indicators
        this.updateStatusIndicator('cpu-status', cpuUsage);
        this.updateStatusIndicator('memory-status', memoryUsage);
    }

    updateProgressBar(elementId, value) {
        const progressBar = document.getElementById(elementId);
        if (progressBar) {
            progressBar.style.width = `${value}%`;
            progressBar.setAttribute('aria-valuenow', value);

            // Change color based on usage
            progressBar.className = 'progress-bar';
            if (value > 80) {
                progressBar.classList.add('bg-danger');
            } else if (value > 60) {
                progressBar.classList.add('bg-warning');
            } else {
                progressBar.classList.add('bg-success');
            }
        }
    }

    updateStatusIndicator(elementId, value) {
        const indicator = document.getElementById(elementId);
        if (indicator) {
            indicator.className = 'status-indicator';
            if (value > 80) {
                indicator.classList.add('status-danger');
                indicator.title = 'High usage';
            } else if (value > 60) {
                indicator.classList.add('status-warning');
                indicator.title = 'Medium usage';
            } else {
                indicator.classList.add('status-success');
                indicator.title = 'Normal usage';
            }
        }
    }

    async loadRecentActivity() {
        try {
            const response = await fetch('/api/dashboard/activity?limit=10', {
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load recent activity');
            }

            const activities = await response.json();
            this.updateActivityFeed(activities);
        } catch (error) {
            console.error('Error loading recent activity:', error);
        }
    }

    updateActivityFeed(activities) {
        const activityList = document.getElementById('activity-list');
        if (!activityList) return;

        activityList.innerHTML = '';

        if (activities.length === 0) {
            activityList.innerHTML = '<li class="list-group-item text-muted">No recent activity</li>';
            return;
        }

        activities.forEach(activity => {
            const li = document.createElement('li');
            li.className = 'list-group-item';
            li.innerHTML = `
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <i class="${this.getActivityIcon(activity.type)} me-2"></i>
                        <strong>${activity.containerName}</strong>
                        <span class="text-muted ms-2">${activity.message}</span>
                    </div>
                    <small class="text-muted">${this.formatTimestamp(activity.timestamp)}</small>
                </div>
            `;
            activityList.appendChild(li);
        });
    }

    getActivityIcon(type) {
        const icons = {
            'DEPLOYED': 'bi bi-rocket-takeoff text-success',
            'STOPPED': 'bi bi-stop-circle text-danger',
            'RESTARTED': 'bi bi-arrow-clockwise text-info',
            'CREATED': 'bi bi-plus-circle text-primary',
            'DELETED': 'bi bi-trash text-danger',
            'ERROR': 'bi bi-exclamation-triangle text-warning'
        };
        return icons[type] || 'bi bi-info-circle';
    }

    formatTimestamp(timestamp) {
        const date = new Date(timestamp);
        const now = new Date();
        const diff = now - date;

        if (diff < 60000) {
            return 'Just now';
        } else if (diff < 3600000) {
            const minutes = Math.floor(diff / 60000);
            return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
        } else if (diff < 86400000) {
            const hours = Math.floor(diff / 3600000);
            return `${hours} hour${hours > 1 ? 's' : ''} ago`;
        } else {
            return date.toLocaleDateString();
        }
    }

    updateMetricsChart(stats) {
        const ctx = document.getElementById('metricsChart');
        if (!ctx) return;

        if (this.metricsChart) {
            this.metricsChart.destroy();
        }

        this.metricsChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: this.getTimeLabels(),
                datasets: [{
                    label: 'CPU Usage %',
                    data: stats.cpuHistory || [],
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.1)',
                    tension: 0.4
                }, {
                    label: 'Memory Usage %',
                    data: stats.memoryHistory || [],
                    borderColor: 'rgb(153, 102, 255)',
                    backgroundColor: 'rgba(153, 102, 255, 0.1)',
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: {
                        beginAtZero: true,
                        max: 100
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top'
                    }
                }
            }
        });
    }

    getTimeLabels() {
        const labels = [];
        const now = new Date();
        for (let i = 9; i >= 0; i--) {
            const time = new Date(now - i * 60000);
            labels.push(time.toLocaleTimeString());
        }
        return labels;
    }

    startAutoRefresh() {
        setInterval(() => {
            this.loadDashboardStats();
            this.loadRecentActivity();
        }, this.refreshInterval);
    }

    setupEventListeners() {
        // Refresh button
        const refreshBtn = document.getElementById('refresh-dashboard');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => {
                this.loadDashboardStats();
                this.loadRecentActivity();
            });
        }

        // Quick action buttons
        document.querySelectorAll('.quick-action-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const action = e.target.dataset.action;
                this.handleQuickAction(action);
            });
        });
    }

    handleQuickAction(action) {
        switch(action) {
            case 'create':
                window.location.href = '/containers?action=create';
                break;
            case 'deploy':
                window.location.href = '/containers?action=deploy';
                break;
            case 'monitor':
                window.location.href = '/metrics';
                break;
            default:
                console.log('Unknown action:', action);
        }
    }

    getAuthToken() {
        return localStorage.getItem('authToken') || '';
    }

    showError(message) {
        const alertDiv = document.createElement('div');
        alertDiv.className = 'alert alert-danger alert-dismissible fade show';
        alertDiv.innerHTML = `
            ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        document.querySelector('.container-fluid').prepend(alertDiv);
    }
}

// Initialize dashboard when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new Dashboard();
});
