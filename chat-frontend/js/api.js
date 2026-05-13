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

    sendMessage(sessionId, senderId, content, senderRole) {
        const form = new FormData();
        form.append('sessionId', sessionId);
        form.append('senderId', senderId);
        form.append('content', content);
        form.append('senderRole', senderRole || 'USER');
        return fetch(this.baseUrl + '/chat/message', {
            method: 'POST',
            body: form
        }).then(r => r.json());
    },

    getMessages(sessionId) {
        return this.get('/chat/messages/' + sessionId);
    },

    getSessions(userId) {
        return this.get('/chat/session/list?userId=' + userId);
    },

    getAgentSessions(agentId) {
        return this.get('/chat/session/agent?agentId=' + agentId);
    },

    getAgentStatus() {
        return this.get('/agent/status');
    },

    agentGoOnline(agentId) {
        return this.post('/agent/online?agentId=' + agentId);
    },

    agentGoOffline(agentId) {
        return this.post('/agent/offline?agentId=' + agentId);
    },

    // 知识库管理
    uploadKnowledge(file) {
        const form = new FormData();
        form.append('file', file);
        return fetch(this.baseUrl + '/knowledge/upload', {
            method: 'POST',
            body: form
        }).then(r => r.json());
    },

    getKnowledgeList() {
        return this.get('/knowledge/list');
    },

    deleteKnowledge(id) {
        return fetch(this.baseUrl + '/knowledge/' + id, {
            method: 'DELETE'
        }).then(r => r.json());
    }
};
