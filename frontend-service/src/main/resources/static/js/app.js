/**
 * Router and shell. Views register themselves in App.views; the hash decides
 * which one renders. Nav links and guards depend on the logged-in role.
 */
'use strict';

const App = {
    views: {},
    cleanup: null,

    register(path, view) { App.views[path] = view; },

    /** path -> { link label, render(params), roles: null (any) or [...] } */
    nav() {
        return [
            { path: '/', label: 'Menu', roles: ['CUSTOMER'] },
            { path: '/cart', label: 'Cart', roles: ['CUSTOMER'] },
            { path: '/orders', label: 'Orders', roles: ['CUSTOMER'] },
            { path: '/notifications', label: 'Inbox', roles: ['CUSTOMER'] },
            { path: '/kitchen', label: 'Kitchen', roles: ['ADMIN'] },
            { path: '/rider', label: 'Rider', roles: ['DELIVERYMAN'] }
        ];
    },

    drawNav(activePath) {
        const nav = document.getElementById('nav');
        nav.replaceChildren();
        if (!Auth.loggedIn) return;
        for (const item of App.nav()) {
            if (item.roles && !item.roles.includes(Auth.role)) continue;
            const link = UI.el('a', {
                href: '#' + item.path,
                class: activePath === item.path ? 'active' : '',
                id: 'nav-' + item.path.replace(/\W/g, '')
            }, item.label);
            nav.append(link);
        }
    },

    drawUserBox() {
        const box = document.getElementById('user-box');
        box.replaceChildren();
        if (!Auth.loggedIn) return;
        const user = Auth.user || {};
        box.append(
            UI.el('span', { class: 'who' },
                UI.el('b', {}, user.name || 'user'), ' · ' + (user.role || '')),
            UI.el('button', { class: 'btn-ghost btn-small', onclick: () => Auth.logout() }, 'Log out')
        );
    },

    async refreshUnreadBadge() {
        if (!Auth.loggedIn || Auth.role !== 'CUSTOMER') return;
        try {
            const res = await API.call('/notifications/me/unread-count');
            const count = res.unread !== undefined ? res.unread : Object.values(res)[0] || 0;
            const link = document.getElementById('nav-notifications');
            if (!link) return;
            link.querySelector('.badge')?.remove();
            if (count > 0) link.append(UI.el('span', { class: 'badge' }, String(count)));
        } catch { /* badge is cosmetic - never let it break the shell */ }
    },

    async route() {
        if (App.cleanup) { App.cleanup(); App.cleanup = null; }

        const raw = location.hash.slice(1) || '/';
        const [path, ...params] = raw.split('/').filter(Boolean);
        const key = '/' + (path || '');

        let view = App.views[key];
        if (!view) view = Auth.loggedIn ? App.views[Auth.home().slice(1)] : App.views['/login'];

        // Guard: private screens need a session; logged-in users never see login/signup.
        if (view && view.auth === false && Auth.loggedIn && key !== Auth.home().slice(1)) {
            location.hash = Auth.home();
            return;
        }
        if ((!view || view.auth !== false) && !Auth.loggedIn) {
            location.hash = '#/login';
            return;
        }
        if (view && view.roles && !view.roles.includes(Auth.role)) {
            location.hash = Auth.home();
            return;
        }

        App.drawNav('/' + (path || ''));
        App.drawUserBox();
        try {
            App.cleanup = await view.render(...params) || null;
        } catch (err) {
            UI.error(err);
        }
        App.refreshUnreadBadge();
    }
};

window.addEventListener('hashchange', App.route);
document.addEventListener('DOMContentLoaded', () => {
    if (!location.hash) location.hash = Auth.loggedIn ? Auth.home() : '#/login';
    App.route();
});
