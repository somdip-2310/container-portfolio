// Deployment Actions JavaScript

function viewDeploymentDetails(deploymentId) {
    window.location.href = '/deployments/' + deploymentId;
}

function viewDeployment(containerName) {
    alert('Viewing deployment details for: ' + containerName + '\n\nThis feature requires backend implementation.');
}

function retryDeployment(containerName) {
    if (confirm('Are you sure you want to retry the deployment for "' + containerName + '"?')) {
        alert('Retrying deployment for: ' + containerName + '\n\nThis feature requires backend implementation.');
    }
}

// Filtering functionality
document.addEventListener('DOMContentLoaded', function() {
    const statusFilter = document.getElementById('statusFilter');
    const timeRangeFilter = document.getElementById('timeRange');

    function filterDeployments() {
        const deploymentRows = document.querySelectorAll('.deployment-row');
        const selectedStatus = statusFilter ? statusFilter.value : '';
        const selectedTimeRange = timeRangeFilter ? timeRangeFilter.value : '7d';
        const now = new Date();

        deploymentRows.forEach(row => {
            let showRow = true;

            // Status filter
            if (selectedStatus) {
                const rowStatus = row.getAttribute('data-status');
                if (rowStatus !== selectedStatus) {
                    showRow = false;
                }
            }

            // Time range filter
            if (showRow && selectedTimeRange !== 'all') {
                const dateCell = row.querySelector('td:nth-child(4) div:first-child');
                if (dateCell) {
                    const dateText = dateCell.textContent.trim();
                    // Parse date in format "MMM dd, yyyy HH:mm"
                    const deploymentDate = new Date(dateText);

                    let cutoffDate = new Date(now);
                    switch(selectedTimeRange) {
                        case '24h':
                            cutoffDate.setHours(now.getHours() - 24);
                            break;
                        case '7d':
                            cutoffDate.setDate(now.getDate() - 7);
                            break;
                        case '30d':
                            cutoffDate.setDate(now.getDate() - 30);
                            break;
                    }

                    if (deploymentDate < cutoffDate) {
                        showRow = false;
                    }
                }
            }

            row.style.display = showRow ? '' : 'none';
        });
    }

    // Add event listeners
    if (statusFilter) {
        statusFilter.addEventListener('change', filterDeployments);
    }

    if (timeRangeFilter) {
        timeRangeFilter.addEventListener('change', filterDeployments);
    }
});
