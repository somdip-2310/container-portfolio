// static/js/containers.js
async function startContainer(containerId) {
    try {
        const response = await fetch(`/api/containers/${containerId}/start`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [header]: token // CSRF token
            }
        });
        
        if (response.ok) {
            ToastNotification.show('Container started successfully', 'success');
            location.reload();
        } else {
            ToastNotification.show('Failed to start container', 'error');
        }
    } catch (error) {
        ToastNotification.show('Network error', 'error');
    }
}

async function stopContainer(containerId) {
    // Similar implementation
}

async function deleteContainer(containerId) {
    if (confirm('Are you sure you want to delete this container?')) {
        // Implementation
    }
}