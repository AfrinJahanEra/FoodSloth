/**
 * Tiny UI toolkit: DOM building, toasts, status chips and formatters.
 * el('div', { class: 'card', onclick: fn }, children...) everywhere,
 * so no screen ever builds HTML by string concatenation.
 */
'use strict';

const UI = {
    el(tag, attrs = {}, ...children) {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(attrs || {})) {
            if (value === null || value === undefined) continue;
            if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
            else if (key === 'value') node.value = value;
            else if (key === 'disabled') node.disabled = !!value;
            else node.setAttribute(key, value);
        }
        for (const child of children.flat()) {
            if (child === null || child === undefined) continue;
            node.append(child.nodeType ? child : document.createTextNode(child));
        }
        return node;
    },

    toast(message, kind = '') {
        const box = document.getElementById('toasts');
        const t = UI.el('div', { class: 'toast ' + kind }, message);
        box.append(t);
        setTimeout(() => t.remove(), 4200);
    },

    /** Status badge with a colour that matches the meaning, not the string. */
    chip(status) {
        const tone = ({
            DELIVERED: 'ok', CONFIRMED: 'ok', SUCCEEDED: 'ok', READY: 'ok', ACCEPTED: 'ok',
            OUT_FOR_DELIVERY: 'info', PREPARING: 'info', QUEUED: 'info', ASSIGNED: 'info',
            PICKED_UP: 'info', PENDING_PAYMENT: 'warn', PENDING_ASSIGNMENT: 'warn',
            AWAITING_PAYMENT: 'warn', REFUNDED: 'warn', ONLINE: 'ok',
            PAYMENT_FAILED: 'err', FAILED: 'err', CANCELLED: 'err', REJECTED: 'err',
            UNAVAILABLE: 'err', OFFLINE: '', DECLINED: 'err'
        })[status] || '';
        return UI.el('span', { class: 'chip ' + tone }, (status || 'UNKNOWN').replace(/_/g, ' '));
    },

    /** Prices on the wire are major units (taka). */
    money(value, currency = 'BDT') {
        if (value === null || value === undefined) return '-';
        return currency + ' ' + Number(value).toFixed(2);
    },

    time(iso) {
        if (!iso) return '-';
        return new Date(iso).toLocaleString();
    },

    /** Renders into #view, clearing whatever was there. */
    render(...nodes) {
        const view = document.getElementById('view');
        view.replaceChildren(...nodes);
        return view;
    },

    loading(text = 'Loading...') {
        return UI.el('p', { class: 'muted' }, text);
    },

    error(err) {
        UI.toast(err.message || 'Something went wrong', 'err');
    },

    /** Runs fn with an interval while its view is on screen. Returns stop(). */
    poll(fn, ms) {
        let stopped = false;
        let timer = null;
        const tick = async () => {
            if (stopped) return;
            try { await fn(); } catch (e) { /* keep polling through transient errors */ }
            if (!stopped) timer = setTimeout(tick, ms);
        };
        timer = setTimeout(tick, 0);
        return () => { stopped = true; clearTimeout(timer); };
    }
};
