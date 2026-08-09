/**
 * Users (admin) - everyone registered on the platform, filterable by role.
 * Deletion rules mirror the backend: an admin may remove customers and delivery
 * men, but never another admin and never their own account.
 */
'use strict';

App.register('/admin-users', {
    roles: ['ADMIN'],

    async render() {
        UI.render(UI.el('h1', {}, 'Users'), UI.loading());

        let roleFilter = 'ALL';

        const initials = (name) =>
            (name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0].toUpperCase()).join('') || '?';
        const ROLE_TONE = { ADMIN: 'err', DELIVERYMAN: 'info', CUSTOMER: 'ok' };
        const roleChip = (role) => UI.el('span', { class: 'chip ' + (ROLE_TONE[role] || '') },
            (role || 'UNKNOWN').replace(/_/g, ' '));

        const TABS = [
            ['ALL', () => true],
            ['CUSTOMERS', u => u.role === 'CUSTOMER'],
            ['DELIVERY MEN', u => u.role === 'DELIVERYMAN'],
            ['ADMINS', u => u.role === 'ADMIN']
        ];

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Users'),
                UI.el('div', { class: 'sub' }, 'Every account registered on the platform')));

        const stats = UI.el('div', { class: 'stats-row' });
        const usersCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Users'));

        const drawUsers = async () => {
            const users = await API.call('/users');
            const count = (role) => users.filter(u => u.role === role).length;
            stats.replaceChildren(
                UI.el('div', { class: 'stat-card' },
                    UI.el('div', { class: 'dot brand' }, Icon.of('users', 18)),
                    UI.el('div', {}, UI.el('div', { class: 'num' }, String(users.length)),
                        UI.el('div', { class: 'cap' }, 'Total users'))),
                UI.el('div', { class: 'stat-card' },
                    UI.el('div', { class: 'dot ok' }, Icon.of('utensils', 18)),
                    UI.el('div', {}, UI.el('div', { class: 'num' }, String(count('CUSTOMER'))),
                        UI.el('div', { class: 'cap' }, 'Customers'))),
                UI.el('div', { class: 'stat-card' },
                    UI.el('div', { class: 'dot info' }, Icon.of('bike', 18)),
                    UI.el('div', {}, UI.el('div', { class: 'num' }, String(count('DELIVERYMAN'))),
                        UI.el('div', { class: 'cap' }, 'Delivery men'))),
                UI.el('div', { class: 'stat-card' },
                    UI.el('div', { class: 'dot err' }, Icon.of('key', 18)),
                    UI.el('div', {}, UI.el('div', { class: 'num' }, String(count('ADMIN'))),
                        UI.el('div', { class: 'cap' }, 'Admins'))));

            const matches = TABS.find(t => t[0] === roleFilter) || TABS[0];
            const shown = users.filter(matches[1]);

            const pills = UI.el('div', { class: 'pills' });
            for (const [label] of TABS) {
                pills.append(UI.el('button', {
                    class: label === roleFilter ? 'on' : '',
                    onclick: () => { roleFilter = label; drawUsers().catch(UI.error); }
                }, label));
            }

            usersCard.replaceChildren(UI.el('h2', {}, 'Users (' + shown.length + ')'), pills);
            if (!shown.length) {
                usersCard.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('users', 34)), 'No users in this view.'));
                return;
            }
            const table = UI.el('table', {},
                UI.el('tr', {}, UI.el('th', {}, 'ID'), UI.el('th', {}, 'Name'), UI.el('th', {}, 'Contact'),
                    UI.el('th', {}, 'Role'), UI.el('th', {}, '')));
            for (const u of shown) {
                const self = u.id === Auth.userId;
                // Admins are protected: no delete for them and none for the signed-in account.
                const canDelete = u.role !== 'ADMIN' && !self;
                table.append(UI.el('tr', {},
                    UI.el('td', {}, UI.el('b', {}, u.code || '-')),
                    UI.el('td', {}, UI.el('div', { style: 'display:flex;align-items:center;gap:10px' },
                        UI.el('div', { class: 'avatar sm' }, initials(u.name)),
                        UI.el('b', {}, u.name || '-') , self ? UI.el('span', { class: 'chip' }, 'you') : null)),
                    UI.el('td', { class: 'muted' }, (u.email || '-') + (u.phone ? ' · ' + u.phone : '')),
                    UI.el('td', {}, roleChip(u.role)),
                    UI.el('td', { class: 'right' }, canDelete ? UI.el('button', {
                        class: 'btn-danger btn-small',
                        onclick: async () => {
                            if (!confirm('Delete ' + (u.code ? u.code + ' · ' : '') + (u.name || u.id) + '?')) return;
                            try {
                                await API.call('/users/' + u.id, { method: 'DELETE' });
                                UI.toast('User deleted', 'ok');
                                await drawUsers();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Delete') : null)));
            }
            usersCard.append(table);
        };

        UI.render(head, stats, usersCard);
        try {
            await drawUsers();
        } catch (err) {
            UI.render(head,
                UI.el('p', { class: 'muted' }, 'Could not load users.'));
            UI.error(err);
        }
    }
});
