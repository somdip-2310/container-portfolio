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
        const gridView = document.getElementById('gridView');
        const listView = document.getElementById('listView');
        const containerGrid = document.getElementById('containerGrid');

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
            // Implement sorting logic if needed
            console.log('Sort functionality - to be implemented');
        }

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
                    const error = await getErrorMessage(response);
                    showToast('Failed to start container: ' + error, 'error');
                }
            } catch (error) {
                hideProgressModal();
                console.error('Error starting container:', error);
                showToast('Error starting container', 'error');
            }
        }

        // Confirm stop container
        function confirmStopContainer(containerId) {
            if (confirm('Are you sure you want to stop this container? The container will be shut down gracefully.')) {
                stopContainer(containerId);
            }
        }

        // Confirm delete container
        function confirmDeleteContainer(containerId) {
            if (confirm('Are you sure you want to delete this container? This action cannot be undone.')) {
                deleteContainer(containerId);
            }
        }
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
                    showToast('Container stopped successfully', 'success');
                    setTimeout(() => location.reload(), 2000);
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

        // Show deploy modal
        function showDeployModal() {
            const modal = document.getElementById('deployModal');
            if (modal) {
                modal.classList.remove('hidden');
            }
        }

        // Hide deploy modal
        function hideDeployModal() {
            const modal = document.getElementById('deployModal');
            if (modal) {
                modal.classList.add('hidden');
                document.getElementById('deployForm').reset();
                document.getElementById('sourceCodeForm').reset();
                document.getElementById('selectedFileName').textContent = '';
                switchDeployTab('dockerImage');
            }
        }

        // Switch deployment tabs
        function switchDeployTab(tabName) {
            const dockerImageTab = document.getElementById('dockerImageTab');
            const sourceCodeTab = document.getElementById('sourceCodeTab');
            const dockerImageForm = document.getElementById('deployForm');
            const sourceCodeForm = document.getElementById('sourceCodeForm');

            if (tabName === 'dockerImage') {
                dockerImageTab.classList.add('text-purple-600', 'border-b-2', 'border-purple-600');
                dockerImageTab.classList.remove('text-gray-500', 'dark:text-gray-400');
                sourceCodeTab.classList.remove('text-purple-600', 'border-b-2', 'border-purple-600');
                sourceCodeTab.classList.add('text-gray-500', 'dark:text-gray-400');

                dockerImageForm.classList.remove('hidden');
                sourceCodeForm.classList.add('hidden');
            } else {
                sourceCodeTab.classList.add('text-purple-600', 'border-b-2', 'border-purple-600');
                sourceCodeTab.classList.remove('text-gray-500', 'dark:text-gray-400');
                dockerImageTab.classList.remove('text-purple-600', 'border-b-2', 'border-purple-600');
                dockerImageTab.classList.add('text-gray-500', 'dark:text-gray-400');

                sourceCodeForm.classList.remove('hidden');
                dockerImageForm.classList.add('hidden');
            }
        }

        // Update file name display
        // Validate container name as user types
        function validateContainerName(input) {
            const value = input.value;
            const errorId = input.id + 'Error';
            const errorEl = document.getElementById(errorId);

            // Clear previous error
            input.setCustomValidity('');
            if (errorEl) {
                errorEl.classList.add('hidden');
                errorEl.textContent = '';
            }

            if (!value) return; // Don't show error for empty field (required will handle this)

            let errorMessage = '';

            // Check length
            if (value.length < 2) {
                errorMessage = 'Container name must be at least 2 characters long';
            } else if (value.length > 63) {
                errorMessage = 'Container name must be 63 characters or less';
            }
            // Check if starts or ends with hyphen
            else if (value.startsWith('-') || value.endsWith('-')) {
                errorMessage = 'Container name cannot start or end with a hyphen';
            }
            // Check for uppercase letters
            else if (/[A-Z]/.test(value)) {
                errorMessage = 'Container name must be lowercase only';
            }
            // Check for invalid characters
            else if (!/^[a-z0-9-]+$/.test(value)) {
                errorMessage = 'Container name can only contain lowercase letters, numbers, and hyphens';
            }
            // Check consecutive hyphens
            else if (/--/.test(value)) {
                errorMessage = 'Container name cannot contain consecutive hyphens';
            }

            if (errorMessage) {
                input.setCustomValidity(errorMessage);
                if (errorEl) {
                    errorEl.textContent = errorMessage;
                    errorEl.classList.remove('hidden');
                }
                input.classList.add('border-red-500');
                input.classList.remove('border-gray-300', 'dark:border-gray-600');
            } else {
                input.classList.remove('border-red-500');
                input.classList.add('border-gray-300', 'dark:border-gray-600');
            }
        }

        function updateFileName(input) {
            const fileName = input.files[0]?.name || '';
            document.getElementById('selectedFileName').textContent = fileName;
        }

        // Deploy from source code
        async function deployFromSource(event) {
            event.preventDefault();

            const fileInput = document.getElementById('sourceCodeFile');
            const containerName = document.getElementById('sourceContainerName').value;

            // Validate file input exists
            if (!fileInput) {
                showToast('File input not found. Please refresh the page.', 'error');
                console.error('File input element not found');
                return;
            }

            // Validate file is selected
            if (!fileInput.files || fileInput.files.length === 0) {
                showToast('Please select a ZIP file to upload', 'error');
                console.error('No file selected');
                return;
            }

            const file = fileInput.files[0];

            // Validate file object
            if (!file) {
                showToast('File object is invalid. Please try selecting the file again.', 'error');
                console.error('File object is null or undefined');
                return;
            }

            // Validate file is a ZIP
            if (!file.name.toLowerCase().endsWith('.zip')) {
                showToast('Please select a ZIP file', 'error');
                return;
            }

            console.log('File to upload:', file);
            console.log('File name:', file.name);
            console.log('File size:', file.size);
            console.log('File type:', file.type);

            const submitBtn = document.getElementById('sourceCodeSubmitBtn');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i>Uploading...';
            }

            // Hide deploy modal and show progress modal
            hideDeployModal();
            showProgressModal('Uploading Source Code', 'Preparing your source code for deployment...');
            updateProgress(5);

            try {
                const formData = new FormData();
                formData.append('file', file);
                formData.append('containerName', containerName);

                // Debug: Log formData contents
                for (let pair of formData.entries()) {
                    console.log(pair[0] + ':', pair[1]);
                }

                updateProgressWithMessage(10, 'Uploading source code...');

                const response = await fetch('/api/source/deploy', {
                    method: 'POST',
                    credentials: 'same-origin',
                    body: formData
                });

                if (response.ok) {
                    const result = await response.json();
                    updateProgressWithMessage(30, 'Project analyzed: ' + result.projectTypeDisplay);

                    // Start polling for deployment status
                    if (result.deploymentId) {
                        await pollDeploymentStatus(result.deploymentId);
                    } else {
                        // If no deployment tracking, show success after delay
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
            const maxAttempts = 60; // 5 minutes max
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

                        // Funny messages for different stages
                        const funnyMessages = {
                            'ANALYZING': [
                                'Reading tea leaves and source code...',
                                'Consulting the documentation gods...',
                                'Checking if you forgot semicolons...',
                                'Deciphering ancient runes (a.k.a. your code)...'
                            ],
                            'BUILDING': [
                                'Teaching containers to containerize...',
                                'Downloading the internet (just kidding)...',
                                'Compiling 1s and 0s into something useful...',
                                'Making Docker do the heavy lifting...',
                                'Convincing servers this is a good idea...'
                            ],
                            'PUSHING': [
                                'Uploading to the cloud (literally)...',
                                'Shoving bits through tubes...',
                                'Negotiating with the registry...',
                                'Making sure no one steals your image...'
                            ],
                            'DEPLOYING': [
                                'Waking up the servers from their nap...',
                                'Deploying with style and grace...',
                                'Teaching ECS tasks to task properly...',
                                'Almost there! Putting on finishing touches...',
                                'Telling AWS to get to work...',
                                'Spinning up your container like a DJ...',
                                'Making the load balancer feel important...',
                                'Convincing the health checks to pass...',
                                'Registering targets (they agreed to it)...',
                                'Waiting for AWS to finish its coffee break...',
                                'Configuring networking (it\'s complicated)...',
                                'Setting up auto-scaling (just in case)...',
                                'Deploying containers like a boss...',
                                'Making sure everything talks to each other...',
                                'Testing if the deployment gods are smiling...'
                            ]
                        };

                        // Update progress based on status
                        if (status.status === 'ANALYZING') {
                            const msg = funnyMessages.ANALYZING[Math.floor(Math.random() * funnyMessages.ANALYZING.length)];
                            updateProgressWithMessage(35, msg);
                        } else if (status.status === 'BUILDING') {
                            const msg = funnyMessages.BUILDING[Math.floor(Math.random() * funnyMessages.BUILDING.length)];
                            updateProgressWithMessage(50, msg);
                        } else if (status.status === 'PUSHING') {
                            const msg = funnyMessages.PUSHING[Math.floor(Math.random() * funnyMessages.PUSHING.length)];
                            updateProgressWithMessage(70, msg);
                        } else if (status.status === 'DEPLOYING') {
                            const msg = funnyMessages.DEPLOYING[Math.floor(Math.random() * funnyMessages.DEPLOYING.length)];
                            updateProgressWithMessage(85, msg);
                        } else if (status.status === 'COMPLETED') {
                            updateProgress(100);
                            setTimeout(() => {
                                hideProgressModal();
                                showToast('Deployment completed successfully! Your container is now running.', 'success');
                                setTimeout(() => location.reload(), 3000);
                            }, 1000);
                            return;
                        } else if (status.status === 'FAILED') {
                            hideProgressModal();
                            showToast('Deployment failed: ' + (status.error || 'Unknown error'), 'error');
                            return;
                        }

                        // Continue polling if not finished
                        attempts++;
                        if (attempts < maxAttempts) {
                            setTimeout(poll, 5000); // Poll every 5 seconds
                        } else {
                            hideProgressModal();
                            showToast('Deployment timeout - please check container status', 'warning');
                        }
                    } else {
                        // Status endpoint not available, fall back to simple progress
                        updateProgressWithMessage(70, 'Building and deploying...');
                        await new Promise(resolve => setTimeout(resolve, 3000));
                        updateProgress(100);
                        setTimeout(() => {
                            hideProgressModal();
                            showToast('Deployment initiated successfully!', 'success');
                            setTimeout(() => location.reload(), 1000);
                        }, 500);
                    }
                } catch (error) {
                    console.error('Error polling deployment status:', error);
                    // Continue with simple progress
                    updateProgressWithMessage(70, 'Building and deploying...');
                    await new Promise(resolve => setTimeout(resolve, 3000));
                    updateProgress(100);
                    setTimeout(() => {
                        hideProgressModal();
                        showToast('Deployment initiated successfully!', 'success');
                        setTimeout(() => location.reload(), 1000);
                    }, 500);
                }
            };

            poll();
        }

        // Set quick image preset
        function setQuickImage(image, tag, port) {
            document.getElementById('containerImage').value = image;
            document.getElementById('imageTag').value = tag;
            document.getElementById('containerPort').value = port;
        }

        // Deploy your application
        async function deployNewContainer(event) {
            event.preventDefault();

            const formData = {
                name: document.getElementById('containerName').value,
                image: document.getElementById('containerImage').value,
                imageTag: document.getElementById('imageTag').value || 'latest',
                port: parseInt(document.getElementById('containerPort').value) || 8080
            };

            try {
                const response = await fetch('/web/api/containers', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        [csrfHeader]: csrfToken,
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify(formData)
                });

                if (response.ok) {
                    showNotification('Container created successfully!', 'success');
                    hideDeployModal();
                    setTimeout(() => location.reload(), 1500);
                } else {
                    const error = await getErrorMessage(response);
                    showNotification('Failed to create container: ' + error, 'error');
                }
            } catch (error) {
                console.error('Error creating container:', error);
                showNotification('Error creating container', 'error');
            }
        }

        // Current container ID for modal actions
        let currentContainerId = null;

        // Show container menu
        function showContainerMenu(containerId) {
            console.log('showContainerMenu called with containerId:', containerId);
            currentContainerId = containerId;
            console.log('currentContainerId set to:', currentContainerId);
            const modal = document.getElementById('containerActionsModal');
            console.log('Modal element:', modal);
            if (modal) {
                modal.classList.remove('hidden');
                console.log('Modal should now be visible');
            } else {
                console.error('Modal element not found!');
            }
        }

        // Hide container actions modal
        function hideContainerActionsModal() {
            const modal = document.getElementById('containerActionsModal');
            if (modal) {
                modal.classList.add('hidden');
                currentContainerId = null;
            }
        }

        // Handle view details
        function handleViewDetails() {
            console.log('handleViewDetails called, currentContainerId:', currentContainerId);
            const containerId = currentContainerId; // Store before hiding modal
            hideContainerActionsModal();
            if (containerId) {
                window.location.href = '/containers/' + containerId;
            } else {
                showToast('Container ID not found', 'error');
            }
        }

        // Handle restart container
        async function handleRestartContainer() {
            console.log('handleRestartContainer called, currentContainerId:', currentContainerId);
            const containerId = currentContainerId; // Store before hiding modal
            hideContainerActionsModal();
            if (containerId) {
                await restartContainer(containerId);
            } else {
                showToast('Container ID not found', 'error');
            }
        }

        // Handle view metrics
        function handleViewMetrics() {
            console.log('handleViewMetrics called, currentContainerId:', currentContainerId);
            const containerId = currentContainerId; // Store before hiding modal
            hideContainerActionsModal();
            if (containerId) {
                window.location.href = '/logs?containerId=' + containerId;
            } else {
                showToast('Container ID not found', 'error');
            }
        }

        // Handle delete container
        async function handleDeleteContainer() {
            console.log('handleDeleteContainer called, currentContainerId:', currentContainerId);
            const containerId = currentContainerId; // Store before hiding modal
            hideContainerActionsModal();
            if (containerId) {
                const confirmed = confirm('Are you sure you want to delete this container? This action cannot be undone.');
                if (confirmed) {
                    await deleteContainer(containerId);
                }
            } else {
                showToast('Container ID not found', 'error');
            }
        }

        // Delete container
        async function deleteContainer(containerId) {
            try {
                // Show progress modal
                showProgressModal('Deleting Container', 'Removing container and associated resources...');
                updateProgress(10);

                const response = await fetch('/web/api/containers/' + containerId, {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    headers: {
                        [csrfHeader]: csrfToken
                    }
                });

                updateProgress(60);

                if (response.ok) {
                    updateProgress(100);
                    setTimeout(() => {
                        hideProgressModal();
                        showToast('Container deleted successfully', 'success');
                        setTimeout(() => location.reload(), 1000);
                    }, 500);
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

        // Progress modal functions
        function showProgressModal(title, message) {
            const modal = document.getElementById('progressModal');
            if (!modal) {
                console.error('Progress modal not found');
                return;
            }
            const titleEl = document.getElementById('progressTitle');
            const messageEl = document.getElementById('progressMessage');
            const barEl = document.getElementById('progressBar');
            const percentEl = document.getElementById('progressPercent');
            
            if (titleEl) titleEl.textContent = title;
            if (messageEl) messageEl.textContent = message;
            if (barEl) barEl.style.width = '0%';
            if (percentEl) percentEl.textContent = '0%';
            modal.classList.remove('hidden');
        }

        function hideProgressModal() {
            const modal = document.getElementById('progressModal');
            if (modal) {
                modal.classList.add('hidden');
            }
        }

        function updateProgress(percent) {
            const barEl = document.getElementById('progressBar');
            const percentEl = document.getElementById('progressPercent');
            if (barEl) barEl.style.width = percent + '%';
            if (percentEl) percentEl.textContent = percent + '%';
        }

        function updateProgressWithMessage(percent, message) {
            updateProgress(percent);
            const messageEl = document.getElementById('progressMessage');
            if (messageEl) messageEl.textContent = message;
        }

        // Toast notification function
        function showToast(message, type = 'info') {
            const container = document.getElementById('toastContainer');
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

            // Auto remove after 5 seconds
            setTimeout(() => {
                toast.style.transform = 'translateX(400px)';
                toast.style.opacity = '0';
                setTimeout(() => toast.remove(), 300);
            }, 5000);
        }

        // Show notification (backward compatibility)
        function showNotification(message, type = 'info') {
            showToast(message, type);
        }

        // Add event listeners for button clicks using delegation
        document.addEventListener('click', function(e) {
            const target = e.target.closest('button');
            if (!target) return;

            const containerId = target.getAttribute('data-container-id');
            if (!containerId) return;

            if (target.classList.contains('btn-start-container')) {
                e.preventDefault();
                startContainer(containerId);
            } else if (target.classList.contains('btn-stop-container')) {
                e.preventDefault();
                stopContainer(containerId);
            } else if (target.classList.contains('btn-show-menu')) {
                e.preventDefault();
                showContainerMenu(containerId);
            }
        });

        // Format deployment times in user's timezone
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
        // Ensure modals are hidden on page load
        document.addEventListener("DOMContentLoaded", function() {
            hideProgressModal();
            hideDeployModal();
        });
