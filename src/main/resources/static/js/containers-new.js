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
            const error = await response.text();
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

// Load GitHub repositories
async function loadGitHubRepos() {
    try {
        const response = await fetch('/api/github/repos?perPage=50');
        if (response.ok) {
            githubDeployState.repos = await response.json();
        }
    } catch (error) {
        console.error('Error loading repos:', error);
    }
}

// Search GitHub repositories (debounced)
let searchTimeout = null;
function searchGitHubRepos(query) {
    clearTimeout(searchTimeout);

    const repoList = document.getElementById('repoList');

    if (!query || query.length < 1) {
        repoList.classList.add('hidden');
        return;
    }

    searchTimeout = setTimeout(async () => {
        try {
            let repos = [];

            // Search from cached repos first
            if (githubDeployState.repos.length > 0) {
                const lowerQuery = query.toLowerCase();
                repos = githubDeployState.repos.filter(repo =>
                    repo.fullName.toLowerCase().includes(lowerQuery) ||
                    (repo.description && repo.description.toLowerCase().includes(lowerQuery))
                );
            }

            // If no cached results or query is longer, search API
            if (repos.length === 0 && query.length >= 2) {
                const response = await fetch('/api/github/repos/search?q=' + encodeURIComponent(query));
                if (response.ok) {
                    repos = await response.json();
                }
            }

            displayRepoList(repos);
        } catch (error) {
            console.error('Error searching repos:', error);
        }
    }, 300);
}

// Display repository list
function displayRepoList(repos) {
    const repoList = document.getElementById('repoList');

    if (repos.length === 0) {
        repoList.innerHTML = '<div class="p-4 text-center text-gray-500">No repositories found</div>';
        repoList.classList.remove('hidden');
        return;
    }

    repoList.innerHTML = repos.map(repo => `
        <div class="p-3 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer border-b border-gray-100 dark:border-gray-700 last:border-b-0"
             onclick="selectRepository('${repo.fullName}', '${(repo.description || '').replace(/'/g, "\\'")}', '${repo.private}', '${repo.defaultBranch}')">
            <div class="flex items-center justify-between">
                <div class="flex items-center space-x-2">
                    <i class="fab fa-github text-gray-400"></i>
                    <span class="font-medium text-gray-900 dark:text-white">${repo.fullName}</span>
                    ${repo.private ? '<span class="px-1.5 py-0.5 text-xs bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200 rounded">Private</span>' : ''}
                </div>
            </div>
            ${repo.description ? `<p class="text-xs text-gray-500 dark:text-gray-400 mt-1 truncate">${repo.description}</p>` : ''}
        </div>
    `).join('');

    repoList.classList.remove('hidden');
}

// Select a repository
async function selectRepository(fullName, description, isPrivate, defaultBranch) {
    githubDeployState.selectedRepo = {
        fullName: fullName,
        description: description,
        isPrivate: isPrivate === 'true',
        defaultBranch: defaultBranch
    };

    // Hide repo list and search
    document.getElementById('repoList').classList.add('hidden');
    document.getElementById('repoSearch').value = '';

    // Show selected repo
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
        const response = await fetch(`/api/github/repos/${owner}/${repo}/branches`);

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
    const hasRepo = githubDeployState.selectedRepo !== null;
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

// Deploy from GitHub
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
    deployBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Deploying...';

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
                port: 8080
            })
        });

        if (!containerResponse.ok) {
            const error = await containerResponse.text();
            throw new Error('Failed to create container: ' + error);
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

        updateProgressWithMessage(60, 'Build triggered! Waiting for deployment...');

        // Success - close modal and show message
        updateProgress(100);
        setTimeout(() => {
            hideProgressModal();
            hideDeployModal();
            showToast('GitHub repository linked successfully! Build has been triggered.', 'success');

            // Reset GitHub form state
            clearSelectedRepo();
            document.getElementById('githubContainerName').value = '';

            // Refresh page after short delay
            setTimeout(() => location.reload(), 2000);
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
            const error = await response.text();
            showToast('Failed to deploy container: ' + error, 'error');
        }
    } catch (error) {
        hideProgressModal();
        console.error('Error deploying container:', error);
        showToast('Error deploying container: ' + error.message, 'error');
    }
}
