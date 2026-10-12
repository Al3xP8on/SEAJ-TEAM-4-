const form = document.getElementById('loginForm');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const message = document.getElementById('loginMessage');
const authService = new AuthService({
    loginUrl: form.dataset.loginUrl || (['8000', '5500'].includes(window.location.port) || window.location.protocol === 'file:' ? 'http://localhost:3000/auth/login' : '/auth/login'),
});
let verificationState = null;
let viewGeneration = 0;

function setPanel(id) {
    for (const panel of ['loginDiv', 'registerDiv', 'verificationDiv']) {
        document.getElementById(panel).style.display = panel === id ? 'block' : 'none';
    }
}
function showRegister() { viewGeneration += 1; verificationState = null; setPanel('registerDiv'); }
function showLogin() { viewGeneration += 1; verificationState = null; setPanel('loginDiv'); }
function cancelVerification() { authService.logout(); showLogin(); }
function showVerification(purpose, challenge) {
    verificationState = { purpose, challengeId: challenge.challenge_id, username: document.getElementById('regUsername').value.trim() };
    document.getElementById('verificationCode').value = '';
    document.getElementById('verificationMessage').textContent = challenge.message;
    setPanel('verificationDiv');
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    const username = usernameInput.value.trim();
    const password = passwordInput.value;
    if (username.length < 3 || username.length > 100 || !password) {
        message.textContent = 'Enter your username and password.';
        message.className = 'error';
        return;
    }
    const button = form.querySelector('button[type="submit"]');
    const generation = viewGeneration;
    button.disabled = true;
    message.textContent = 'Sending verification code…';
    message.className = '';
    try {
        const challenge = await authService.login({ username, password });
        passwordInput.value = '';
        if (generation === viewGeneration) showVerification('LOGIN', challenge);
    } catch (error) {
        if (generation === viewGeneration) { message.textContent = error.message; message.className = 'error'; }
    } finally { button.disabled = false; }
});

usernameInput.addEventListener('input', () => { message.textContent = ''; message.className = ''; });

async function submitRegister() {
    const feedback = document.getElementById('registerMessage');
    const button = document.getElementById('registerButton');
    const emailInput = document.getElementById('regEmail');
    const password = document.getElementById('regPassword').value;
    const username = document.getElementById('regUsername').value.trim();
    if (!emailInput.value || !emailInput.validity.valid || password.length < 8 || new TextEncoder().encode(password).length > 72 || username.length < 3 || username.length > 100) {
        feedback.textContent = 'Enter a unique username (3–100 characters), a valid email, and a password of at least 8 characters (maximum 72 bytes).';
        return;
    }
    const generation = viewGeneration;
    button.disabled = true;
    feedback.textContent = 'Sending verification code…';
    try {
        const registerUrl = authService.loginUrl.replace(/\/login$/, '/register');
        const { result: challenge } = await authService.request(registerUrl, { email: emailInput.value.trim(), password, username });
        document.getElementById('regPassword').value = '';
        if (generation === viewGeneration) showVerification('REGISTER', challenge);
    } catch (error) { if (generation === viewGeneration) feedback.textContent = error.message; }
    finally { button.disabled = false; }
}

document.getElementById('verificationForm').addEventListener('submit', async event => {
    event.preventDefault();
    const state = verificationState;
    const generation = viewGeneration;
    const code = document.getElementById('verificationCode').value.trim();
    const feedback = document.getElementById('verificationMessage');
    if (!state || !/^[0-9]{6}$/.test(code)) { feedback.textContent = 'Enter the six-digit code from your email.'; return; }
    const button = document.getElementById('verifyButton');
    button.disabled = true;
    try {
        if (state.purpose === 'LOGIN') {
            await authService.verifyLogin(code);
            if (generation === viewGeneration) { showLogin(); message.textContent = 'Login successful.'; message.className = 'success'; }
        } else {
            const url = authService.loginUrl.replace(/\/login$/, '/register/verify');
            const { result: user } = await authService.request(url, { challenge_id: state.challengeId, code });
            if (generation === viewGeneration) {
                usernameInput.value = user.username;
                showLogin();
                message.textContent = `Account ${user.account_id} created. Please log in with your username and password.`;
                message.className = 'success';
            }
        }
    } catch (error) { if (generation === viewGeneration) feedback.textContent = error.message; }
    finally { button.disabled = false; }
});
