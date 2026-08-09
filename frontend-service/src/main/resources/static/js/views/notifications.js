/**
 * Notification inbox + delivery-channel settings (device token, email/SMS,
 * per-channel switches).
 */
'use strict';

App.register('/notifications', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Notifications'), UI.loading());

        const [inbox, prefs] = await Promise.all([
            API.call('/notifications/me'),
            API.call('/notifications/preferences').catch(() => ({}))
        ]);

        // ---- Inbox ----
        const inboxCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Inbox'));
        const drawInbox = (items) => {
            inboxCard.replaceChildren(UI.el('h2', {}, 'Inbox'));
            if (!items.length) {
                inboxCard.append(UI.el('p', { class: 'muted' }, 'Nothing here yet - order something!'));
                return;
            }
            for (const n of items) {
                inboxCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', { style: n.read ? '' : 'font-weight:800' }, n.title || n.type),
                        UI.el('div', { class: 'muted' }, n.body || ''),
                        UI.el('div', { class: 'muted' }, UI.time(n.createdAt) + ' · ' + (n.channel || '') +
                            (n.orderId ? ' · order ' + n.orderId.slice(0, 8) + '…' : ''))),
                    n.read ? UI.chip('READ') : UI.el('button', {
                        class: 'btn-ghost btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/notifications/${n.id}/read`, { method: 'PATCH', body: {} });
                                n.read = true;
                                drawInbox(items);
                                App.refreshUnreadBadge();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Mark read')));
            }
            inboxCard.append(UI.el('div', { class: 'form-actions' },
                UI.el('button', {
                    class: 'btn-ghost btn-small',
                    onclick: async () => {
                        try {
                            await API.call('/notifications/me/read-all', { method: 'PATCH', body: {} });
                            items.forEach(n => { n.read = true; });
                            drawInbox(items);
                            App.refreshUnreadBadge();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Mark all read')));
        };
        drawInbox(inbox);

        // ---- Settings ----
        const push = UI.el('input', { type: 'checkbox' }); push.checked = prefs.pushEnabled !== false;
        const email = UI.el('input', { type: 'checkbox' }); email.checked = prefs.emailEnabled !== false;
        const sms = UI.el('input', { type: 'checkbox' }); sms.checked = !!prefs.smsEnabled;
        const marketing = UI.el('input', { type: 'checkbox' }); marketing.checked = !!prefs.marketingOptIn;

        const savePrefs = async () => {
            try {
                await API.call('/notifications/preferences', {
                    method: 'PUT',
                    body: {
                        pushEnabled: push.checked, emailEnabled: email.checked,
                        smsEnabled: sms.checked, marketingOptIn: marketing.checked
                    }
                });
                UI.toast('Preferences saved', 'ok');
            } catch (err) { UI.error(err); }
        };

        const settingsCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'How we reach you'),
            UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Push notifications'), push),
            UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Email receipts'), email),
            UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'SMS alerts'), sms),
            UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Promotional messages'), marketing),
            UI.el('div', { class: 'form-actions' },
                UI.el('button', { onclick: savePrefs }, 'Save preferences')));

        // ---- Contact + device ----
        const contactEmail = UI.el('input', { type: 'email', value: prefs.email || '' });
        const contactPhone = UI.el('input', { type: 'text', value: prefs.phone || '' });
        const deviceToken = UI.el('input', { type: 'text', placeholder: 'token from FCM/APNs' });

        const contactCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Contact details & device'),
            UI.el('label', {}, 'Receipt email'), contactEmail,
            UI.el('label', {}, 'Alert phone'), contactPhone,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                class: 'btn-ghost',
                onclick: async () => {
                    try {
                        await API.call('/notifications/contact', {
                            body: { email: contactEmail.value.trim() || null, phone: contactPhone.value.trim() || null }
                        });
                        UI.toast('Contact details saved', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Save contact')),
            UI.el('label', {}, 'Push device token'), deviceToken,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                class: 'btn-ghost',
                onclick: async () => {
                    try {
                        await API.call('/notifications/devices', { body: { deviceToken: deviceToken.value.trim() } });
                        UI.toast('Device registered', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Register device')));

        UI.render(UI.el('h1', {}, 'Notifications'), inboxCard,
            UI.el('div', { class: 'row' }, settingsCard, contactCard));
    }
});
