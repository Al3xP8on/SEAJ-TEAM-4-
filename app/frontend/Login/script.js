// TODO 1: select the form, both inputs, and the message element
const form = document.getElementById('loginForm');
const usernameInput = document.getElementById('username');
const passwordInput = document.getElementById('password');
const message = document.getElementById('loginMessage');

// TODO 2: handle submit - preventDefault, validate, show error or success feedback
form.addEventListener('submit', (e) => {
    e.preventDefault();

    const username = usernameInput.value.trim();
    const password = passwordInput.value;

    if (!username || username.length < 3) {
        usernameInput.style.borderColor = '#e74c3c';
        message.textContent = 'Username must be at least 3 characters.';
        message.className = 'error';
        return;
    }

    if (!password || password.length < 6) {
        message.textContent = 'Password must be at least 6 characters.';
        message.className = 'error';
        return;
    }

    message.textContent = `Welcome, ${username}! Login successful.`;
    message.className = 'success';
});

// TODO 3: clear the username's invalid state as the user types
usernameInput.addEventListener('input', () => {
    usernameInput.style.borderColor = '';
    if (message.className === 'error') {
        message.textContent = '';
        message.className = '';
    }
});
