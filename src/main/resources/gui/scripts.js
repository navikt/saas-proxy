window.onload = function() {
    console.log("Do on load");
    loadPing();
};

const runTest = async () => {
    const response = await fetch('/internal/testcall', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    document.getElementById('result').textContent = response.status.toString();
}

const loadPing = async () => {
    const response = await fetch('/internal/authping', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json'
        }
    });

    if (response.status === 401) {
        // Unauthorized
        document.getElementById('authorization-message').innerHTML = 'Unauthorized <button id="login-button" onclick="login()">Login</button>';
        clearInterval(interval);
        document.getElementById('name-info').innerHTML = ''
        document.getElementById('expire-info').innerHTML = ''
        document.getElementById('logout-button-holder').innerHTML = ''
        return;
    }

    const data = await response.json();
    const records = data.records;
    currentPage = data.page
    pageCount = data.pageCount
    pageSize = data.pageSize
    count = data.count

    const nameInfo = document.getElementById('name-info');

    nameInfo.textContent = data.username

    updateExpirationTime(data.expireTime);

    document.getElementById('authorization-message').textContent = '';
};

const login = () => {
    window.location.href = '/oauth2/login?redirect=/internal/gui';
}

const logout = () => {
    window.location.href = '/oauth2/logout?redirect=/internal/gui';
}

let interval;

const updateExpirationTime = (expireTime) => {
    const expireInfo = document.getElementById('expire-info');
    const logoutButtonHolder = document.getElementById('logout-button-holder');

    const updateTime = () => {
        const currentTime = Date.now();
        const timeLeft = expireTime - currentTime;

        if (timeLeft > 0) {
            const minutes = Math.floor(timeLeft / 60000);
            const seconds = Math.floor((timeLeft % 60000) / 1000);
            expireInfo.textContent = `${minutes} minutes and ${seconds} seconds remaining`;
            logoutButtonHolder.innerHTML = `<button id="logout-button" onClick="logout()">Logout</button>`
        } else {
            expireInfo.textContent = 'Token has expired';
            logoutButtonHolder.innerHTML = ``
            clearInterval(interval);
        }
    };

    // Initial call to display time immediately
    updateTime();

    if (interval) {
        clearInterval(interval);
    }

    // Update every second
    interval = setInterval(updateTime, 1000);
};