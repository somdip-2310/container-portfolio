// Deployment Tracking and Monitoring
class DeploymentTracker {
    constructor() {
        this.deployments = [];
        this.selectedDeployment = null;
        this.refreshInterval = 5000; // 5 seconds
        this.autoRefresh = true;
        this.init();
    }

    init() {
        console.log('Initializing deployment tracker...');
        this.loadDeployments();
        this.startAutoRefresh();
        this.setupEventListeners();
    }

    async loadDeployments() {
        try {
            const response = await fetch('/api/deployments', {
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load deployments');
            }

            this.deployments = await response.json();
            this.renderDeployments();
        } catch (error) {
            console.error('Error loading deployments:', error);
            this.showNotification('Failed to load deployments', 'error');
        }
    }

    renderDeployments() {
        const deploymentList = document.getElementById('deployment-list');
        if (!deploymentList) return;

        if (this.deployments.length === 0) {
            deploymentList.innerHTML = `
                <div class="text-center py-5">
                    <i class="bi bi-inbox fs-1 text-muted"></i>
                    <p class="text-muted mt-3">No deployments yet.</p>
                </div>
            `;
            return;
        }

        deploymentList.innerHTML = this.deployments
            .map(deployment => this.createDeploymentCard(deployment))
            .join('');
    }

    createDeploymentCard(deployment) {
        const statusBadge = this.getStatusBadge(deployment.status);
        const progressPercent = this.calculateProgress(deployment);
        const duration = this.calculateDuration(deployment);

        return `
            <div class="card mb-3 deployment-card" data-deployment-id="${deployment.deploymentId}">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <div>
                        <h6 class="mb-0">
                            ${deployment.containerName}
                            ${statusBadge}
                        </h6>
                        <small class="text-muted">Deployment ID: ${deployment.deploymentId.substring(0, 8)}...</small>
                    </div>
                    <div>
                        <small class="text-muted">${this.formatTimestamp(deployment.startedAt)}</small>
                    </div>
                </div>
                <div class="card-body">
                    ${this.renderDeploymentSteps(deployment)}
                    <div class="progress mt-3" style="height: 25px;">
                        <div class="progress-bar ${this.getProgressBarClass(deployment.status)}"
                             role="progressbar"
                             style="width: ${progressPercent}%"
                             aria-valuenow="${progressPercent}"
                             aria-valuemin="0"
                             aria-valuemax="100">
                            ${progressPercent}%
                        </div>
                    </div>
                    ${duration ? `
                        <div class="mt-2 text-muted small">
                            <i class="bi bi-clock"></i> Duration: ${duration}
                        </div>
                    ` : ''}
                </div>
                <div class="card-footer">
                    <div class="btn-group btn-group-sm" role="group">
                        <button class="btn btn-outline-primary"
                                onclick="deploymentTracker.viewDetails('${deployment.deploymentId}')">
                            <i class="bi bi-info-circle"></i> Details
                        </button>
                        ${deployment.status === 'FAILED' ? `
                            <button class="btn btn-outline-warning"
                                    onclick="deploymentTracker.retryDeployment('${deployment.deploymentId}')">
                                <i class="bi bi-arrow-clockwise"></i> Retry
                            </button>
                        ` : ''}
                        ${deployment.status === 'IN_PROGRESS' ? `
                            <button class="btn btn-outline-danger"
                                    onclick="deploymentTracker.cancelDeployment('${deployment.deploymentId}')">
                                <i class="bi bi-x-circle"></i> Cancel
                            </button>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;
    }

    renderDeploymentSteps(deployment) {
        if (!deployment.steps || deployment.steps.length === 0) {
            return '<p class="text-muted mb-0">No deployment steps available</p>';
        }

        return `
            <div class="deployment-steps">
                ${deployment.steps.map(step => this.renderStep(step)).join('')}
            </div>
        `;
    }

    renderStep(step) {
        const icon = this.getStepIcon(step.status);
        const statusClass = this.getStepStatusClass(step.status);

        return `
            <div class="deployment-step ${statusClass}">
                <div class="step-icon">${icon}</div>
                <div class="step-content">
                    <div class="step-name">${step.name}</div>
                    ${step.message ? `<div class="step-message text-muted small">${step.message}</div>` : ''}
                    ${step.startedAt ? `
                        <div class="step-time text-muted small">
                            Started: ${this.formatTimestamp(step.startedAt)}
                        </div>
                    ` : ''}
                </div>
            </div>
        `;
    }

    getStepIcon(status) {
        const icons = {
            'PENDING': '<i class="bi bi-circle text-secondary"></i>',
            'IN_PROGRESS': '<i class="bi bi-arrow-repeat spin text-primary"></i>',
            'COMPLETED': '<i class="bi bi-check-circle-fill text-success"></i>',
            'FAILED': '<i class="bi bi-x-circle-fill text-danger"></i>',
            'SKIPPED': '<i class="bi bi-skip-forward text-muted"></i>'
        };
        return icons[status] || icons['PENDING'];
    }

    getStepStatusClass(status) {
        const classes = {
            'PENDING': 'step-pending',
            'IN_PROGRESS': 'step-in-progress',
            'COMPLETED': 'step-completed',
            'FAILED': 'step-failed',
            'SKIPPED': 'step-skipped'
        };
        return classes[status] || '';
    }

    getStatusBadge(status) {
        const badges = {
            'IN_PROGRESS': '<span class="badge bg-primary"><i class="bi bi-arrow-repeat spin"></i> In Progress</span>',
            'COMPLETED': '<span class="badge bg-success"><i class="bi bi-check-circle"></i> Completed</span>',
            'FAILED': '<span class="badge bg-danger"><i class="bi bi-x-circle"></i> Failed</span>',
            'CANCELLED': '<span class="badge bg-secondary"><i class="bi bi-stop-circle"></i> Cancelled</span>',
            'PENDING': '<span class="badge bg-warning"><i class="bi bi-clock"></i> Pending</span>'
        };
        return badges[status] || '<span class="badge bg-secondary">Unknown</span>';
    }

    getProgressBarClass(status) {
        const classes = {
            'IN_PROGRESS': 'progress-bar-striped progress-bar-animated bg-primary',
            'COMPLETED': 'bg-success',
            'FAILED': 'bg-danger',
            'CANCELLED': 'bg-secondary',
            'PENDING': 'bg-warning'
        };
        return classes[status] || '';
    }

    calculateProgress(deployment) {
        if (deployment.status === 'COMPLETED') return 100;
        if (deployment.status === 'FAILED') return 100;
        if (deployment.status === 'CANCELLED') return 100;

        if (!deployment.steps || deployment.steps.length === 0) return 0;

        const completedSteps = deployment.steps.filter(s => s.status === 'COMPLETED').length;
        return Math.round((completedSteps / deployment.steps.length) * 100);
    }

    calculateDuration(deployment) {
        if (!deployment.startedAt) return null;

        const start = new Date(deployment.startedAt);
        const end = deployment.completedAt ? new Date(deployment.completedAt) : new Date();
        const diffMs = end - start;

        const seconds = Math.floor(diffMs / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);

        if (hours > 0) {
            return `${hours}h ${minutes % 60}m`;
        } else if (minutes > 0) {
            return `${minutes}m ${seconds % 60}s`;
        } else {
            return `${seconds}s`;
        }
    }

    async viewDetails(deploymentId) {
        try {
            const response = await fetch(`/api/deployments/${deploymentId}`, {
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load deployment details');
            }

            const deployment = await response.json();
            this.showDetailsModal(deployment);
        } catch (error) {
            console.error('Error loading deployment details:', error);
            this.showNotification('Failed to load deployment details', 'error');
        }
    }

    showDetailsModal(deployment) {
        const modalBody = document.getElementById('deploymentDetailsBody');
        if (!modalBody) return;

        modalBody.innerHTML = `
            <dl class="row">
                <dt class="col-sm-4">Deployment ID:</dt>
                <dd class="col-sm-8"><code>${deployment.deploymentId}</code></dd>

                <dt class="col-sm-4">Container:</dt>
                <dd class="col-sm-8">${deployment.containerName}</dd>

                <dt class="col-sm-4">Status:</dt>
                <dd class="col-sm-8">${this.getStatusBadge(deployment.status)}</dd>

                <dt class="col-sm-4">Started:</dt>
                <dd class="col-sm-8">${this.formatTimestamp(deployment.startedAt)}</dd>

                ${deployment.completedAt ? `
                    <dt class="col-sm-4">Completed:</dt>
                    <dd class="col-sm-8">${this.formatTimestamp(deployment.completedAt)}</dd>
                ` : ''}

                ${deployment.errorMessage ? `
                    <dt class="col-sm-4">Error:</dt>
                    <dd class="col-sm-8"><span class="text-danger">${deployment.errorMessage}</span></dd>
                ` : ''}
            </dl>

            <h6 class="mt-4">Deployment Steps:</h6>
            ${this.renderDeploymentSteps(deployment)}
        `;

        const modal = new bootstrap.Modal(document.getElementById('deploymentDetailsModal'));
        modal.show();
    }

    async retryDeployment(deploymentId) {
        if (!confirm('Are you sure you want to retry this deployment?')) {
            return;
        }

        try {
            const deployment = this.deployments.find(d => d.deploymentId === deploymentId);
            if (!deployment) {
                throw new Error('Deployment not found');
            }

            const response = await fetch(`/api/containers/${deployment.containerId}/deploy`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to retry deployment');
            }

            this.showNotification('Deployment retry initiated', 'success');
            this.loadDeployments();
        } catch (error) {
            console.error('Error retrying deployment:', error);
            this.showNotification(error.message, 'error');
        }
    }

    async cancelDeployment(deploymentId) {
        if (!confirm('Are you sure you want to cancel this deployment?')) {
            return;
        }

        try {
            const response = await fetch(`/api/deployments/${deploymentId}/cancel`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.getAuthToken()}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to cancel deployment');
            }

            this.showNotification('Deployment cancelled', 'success');
            this.loadDeployments();
        } catch (error) {
            console.error('Error cancelling deployment:', error);
            this.showNotification(error.message, 'error');
        }
    }

    formatTimestamp(timestamp) {
        if (!timestamp) return 'N/A';
        const date = new Date(timestamp);
        return date.toLocaleString();
    }

    startAutoRefresh() {
        if (this.autoRefresh) {
            setInterval(() => {
                // Only refresh if there are in-progress deployments
                const hasInProgress = this.deployments.some(d => d.status === 'IN_PROGRESS');
                if (hasInProgress) {
                    this.loadDeployments();
                }
            }, this.refreshInterval);
        }
    }

    setupEventListeners() {
        // Refresh button
        const refreshBtn = document.getElementById('refresh-deployments');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.loadDeployments());
        }

        // Filter buttons
        document.querySelectorAll('.deployment-filter').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const filter = e.target.dataset.filter;
                this.filterDeployments(filter);

                // Update active state
                document.querySelectorAll('.deployment-filter').forEach(b => b.classList.remove('active'));
                e.target.classList.add('active');
            });
        });

        // Auto-refresh toggle
        const autoRefreshToggle = document.getElementById('toggle-auto-refresh');
        if (autoRefreshToggle) {
            autoRefreshToggle.addEventListener('change', (e) => {
                this.autoRefresh = e.target.checked;
            });
        }
    }

    filterDeployments(filter) {
        let filtered;

        if (filter === 'all') {
            filtered = this.deployments;
        } else {
            filtered = this.deployments.filter(d => d.status === filter.toUpperCase());
        }

        const deploymentList = document.getElementById('deployment-list');
        deploymentList.innerHTML = filtered
            .map(deployment => this.createDeploymentCard(deployment))
            .join('');
    }

    getAuthToken() {
        return localStorage.getItem('authToken') || '';
    }

    showNotification(message, type = 'info') {
        if (window.ToastNotification) {
            window.ToastNotification.show(message, type);
        } else {
            alert(message);
        }
    }
}

// Initialize deployment tracker when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    window.deploymentTracker = new DeploymentTracker();
});
