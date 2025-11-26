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
