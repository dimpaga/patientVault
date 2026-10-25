/* PatientVault client behaviour. No eval, no inline handlers; works with htmx hx-boost body swaps. */
(function () {
    'use strict';

    var root = document.documentElement;

    function store(key, value) {
        try { localStorage.setItem(key, value); } catch (e) { /* ignore */ }
    }

    document.addEventListener('click', function (event) {
        var themeToggle = event.target.closest('[data-pv-theme-toggle]');
        if (themeToggle) {
            var next = root.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-bs-theme', next);
            store('pv-theme', next);
            return;
        }
        if (event.target.closest('[data-pv-print]')) {
            window.print();
            return;
        }
        if (event.target.closest('[data-pv-sidebar-toggle]')) {
            var collapsed = root.classList.toggle('pv-sidebar-collapsed');
            store('pv-sidebar', collapsed ? 'collapsed' : 'expanded');
        }
    });

    // Search panel opened from the magnifier in the top bar (the link itself goes to /patients without JavaScript).
    function searchPanel() { return document.querySelector('[data-pv-search-panel]'); }

    function setSearch(open) {
        var panel = searchPanel();
        if (!panel) { return; }
        panel.hidden = !open;
        document.querySelectorAll('[data-pv-search-toggle]').forEach(function (button) {
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        });
        var input = panel.querySelector('[data-pv-search-input]');
        if (open && input) { input.focus(); input.select(); }
    }

    document.addEventListener('click', function (event) {
        var toggle = event.target.closest('[data-pv-search-toggle]');
        if (toggle) {
            event.preventDefault();
            var panel = searchPanel();
            setSearch(panel ? panel.hidden : false);
            return;
        }
        if (event.target.closest('[data-pv-search-close]') || event.target === searchPanel()) {
            setSearch(false);
        }
    });

    document.addEventListener('keydown', function (event) {
        var panel = searchPanel();
        if (event.key === 'Escape' && panel && !panel.hidden) {
            setSearch(false);
            var toggle = document.querySelector('[data-pv-search-toggle]');
            if (toggle) { toggle.focus(); }
        }
    });

    var LEVELS = ['success', 'danger', 'warning', 'info', 'secondary'];

    function showToast(message, level) {
        var container = document.getElementById('pv-toasts');
        if (!container || !window.bootstrap || !message) { return; }
        var tone = LEVELS.indexOf(level) >= 0 ? level : 'secondary';

        var toast = document.createElement('div');
        toast.className = 'toast align-items-center border-0 text-bg-' + tone;
        toast.setAttribute('role', tone === 'danger' ? 'alert' : 'status');
        toast.setAttribute('aria-atomic', 'true');

        var wrapper = document.createElement('div');
        wrapper.className = 'd-flex';
        var body = document.createElement('div');
        body.className = 'toast-body';
        body.textContent = message;
        var close = document.createElement('button');
        close.type = 'button';
        close.className = 'btn-close btn-close-white me-2 m-auto';
        close.setAttribute('data-bs-dismiss', 'toast');
        close.setAttribute('aria-label', container.getAttribute('data-pv-close') || 'Close');
        wrapper.appendChild(body);
        wrapper.appendChild(close);
        toast.appendChild(wrapper);
        container.appendChild(toast);

        toast.addEventListener('hidden.bs.toast', function () { toast.remove(); });
        new window.bootstrap.Toast(toast, { delay: 6000 }).show();
    }

    // Server-triggered toasts: HX-Trigger: {"showToast": {"message": "...", "level": "success"}}
    document.body.addEventListener('showToast', function (event) {
        var detail = event.detail || {};
        showToast(detail.message, detail.level);
    });

    function errorMessage() {
        var container = document.getElementById('pv-toasts');
        return container ? container.getAttribute('data-pv-error') : null;
    }

    document.body.addEventListener('htmx:responseError', function (event) {
        // 401 is answered with HX-Redirect to the login page by the server
        if (event.detail.xhr && event.detail.xhr.status !== 401) { showToast(errorMessage(), 'danger'); }
    });
    document.body.addEventListener('htmx:sendError', function () { showToast(errorMessage(), 'danger'); });

    // Focus management: after a boosted navigation move focus to <main>; honour data-pv-focus in swaps.
    document.body.addEventListener('htmx:afterSettle', function (event) {
        var target = event.detail.target;
        var focusTarget = target && target.querySelector ? target.querySelector('[data-pv-focus]') : null;
        if (!focusTarget && event.detail.boosted) { focusTarget = document.getElementById('pv-main'); }
        if (focusTarget) { focusTarget.focus({ preventScroll: false }); }
    });
})();
