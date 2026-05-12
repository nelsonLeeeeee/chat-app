/**
 * 企业客服系统 — 前端主逻辑
 */
(function () {
    'use strict';

    // DOM 元素
    const loginBox = document.getElementById('loginBox');
    const registerBox = document.getElementById('registerBox');
    const chatBox = document.getElementById('chatBox');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const loginMsg = document.getElementById('loginMsg');
    const regMsg = document.getElementById('regMsg');
    const currentUser = document.getElementById('currentUser');
    const sessionList = document.getElementById('sessionList');
    const messageList = document.getElementById('messageList');
    const chatHeader = document.getElementById('chatHeader');
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    const newSessionBtn = document.getElementById('newSessionBtn');
    const logoutBtn = document.getElementById('logoutBtn');

    // 状态
    let user = null;
    let sessions = [];
    let activeSessionId = null;
    let agentOnline = false;
    let pollInterval = null;
    let statusPollInterval = null;
    let lastMessageCount = 0;

    /* ========== 页面切换 ========== */
    function showLogin() {
        loginBox.style.display = 'flex';
        registerBox.style.display = 'none';
        chatBox.style.display = 'none';
    }

    function showRegister() {
        loginBox.style.display = 'none';
        registerBox.style.display = 'flex';
        chatBox.style.display = 'none';
    }

    function showChat() {
        loginBox.style.display = 'none';
        registerBox.style.display = 'none';
        chatBox.style.display = 'flex';
        currentUser.textContent = user.nickname || user.username;
    }

    document.getElementById('showRegister').onclick = showRegister;
    document.getElementById('showLogin').onclick = showLogin;

    /* ========== 登录 ========== */
    loginForm.onsubmit = async function (e) {
        e.preventDefault();
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        if (!username || !password) return;

        try {
            const res = await API.login(username, password);
            if (res.code === 200) {
                user = res.data;
                showChat();
                loginMsg.textContent = '';
                document.getElementById('password').value = '';
                await loadSessions();
                startStatusPolling();
            } else {
                loginMsg.textContent = res.message || '登录失败';
                loginMsg.className = 'msg error';
            }
        } catch (err) {
            loginMsg.textContent = '网络错误，请重试';
            loginMsg.className = 'msg error';
        }
    };

    /* ========== 注册 ========== */
    registerForm.onsubmit = async function (e) {
        e.preventDefault();
        const username = document.getElementById('regUsername').value.trim();
        const password = document.getElementById('regPassword').value;
        const nickname = document.getElementById('regNickname').value.trim();

        if (!username || !password) return;

        try {
            const res = await API.register({ username, password, nickname });
            if (res.code === 200) {
                regMsg.textContent = '注册成功，请登录';
                regMsg.className = 'msg success';
                registerForm.reset();
                setTimeout(showLogin, 1200);
            } else {
                regMsg.textContent = res.message || '注册失败';
                regMsg.className = 'msg error';
            }
        } catch (err) {
            regMsg.textContent = '网络错误，请重试';
            regMsg.className = 'msg error';
        }
    };

    /* ========== 退出 ========== */
    logoutBtn.onclick = function () {
        stopPolling();
        stopStatusPolling();
        user = null;
        sessions = [];
        activeSessionId = null;
        lastMessageCount = 0;
        showLogin();
    };

    /* ========== 会话管理 ========== */
    async function loadSessions() {
        if (!user) return;
        try {
            const res = await API.getSessions(user.id);
            if (res.code === 200) {
                sessions = res.data || [];
                renderSessions();
                if (sessions.length > 0 && !activeSessionId) {
                    switchSession(sessions[0].id);
                }
            }
        } catch (err) {
            console.error('加载会话列表失败', err);
        }
    }

    newSessionBtn.onclick = async function () {
        if (!user) return;
        try {
            const res = await API.createSession(user.id);
            if (res.code === 200) {
                sessions.unshift(res.data);
                renderSessions();
                switchSession(res.data.id);
            }
        } catch (err) {
            console.error('创建会话失败', err);
        }
    };

    function renderSessions() {
        if (sessions.length === 0) {
            sessionList.innerHTML = '<div class="session-empty">暂无会话</div>';
            return;
        }
        sessionList.innerHTML = sessions.map(function (s) {
            var active = s.id === activeSessionId ? ' active' : '';
            var statusText = { 'WAITING': '等待中', 'ACTIVE': '进行中', 'CLOSED': '已关闭' }[s.status] || s.status;
            var agentLabel = '';
            if (s.agentType === 'AI') {
                agentLabel = ' | <span class="agent-tag ai">AI</span>';
            } else if (s.agentType === 'HUMAN') {
                agentLabel = ' | <span class="agent-tag human">人工</span>';
            }
            return '<div class="session-item' + active + '" data-id="' + s.id + '">'
                + '会话 #' + s.id + ' <small>(' + statusText + ')' + agentLabel + '</small></div>';
        }).join('');

        sessionList.querySelectorAll('.session-item').forEach(function (item) {
            item.onclick = function () {
                switchSession(parseInt(this.dataset.id));
            };
        });
    }

    function switchSession(sessionId) {
        activeSessionId = sessionId;
        renderSessions();
        startPolling(sessionId);
        lastMessageCount = 0;
        loadMessages(sessionId);

        var session = sessions.find(function (s) { return s.id === sessionId; });
        if (session) {
            var agentInfo = '';
            if (session.agentType === 'AI') {
                agentInfo = ' | 服务方: <span style="color:#9b59b6">智能客服</span>';
            } else if (session.agentType === 'HUMAN') {
                agentInfo = ' | 服务方: <span style="color:#27ae60">人工客服 (#' + (session.agentId || '?') + ')</span>';
            }
            var statusText = { 'WAITING': '等待客服接入', 'ACTIVE': '聊天中', 'CLOSED': '已关闭' }[session.status] || session.status;
            chatHeader.innerHTML = '<span>会话 #' + sessionId + ' (' + statusText + ')' + agentInfo + '</span>'
                + '<span id="agentStatusBadge" class="status-badge '
                + (agentOnline ? 'online' : 'offline') + '">'
                + (agentOnline ? '人工客服在线' : '人工客服离线') + '</span>';
        }

        var disabled = !session || session.status === 'CLOSED';
        messageInput.disabled = disabled;
        sendBtn.disabled = disabled;
        messageInput.placeholder = disabled ? (session && session.status === 'CLOSED' ? '会话已关闭' : '请等待客服接入') : '输入消息...';
    }

    /* ========== 消息加载 ========== */
    async function loadMessages(sessionId) {
        try {
            var res = await API.getMessages(sessionId);
            if (res.code === 200 && res.data) {
                renderMessages(res.data);
            } else {
                messageList.innerHTML = '<div class="message-empty">暂无消息</div>';
            }
        } catch (err) {
            messageList.innerHTML = '<div class="message-empty">加载失败</div>';
        }
    }

    function renderMessages(messages) {
        if (!messages || messages.length === 0) {
            messageList.innerHTML = '<div class="message-empty">暂无消息，发送第一条消息吧</div>';
            lastMessageCount = 0;
            return;
        }
        if (messages.length === lastMessageCount && lastMessageCount > 0) return;
        lastMessageCount = messages.length;

        messageList.innerHTML = messages.map(function (m) {
            var roleClass, roleLabel = '';
            if (m.senderRole === 'AI') {
                roleClass = 'ai';
                roleLabel = '智能客服';
            } else if (m.senderRole === 'AGENT') {
                roleClass = 'agent';
                roleLabel = '客服';
            } else if (m.senderId === user.id) {
                roleClass = 'self';
            } else {
                roleClass = 'user';
            }
            return '<div class="message-item ' + roleClass + '">'
                + (roleLabel ? '<span class="message-role-label ' + roleClass + '">' + roleLabel + '</span>' : '')
                + '<div class="message-bubble">' + escapeHtml(m.content) + '</div>'
                + '<span class="message-time">' + (m.createTime || '') + '</span>'
                + '</div>';
        }).join('');
        messageList.scrollTop = messageList.scrollHeight;
    }

    /* ========== 发送消息 ========== */
    sendBtn.onclick = async function () {
        var content = messageInput.value.trim();
        if (!content || !activeSessionId || !user) return;

        try {
            var res = await API.sendMessage(activeSessionId, user.id, content, user.role);
            if (res.code === 200) {
                messageInput.value = '';
                lastMessageCount = 0;
                loadMessages(activeSessionId);
            }
        } catch (err) {
            console.error('发送失败', err);
        }
    };

    messageInput.onkeydown = function (e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendBtn.click();
        }
    };

    /* ========== 轮询 ========== */
    function startPolling(sessionId) {
        stopPolling();
        pollInterval = setInterval(function () {
            loadMessages(sessionId);
        }, 3000);
    }

    function stopPolling() {
        if (pollInterval) {
            clearInterval(pollInterval);
            pollInterval = null;
        }
    }

    function startStatusPolling() {
        stopStatusPolling();
        checkAgentStatus();
        statusPollInterval = setInterval(checkAgentStatus, 10000);
    }

    function stopStatusPolling() {
        if (statusPollInterval) {
            clearInterval(statusPollInterval);
            statusPollInterval = null;
        }
    }

    async function checkAgentStatus() {
        try {
            var res = await API.getAgentStatus();
            if (res.code === 200) {
                agentOnline = res.data && res.data.hasOnline;
                var badge = document.getElementById('agentStatusBadge');
                if (badge) {
                    badge.className = 'status-badge ' + (agentOnline ? 'online' : 'offline');
                    badge.textContent = agentOnline ? '人工客服在线' : '人工客服离线';
                }
            }
        } catch (err) {
            console.error('状态检查失败', err);
        }
    }

    /* ========== 工具函数 ========== */
    function escapeHtml(text) {
        var div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /* ========== 初始化 ========== */
    showLogin();
})();
