/* Runs synchronously in <head> to apply the saved theme before first paint. Stores no personal data. */
(function () {
    try {
        var theme = localStorage.getItem('pv-theme');
        if (theme !== 'light' && theme !== 'dark') {
            theme = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }
        document.documentElement.setAttribute('data-bs-theme', theme);
        if (localStorage.getItem('pv-sidebar') === 'collapsed') {
            document.documentElement.classList.add('pv-sidebar-collapsed');
        }
    } catch (e) {
        /* storage unavailable: keep defaults */
    }
})();
