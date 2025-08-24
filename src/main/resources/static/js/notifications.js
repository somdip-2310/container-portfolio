// static/js/notifications.js
class ToastNotification {
    static show(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `fixed top-4 right-4 px-6 py-3 rounded-lg shadow-lg text-white z-50 
                          ${type === 'success' ? 'bg-green-600' : 
                            type === 'error' ? 'bg-red-600' : 
                            type === 'warning' ? 'bg-yellow-600' : 'bg-blue-600'}`;
        toast.innerHTML = `
            <div class="flex items-center">
                <i class="fas fa-${type === 'success' ? 'check-circle' : 
                                   type === 'error' ? 'exclamation-circle' : 
                                   type === 'warning' ? 'exclamation-triangle' : 'info-circle'} mr-2"></i>
                ${message}
            </div>
        `;
        document.body.appendChild(toast);
        
        setTimeout(() => {
            toast.remove();
        }, 5000);
    }
}