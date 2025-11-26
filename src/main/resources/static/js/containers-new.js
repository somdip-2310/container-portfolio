// Container Management JavaScript
// All functions for container page interactions

// Get CSRF token for API calls
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

// View toggle functionality
document.addEventListener('DOMContentLoaded', function() {
    const gridView = document.getElementById('gridView');
    const listView = document.getElementById('listView');

    if (gridView && listView) {
        gridView.addEventListener('click', () => {
            gridView.classList.add('bg-white', 'dark:bg-gray-600', 'shadow-sm');
            listView.classList.remove('bg-white', 'dark:bg-gray-600', 'shadow-sm');
            listView.classList.add('text-gray-500', 'dark:text-gray-400');
        });

        listView.addEventListener('click', () => {
            listView.classList.add('bg-white', 'dark:bg-gray-600', 'shadow-sm');
            gridView.classList.remove('bg-white', 'dark:bg-gray-600', 'shadow-sm');
            gridView.classList.add('text-gray-500', 'dark:text-gray-400');
        });
    }

    // Hide modals on page load
    hideProgressModal();
    hideDeployModal();
});

// Filter containers
function filterContainers() {
    const searchText = document.getElementById('searchInput')?.value.toLowerCase() || '';
    const statusFilter = document.getElementById('statusFilter')?.value || '';
    const cards = document.querySelectorAll('.container-card');

    cards.forEach(card => {
        const name = card.getAttribute('data-name')?.toLowerCase() || '';
        const status = card.getAttribute('data-status') || '';
        const matchesSearch = name.includes(searchText);
        const matchesStatus = !statusFilter || status === statusFilter;
        card.style.display = (matchesSearch && matchesStatus) ? 'block' : 'none';
    });
}

// Sort containers
function sortContainers() {
    console.log('Sort functionality - to be implemented');
}

// Start container
async function startContainer(containerId) {
    try {
        showProgressModal('Starting Container', 'Initiating start...');
        updateProgress(10);

        const response = await fetch('/web/api/containers/' + containerId + '/start', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            updateProgressWithMessage(50, 'Start initiated successfully...');
            setTimeout(() => {
                updateProgress(100);
                setTimeout(() => {
                    hideProgressModal();
                    showToast('Container start initiated. Page will refresh...', 'success');
                    setTimeout(() => location.reload(), 1500);
                }, 500);
            }, 1000);
        } else {
            hideProgressModal();
            const error = await response.text();
            showToast('Failed to start container: ' + error, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error starting container:', error);
        showToast('Error starting container', 'error');
    }
}

// Confirm and stop container
function confirmStopContainer(containerId) {
    if (confirm('Are you sure you want to stop this container? The container will be shut down gracefully.')) {
        stopContainer(containerId);
    }
}

// Stop container
async function stopContainer(containerId) {
    try {
        showToast('Stopping container...', 'info');
        const response = await fetch('/web/api/containers/' + containerId + '/stop', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            showToast('Container stopped successfully. Page will refresh...', 'success');
            setTimeout(() => location.reload(), 1500);
        } else {
            const error = await response.text();
            showToast('Failed to stop container: ' + error, 'error');
        }
    } catch (error) {
        console.error('Error stopping container:', error);
        showToast('Error stopping container', 'error');
    }
}

// View logs
function viewLogs(containerId) {
    window.location.href = '/logs?containerId=' + containerId;
}

// Confirm and delete container
function confirmDeleteContainer(containerId) {
    if (confirm('Are you sure you want to delete this container? This action cannot be undone.')) {
        deleteContainer(containerId);
    }
}

// Delete container
async function deleteContainer(containerId) {
    try {
        showProgressModal('Deleting Container', 'Removing container and associated resources...');
        updateProgress(10);

        const response = await fetch('/web/api/containers/' + containerId, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken
            }
        });

        if (response.ok) {
            updateProgressWithMessage(50, 'Container deleted successfully...');
            setTimeout(() => {
                updateProgress(100);
                setTimeout(() => {
                    hideProgressModal();
                    showToast('Container deleted successfully. Page will refresh...', 'success');
                    setTimeout(() => location.reload(), 1500);
                }, 500);
            }, 1000);
        } else {
            hideProgressModal();
            const error = await response.text();
            showToast('Failed to delete container: ' + error, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error deleting container:', error);
        showToast('Error deleting container', 'error');
    }
}

// Show deploy modal
function showDeployModal() {
    const modal = document.getElementById('deployModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('flex');
    }
}

// Hide deploy modal
function hideDeployModal() {
    const modal = document.getElementById('deployModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('flex');
        // Reset to docker image tab
        switchDeployTab('dockerImage');
    }
}

// Switch between deployment tabs (Docker Image vs Source Code)
function switchDeployTab(tabName) {
    const dockerImageTab = document.getElementById('dockerImageTab');
    const sourceCodeTab = document.getElementById('sourceCodeTab');
    const dockerImageForm = document.getElementById('deployForm');
    const sourceCodeForm = document.getElementById('sourceCodeForm');

    if (tabName === 'dockerImage') {
        // Show Docker Image tab and form
        dockerImageTab.classList.add('text-purple-600', 'border-b-2', 'border-purple-600');
        dockerImageTab.classList.remove('text-gray-500', 'dark:text-gray-400');
        sourceCodeTab.classList.remove('text-purple-600', 'border-b-2', 'border-purple-600');
        sourceCodeTab.classList.add('text-gray-500', 'dark:text-gray-400');

        dockerImageForm.classList.remove('hidden');
        sourceCodeForm.classList.add('hidden');
    } else if (tabName === 'sourceCode') {
        // Show Source Code tab and form
        sourceCodeTab.classList.add('text-purple-600', 'border-b-2', 'border-purple-600');
        sourceCodeTab.classList.remove('text-gray-500', 'dark:text-gray-400');
        dockerImageTab.classList.remove('text-purple-600', 'border-b-2', 'border-purple-600');
        dockerImageTab.classList.add('text-gray-500', 'dark:text-gray-400');

        sourceCodeForm.classList.remove('hidden');
        dockerImageForm.classList.add('hidden');
    }
}

// Show progress modal
function showProgressModal(title, message) {
    const modal = document.getElementById('progressModal');
    const titleEl = document.getElementById('progressTitle');
    const messageEl = document.getElementById('progressMessage');

    if (modal && titleEl && messageEl) {
        titleEl.textContent = title;
        messageEl.textContent = message;
        modal.classList.remove('hidden');
        modal.classList.add('flex');
    }
}

// Hide progress modal
function hideProgressModal() {
    const modal = document.getElementById('progressModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('flex');
    }
}

// Update progress
function updateProgress(percent) {
    const bar = document.getElementById('progressBar');
    const text = document.getElementById('progressPercent');

    if (bar) bar.style.width = percent + '%';
    if (text) text.textContent = percent + '%';
}

// Update progress with message
function updateProgressWithMessage(percent, message) {
    updateProgress(percent);
    const messageEl = document.getElementById('progressMessage');
    if (messageEl) messageEl.textContent = message;
}

// Deploy from source
async function deployFromSource(event) {
    event.preventDefault();

    const form = event.target;
    const formData = new FormData(form);
    const submitBtn = form.querySelector('button[type="submit"]');

    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Deploying...';

    try {
        showProgressModal('Deploying from Source', 'Uploading source code...');
        updateProgress(10);

        const response = await fetch('/api/source/deploy', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken
            },
            body: formData
        });

        if (response.ok) {
            const result = await response.json();
            updateProgressWithMessage(30, 'Project analyzed: ' + result.projectTypeDisplay);

            if (result.deploymentId) {
                await pollDeploymentStatus(result.deploymentId);
            } else {
                updateProgressWithMessage(70, 'Building Docker image...');
                await new Promise(resolve => setTimeout(resolve, 2000));
                updateProgressWithMessage(90, 'Creating container...');
                await new Promise(resolve => setTimeout(resolve, 1000));
                updateProgress(100);
                setTimeout(() => {
                    hideProgressModal();
                    showToast('Deployment completed successfully!', 'success');
                    setTimeout(() => location.reload(), 1000);
                }, 500);
            }

            hideDeployModal();
        } else {
            hideProgressModal();
            let errorMessage = 'Unknown error';
            const contentType = response.headers.get('content-type');

            if (contentType && contentType.includes('application/json')) {
                try {
                    const error = await response.json();
                    errorMessage = error.error || error.message || 'Unknown error';
                } catch (e) {
                    errorMessage = 'Failed to parse error response';
                }
            } else {
                try {
                    errorMessage = await response.text() || 'Server error';
                } catch (e) {
                    errorMessage = 'Failed to read error response';
                }
            }
            showToast('Deployment failed: ' + errorMessage, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error deploying from source:', error);
        showToast('Error deploying from source: ' + error.message, 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<i class="fas fa-upload mr-2"></i>Deploy from Source';
    }
}

// Poll deployment status
async function pollDeploymentStatus(deploymentId) {
    const maxAttempts = 60;
    let attempts = 0;

    const poll = async () => {
        try {
            const response = await fetch('/api/source/status/' + deploymentId, {
                credentials: 'same-origin',
                headers: {
                    [csrfHeader]: csrfToken
                }
            });

            if (response.ok) {
                const status = await response.json();

                if (status.currentStep) {
                    const progress = Math.min(30 + (status.currentStep * 10), 90);
                    updateProgressWithMessage(progress, status.currentMessage || 'Processing...');
                }

                if (status.status === 'COMPLETED') {
                    updateProgress(100);
                    setTimeout(() => {
                        hideProgressModal();
                        showToast('Deployment completed successfully!', 'success');
                        setTimeout(() => location.reload(), 1000);
                    }, 500);
                } else if (status.status === 'FAILED') {
                    hideProgressModal();
                    showToast('Deployment failed: ' + (status.errorMessage || 'Unknown error'), 'error');
                } else {
                    attempts++;
                    if (attempts < maxAttempts) {
                        setTimeout(poll, 2000);
                    } else {
                        hideProgressModal();
                        showToast('Deployment status check timed out', 'warning');
                    }
                }
            }
        } catch (error) {
            console.error('Error polling deployment status:', error);
        }
    };

    poll();
}

// Show toast notification
function showToast(message, type) {
    type = type || 'info';

    const container = document.getElementById('toastContainer');
    if (!container) return;

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

    toast.className = bgColors[type] + ' text-white px-6 py-4 rounded-lg shadow-lg flex items-center space-x-3 min-w-80 transform transition-all duration-300 translate-x-0';
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

// Format deployment timestamps
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.deployment-time').forEach(element => {
        const timestamp = element.getAttribute('data-timestamp');
        if (timestamp) {
            const date = new Date(timestamp);
            const options = {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                timeZoneName: 'short'
            };
            element.textContent = date.toLocaleString(undefined, options);
        }
    });
});
