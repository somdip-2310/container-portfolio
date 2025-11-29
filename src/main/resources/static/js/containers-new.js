// Container Management JavaScript
// All functions for container page interactions

// Get CSRF token for API calls
const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

// Helper function to extract user-friendly error message from response
async function getErrorMessage(response) {
    // Map common HTTP status codes to user-friendly messages
    const statusMessages = {
        400: 'Invalid request. Please check your input.',
        401: 'Session expired. Please log in again.',
        402: 'Usage limit reached. Please upgrade your plan.',
        403: 'You do not have permission to perform this action.',
        404: 'Resource not found.',
        409: 'Container limit reached or resource conflict.',
        429: 'Too many requests. Please wait and try again.',
        500: 'Server error. Please try again later.',
        502: 'Service temporarily unavailable. Please try again.',
        503: 'Service temporarily unavailable. Please try again.',
        504: 'Request timed out. Please try again.'
    };

    // Check for specific status messages first
    if (statusMessages[response.status]) {
        // Try to get more specific error from response body
        try {
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                const data = await response.json();
                if (data.error || data.message) {
                    return data.error || data.message;
                }
            }
        } catch (e) {
            // Ignore parsing errors, use status message
        }
        return statusMessages[response.status];
    }

    // Try to parse response body
    try {
        const contentType = response.headers.get('content-type') || '';
        const text = await response.text();

        // Check if it's HTML (ALB error page)
        if (text.includes('<!DOCTYPE') || text.includes('<html') || text.includes('<HTML')) {
            return 'Server error occurred. Please try again.';
        }

        // Try to parse as JSON
        if (contentType.includes('application/json') || text.startsWith('{')) {
            try {
                const data = JSON.parse(text);
                return data.error || data.message || 'Unknown error';
            } catch (e) {
                // Not valid JSON
            }
        }

        // Return text if it's short and doesn't look like HTML
        if (text.length < 200 && !text.includes('<')) {
            return text;
        }

        return 'An error occurred. Please try again.';
    } catch (e) {
        return 'An error occurred. Please try again.';
    }
}

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

        // Handle successful response (200 OK or 202 Accepted)
        if (response.ok) {
            updateProgressWithMessage(50, 'Container start initiated successfully...');
            updateProgressWithMessage(70, 'Container is starting in the background...');
            setTimeout(() => {
                updateProgress(100);
                setTimeout(() => {
                    hideProgressModal();
                    showToast('Container is starting. Page will refresh to show status...', 'success');
                    setTimeout(() => location.reload(), 2000);
                }, 500);
            }, 1000);
        } else if (response.status === 402) {
            // Payment Required - FREE tier hours exhausted
            hideProgressModal();
            showToast('⏰ Failed to start container: Your FREE tier hours have been exhausted. Upgrade to PRO for unlimited hours!', 'error');
        } else if (response.status === 504 || response.status === 502 || response.status === 503) {
            // Gateway timeout or service unavailable - container might still be starting
            hideProgressModal();
            showToast('Start request sent. Refreshing page to check status...', 'info');
            // Wait a bit longer before refreshing to give the backend time to process
            setTimeout(() => location.reload(), 3000);
        } else {
            hideProgressModal();
            const error = await getErrorMessage(response);
            showToast('Failed to start container: ' + error, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error starting container:', error);
        // Might be a network timeout - container could still be starting
        if (error.name === 'TypeError' || error.message.includes('fetch')) {
            showToast('Request timed out. Refreshing page to check if container is starting...', 'warning');
            setTimeout(() => location.reload(), 3000);
        } else {
            showToast('Error starting container: ' + error.message, 'error');
        }
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
            const error = await getErrorMessage(response);
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
            const error = await getErrorMessage(response);
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
        // Switch to GitHub tab by default and check connection
        switchDeployTab('github');
    }
}

// Hide deploy modal
function hideDeployModal() {
    const modal = document.getElementById('deployModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('flex');
        // Reset to github tab (default)
        switchDeployTab('github');
    }
}

// Switch between deployment tabs (Docker Image, Source Code, GitHub)
function switchDeployTab(tabName) {
    const dockerImageTab = document.getElementById('dockerImageTab');
    const sourceCodeTab = document.getElementById('sourceCodeTab');
    const githubTab = document.getElementById('githubTab');
    const dockerImageForm = document.getElementById('deployForm');
    const sourceCodeForm = document.getElementById('sourceCodeForm');
    const githubForm = document.getElementById('githubForm');

    // Reset all tabs
    const allTabs = [dockerImageTab, sourceCodeTab, githubTab];
    const allForms = [dockerImageForm, sourceCodeForm, githubForm];

    allTabs.forEach(tab => {
        if (tab) {
            tab.classList.remove('text-purple-600', 'border-purple-600');
            tab.classList.add('text-gray-500', 'dark:text-gray-400', 'border-transparent');
        }
    });
    allForms.forEach(form => {
        if (form) form.classList.add('hidden');
    });

    if (tabName === 'dockerImage') {
        dockerImageTab.classList.add('text-purple-600', 'border-purple-600');
        dockerImageTab.classList.remove('text-gray-500', 'dark:text-gray-400', 'border-transparent');
        dockerImageForm.classList.remove('hidden');
    } else if (tabName === 'sourceCode') {
        sourceCodeTab.classList.add('text-purple-600', 'border-purple-600');
        sourceCodeTab.classList.remove('text-gray-500', 'dark:text-gray-400', 'border-transparent');
        sourceCodeForm.classList.remove('hidden');
    } else if (tabName === 'github') {
        githubTab.classList.add('text-purple-600', 'border-purple-600');
        githubTab.classList.remove('text-gray-500', 'dark:text-gray-400', 'border-transparent');
        githubForm.classList.remove('hidden');
        // Check GitHub connection status when tab is selected
        checkGitHubConnectionForDeploy();
    }
}

// Update filename display when file is selected
function updateFileName(input) {
    const fileName = input.files[0]?.name || '';
    const fileNameDisplay = document.getElementById('selectedFileName');
    if (fileNameDisplay) {
        if (fileName) {
            fileNameDisplay.textContent = 'Selected: ' + fileName;
            fileNameDisplay.classList.remove('text-gray-500');
            fileNameDisplay.classList.add('text-purple-600', 'font-medium');
        } else {
            fileNameDisplay.textContent = '';
        }
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
        } else if (response.status === 402) {
            // Payment Required - FREE tier hours exhausted
            hideProgressModal();
            showToast('⏰ Deployment failed: Your FREE tier hours have been exhausted. Upgrade to PRO for unlimited hours!', 'error');
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
    const maxAttempts = 180; // 180 * 2 = 360 seconds (6 minutes)
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
                } else if (status.status === 'IN_PROGRESS' || status.status === 'PENDING') {
                    // Still in progress
                    attempts++;
                    if (attempts < maxAttempts) {
                        // Show time elapsed every 30 seconds
                        if (attempts % 15 === 0) {
                            const elapsed = Math.floor(attempts * 2 / 60);
                            updateProgressWithMessage(Math.min(30 + attempts, 85),
                                'Deployment in progress... (' + elapsed + ' min elapsed)');
                        }
                        setTimeout(poll, 2000);
                    } else {
                        hideProgressModal();
                        showToast('Deployment is taking longer than expected. Please check the Deployments page for status.', 'warning');
                    }
                } else {
                    attempts++;
                    if (attempts < maxAttempts) {
                        setTimeout(poll, 2000);
                    } else {
                        hideProgressModal();
                        showToast('Deployment status check timed out. Please check the Deployments page for status.', 'warning');
                    }
                }
            }
        } catch (error) {
            console.error('Error polling deployment status:', error);
            attempts++;
            if (attempts < maxAttempts) {
                setTimeout(poll, 2000);
            }
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

// ==========================================
// GitHub Deployment Functions
// ==========================================

// Store state for GitHub deployment
let githubDeployState = {
    connected: false,
    selectedRepo: null,
    repos: [],
    branches: []
};

// Check GitHub connection status for deploy modal
async function checkGitHubConnectionForDeploy() {
    try {
        const response = await fetch('/auth/github/status');
        const data = await response.json();

        if (data.connected) {
            githubDeployState.connected = true;
            document.getElementById('githubNotConnectedDeploy').classList.add('hidden');
            document.getElementById('githubConnectedDeploy').classList.remove('hidden');

            // Update user info
            document.getElementById('deployGithubUsername').textContent = data.githubUsername || 'Unknown';
            if (data.avatarUrl) {
                document.getElementById('deployGithubAvatar').src = data.avatarUrl;
            }

            // Load initial repositories
            loadGitHubRepos();
        } else {
            githubDeployState.connected = false;
            document.getElementById('githubNotConnectedDeploy').classList.remove('hidden');
            document.getElementById('githubConnectedDeploy').classList.add('hidden');
        }
    } catch (error) {
        console.error('Error checking GitHub connection:', error);
        document.getElementById('githubNotConnectedDeploy').classList.remove('hidden');
        document.getElementById('githubConnectedDeploy').classList.add('hidden');
    }
}

// Load GitHub repositories into dropdown
async function loadGitHubRepos() {
    const repoSelect = document.getElementById('repoSelect');
    repoSelect.innerHTML = '<option value="">Loading repositories...</option>';
    repoSelect.disabled = true;

    try {
        const response = await fetch('/api/github/repos?perPage=100', {
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken
            }
        });
        if (response.ok) {
            githubDeployState.repos = await response.json();
            populateRepoDropdown();
        } else {
            repoSelect.innerHTML = '<option value="">Error loading repositories</option>';
            console.error('Failed to load repos:', response.status);
        }
    } catch (error) {
        console.error('Error loading repos:', error);
        repoSelect.innerHTML = '<option value="">Error loading repositories</option>';
    } finally {
        repoSelect.disabled = false;
    }
}

// Populate repository dropdown
function populateRepoDropdown() {
    const repoSelect = document.getElementById('repoSelect');
    const repos = githubDeployState.repos;

    if (!repos || repos.length === 0) {
        repoSelect.innerHTML = '<option value="">No repositories found</option>';
        return;
    }

    repoSelect.innerHTML = '<option value="">-- Select a repository --</option>' +
        repos.map(repo =>
            `<option value="${repo.fullName}" data-description="${(repo.description || '').replace(/"/g, '&quot;')}" data-private="${repo.private}" data-default-branch="${repo.defaultBranch}">${repo.fullName}${repo.private ? ' (Private)' : ''}</option>`
        ).join('');
}

// Handle repository dropdown selection
async function onRepoSelectChange() {
    const repoSelect = document.getElementById('repoSelect');
    const selectedOption = repoSelect.options[repoSelect.selectedIndex];
    const fullName = repoSelect.value;

    if (!fullName) {
        clearSelectedRepo();
        return;
    }

    const description = selectedOption.getAttribute('data-description') || '';
    const isPrivate = selectedOption.getAttribute('data-private') === 'true';
    const defaultBranch = selectedOption.getAttribute('data-default-branch') || 'main';

    githubDeployState.selectedRepo = {
        fullName: fullName,
        description: description,
        isPrivate: isPrivate,
        defaultBranch: defaultBranch
    };

    // Show selected repo info
    document.getElementById('selectedRepoName').textContent = fullName;
    document.getElementById('selectedRepoDesc').textContent = description || 'No description';
    document.getElementById('selectedRepo').classList.remove('hidden');

    // Show branch selection and other options
    document.getElementById('branchSelectContainer').classList.remove('hidden');
    document.getElementById('advancedOptionsContainer').classList.remove('hidden');
    document.getElementById('autoDeployContainer').classList.remove('hidden');

    // Load branches
    await loadBranches(fullName, defaultBranch);

    // Update deploy button state
    updateDeployButtonState();
}

// Clear selected repository
function clearSelectedRepo() {
    githubDeployState.selectedRepo = null;
    githubDeployState.branches = [];

    // Reset dropdown selection
    const repoSelect = document.getElementById('repoSelect');
    if (repoSelect) repoSelect.value = '';

    document.getElementById('selectedRepo').classList.add('hidden');
    document.getElementById('branchSelectContainer').classList.add('hidden');
    document.getElementById('advancedOptionsContainer').classList.add('hidden');
    document.getElementById('autoDeployContainer').classList.add('hidden');
    document.getElementById('advancedOptions').classList.add('hidden');

    // Reset form fields
    document.getElementById('rootDirectory').value = '';
    document.getElementById('dockerfilePath').value = '';
    document.getElementById('autoDeploy').checked = true;

    updateDeployButtonState();
}

// Load branches for selected repository
async function loadBranches(fullName, defaultBranch) {
    const branchSelect = document.getElementById('deployBranch');
    branchSelect.innerHTML = '<option value="">Loading branches...</option>';

    try {
        const [owner, repo] = fullName.split('/');
        const response = await fetch(`/api/github/repos/${owner}/${repo}/branches`, {
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken
            }
        });

        if (response.ok) {
            const branches = await response.json();
            githubDeployState.branches = branches;

            branchSelect.innerHTML = branches.map(branch =>
                `<option value="${branch.name}" ${branch.name === defaultBranch ? 'selected' : ''}>${branch.name}</option>`
            ).join('');
        } else {
            branchSelect.innerHTML = `<option value="${defaultBranch}" selected>${defaultBranch}</option>`;
        }
    } catch (error) {
        console.error('Error loading branches:', error);
        branchSelect.innerHTML = `<option value="${defaultBranch}" selected>${defaultBranch}</option>`;
    }
}

// Toggle advanced options
function toggleAdvancedOptions() {
    const options = document.getElementById('advancedOptions');
    const icon = document.getElementById('advancedOptionsIcon');

    if (options.classList.contains('hidden')) {
        options.classList.remove('hidden');
        icon.classList.add('rotate-90');
    } else {
        options.classList.add('hidden');
        icon.classList.remove('rotate-90');
    }
}

// Update deploy button state
function updateDeployButtonState() {
    const deployBtn = document.getElementById('deployFromGithubBtn');
    const containerName = document.getElementById('githubContainerName').value.trim();
    const repoSelect = document.getElementById('repoSelect');
    const hasRepo = repoSelect && repoSelect.value && githubDeployState.selectedRepo !== null;
    const hasBranch = document.getElementById('deployBranch')?.value;

    const isValid = containerName.length >= 2 && hasRepo && hasBranch;
    deployBtn.disabled = !isValid;
}

// Add input listener for container name
document.addEventListener('DOMContentLoaded', function() {
    const githubContainerName = document.getElementById('githubContainerName');
    if (githubContainerName) {
        githubContainerName.addEventListener('input', updateDeployButtonState);
    }

    const deployBranch = document.getElementById('deployBranch');
    if (deployBranch) {
        deployBranch.addEventListener('change', updateDeployButtonState);
    }
});

// State for environment variables
let envVarsState = {
    detectedEnvVars: [],
    userEnvVars: {}
};

// Deploy from GitHub - now checks for env vars first
async function deployFromGitHub() {
    const containerName = document.getElementById('githubContainerName').value.trim();
    const deployBranch = document.getElementById('deployBranch').value;
    const rootDirectory = document.getElementById('rootDirectory').value.trim() || '/';
    const dockerfilePath = document.getElementById('dockerfilePath').value.trim() || 'Dockerfile';
    const autoDeploy = document.getElementById('autoDeploy').checked;

    if (!githubDeployState.selectedRepo || !containerName || !deployBranch) {
        showToast('Please fill in all required fields', 'error');
        return;
    }

    const deployBtn = document.getElementById('deployFromGithubBtn');
    deployBtn.disabled = true;
    deployBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Checking environment variables...';

    try {
        // Step 0: Detect environment variables from the repository
        const [owner, repo] = githubDeployState.selectedRepo.fullName.split('/');
        const envVarsResponse = await fetch('/api/github/repos/' + owner + '/' + repo + '/detect-env-vars?branch=' + deployBranch + '&rootDir=' + encodeURIComponent(rootDirectory) + '&dockerfilePath=' + encodeURIComponent(dockerfilePath), {
            method: 'GET',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken
            }
        });

        if (envVarsResponse.ok) {
            const envVarsData = await envVarsResponse.json();
            envVarsState.detectedEnvVars = envVarsData.suggestions || [];

            // If there are required env vars or secrets, show the modal
            if (envVarsData.hasRequired || envVarsData.hasSecrets || envVarsState.detectedEnvVars.length > 0) {
                deployBtn.disabled = false;
                deployBtn.innerHTML = '<i class="fab fa-github mr-2"></i>Deploy from GitHub';
                showEnvVarsModal(containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy);
                return;
            }
        }

        // No env vars detected or detection failed - proceed with deployment
        deployBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Deploying...';
        await executeDeployment(containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy, {});

    } catch (error) {
        console.error('Error in deployment flow:', error);
        showToast('Deployment failed: ' + error.message, 'error');
        deployBtn.disabled = false;
        deployBtn.innerHTML = '<i class="fab fa-github mr-2"></i>Deploy from GitHub';
    }
}

// Show environment variables modal
function showEnvVarsModal(containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy) {
    // Store deployment params for later use
    envVarsState.deploymentParams = { containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy };

    // Create modal HTML
    const modalHtml = '<div id="envVarsModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">' +
        '<div class="bg-white dark:bg-gray-800 rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[90vh] overflow-hidden">' +
        '<div class="p-6 border-b border-gray-200 dark:border-gray-700">' +
        '<h3 class="text-lg font-semibold text-gray-900 dark:text-white"><i class="fas fa-key mr-2 text-yellow-500"></i>Environment Variables Detected</h3>' +
        '<p class="text-sm text-gray-500 dark:text-gray-400 mt-1">We detected these environment variables in your repository. Please provide values for the required ones.</p>' +
        '</div>' +
        '<div class="p-6 overflow-y-auto max-h-[50vh]">' +
        '<div id="envVarsList"></div>' +
        '</div>' +
        '<div class="p-6 border-t border-gray-200 dark:border-gray-700 flex justify-end space-x-3">' +
        '<button onclick="closeEnvVarsModal()" class="px-4 py-2 text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600">Cancel</button>' +
        '<button onclick="proceedWithEnvVars()" class="px-4 py-2 text-white bg-blue-600 rounded-lg hover:bg-blue-700"><i class="fas fa-rocket mr-2"></i>Deploy</button>' +
        '</div>' +
        '</div>' +
        '</div>';

    // Add modal to page
    document.body.insertAdjacentHTML('beforeend', modalHtml);

    // Populate env vars list
    const listContainer = document.getElementById('envVarsList');
    let html = '';

    // Sort: required first, then by name
    const sortedVars = [...envVarsState.detectedEnvVars].sort((a, b) => {
        if (a.required !== b.required) return b.required - a.required;
        if (a.isSecret !== b.isSecret) return b.isSecret - a.isSecret;
        return a.name.localeCompare(b.name);
    });

    sortedVars.forEach((envVar, index) => {
        const isSecret = envVar.isSecret;
        const isRequired = envVar.required;
        const inputType = isSecret ? 'password' : 'text';
        const requiredBadge = isRequired ? '<span class="ml-2 px-2 py-0.5 text-xs bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300 rounded">Required</span>' : '';
        const secretBadge = isSecret ? '<span class="ml-2 px-2 py-0.5 text-xs bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300 rounded"><i class="fas fa-lock mr-1"></i>Secret</span>' : '';
        const frameworkBadge = envVar.framework && envVar.framework !== 'GENERIC' ? '<span class="ml-2 px-2 py-0.5 text-xs bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300 rounded">' + envVar.framework + '</span>' : '';
        const description = envVar.description ? '<p class="text-xs text-gray-500 dark:text-gray-400 mt-1">' + envVar.description + '</p>' : '';

        html += '<div class="mb-4 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">' +
            '<label class="flex items-center text-sm font-medium text-gray-700 dark:text-gray-300">' +
            '<span class="font-mono">' + envVar.name + '</span>' + requiredBadge + secretBadge + frameworkBadge +
            '</label>' +
            description +
            '<input type="' + inputType + '" id="envVar_' + index + '" data-name="' + envVar.name + '" ' +
            'class="mt-2 w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500" ' +
            'placeholder="' + (envVar.defaultValue || 'Enter value') + '" ' +
            'value="' + (envVar.defaultValue && !isSecret ? envVar.defaultValue : '') + '"' +
            (isRequired ? ' required' : '') + '>' +
            '</div>';
    });

    if (sortedVars.length === 0) {
        html = '<p class="text-gray-500 dark:text-gray-400 text-center py-4">No environment variables detected.</p>';
    }

    listContainer.innerHTML = html;
}

// Close env vars modal
function closeEnvVarsModal() {
    const modal = document.getElementById('envVarsModal');
    if (modal) {
        modal.remove();
    }
    envVarsState.detectedEnvVars = [];
    envVarsState.userEnvVars = {};
}

// Proceed with deployment after env vars
async function proceedWithEnvVars() {
    // Collect env var values from inputs
    const envVars = {};
    let hasAllRequired = true;
    const missingVars = [];

    envVarsState.detectedEnvVars.forEach((envVar, index) => {
        const input = document.getElementById('envVar_' + index);
        if (input) {
            const value = input.value.trim();
            if (value) {
                envVars[envVar.name] = value;
            } else if (envVar.required) {
                hasAllRequired = false;
                missingVars.push(envVar.name);
            }
        }
    });

    if (!hasAllRequired) {
        showToast('Please fill in all required environment variables: ' + missingVars.join(', '), 'error');
        return;
    }

    // Close modal and proceed with deployment
    closeEnvVarsModal();

    const { containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy } = envVarsState.deploymentParams;

    const deployBtn = document.getElementById('deployFromGithubBtn');
    deployBtn.disabled = true;
    deployBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Deploying...';

    try {
        await executeDeployment(containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy, envVars);
    } catch (error) {
        console.error('Error in deployment:', error);
        showToast('Deployment failed: ' + error.message, 'error');
        deployBtn.disabled = false;
        deployBtn.innerHTML = '<i class="fab fa-github mr-2"></i>Deploy from GitHub';
    }
}

// Execute the actual deployment
async function executeDeployment(containerName, deployBranch, rootDirectory, dockerfilePath, autoDeploy, envVars) {
    const deployBtn = document.getElementById('deployFromGithubBtn');

    try {
        showProgressModal('Deploying from GitHub', 'Creating container...');
        updateProgress(10);

        // Step 1: Create the container first
        const containerResponse = await fetch('/web/api/containers', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: containerName,
                image: 'placeholder:latest', // Will be updated after build
                port: 8080,
                environmentVariables: envVars // Include detected environment variables
            })
        });

        if (!containerResponse.ok) {
            let errorMessage = 'Container creation failed';
            const responseText = await containerResponse.text();
            try {
                const errorData = JSON.parse(responseText);
                errorMessage = errorData.error || errorData.message || errorMessage;
            } catch (e) {
                // Not JSON - only use text if it's not HTML
                if (responseText && !responseText.startsWith('<')) {
                    errorMessage = responseText;
                }
            }
            throw new Error(errorMessage);
        }

        const container = await containerResponse.json();
        updateProgressWithMessage(30, 'Linking GitHub repository...');

        // Step 2: Link the repository to the container
        const linkResponse = await fetch('/api/github/link', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                containerId: container.containerId,
                repoFullName: githubDeployState.selectedRepo.fullName,
                deployBranch: deployBranch,
                rootDirectory: rootDirectory,
                dockerfilePath: dockerfilePath,
                autoDeploy: autoDeploy
            })
        });

        if (!linkResponse.ok) {
            const error = await linkResponse.json();
            throw new Error(error.error || 'Failed to link repository');
        }

        const linkResult = await linkResponse.json();
        updateProgressWithMessage(60, 'Build triggered! Opening deployment logs...');

        // Success - hide progress modal and deploy modal
        updateProgress(100);
        setTimeout(() => {
            hideProgressModal();
            hideDeployModal();

            // Reset GitHub form state
            clearSelectedRepo();
            document.getElementById('githubContainerName').value = '';

            // Check if deployment was triggered and show logs modal
            if (linkResult.deploymentId && linkResult.buildTriggered) {
                const commitInfo = githubDeployState.selectedRepo ?
                    githubDeployState.selectedRepo.fullName + ' @ ' + deployBranch : '';
                showDeploymentLogsModal(linkResult.deploymentId, containerName, commitInfo);
            } else {
                // Fallback: just show toast and refresh
                showToast('GitHub repository linked successfully! Build has been triggered.', 'success');
                setTimeout(() => location.reload(), 2000);
            }
        }, 500);

    } catch (error) {
        hideProgressModal();
        console.error('Error deploying from GitHub:', error);
        showToast('Deployment failed: ' + error.message, 'error');
    } finally {
        deployBtn.disabled = false;
        deployBtn.innerHTML = '<i class="fab fa-github mr-2"></i>Deploy from GitHub';
    }
}

// Validate container name (shared function)
function validateContainerName(input) {
    const value = input.value;
    const errorEl = document.getElementById(input.id + 'Error');

    // Convert to lowercase
    input.value = value.toLowerCase();

    // Check pattern
    const pattern = /^[a-z0-9][a-z0-9-]*[a-z0-9]$/;
    const isValid = value.length >= 2 && pattern.test(value);

    if (errorEl) {
        if (!isValid && value.length > 0) {
            errorEl.textContent = 'Must start and end with lowercase letter or number, can contain hyphens';
            errorEl.classList.remove('hidden');
        } else {
            errorEl.classList.add('hidden');
        }
    }

    // Update deploy button state if on GitHub tab
    if (input.id === 'githubContainerName') {
        updateDeployButtonState();
    }

    return isValid;
}

// Set quick image (for Docker Image tab)
function setQuickImage(image, tag, port) {
    document.getElementById('containerImage').value = image;
    document.getElementById('imageTag').value = tag;
    document.getElementById('containerPort').value = port;
}

// Deploy new container (Docker Image tab)
async function deployNewContainer(event) {
    event.preventDefault();

    const containerName = document.getElementById('containerName').value.trim();
    const image = document.getElementById('containerImage').value.trim();
    const tag = document.getElementById('imageTag').value.trim() || 'latest';
    const port = parseInt(document.getElementById('containerPort').value) || 8080;

    if (!containerName || !image) {
        showToast('Please fill in all required fields', 'error');
        return;
    }

    const fullImage = image.includes(':') ? image : `${image}:${tag}`;

    try {
        showProgressModal('Deploying Container', 'Creating container...');
        updateProgress(10);

        const response = await fetch('/web/api/containers', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                [csrfHeader]: csrfToken,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                name: containerName,
                image: fullImage,
                port: port
            })
        });

        if (response.ok) {
            updateProgressWithMessage(50, 'Container created, starting...');
            setTimeout(() => {
                updateProgress(100);
                setTimeout(() => {
                    hideProgressModal();
                    hideDeployModal();
                    showToast('Container deployed successfully!', 'success');
                    setTimeout(() => location.reload(), 1500);
                }, 500);
            }, 1000);
        } else if (response.status === 402) {
            hideProgressModal();
            showToast('Deployment failed: Your FREE tier hours have been exhausted. Upgrade to PRO for unlimited hours!', 'error');
        } else {
            hideProgressModal();
            const error = await getErrorMessage(response);
            showToast('Failed to deploy container: ' + error, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error deploying container:', error);
        showToast('Error deploying container: ' + error.message, 'error');
    }
}

// =====================================================
// Deployment Logs Streaming (SSE)
// =====================================================

// Deployment logs state
let deploymentLogsState = {
    eventSource: null,
    deploymentId: null,
    startTime: null,
    steps: {}
};

// Show deployment logs modal and start SSE stream
function showDeploymentLogsModal(deploymentId, containerName, commitInfo) {
    const modal = document.getElementById('deploymentLogsModal');
    if (modal) {
        modal.classList.remove('hidden');
        modal.classList.add('flex');
    }

    // Set container name
    document.getElementById('deploymentContainerName').textContent = containerName || '';

    // Set deployment ID for reference
    const deploymentIdEl = document.getElementById('deploymentIdDisplay');
    if (deploymentIdEl) {
        deploymentIdEl.textContent = deploymentId ? 'ID: ' + deploymentId : '';
    }

    // Set commit info if available
    const commitInfoEl = document.getElementById('deploymentCommitInfo');
    if (commitInfo) {
        commitInfoEl.textContent = commitInfo;
    } else {
        commitInfoEl.textContent = '';
    }

    // Reset state
    deploymentLogsState.deploymentId = deploymentId;
    deploymentLogsState.startTime = Date.now();
    deploymentLogsState.steps = {};

    // Reset UI
    document.getElementById('deploymentSteps').innerHTML = '';
    updateDeploymentStatusBadge('IN_PROGRESS');
    updateDeploymentDuration();

    // Start duration timer
    const durationInterval = setInterval(() => {
        if (!deploymentLogsState.deploymentId) {
            clearInterval(durationInterval);
            return;
        }
        updateDeploymentDuration();
    }, 1000);

    // Start SSE stream
    startDeploymentLogStream(deploymentId);
}

// Hide deployment logs modal
function hideDeploymentLogsModal() {
    const modal = document.getElementById('deploymentLogsModal');
    if (modal) {
        modal.classList.add('hidden');
        modal.classList.remove('flex');
    }

    // Close SSE connection
    if (deploymentLogsState.eventSource) {
        deploymentLogsState.eventSource.close();
        deploymentLogsState.eventSource = null;
    }

    deploymentLogsState.deploymentId = null;
}

// Close modal and refresh page
function closeDeploymentLogsAndRefresh() {
    hideDeploymentLogsModal();
    location.reload();
}

// Start SSE stream for deployment logs
function startDeploymentLogStream(deploymentId) {
    // Close existing connection if any
    if (deploymentLogsState.eventSource) {
        deploymentLogsState.eventSource.close();
    }

    const url = '/api/deployments/' + deploymentId + '/stream';
    const eventSource = new EventSource(url);
    deploymentLogsState.eventSource = eventSource;

    // Handle initial state
    eventSource.addEventListener('init', function(event) {
        const data = JSON.parse(event.data);
        console.log('Deployment init:', data);

        if (data.containerName) {
            document.getElementById('deploymentContainerName').textContent = data.containerName;
        }

        if (data.commitSha) {
            const shortSha = data.commitSha.substring(0, 7);
            const commitMsg = data.commitMessage ? ': ' + data.commitMessage.substring(0, 50) : '';
            document.getElementById('deploymentCommitInfo').textContent = shortSha + commitMsg;
        }

        updateDeploymentStatusBadge(data.status);
    });

    // Handle step updates
    eventSource.addEventListener('step', function(event) {
        const data = JSON.parse(event.data);
        console.log('Step update:', data);
        updateDeploymentStep(data.stepName, data.status, data.message);
    });

    // Handle log messages
    eventSource.addEventListener('log', function(event) {
        const data = JSON.parse(event.data);
        console.log('Log:', data.message);
        // Could add log lines to the UI if needed
    });

    // Handle status changes
    eventSource.addEventListener('status', function(event) {
        const data = JSON.parse(event.data);
        console.log('Status update:', data);
        updateDeploymentStatusBadge(data.status);

        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
            eventSource.close();
            deploymentLogsState.eventSource = null;

            // Show appropriate message
            if (data.status === 'COMPLETED') {
                showToast('Deployment completed successfully!', 'success');
                hideErrorLogs(); // Hide error logs section on success
                // Auto-close modal after showing success state for 2 seconds
                setTimeout(() => {
                    closeDeploymentLogsAndRefresh();
                }, 2000);
            } else {
                // Show error logs in the modal
                const errorMessage = data.message || 'Unknown error';
                showErrorLogs(errorMessage);
                showToast('Deployment failed. Check error details below.', 'error');
                // Keep modal open on failure so user can see what went wrong
            }
        }
    });

    // Handle errors
    eventSource.onerror = function(error) {
        console.error('SSE error:', error);
        // Don't close immediately - the server might reconnect
        if (eventSource.readyState === EventSource.CLOSED) {
            console.log('SSE connection closed');
        }
    };
}

// Update a deployment step in the UI
function updateDeploymentStep(stepName, status, message) {
    const stepsContainer = document.getElementById('deploymentSteps');

    // Check if step already exists
    let stepEl = document.getElementById('step-' + stepName);

    if (!stepEl) {
        // Create new step element
        stepEl = document.createElement('div');
        stepEl.id = 'step-' + stepName;
        stepEl.className = 'flex items-start space-x-3 p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50';
        stepsContainer.appendChild(stepEl);
    }

    // Get icon and color based on status
    const { icon, colorClass } = getStepStatusStyles(status);

    stepEl.innerHTML = '<div class="flex-shrink-0 mt-0.5">' + icon + '</div>' +
        '<div class="flex-1 min-w-0">' +
            '<p class="text-sm font-medium text-gray-900 dark:text-white">' + formatStepName(stepName) + '</p>' +
            '<p class="text-sm text-gray-500 dark:text-gray-400 truncate">' + (message || '') + '</p>' +
        '</div>' +
        '<span class="flex-shrink-0 px-2 py-1 text-xs font-medium rounded-full ' + colorClass + '">' + status + '</span>';

    // Scroll to bottom
    stepsContainer.scrollTop = stepsContainer.scrollHeight;

    // Store step state
    deploymentLogsState.steps[stepName] = { status, message };
}

// Get icon and styles for step status
function getStepStatusStyles(status) {
    switch (status) {
        case 'COMPLETED':
        case 'SUCCEEDED':
            return {
                icon: '<svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>',
                colorClass: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300'
            };
        case 'FAILED':
        case 'FAULT':
        case 'STOPPED':
            return {
                icon: '<svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>',
                colorClass: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300'
            };
        case 'IN_PROGRESS':
        default:
            return {
                icon: '<svg class="w-5 h-5 text-blue-500 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path></svg>',
                colorClass: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300'
            };
    }
}

// Format step name for display
function formatStepName(stepName) {
    const names = {
        'INITIALIZING': 'Initializing',
        'AUTHENTICATING': 'Authenticating',
        'STARTING_BUILD': 'Starting Build',
        'QUEUED': 'Build Queued',
        'PROVISIONING': 'Provisioning Build Environment',
        'CLONING': 'Cloning Repository',
        'INSTALLING': 'Installing Dependencies',
        'INSTALLING_DEPENDENCIES': 'Installing Dependencies',
        'PRE_BUILD': 'Running Pre-Build',
        'BUILDING': 'Building Docker Image',
        'POST_BUILD': 'Running Post-Build',
        'PUSHING_IMAGE': 'Pushing Image to Registry',
        'FINALIZING': 'Finalizing Build',
        'COMPLETED': 'Build Complete',
        'BUILD_COMPLETE': 'Build Complete',
        'BUILD_FAILED': 'Build Failed',
        'DEPLOYING': 'Deploying Container',
        'DEPLOY_FAILED': 'Deploy Failed',
        'ERROR': 'Error',
        'TIMEOUT': 'Timeout'
    };
    return names[stepName] || stepName;
}

// Update deployment status badge
function updateDeploymentStatusBadge(status) {
    const badge = document.getElementById('deploymentStatusBadge');
    if (!badge) return;

    switch (status) {
        case 'COMPLETED':
            badge.className = 'px-3 py-1 rounded-full text-sm font-medium bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300';
            badge.innerHTML = '<i class="fas fa-check-circle mr-2"></i>Completed';
            break;
        case 'FAILED':
            badge.className = 'px-3 py-1 rounded-full text-sm font-medium bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300';
            badge.innerHTML = '<i class="fas fa-times-circle mr-2"></i>Failed';
            break;
        case 'PENDING':
            badge.className = 'px-3 py-1 rounded-full text-sm font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300';
            badge.innerHTML = '<i class="fas fa-clock mr-2"></i>Pending';
            break;
        case 'IN_PROGRESS':
        default:
            badge.className = 'px-3 py-1 rounded-full text-sm font-medium bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300';
            badge.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>In Progress';
            break;
    }
}

// Update deployment duration display
function updateDeploymentDuration() {
    const durationEl = document.getElementById('deploymentDuration');
    if (!durationEl || !deploymentLogsState.startTime) return;

    const elapsed = Math.floor((Date.now() - deploymentLogsState.startTime) / 1000);
    const minutes = Math.floor(elapsed / 60);
    const seconds = elapsed % 60;

    durationEl.textContent = 'Duration: ' + minutes + 'm ' + seconds + 's';
}

// =====================================================
// Error Logs Display Functions
// =====================================================

// Show error logs in the deployment modal
function showErrorLogs(errorMessage) {
    const errorLogsSection = document.getElementById('deploymentErrorLogs');
    const errorLogsContent = document.getElementById('errorLogsContent');

    if (errorLogsSection && errorLogsContent) {
        // Store the error message for copy functionality
        deploymentLogsState.errorMessage = errorMessage;

        // Format the error message - handle multiline logs
        let formattedMessage = errorMessage;

        // Check if message contains container logs separator
        if (errorMessage.includes('--- Container Logs ---')) {
            const parts = errorMessage.split('--- Container Logs ---');
            const mainError = parts[0].trim();
            const containerLogs = parts[1] ? parts[1].trim() : '';

            formattedMessage = '=== Error ===\n' + mainError;
            if (containerLogs) {
                formattedMessage += '\n\n=== Container Logs ===\n' + containerLogs;
            }
        }

        errorLogsContent.textContent = formattedMessage;
        errorLogsSection.classList.remove('hidden');

        // Scroll to show error logs
        errorLogsSection.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
}

// Hide error logs section
function hideErrorLogs() {
    const errorLogsSection = document.getElementById('deploymentErrorLogs');
    if (errorLogsSection) {
        errorLogsSection.classList.add('hidden');
    }
    deploymentLogsState.errorMessage = null;
}

// Copy error logs to clipboard
function copyErrorLogs() {
    const errorMessage = deploymentLogsState.errorMessage;
    if (!errorMessage) {
        showToast('No error logs to copy', 'warning');
        return;
    }

    navigator.clipboard.writeText(errorMessage).then(() => {
        showToast('Error logs copied to clipboard', 'success');
    }).catch(err => {
        console.error('Failed to copy:', err);
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = errorMessage;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
        showToast('Error logs copied to clipboard', 'success');
    });
}
