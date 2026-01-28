// Управление аутентификацией
const auth = {
    // Вход в систему (базовая аутентификация)
    async login(email, password) {
        // Создаем Basic Auth заголовок
        const token = btoa(`${email}:${password}`);

        try {
            // Пробуем получить пользователя по email
            const user = await api.getUserByEmail(email);

            // Сохраняем данные в localStorage
            localStorage.setItem('token', token);
            localStorage.setItem('user', JSON.stringify(user));
            localStorage.setItem('email', email);

            return user;

        } catch (error) {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            localStorage.removeItem('email');
            throw new Error('Неверный email или пароль');
        }
    },

    // Выход из системы
    logout() {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        localStorage.removeItem('email');
        window.location.href = '/login.html';
    },

    // Проверка авторизации
    isAuthenticated() {
        return !!localStorage.getItem('token');
    },

    // Получение текущего пользователя
    getCurrentUser() {
        const userStr = localStorage.getItem('user');
        return userStr ? JSON.parse(userStr) : null;
    },

    // Проверка и перенаправление если не авторизован
    checkAuth() {
        if (!this.isAuthenticated() &&
            !window.location.pathname.includes('/login.html') &&
            !window.location.pathname.includes('/register.html') &&
            window.location.pathname !== '/') {
            window.location.href = '/login.html';
        }
    },

    // Получение токена авторизации для заголовков
    getAuthHeader() {
        const token = localStorage.getItem('token');
        return token ? { 'Authorization': `Basic ${token}` } : {};
    }
};

// Экспорт для использования в других файлах
window.auth = auth;