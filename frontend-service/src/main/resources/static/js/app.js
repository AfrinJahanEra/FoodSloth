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
            { path: '/', label: 'Home', icon: 'home', roles: ['CUSTOMER'] },
            { path: '/cart', label: 'Cart', icon: 'cart', roles: ['CUSTOMER'] },
            { path: '/orders', label: 'Orders', icon: 'receipt', roles: ['CUSTOMER'] },
            { path: '/search', label: '', icon: 'search', title: 'Search food', roles: ['CUSTOMER'] },
            { path: '/notifications', label: 'Inbox', icon: 'bell', roles: ['CUSTOMER', 'ADMIN', 'DELIVERYMAN'] },
            { path: '/kitchen', label: 'Orders', icon: 'chefhat', roles: ['ADMIN'] },
            { path: '/admin-menu', label: 'Menu', icon: 'utensils', roles: ['ADMIN'] },
            { path: '/admin-users', label: 'Users', icon: 'users', roles: ['ADMIN'] },
            { path: '/admin-payments', label: 'Payments', icon: 'creditcard', roles: ['ADMIN'] },
            { path: '/profile', label: 'Profile', icon: 'user', roles: ['CUSTOMER', 'ADMIN'] },
            { path: '/rider', label: 'Rider', icon: 'bike', roles: ['DELIVERYMAN'] }
        ];
    },

    drawNav(activePath) {
        const nav = document.getElementById('nav');
        nav.replaceChildren();
        if (!Auth.loggedIn) {
            // Public landing: guests only get a brand link so the top bar stays calm.
            nav.append(UI.el('a', { href: '#/', class: activePath === '/' ? 'active' : '' }, 'Home'));
            return;
        }
        for (const item of App.nav()) {
            if (item.roles && !item.roles.includes(Auth.role)) continue;
            const attrs = {
                href: '#' + item.path,
                class: activePath === item.path ? 'active' : '',
                id: 'nav-' + item.path.replace(/\W/g, '')
            };
            if (item.title) attrs.title = item.title;
            const link = UI.el('a', attrs);
            if (item.icon) link.append(Icon.of(item.icon, 16));
            if (item.label) link.append(item.label);
            nav.append(link);
        }
    },

    drawUserBox() {
        const box = document.getElementById('user-box');
        box.replaceChildren();
        if (!Auth.loggedIn) {
            // Guest top bar: sign up first (filled), log in second (outline).
            box.append(
                UI.el('a', { class: 'btn btn-small', href: '#/signup' }, 'Sign up'),
                UI.el('a', { class: 'btn btn-ghost btn-small', href: '#/login' }, 'Log in')
            );
            return;
        }
        const user = Auth.user || {};
        // Admins get a purely modular top bar - no personal profile chip.
        if (Auth.role !== 'ADMIN') {
            box.append(UI.el('span', { class: 'who' },
                UI.el('b', {}, user.name || 'user'), ' · ' + (user.role || '')));
        }
        box.append(
            UI.el('button', { class: 'btn-ghost btn-small', onclick: () => Auth.logout() }, 'Log out')
        );
    },

    async refreshUnreadBadge() {
        // Every role now has an inbox: customers (orders), admins (order placed / delivered),
        // deliverymen (new assignments).
        if (!Auth.loggedIn) return;
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
        if (!view) view = Auth.loggedIn ? App.views[Auth.home().slice(1)] : App.views['/'];

        // Guard: private screens need a session; logged-in users never see login/signup.
        if (view && view.auth === false && Auth.loggedIn && key !== Auth.home().slice(1)) {
            location.hash = Auth.home();
            return;
        }
        // '/' is the public landing page - guests see hero + menu without a session.
        if ((!view || view.auth !== false) && !Auth.loggedIn && key !== '/') {
            location.hash = '#/';
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
    if (!location.hash) location.hash = Auth.loggedIn ? Auth.home() : '#/';
    App.route();
});
