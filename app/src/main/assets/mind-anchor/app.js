// DOM Elements
const timerDisplay = document.getElementById('timer-display');
const timerToggleBtn = document.getElementById('timer-toggle-btn');
const timerResetBtn = document.getElementById('timer-reset-btn');
const timerMinutesInput = document.getElementById('timer-minutes-input');
const moodBtns = document.querySelectorAll('.mood-btn');
const moodSelectedLabel = document.getElementById('mood-selected-label');
const logInput = document.getElementById('log-input');
const logSubmitBtn = document.getElementById('log-submit-btn');
const anchorTimeline = document.getElementById('anchor-timeline');
const groundingBtn = document.getElementById('grounding-btn');
const groundingModal = document.getElementById('grounding-modal');
const closeModalBtn = document.getElementById('close-modal-btn');
const groundingText = document.getElementById('grounding-text');
const groundingSteps = document.getElementById('grounding-steps');

// Journal DOM Elements
const journalText = document.getElementById('journal-text');
const journalSaveBtn = document.getElementById('journal-save-btn');
const journalTimeline = document.getElementById('journal-timeline');

// State
let timerInterval = null;
let isTimerRunning = false;
let timeLeft = 25 * 60;

// --- HELPER: Try to notify Android WebView interface ---
function notifyAndroid(key, value) {
    try {
        if (window.Android && typeof window.Android.savePreference === 'function') {
            window.Android.savePreference(key, value);
        }
    } catch(e) { /* not in Android env */ }
}

// --- FEATURE: Forgiving Study Timer ---
function getCustomMinutes() {
    const val = parseInt(timerMinutesInput.value);
    return (isNaN(val) || val < 1) ? 25 : Math.min(val, 120);
}

function updateTimerDisplay() {
    const m = Math.floor(timeLeft / 60).toString().padStart(2, '0');
    const s = (timeLeft % 60).toString().padStart(2, '0');
    timerDisplay.textContent = `${m}:${s}`;
}

function toggleTimer() {
    if (isTimerRunning) {
        clearInterval(timerInterval);
        isTimerRunning = false;
        timerToggleBtn.textContent = 'Resume Session';
        timerToggleBtn.classList.replace('primary-btn', 'secondary-btn');
    } else {
        // If starting fresh, read the custom minutes
        if (timerToggleBtn.textContent === 'Start Session') {
            timeLeft = getCustomMinutes() * 60;
            updateTimerDisplay();
        }
        isTimerRunning = true;
        timerToggleBtn.textContent = 'Pause Session';
        timerToggleBtn.classList.replace('secondary-btn', 'primary-btn');
        timerMinutesInput.disabled = true;
        timerInterval = setInterval(() => {
            if (timeLeft > 0) {
                timeLeft--;
                updateTimerDisplay();
            } else {
                clearInterval(timerInterval);
                isTimerRunning = false;
                timerToggleBtn.textContent = 'Start Session';
                timerToggleBtn.classList.replace('secondary-btn', 'primary-btn');
                timerMinutesInput.disabled = false;
                addLogEntry(`Completed a ${getCustomMinutes()}-minute focus session! 🎉`);
            }
        }, 1000);
    }
}

function resetTimer() {
    clearInterval(timerInterval);
    isTimerRunning = false;
    timeLeft = getCustomMinutes() * 60;
    updateTimerDisplay();
    timerToggleBtn.textContent = 'Start Session';
    timerToggleBtn.classList.replace('secondary-btn', 'primary-btn');
    timerMinutesInput.disabled = false;
}

// Update display immediately when input changes (only when not running)
timerMinutesInput.addEventListener('input', () => {
    if (!isTimerRunning) {
        timeLeft = getCustomMinutes() * 60;
        updateTimerDisplay();
    }
});

timerToggleBtn.addEventListener('click', toggleTimer);
timerResetBtn.addEventListener('click', resetTimer);

// --- FEATURE: Anchor Log ---
function loadLogs() {
    const logs = JSON.parse(localStorage.getItem('mindAnchorLogs') || '[]');
    anchorTimeline.innerHTML = '';
    logs.forEach(log => renderLogEntry(log));
}

function addLogEntry(text) {
    const trimmed = text.trim();
    if (!trimmed) return;
    const logs = JSON.parse(localStorage.getItem('mindAnchorLogs') || '[]');
    const newLog = {
        id: Date.now(),
        text: trimmed,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };
    logs.unshift(newLog);
    if (logs.length > 50) logs.pop();
    localStorage.setItem('mindAnchorLogs', JSON.stringify(logs));
    renderLogEntry(newLog, true);
    logInput.value = '';
    return newLog.id;
}

function deleteLogEntry(id) {
    let logs = JSON.parse(localStorage.getItem('mindAnchorLogs') || '[]');
    logs = logs.filter(log => log.id !== id);
    localStorage.setItem('mindAnchorLogs', JSON.stringify(logs));
    const el = document.getElementById(`log-${id}`);
    if (el) el.remove();
}

function renderLogEntry(log, isNew = false) {
    const div = document.createElement('div');
    div.className = 'log-item';
    div.id = `log-${log.id}`;
    div.innerHTML = `
        <div class="log-header">
            <div class="log-time">${log.timestamp}</div>
            <button class="log-delete-btn" onclick="deleteLogEntry(${log.id})">🗑️</button>
        </div>
        <div class="log-text">${log.text}</div>
    `;
    if (isNew) {
        anchorTimeline.prepend(div);
    } else {
        anchorTimeline.appendChild(div);
    }
}

logSubmitBtn.addEventListener('click', () => addLogEntry(logInput.value));
logInput.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') addLogEntry(logInput.value);
});

// --- FEATURE: Mood Check-in ---
// Load previously saved mood
function loadMood() {
    const savedMood = localStorage.getItem('todayMood');
    const savedLabel = localStorage.getItem('todayMoodLabel');
    if (savedMood) {
        moodBtns.forEach(btn => {
            if (btn.getAttribute('data-mood') === savedMood) {
                btn.classList.add('selected');
            }
        });
        if (savedLabel) {
            moodSelectedLabel.textContent = `You're feeling: ${savedMood} ${savedLabel}`;
            moodSelectedLabel.style.display = 'block';
        }
    }
}

moodBtns.forEach(btn => {
    btn.addEventListener('click', () => {
        const moodEmoji = btn.getAttribute('data-mood');
        const moodLabel = btn.getAttribute('data-label');

        if (btn.classList.contains('selected')) {
            // Deselect
            btn.classList.remove('selected');
            localStorage.removeItem('todayMood');
            localStorage.removeItem('todayMoodLabel');
            moodSelectedLabel.style.display = 'none';
            notifyAndroid('mood', '--');
        } else {
            // Select this mood
            moodBtns.forEach(b => b.classList.remove('selected'));
            btn.classList.add('selected');
            localStorage.setItem('todayMood', moodEmoji);
            localStorage.setItem('todayMoodLabel', moodLabel);
            moodSelectedLabel.textContent = `You're feeling: ${moodEmoji} ${moodLabel}`;
            moodSelectedLabel.style.display = 'block';
            // Notify Android to save to SharedPreferences for the dashboard
            notifyAndroid('mood', moodEmoji);
        }
    });
});

// --- FEATURE: Thought Journal ---
function loadThoughts() {
    const thoughts = JSON.parse(localStorage.getItem('mindAnchorThoughts') || '[]');
    journalTimeline.innerHTML = '';
    thoughts.forEach(thought => renderThoughtEntry(thought));
}

function saveThought() {
    const text = journalText.value.trim();
    if (!text) return;
    const thoughts = JSON.parse(localStorage.getItem('mindAnchorThoughts') || '[]');
    const newThought = {
        id: Date.now(),
        text: text,
        timestamp: new Date().toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })
    };
    thoughts.unshift(newThought);
    localStorage.setItem('mindAnchorThoughts', JSON.stringify(thoughts));
    renderThoughtEntry(newThought, true);
    journalText.value = '';
}

function deleteThoughtEntry(id) {
    let thoughts = JSON.parse(localStorage.getItem('mindAnchorThoughts') || '[]');
    thoughts = thoughts.filter(t => t.id !== id);
    localStorage.setItem('mindAnchorThoughts', JSON.stringify(thoughts));
    const el = document.getElementById(`thought-${id}`);
    if (el) el.remove();
}

function renderThoughtEntry(thought, isNew = false) {
    const div = document.createElement('div');
    div.className = 'thought-card';
    div.id = `thought-${thought.id}`;
    div.innerHTML = `
        <div class="thought-header">
            <span>${thought.timestamp}</span>
            <button class="log-delete-btn" onclick="deleteThoughtEntry(${thought.id})">🗑️</button>
        </div>
        <div class="log-text">${thought.text.replace(/\n/g, '<br>')}</div>
    `;
    if (isNew) journalTimeline.prepend(div);
    else journalTimeline.appendChild(div);
}

journalSaveBtn.addEventListener('click', saveThought);

// --- FEATURE: Grounding Panic Button ---
let breathingTimeout;
function openGrounding() {
    if (isTimerRunning) toggleTimer();
    groundingModal.classList.remove('hidden');
    groundingSteps.classList.add('hidden');
    groundingText.textContent = "Breathe In...";
    breathingTimeout = setTimeout(() => {
        groundingText.textContent = "Hold...";
        breathingTimeout = setTimeout(() => {
            groundingText.textContent = "Breathe Out...";
            breathingTimeout = setTimeout(() => {
                groundingText.textContent = "You are safe.";
                groundingSteps.classList.remove('hidden');
            }, 4000);
        }, 2000);
    }, 4000);
}
function closeGrounding() {
    clearTimeout(breathingTimeout);
    groundingModal.classList.add('hidden');
}
groundingBtn.addEventListener('click', openGrounding);
closeModalBtn.addEventListener('click', closeGrounding);

// Initialize
updateTimerDisplay();
loadLogs();
loadThoughts();
loadMood();
