/**
 * Notification inbox + push settings. Push is the only channel on the platform,
 * so all that matters here is the device tokens and the push switch.
 */
'use strict';

App.register('/notifications', {
    roles: ['CUSTOMER', 'ADMIN', 'DELIVERYMAN'],

    async render() {
        UI.render(UI.el('h1', {}, 'Notifications'), UI.loading());

        const [inbox, prefs] = await Promise.all([
            API.call('/notifications/me'),
            API.call('/notifications/preferences').catch(() => ({}))
        ]);

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Notifications'),
                UI.el('div', { class: 'sub' }, 'Push notifications only - order, payment and delivery updates')));

        // ---- Inbox ----
        const TYPE_ICON = {
            ORDER_CONFIRMED: 'checkcircle', ORDER_ACCEPTED: 'chefhat', ORDER_READY: 'package', ORDER_DELIVERED: 'gift',
            ORDER_CANCELLED: 'xcircle', ORDER_REJECTED: 'ban', PAYMENT_RECEIPT: 'receipt', PAYMENT_FAILED: 'creditcard',
            RIDER_ASSIGNED: 'bike', OUT_FOR_DELIVERY: 'truck', RIDER_ARRIVING: 'mappin', PROMOTION: 'megaphone'
        };

        const inboxCard = UI.el('div', { class: 'card' });
        const drawInbox = (items) => {
            inboxCard.replaceChildren(UI.el('h2', {}, 'Inbox (' + items.length + ')'));
            if (!items.length) {
                inboxCard.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('bell', 34)),
                    Auth.role === 'ADMIN' ? 'Nothing here yet - new orders and finished deliveries will appear.'
                        : Auth.role === 'DELIVERYMAN' ? 'Nothing here yet - new delivery assignments will appear.'
                            : 'Nothing here yet - order something!'));
                return;
            }
            for (const n of items) {
                const actions = [];
                if (!n.read) {
                    actions.push(UI.el('button', {
                        class: 'btn-ghost btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/notifications/${n.id}/read`, { method: 'PATCH', body: {} });
                                n.read = true;
                                drawInbox(items);
                                App.refreshUnreadBadge();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Mark read'));
                }
                inboxCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', { style: 'display:flex;align-items:flex-start;gap:12px' },
                        UI.el('div', { class: 'dot ' + (n.read ? '' : 'brand') }, Icon.of(TYPE_ICON[n.type] || 'bell', 16)),
                        UI.el('div', {},
                            UI.el('div', { style: 'display:flex;align-items:center;gap:8px' },
                                UI.el('b', { style: n.read ? 'font-weight:600' : 'font-weight:800' }, n.title || n.type),
                                n.read ? null : UI.el('span', { class: 'chip err' }, 'New')),
                            UI.el('div', { class: 'muted' }, n.body || ''),
                            UI.el('div', { class: 'muted' }, UI.time(n.createdAt)))),
                    UI.el('div', { style: 'display:flex;gap:8px' }, ...actions)));
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

        // ---- Push preferences ----
        const push = UI.el('input', { type: 'checkbox' }); push.checked = prefs.pushEnabled !== false;

        const settingsCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Push settings'),
            UI.el('div', { class: 'line-item' },
                UI.el('div', {},
                    UI.el('b', {}, 'Push notifications'),
                    UI.el('div', { class: 'muted' }, 'Order, payment and delivery updates on your device')),
                push),
            UI.el('div', { class: 'form-actions' },
                UI.el('button', {
                    onclick: async () => {
                        try {
                            await API.call('/notifications/preferences', {
                                method: 'PUT',
                                body: { pushEnabled: push.checked }
                            });
                            UI.toast('Preferences saved', 'ok');
                        } catch (err) { UI.error(err); }
                    }
                }, 'Save preferences')));

        // ---- Device tokens ----
        const deviceToken = UI.el('input', { type: 'text', placeholder: 'Token from FCM / APNs' });
        const tokensBox = UI.el('div', {});
        const mask = (token) => token.length <= 6 ? '•••' : '•••' + token.slice(-6);
        const drawTokens = () => {
            tokensBox.replaceChildren();
            const tokens = prefs.deviceTokens || [];
            if (!tokens.length) {
                tokensBox.append(UI.el('p', { class: 'muted' }, 'No device registered yet - push needs a token.'));
                return;
            }
            for (const token of tokens) {
                tokensBox.append(UI.el('div', { class: 'line-item' },
                    UI.el('span', { style: 'display:inline-flex;align-items:center;gap:7px' },
                        Icon.of('phone', 15), mask(token)),
                    UI.el('button', {
                        class: 'btn-danger btn-small',
                        onclick: async () => {
                            try {
                                const updated = await API.call('/notifications/devices/' + encodeURIComponent(token), { method: 'DELETE' });
                                prefs.deviceTokens = updated.deviceTokens || [];
                                UI.toast('Device unregistered', 'ok');
                                drawTokens();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Remove')));
            }
        };
        drawTokens();

        const deviceCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Your devices'),
            tokensBox,
            UI.el('label', {}, 'Register a new device'),
            deviceToken,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                class: 'btn-ghost',
                onclick: async () => {
                    const token = deviceToken.value.trim();
                    if (!token) { UI.toast('Paste the device token first', 'err'); return; }
                    try {
                        const updated = await API.call('/notifications/devices', { body: { deviceToken: token } });
                        prefs.deviceTokens = updated.deviceTokens || [];
                        deviceToken.value = '';
                        UI.toast('Device registered', 'ok');
                        drawTokens();
                    } catch (err) { UI.error(err); }
                }
            }, 'Register device')));

        UI.render(head, inboxCard, UI.el('div', { class: 'row' }, settingsCard, deviceCard));
    }
});
