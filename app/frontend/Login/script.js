const AUTH_URL = 'http://localhost:3000/auth/login';
const ACCOUNTS_URL = 'http://localhost:8081/api/v1/accounts';

const authService = new AuthService({ loginUrl: formLoginUrl() });

function formLoginUrl() {
    return document.getElementById('loginForm').dataset.loginUrl || '/auth/login';
}

const form = document.getElementById('loginForm');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const message = document.getElementById('loginMessage');
const submitBtn = form.querySelector('button[type="submit"]');
const registerUsernameInput = document.getElementById('regUsername');
const registerPasswordInput = document.getElementById('regPassword');
const registerEmailInput = document.getElementById('regEmail');
const registerPhoneInput = document.getElementById('regPhone');
const registerMessage = document.getElementById('registerMessage');
const registerBtn = document.querySelector('#registerDiv .submit-btn');

function showMessage(text, type) {
    message.textContent = text;
    message.className = type;
}

function showRegisterMessage(text, type) {
    registerMessage.textContent = text;
    registerMessage.className = type;
}

async function parseJsonSafely(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

async function parseErrorText(response, fallbackPrefix) {
    const contentType = response.headers.get('content-type') || '';

    if (contentType.includes('application/json')) {
        const errorBody = await parseJsonSafely(response);
        if (typeof errorBody?.message === 'string') {
            return `${fallbackPrefix}: ${errorBody.message}`;
        }
        return `${fallbackPrefix} with status ${response.status}`;
    }

    const text = await response.text();
    if (text) {
        return `${fallbackPrefix}: ${text}`;
    }

    return `${fallbackPrefix} with status ${response.status}`;
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    if (!username || username.length < 3) {
        usernameInput.style.borderColor = '#e74c3c';
        showMessage('Username must be at least 3 characters.', 'error');
        return;
    }

    if (!password || password.length < 8) {
        showMessage('Password must be at least 8 characters.', 'error');
        return;
    }

    submitBtn.disabled = true;
    showMessage('', '');

    try {
        const response = await fetch(AUTH_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
        });

        if (!response.ok) {
            const errorBody = await parseJsonSafely(response);
            const errorText =
                errorBody?.message ||
                `Login failed with status ${response.status}`;

            showMessage(`Login failed: ${errorText}`, 'error');
            return;
        }

        const data = await parseJsonSafely(response);
        const token = data?.access_token || data?.accessToken || data?.token;

        if (!token) {
            showMessage('Login succeeded but no token was returned.', 'error');
            return;
        }

        showMessage(`Logged in. Token starts: ${token.slice(0, 20)}...`, 'success');
    } catch (err) {
        console.error('fetch() rejected:', err);
        const errorMessage = err instanceof Error ? err.message : 'Unknown network error';
        showMessage(`Request failed: ${errorMessage}`, 'error');
    } finally {
        submitBtn.disabled = false;
    }
});

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    message.textContent = 'Signing in…';
    message.className = '';
    try {
        await authService.login({ username, password });
        passwordInput.value = '';
        message.textContent = `Welcome, ${username}! Login successful.`;
        message.className = 'success';
    } catch (error) {
        message.textContent = error.message;
        message.className = 'error';
    } finally {
        button.disabled = false;
    }
});

usernameInput.addEventListener('input', () => {
    usernameInput.style.borderColor = '';
    if (message.className === 'error') {
        showMessage('', '');
    }
});

async function submitRegister() {
    const username = registerUsernameInput.value.trim();
    const password = registerPasswordInput.value;
    const email = registerEmailInput.value.trim();
    const phone = registerPhoneInput.value.trim();

    if (!username || username.length < 3) {
        showRegisterMessage('Username must be at least 3 characters.', 'error');
        return;
    }

    if (!password || password.length < 8) {
        showRegisterMessage('Password must be at least 8 characters.', 'error');
        return;
    }

    if (email && !registerEmailInput.checkValidity()) {
        showRegisterMessage('Please enter a valid email address.', 'error');
        return;
    }

    registerBtn.disabled = true;
    showRegisterMessage('', '');

    try {
        const response = await fetch(ACCOUNTS_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username,
                password,
                email: email || null,
                phone: phone || null,
            }),
        });

        if (!response.ok) {
            const errorText = await parseErrorText(response, 'Account creation failed');
            showRegisterMessage(errorText, 'error');
            return;
        }

        const data = await parseJsonSafely(response);
        const createdAccountId = data?.accountId ? ` (${data.accountId})` : '';
        showRegisterMessage(`Account created successfully${createdAccountId}. You can log in now.`, 'success');
    } catch (err) {
        console.error('fetch() rejected:', err);
        const errorMessage = err instanceof Error ? err.message : 'Unknown network error';
        showRegisterMessage(`Request failed: ${errorMessage}`, 'error');
    } finally {
        registerBtn.disabled = false;
    }
}

window.submitRegister = submitRegister;
