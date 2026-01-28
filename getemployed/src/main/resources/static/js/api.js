// Базовый API клиент
const api = {
    baseUrl: '/api/v1',

    // Общий метод для запросов
    async request(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;

        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            }
        };

        // Добавляем токен авторизации если есть
        const token = localStorage.getItem('token');
        if (token) {
            defaultOptions.headers['Authorization'] = `Bearer ${token}`;
        }

        const config = {
            ...defaultOptions,
            ...options,
            headers: {
                ...defaultOptions.headers,
                ...options.headers
            }
        };

        try {
            const response = await fetch(url, config);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP error ${response.status}`);
            }

            // Для DELETE запросов может не быть тела
            if (response.status === 204) {
                return null;
            }

            return await response.json();

        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    },

    // Регистрация пользователя
    async register(userData) {
        return this.request('/users', {
            method: 'POST',
            body: JSON.stringify(userData)
        });
    },

    // Получение текущего пользователя
    async getCurrentUser() {
        return this.request('/users/me');
    },

    // Обновление пользователя
    async updateUser(userId, userData) {
        return this.request(`/users/${userId}`, {
            method: 'PUT',
            body: JSON.stringify(userData)
        });
    },

    // Получение пользователя по email
    async getUserByEmail(email) {
        return this.request(`/users/email/${encodeURIComponent(email)}`);
    },

    // Health check
    async healthCheck() {
        return this.request('/users/health');
    }
};

// Экспорт для использования в других файлах
window.api = api;