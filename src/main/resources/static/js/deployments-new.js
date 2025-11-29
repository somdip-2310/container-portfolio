// Deployments Page JavaScript
// Functions for deployments page interactions

// Get CSRF token for API calls
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

// Filter deployments by status
document.addEventListener('DOMContentLoaded', function() {
    const statusFilter = document.getElementById('statusFilter');
    const timeRange = document.getElementById('timeRange');

    if (statusFilter) {
        statusFilter.addEventListener('change', filterDeployments);
    }
    if (timeRange) {
        timeRange.addEventListener('change', filterDeployments);
    }
});

function filterDeployments() {
    const statusFilter = document.getElementById('statusFilter')?.value || '';
    const rows = document.querySelectorAll('.deployment-row');

    rows.forEach(row => {
        const status = row.getAttribute('data-status') || '';
        const matchesStatus = !statusFilter || status === statusFilter;
        row.style.display = matchesStatus ? '' : 'none';
    });
}

// View deployment details in a modal
async function viewDeploymentDetails(deploymentId) {
    if (!deploymentId) {
        console.error('No deployment ID provided');
        return;
    }

    // Show loading state
    showDeploymentModal(deploymentId, null, true);

    try {
        const response = await fetch('/api/deployments/' + deploymentId, {
            method: 'GET',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            }
        });

        if (response.ok) {
            const deployment = await response.json();
            showDeploymentModal(deploymentId, deployment, false);
        } else {
            console.error('Failed to fetch deployment details:', response.status);
            showToast('Failed to load deployment details', 'error');
            hideDeploymentModal();
        }
    } catch (error) {
        console.error('Error fetching deployment details:', error);
        showToast('Error loading deployment details', 'error');
        hideDeploymentModal();
    }
}

// Show deployment details modal
function showDeploymentModal(deploymentId, deployment, isLoading) {
    // Check if modal already exists
    let modal = document.getElementById('deploymentDetailsModal');

    if (!modal) {
        // Create modal
        const modalHtml = `
            <div id="deploymentDetailsModal" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
                <div class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-3xl w-full mx-4 max-h-[90vh] flex flex-col">
                    <!-- Header -->
                    <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
                        <div>
                            <h3 class="text-xl font-bold text-gray-900 dark:text-white">Deployment Details</h3>
                            <p id="modalContainerName" class="text-sm text-gray-500 dark:text-gray-400 mt-1"></p>
                            <p id="modalDeploymentId" class="text-xs text-gray-400 dark:text-gray-500 mt-1 font-mono"></p>
                        </div>
                        <button onclick="hideDeploymentModal()" class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
                            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                            </svg>
                        </button>
                    </div>

                    <!-- Content -->
                    <div id="modalContent" class="flex-1 overflow-y-auto p-6">
                        <!-- Loading state or content goes here -->
                    </div>

                    <!-- Footer -->
                    <div class="p-6 border-t border-gray-200 dark:border-gray-700">
                        <div class="flex justify-end">
                            <button onclick="hideDeploymentModal()" class="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 transition-colors">
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        modal = document.getElementById('deploymentDetailsModal');
    }

    const modalContent = document.getElementById('modalContent');
    const modalContainerName = document.getElementById('modalContainerName');

    if (isLoading) {
        modalContainerName.textContent = 'Loading...';
        modalContent.innerHTML = `
            <div class="flex items-center justify-center py-12">
                <i class="fas fa-spinner fa-spin text-4xl text-purple-500"></i>
            </div>
        `;
        modal.classList.remove('hidden');
        return;
    }

    if (!deployment) {
        modalContent.innerHTML = '<p class="text-gray-500 text-center py-8">No deployment data available</p>';
        return;
    }

    // Update header
    modalContainerName.textContent = deployment.containerName || 'Unknown Container';

    // Set deployment ID for reference
    const modalDeploymentId = document.getElementById('modalDeploymentId');
    if (modalDeploymentId) {
        modalDeploymentId.textContent = deploymentId ? 'ID: ' + deploymentId : '';
    }

    // Build content
    const statusClass = getStatusClass(deployment.status);
    const statusIcon = getStatusIcon(deployment.status);

    let stepsHtml = '';
    if (deployment.steps && deployment.steps.length > 0) {
        stepsHtml = deployment.steps.map(step => {
            const stepStatusClass = getStepStatusClass(step.status);
            const stepIcon = getStepIcon(step.status);
            return `
                <div class="flex items-start space-x-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50">
                    <div class="flex-shrink-0 mt-0.5">${stepIcon}</div>
                    <div class="flex-1 min-w-0">
                        <p class="text-sm font-medium text-gray-900 dark:text-white">${formatStepName(step.stepName)}</p>
                        <p class="text-sm text-gray-500 dark:text-gray-400">${step.message || ''}</p>
                    </div>
                    <span class="flex-shrink-0 px-2 py-1 text-xs font-medium rounded-full ${stepStatusClass}">${step.status}</span>
                </div>
            `;
        }).join('');
    }

    // Error logs section
    let errorLogsHtml = '';
    if (deployment.status === 'FAILED' && deployment.errorMessage) {
        const errorMessage = deployment.errorMessage;
        let formattedError = errorMessage;

        // Check if message contains container logs
        if (errorMessage.includes('--- Container Logs ---')) {
            const parts = errorMessage.split('--- Container Logs ---');
            const mainError = parts[0].trim();
            const containerLogs = parts[1] ? parts[1].trim() : '';
            formattedError = '=== Error ===\\n' + mainError;
            if (containerLogs) {
                formattedError += '\\n\\n=== Container Logs ===\\n' + containerLogs;
            }
        }

        errorLogsHtml = `
            <div class="mt-6 border-t border-red-200 dark:border-red-800 pt-4">
                <div class="flex items-center justify-between mb-3">
                    <div class="flex items-center">
                        <i class="fas fa-exclamation-triangle text-red-500 mr-2"></i>
                        <h4 class="font-medium text-red-700 dark:text-red-300">Error Details & Container Logs</h4>
                    </div>
                    <button onclick="copyDeploymentErrorLogs()" class="text-xs text-red-600 hover:text-red-800 dark:text-red-400">
                        <i class="fas fa-copy mr-1"></i>Copy
                    </button>
                </div>
                <pre id="deploymentErrorContent" class="bg-gray-900 text-green-400 p-4 rounded-lg font-mono text-xs overflow-x-auto max-h-64 overflow-y-auto whitespace-pre-wrap">${escapeHtml(formattedError)}</pre>
                <p class="mt-3 text-xs text-red-600 dark:text-red-400">
                    <i class="fas fa-info-circle mr-1"></i>
                    These logs can help identify why your container failed to start.
                </p>
            </div>
        `;
    }

    modalContent.innerHTML = `
        <!-- Status Badge -->
        <div class="flex items-center space-x-3 mb-6">
            <span class="px-3 py-1 rounded-full text-sm font-medium ${statusClass}">
                ${statusIcon} ${deployment.status}
            </span>
            <span class="text-sm text-gray-500 dark:text-gray-400">
                ${deployment.type || 'Deployment'}
            </span>
        </div>

        <!-- Info Grid -->
        <div class="grid grid-cols-2 gap-4 mb-6">
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
                <p class="text-xs text-gray-500 dark:text-gray-400">Image</p>
                <p class="text-sm font-medium text-gray-900 dark:text-white truncate">${deployment.newImage || 'N/A'}</p>
            </div>
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
                <p class="text-xs text-gray-500 dark:text-gray-400">Duration</p>
                <p class="text-sm font-medium text-gray-900 dark:text-white">${deployment.durationMillis ? (deployment.durationMillis / 1000).toFixed(1) + 's' : 'N/A'}</p>
            </div>
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
                <p class="text-xs text-gray-500 dark:text-gray-400">Started</p>
                <p class="text-sm font-medium text-gray-900 dark:text-white">${deployment.startedAt ? formatDate(deployment.startedAt) : 'N/A'}</p>
            </div>
            <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-3">
                <p class="text-xs text-gray-500 dark:text-gray-400">Completed</p>
                <p class="text-sm font-medium text-gray-900 dark:text-white">${deployment.completedAt ? formatDate(deployment.completedAt) : 'In Progress'}</p>
            </div>
        </div>

        <!-- Steps -->
        ${stepsHtml ? `
            <div class="mb-6">
                <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">Deployment Steps</h4>
                <div class="space-y-2">${stepsHtml}</div>
            </div>
        ` : ''}

        <!-- Error Logs -->
        ${errorLogsHtml}
    `;

    // Store error message for copy functionality
    if (deployment.errorMessage) {
        window.currentDeploymentError = deployment.errorMessage;
    }

    modal.classList.remove('hidden');
}

// Hide deployment details modal
function hideDeploymentModal() {
    const modal = document.getElementById('deploymentDetailsModal');
    if (modal) {
        modal.remove();
    }
    window.currentDeploymentError = null;
}

// Copy deployment error logs
function copyDeploymentErrorLogs() {
    const errorMessage = window.currentDeploymentError;
    if (!errorMessage) {
        showToast('No error logs to copy', 'warning');
        return;
    }

    navigator.clipboard.writeText(errorMessage).then(() => {
        showToast('Error logs copied to clipboard', 'success');
    }).catch(err => {
        console.error('Failed to copy:', err);
        const textArea = document.createElement('textarea');
        textArea.value = errorMessage;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        showToast('Error logs copied to clipboard', 'success');
    });
}

// Helper functions
function getStatusClass(status) {
    switch (status) {
        case 'COMPLETED':
            return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
        case 'FAILED':
            return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
        case 'IN_PROGRESS':
        case 'PENDING':
            return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
        default:
            return 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300';
    }
}

function getStatusIcon(status) {
    switch (status) {
        case 'COMPLETED':
            return '<i class="fas fa-check-circle mr-1"></i>';
        case 'FAILED':
            return '<i class="fas fa-times-circle mr-1"></i>';
        case 'IN_PROGRESS':
        case 'PENDING':
            return '<i class="fas fa-spinner fa-spin mr-1"></i>';
        default:
            return '';
    }
}

function getStepStatusClass(status) {
    switch (status) {
        case 'COMPLETED':
            return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
        case 'FAILED':
            return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
        case 'IN_PROGRESS':
            return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
        default:
            return 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300';
    }
}

function getStepIcon(status) {
    switch (status) {
        case 'COMPLETED':
            return '<svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>';
        case 'FAILED':
            return '<svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>';
        case 'IN_PROGRESS':
            return '<svg class="w-5 h-5 text-blue-500 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>';
        default:
            return '<div class="w-5 h-5 rounded-full bg-gray-300 dark:bg-gray-600"></div>';
    }
}

function formatStepName(stepName) {
    const names = {
        'CREATE_TASK_DEFINITION': 'Create Task Definition',
        'CREATE_SERVICE': 'Create Service',
        'WAIT_FOR_STABLE': 'Wait for Service Stable',
        'GET_TASK_INFO': 'Get Task Info',
        'REGISTER_TARGET_GROUP': 'Register with Load Balancer',
        'CONFIGURE_HEALTH_CHECK': 'Configure Health Check',
        'INITIALIZING': 'Initializing',
        'BUILDING': 'Building',
        'DEPLOYING': 'Deploying'
    };
    return names[stepName] || stepName;
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleString();
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Toast notification function (if not already defined globally)
function showToast(message, type) {
    type = type || 'info';

    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'fixed top-4 right-4 z-50 space-y-2';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');

    const bgColors = {
        'success': 'bg-green-500',
        'error': 'bg-red-500',
        'info': 'bg-blue-500',
        'warning': 'bg-yellow-500'
    };

    const icons = {
        'success': 'fa-check-circle',
        'error': 'fa-exclamation-circle',
        'info': 'fa-info-circle',
        'warning': 'fa-exclamation-triangle'
    };

    toast.className = bgColors[type] + ' text-white px-6 py-4 rounded-lg shadow-lg flex items-center space-x-3 min-w-80';
    toast.innerHTML = '<i class="fas ' + icons[type] + ' text-xl"></i>' +
        '<span class="flex-1">' + message + '</span>' +
        '<button onclick="this.parentElement.remove()" class="text-white hover:text-gray-200">' +
            '<i class="fas fa-times"></i>' +
        '</button>';

    container.appendChild(toast);

    setTimeout(() => {
        toast.remove();
    }, 5000);
}
