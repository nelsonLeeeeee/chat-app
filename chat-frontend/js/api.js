/**
 * API 请求封装
 */
const API = {
    baseUrl: '/api',

    async request(method, path, data) {
        const options = {
            method,
            headers: { 'Content-Type': 'application/json' }
        };

        let url = this.baseUrl + path;

        if (method === 'GET' && data) {
            const params = new URLSearchParams(data).toString();
            url += '?' + params;
        } else if (data) {
            options.body = JSON.stringify(data);
        }

        const response = await fetch(url, options);
        return response.json();
    },

    get(path, data)    { return this.request('GET', path, data); },
    post(path, data)   { return this.request('POST', path, data); },

    // 认证接口
    login(username, password) {
        const form = new FormData();
        form.append('username', username);
        form.append('password', password);
        return fetch(this.baseUrl + '/auth/login', {
            method: 'POST',
            body: form
        }).then(r => r.json());
    },

    register(data) {
        return this.post('/auth/register', data);
    },

    // 聊天接口
    createSession(userId) {
        return this.post('/chat/session?userId=' + userId);
    },

    sendMessage(sessionId, senderId, content) {
        const form = new FormData();
        form.append('sessionId', sessionId);
        form.append('senderId', senderId);
        form.append('content', content);
        return fetch(this.baseUrl + '/chat/message', {
            method: 'POST',
            body: form
        }).then(r => r.json());
    },

    getMessages(sessionId) {
        return this.get('/chat/messages/' + sessionId);
    },

    closeSession(sessionId) {
        return this.post('/chat/session/' + sessionId + '/close');
    }
};
