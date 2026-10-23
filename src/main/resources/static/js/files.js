/*
 * PatientVault file uploads and image preview. One file per request (spec 7.3), processed one after the other so
 * folder trees are created in order; per-file progress, retry for failures, drag-and-drop of files and folders.
 * No eval and no inline handlers; all texts come from data-pv-msg-* attributes rendered by the server.
 */
(function () {
    'use strict';

    var queue = [];
    var running = false;
    var failedItems = [];
    var stats = { ok: 0, failed: 0 };

    function panel() { return document.getElementById('pv-files'); }
    function msg(key) {
        var p = panel();
        return p ? (p.getAttribute('data-pv-msg-' + key) || '') : '';
    }
    function csrfHeaders() {
        var app = document.getElementById('pv-app');
        try { return JSON.parse(app.getAttribute('hx-headers') || '{}'); } catch (e) { return {}; }
    }
    function toast(message, level) {
        document.body.dispatchEvent(new CustomEvent('showToast', { detail: { message: message, level: level } }));
    }

    // ---- queue and rows ---------------------------------------------------------------------

    function enqueue(items) {
        var p = panel();
        if (!p || !items.length) { return; }
        var folder = p.getAttribute('data-pv-folder') || '';
        var url = p.getAttribute('data-pv-upload-url');
        items.forEach(function (item) {
            item.folder = folder;
            item.url = url;
            item.label = item.relativePath ? item.relativePath : item.file.name;
            addRow(item);
            setStatus(item, msg('queued'));
            queue.push(item);
        });
        pump();
    }

    function addRow(item) {
        var list = document.querySelector('[data-pv-upload-list]');
        if (!list) { return; }
        var li = document.createElement('li');
        var name = document.createElement('span');
        name.className = 'pv-up-name';
        name.textContent = item.label;
        var status = document.createElement('span');
        status.className = 'small text-body-secondary';
        var progress = document.createElement('progress');
        progress.max = 100;
        progress.value = 0;
        li.appendChild(name);
        li.appendChild(status);
        li.appendChild(progress);
        list.appendChild(li);
        item.row = { li: li, status: status, progress: progress };
    }

    function setStatus(item, text) {
        if (item.row) { item.row.status.textContent = text; }
    }

    function markFailed(item, message) {
        item.lastError = message;
        failedItems.push(item);
        if (!item.row) { return; }
        item.row.progress.remove();
        setStatus(item, msg('failed'));
        var error = document.createElement('span');
        error.className = 'pv-up-error small';
        error.textContent = message;
        var retry = document.createElement('button');
        retry.type = 'button';
        retry.className = 'btn btn-outline-secondary btn-sm';
        retry.textContent = msg('retry');
        retry.addEventListener('click', function () {
            failedItems = failedItems.filter(function (f) { return f !== item; });
            item.row.li.remove();
            item.row = null;
            enqueue([item]);
        });
        item.row.li.appendChild(error);
        item.row.li.appendChild(retry);
    }

    function pump() {
        if (running) { return; }
        var item = queue.shift();
        if (!item) { finish(); return; }
        running = true;
        send(item, function (ok) {
            running = false;
            if (ok) { stats.ok++; } else { stats.failed++; }
            pump();
        });
    }

    function finish() {
        if (stats.ok === 0 && stats.failed === 0) { return; }
        var summary = msg('summary').replace('{0}', stats.ok).replace('{1}', stats.failed);
        toast(summary, stats.failed ? 'warning' : 'success');
        var uploaded = stats.ok > 0;
        stats = { ok: 0, failed: 0 };
        if (uploaded && panel() && window.htmx) {
            // refresh the listing in place; failed rows are drawn again afterwards (see htmx:afterSettle)
            window.htmx.ajax('GET', window.location.pathname + window.location.search,
                { target: '#pv-tabs', swap: 'outerHTML' });
        }
    }

    function send(item, done) {
        var max = parseInt((panel() || document.body).getAttribute('data-pv-max-bytes') || '0', 10);
        if (max > 0 && item.file.size > max) {
            markFailed(item, msg('toolarge'));
            done(false);
            return;
        }
        var form = new FormData();
        form.append('file', item.file, item.file.name);
        if (item.folder) { form.append('folderId', item.folder); }
        if (item.relativePath) { form.append('relativePath', item.relativePath); }

        var xhr = new XMLHttpRequest();
        xhr.open('POST', item.url);
        xhr.setRequestHeader('Accept', 'application/json');
        var headers = csrfHeaders();
        Object.keys(headers).forEach(function (name) { xhr.setRequestHeader(name, headers[name]); });
        setStatus(item, msg('uploading'));
        xhr.upload.onprogress = function (event) {
            if (event.lengthComputable && item.row) {
                item.row.progress.value = Math.round(event.loaded * 100 / event.total);
            }
        };
        xhr.onload = function () {
            if (xhr.status >= 200 && xhr.status < 300) {
                if (item.row) { item.row.progress.value = 100; }
                setStatus(item, msg('done'));
                done(true);
                return;
            }
            var text = '';
            try { text = JSON.parse(xhr.responseText).error || ''; } catch (e) { text = ''; }
            markFailed(item, text || msg('failed'));
            done(false);
        };
        xhr.onerror = function () { markFailed(item, msg('network')); done(false); };
        xhr.send(form);
    }

    // ---- choosing files --------------------------------------------------------------------

    function fromInput(input) {
        return Array.prototype.map.call(input.files, function (file) {
            // webkitRelativePath is set for folder selections ("Study/Series 1/IM001.dcm")
            return { file: file, relativePath: file.webkitRelativePath || '' };
        });
    }

    document.addEventListener('change', function (event) {
        var input = event.target.closest ? event.target.closest('[data-pv-file-input], [data-pv-folder-input]') : null;
        if (!input) { return; }
        enqueue(fromInput(input));
        input.value = '';
    });

    // ---- drag and drop (files and folders) -------------------------------------------------

    function readAll(reader) {
        return new Promise(function (resolve, reject) {
            var all = [];
            (function next() {
                reader.readEntries(function (batch) {
                    if (!batch.length) { resolve(all); return; }
                    all = all.concat(Array.prototype.slice.call(batch));
                    next();
                }, reject);
            })();
        });
    }

    function collect(entry, parentPath) {
        if (entry.isFile) {
            return new Promise(function (resolve) {
                entry.file(function (file) {
                    resolve([{ file: file, relativePath: parentPath ? parentPath + '/' + file.name : '' }]);
                }, function () { resolve([]); });
            });
        }
        var path = parentPath ? parentPath + '/' + entry.name : entry.name;
        return readAll(entry.createReader()).then(function (entries) {
            return Promise.all(entries.map(function (child) { return collect(child, path); }));
        }).then(function (parts) {
            return [].concat.apply([], parts);
        });
    }

    document.addEventListener('dragover', function (event) {
        var zone = event.target.closest ? event.target.closest('[data-pv-dropzone]') : null;
        if (!zone) { return; }
        event.preventDefault();
        zone.classList.add('pv-dragover');
    });
    document.addEventListener('dragleave', function (event) {
        var zone = event.target.closest ? event.target.closest('[data-pv-dropzone]') : null;
        if (zone && !zone.contains(event.relatedTarget)) { zone.classList.remove('pv-dragover'); }
    });
    document.addEventListener('drop', function (event) {
        var zone = event.target.closest ? event.target.closest('[data-pv-dropzone]') : null;
        if (!zone) { return; }
        event.preventDefault();
        zone.classList.remove('pv-dragover');
        var transfer = event.dataTransfer;
        if (transfer.items && transfer.items.length && transfer.items[0].webkitGetAsEntry) {
            var entries = Array.prototype.map.call(transfer.items, function (item) { return item.webkitGetAsEntry(); })
                .filter(function (entry) { return entry; });
            Promise.all(entries.map(function (entry) { return collect(entry, ''); })).then(function (parts) {
                enqueue([].concat.apply([], parts));
            });
        } else {
            enqueue(Array.prototype.map.call(transfer.files, function (file) { return { file: file, relativePath: '' }; }));
        }
    });

    // ---- capabilities and re-drawing failed rows after the listing refreshed ---------------

    function applyCapabilities() {
        var probe = document.createElement('input');
        var button = document.querySelector('[data-pv-folder-button]');
        if (button && !('webkitdirectory' in probe)) {
            button.hidden = true;
            var hint = document.querySelector('[data-pv-upload-list]');
            if (hint && msg('nofolders')) {
                var note = document.createElement('p');
                note.className = 'small text-body-secondary mb-0';
                note.textContent = msg('nofolders');
                hint.parentNode.insertBefore(note, hint);
            }
        }
    }

    function redrawFailed() {
        if (!panel() || !failedItems.length) { return; }
        var items = failedItems;
        failedItems = [];
        items.forEach(function (item) {
            item.row = null;
            addRow(item);
            markFailed(item, item.lastError || msg('failed'));
        });
    }

    document.addEventListener('DOMContentLoaded', applyCapabilities);
    document.body.addEventListener('htmx:afterSettle', function () {
        applyCapabilities();
        redrawFailed();
    });

    // ---- image preview ----------------------------------------------------------------------

    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('[data-pv-lightbox="true"]') : null;
        if (!link || !window.bootstrap) { return; }
        var modal = document.getElementById('pv-lightbox');
        if (!modal) { return; }
        event.preventDefault();
        var name = link.getAttribute('data-pv-name') || '';
        document.getElementById('pv-lightbox-img').src = link.getAttribute('href');
        document.getElementById('pv-lightbox-img').alt = name;
        document.getElementById('pv-lightbox-title').textContent = name;
        modal.addEventListener('hidden.bs.modal', function () {
            document.getElementById('pv-lightbox-img').removeAttribute('src');
        }, { once: true });
        window.bootstrap.Modal.getOrCreateInstance(modal).show();
    });
})();
