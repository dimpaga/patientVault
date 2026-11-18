/*
 * Appointments page: switching the time window (Today / Next 7 days / This month).
 * The highlight of the glass segmented control slides to the chosen option and the list frosts over until the new
 * one arrives. Everything is a CSS transition or animation, so a new click simply re-targets whatever is running
 * (interruptible); the superseded request is cancelled by hx-sync="...:replace" on the links.
 * No eval and no inline handlers. Both the htmx 4 ("htmx:after:settle") and htmx 2 ("htmx:afterSettle") event names are handled.
 */
(function () {
    'use strict';

    var initialised = new WeakSet();
    var pending = null;      // the window the doctor clicked last, until its list has arrived

    function agenda() { return document.getElementById('pv-agenda'); }

    function place(box) {
        var active = box.querySelector('.nav-link.active');
        if (!active) { return; }
        box.style.setProperty('--pv-thumb-x', active.offsetLeft + 'px');
        box.style.setProperty('--pv-thumb-w', active.offsetWidth + 'px');
    }

    function setActive(box, value) {
        Array.prototype.forEach.call(box.querySelectorAll('.nav-link'), function (link) {
            var on = link.getAttribute('data-pv-range-value') === value;
            link.classList.toggle('active', on);
            if (on) { link.setAttribute('aria-current', 'page'); } else { link.removeAttribute('aria-current'); }
        });
        place(box);
    }

    // a control that is new in the DOM (first load, history restore) is placed without animating
    function init() {
        Array.prototype.forEach.call(document.querySelectorAll('[data-pv-range]'), function (box) {
            if (initialised.has(box)) { return; }
            initialised.add(box);
            box.classList.remove('pv-range-ready');
            place(box);
            void box.offsetWidth;
            window.requestAnimationFrame(function () { box.classList.add('pv-range-ready'); });
        });
    }

    // the list on screen is the truth: after it arrives the highlight follows it (unless a newer click is pending)
    function syncToList() {
        var list = agenda();
        var box = document.querySelector('[data-pv-range]');
        if (!list || !box) { return; }
        var range = list.getAttribute('data-range');
        if (pending && pending !== range) { return; }
        pending = null;
        if (!box.querySelector('.nav-link.active[data-pv-range-value="' + range + '"]')) { setActive(box, range); }
    }

    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('[data-pv-range] .nav-link') : null;
        if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.button) { return; }
        var box = link.closest('[data-pv-range]');
        var value = link.getAttribute('data-pv-range-value');
        pending = value;
        clicks++;
        setActive(box, value);
        var list = agenda();
        if (list) { list.classList.add('pv-agenda-loading'); }
    });

    // if the last request ended without replacing the list (error), thaw it and show the window that is really on screen
    var clicks = 0;
    function requestEnded(event) {
        if (!event.target || !event.target.closest || !event.target.closest('[data-pv-range]')) { return; }
        var mine = clicks;
        window.setTimeout(function () {
            var list = agenda();
            if (mine !== clicks || !list || !list.classList.contains('pv-agenda-loading')) { return; }
            pending = null;
            list.classList.remove('pv-agenda-loading');
            syncToList();
        }, 400);
    }

    function settled() { init(); syncToList(); }

    ['htmx:after:settle', 'htmx:afterSettle'].forEach(function (name) { document.addEventListener(name, settled); });
    ['htmx:after:request', 'htmx:afterRequest'].forEach(function (name) { document.addEventListener(name, requestEnded); });
    window.addEventListener('popstate', function () { window.setTimeout(settled, 50); });
    window.addEventListener('resize', function () {
        Array.prototype.forEach.call(document.querySelectorAll('[data-pv-range]'), place);
    });
    document.addEventListener('DOMContentLoaded', init);
    init();
})();
