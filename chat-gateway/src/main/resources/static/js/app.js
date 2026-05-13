/**
 * 企业B2B客服系统 — 前端主逻辑
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
    const chatHeaderTitle = document.getElementById('chatHeaderTitle');
    const headerButtons = document.getElementById('headerButtons');
    const userSideLogoutBtn = document.getElementById('userSideLogoutBtn');
    const chatMain = document.getElementById('chatMain');
    const chatSidebar = document.querySelector('.chat-sidebar');
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    const newSessionBtn = document.getElementById('newSessionBtn');
    const logoutBtn = document.getElementById('logoutBtn');
    const knowledgePanel = document.getElementById('knowledgePanel');
    const uploadPdfBtn = document.getElementById('uploadPdfBtn');
    const pdfFileInput = document.getElementById('pdfFileInput');
    const uploadStatus = document.getElementById('uploadStatus');
    const knowledgeList = document.getElementById('knowledgeList');

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
        if (user.role === 'USER') {
            chatSidebar.style.display = 'none';
            chatMain.style.flex = '1';
            userSideLogoutBtn.style.display = '';
            headerButtons.style.display = '';
            knowledgePanel.style.display = 'none';
            newSessionBtn.style.display = 'none';
        } else {
            chatSidebar.style.display = '';
            chatMain.style.flex = '';
            userSideLogoutBtn.style.display = 'none';
            headerButtons.style.display = '';
            knowledgePanel.style.display = '';
            newSessionBtn.style.display = 'none';
            loadKnowledgeList();
        }
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
                if (user.role === 'AGENT') {
                    // 客服登录后自动上线，接管等待中的AI会话
                    await API.agentGoOnline(user.id);
                    await loadSessions();
                } else {
                    await loadSessions();
                    if (sessions.length === 0) {
                        var autoRes = await API.createSession(user.id);
                        if (autoRes.code === 200) {
                            activeSessionId = autoRes.data.id;
                            await loadSessions();
                            switchSession(activeSessionId);
                        }
                    }
                }
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
        const role = document.getElementById('regRole').value;

        if (!username || !password) return;

        try {
            const res = await API.register({ username, password, nickname, role });
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
    async function doLogout() {
        if (user && user.role === 'AGENT') {
            try { await API.agentGoOffline(user.id); } catch (e) {}
        }
        stopPolling();
        stopStatusPolling();
        user = null;
        sessions = [];
        activeSessionId = null;
        lastMessageCount = 0;
        showLogin();
    }

    logoutBtn.onclick = doLogout;
    userSideLogoutBtn.onclick = doLogout;

    /* ========== 会话管理 ========== */
    async function loadSessions() {
        if (!user) return;
        try {
            var res = user.role === 'AGENT'
                ? await API.getAgentSessions(user.id)
                : await API.getSessions(user.id);
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
            var displayName = user.role === 'AGENT'
                ? (s.userName || '用户#' + s.userId)
                : (s.agentType === 'AI' ? '智能客服' : (s.agentName || '客服#' + s.agentId));
            var agentLabel = '';
            if (user.role !== 'AGENT') {
                if (s.agentType === 'AI') {
                    agentLabel = ' | <span class="agent-tag ai">AI</span>';
                } else if (s.agentType === 'HUMAN') {
                    agentLabel = ' | <span class="agent-tag human">人工</span>';
                }
            }
            return '<div class="session-item' + active + '" data-id="' + s.id + '">'
                + displayName + agentLabel + '</div>';
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
            var otherParty = user.role === 'AGENT'
                ? (session.userName || '用户#' + session.userId)
                : (session.agentType === 'AI' ? '智能客服' : (session.agentName || '客服#' + session.agentId));
            chatHeaderTitle.innerHTML = '与 ' + otherParty + ' 的商务对话';
        }

        messageInput.disabled = false;
        sendBtn.disabled = false;
        messageInput.placeholder = '输入消息...';
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
            if (m.msgType === 'SYSTEM' || m.senderRole === 'SYSTEM') {
                roleClass = 'system';
            } else if (m.senderRole === 'AI') {
                roleClass = 'ai';
                roleLabel = '智能客服';
            } else if (m.senderRole === 'AGENT') {
                roleClass = 'agent';
                roleLabel = '产品方客服';
            } else if (m.senderId === user.id) {
                roleClass = 'self';
            } else {
                roleClass = 'user';
                roleLabel = '客户方';
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
                var wasOnline = agentOnline;
                agentOnline = res.data && res.data.hasOnline;
                if (activeSessionId && user && user.role !== 'AGENT') {
                    if (wasOnline !== agentOnline) {
                        await loadSessions();
                    }
                    switchSession(activeSessionId);
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

    /* ========== 知识库管理 ========== */
    uploadPdfBtn.onclick = function () {
        pdfFileInput.click();
    };

    pdfFileInput.onchange = async function () {
        var file = pdfFileInput.files[0];
        if (!file) return;
        uploadStatus.textContent = '上传中...';
        uploadStatus.className = 'msg';
        try {
            var res = await API.uploadKnowledge(file);
            if (res.code === 200) {
                uploadStatus.textContent = '上传成功';
                uploadStatus.className = 'msg success';
                pdfFileInput.value = '';
                loadKnowledgeList();
            } else {
                uploadStatus.textContent = res.message || '上传失败';
                uploadStatus.className = 'msg error';
            }
        } catch (err) {
            uploadStatus.textContent = '网络错误';
            uploadStatus.className = 'msg error';
        }
    };

    async function loadKnowledgeList() {
        try {
            var res = await API.getKnowledgeList();
            if (res.code === 200 && res.data) {
                renderKnowledgeList(res.data);
            } else {
                knowledgeList.innerHTML = '<div class="session-empty">暂无文档</div>';
            }
        } catch (err) {
            knowledgeList.innerHTML = '<div class="session-empty">加载失败</div>';
        }
    }

    function renderKnowledgeList(docs) {
        if (!docs || docs.length === 0) {
            knowledgeList.innerHTML = '<div class="session-empty">暂无文档</div>';
            return;
        }
        knowledgeList.innerHTML = docs.map(function (d) {
            var sizeText = d.fileSize ? (d.fileSize / 1024).toFixed(0) + 'KB' : '';
            return '<div class="knowledge-item">'
                + '<span class="doc-name" title="' + escapeHtml(d.fileName || '') + '">' + escapeHtml(d.fileName || '未知文件') + '</span>'
                + '<span class="doc-info">' + sizeText + '</span>'
                + '<button class="btn-delete" data-id="' + d.id + '" title="删除">×</button>'
                + '</div>';
        }).join('');

        knowledgeList.querySelectorAll('.btn-delete').forEach(function (btn) {
            btn.onclick = async function () {
                var id = parseInt(this.dataset.id);
                if (!confirm('确定删除该文档？')) return;
                try {
                    var res = await API.deleteKnowledge(id);
                    if (res.code === 200) {
                        loadKnowledgeList();
                    }
                } catch (err) {
                    console.error('删除失败', err);
                }
            };
        });
    }

    /* ========== 初始化 ========== */
    showLogin();
})();
