/**
 * Kitchen / admin screen - five sections:
 *   1. live order queue with accept / reject / ready (auto-refresh 5s)
 *   2. menu management (add / edit / toggle availability / delete)
 *   3. restaurant open-close switch and operating hours
 *   4. promotional broadcast
 *   5. riders online right now
 */
'use strict';

App.register('/kitchen', {
    roles: ['ADMIN'],

    async render() {
        UI.render(UI.el('h1', {}, 'Kitchen & admin'), UI.loading());

        // ---------------- 1. Live queue ----------------
        const queueCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Order queue'));
        let statusFilter = '';

        const drawQueue = async () => {
            const path = statusFilter
                ? '/restaurant/kitchen/orders/status/' + statusFilter
                : '/restaurant/kitchen/orders';
            const tickets = await API.call(path);

            queueCard.replaceChildren(UI.el('h2', {}, 'Order queue (' + tickets.length + ')'));

            const tabs = UI.el('div', { class: 'form-actions' });
            for (const s of ['', 'AWAITING_PAYMENT', 'QUEUED', 'ACCEPTED', 'READY', 'REJECTED', 'CANCELLED']) {
                tabs.append(UI.el('button', {
                    class: 'btn-ghost btn-small' + (s === statusFilter ? ' btn-ok' : ''),
                    onclick: () => { statusFilter = s; drawQueue().catch(UI.error); }
                }, s || 'ALL'));
            }
            queueCard.append(tabs);

            if (!tickets.length) {
                queueCard.append(UI.el('p', { class: 'muted' }, 'No tickets in this view.'));
                return;
            }
            for (const t of tickets) {
                const lines = (t.items || []).map(i => `${i.quantity} × ${i.name || i.itemId}`).join(', ');
                const actions = [];
                if (t.status === 'QUEUED') {
                    actions.push(UI.el('button', {
                        class: 'btn-ok btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/accept`, { method: 'PATCH', body: {} });
                                await drawQueue();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Accept'));
                    actions.push(UI.el('button', {
                        class: 'btn-danger btn-small',
                        onclick: async () => {
                            const reason = prompt('Rejection reason:', 'Out of stock');
                            if (reason === null) return;
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/reject`, { method: 'PATCH', body: { reason } });
                                await drawQueue();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Reject'));
                }
                if (t.status === 'ACCEPTED') {
                    actions.push(UI.el('button', {
                        class: 'btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/ready`, { method: 'PATCH', body: {} });
                                UI.toast('Food ready - finding a rider', 'ok');
                                await drawQueue();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Mark ready'));
                }

                queueCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, t.id.slice(0, 8) + '… '), UI.chip(t.status),
                        UI.el('div', { class: 'muted' }, lines),
                        UI.el('div', { class: 'muted' },
                            UI.money(t.grandTotal, t.currency) + ' · ' + (t.note || 'no note') +
                            ' · ' + UI.time(t.createdAt))),
                    UI.el('div', { style: 'display:flex;gap:8px' }, ...actions)));
            }
        };

        // ---------------- 2. Menu management ----------------
        const menuCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Menu'));
        const itemName = UI.el('input', { type: 'text', placeholder: 'Name' });
        const itemDesc = UI.el('input', { type: 'text', placeholder: 'Description' });
        const itemCategory = UI.el('input', { type: 'text', placeholder: 'Category' });
        const itemPrice = UI.el('input', { type: 'number', step: '0.01', placeholder: 'Price (taka)' });
        const itemPhoto = UI.el('input', { type: 'text', placeholder: 'Photo URL (optional)' });

        const drawMenu = async () => {
            const menu = await API.call('/restaurant/menu');
            menuCard.replaceChildren(UI.el('h2', {}, 'Menu'));
            for (const item of menu) {
                menuCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, item.name),
                        UI.el('div', { class: 'muted' }, item.category + ' · ' + UI.money(item.price))),
                    UI.el('div', { style: 'display:flex;gap:8px' },
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: async () => {
                                try {
                                    await API.call('/restaurant/menu/' + item.id, {
                                        method: 'PUT',
                                        body: { ...item, available: !item.available }
                                    });
                                    await drawMenu();
                                } catch (err) { UI.error(err); }
                            }
                        }, item.available === false ? 'Restock' : 'Sold out'),
                        UI.el('button', {
                            class: 'btn-danger btn-small',
                            onclick: async () => {
                                try {
                                    await API.call('/restaurant/menu/' + item.id, { method: 'DELETE' });
                                    await drawMenu();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Delete'))));
            }
            menuCard.append(
                UI.el('h3', { style: 'margin-top:16px' }, 'Add item'),
                UI.el('div', { class: 'row' },
                    UI.el('div', {}, itemName),
                    UI.el('div', {}, itemPrice)),
                UI.el('div', { class: 'row' },
                    UI.el('div', {}, itemDesc),
                    UI.el('div', {}, itemCategory)),
                itemPhoto,
                UI.el('div', { class: 'form-actions' }, UI.el('button', {
                    onclick: async () => {
                        try {
                            await API.call('/restaurant/menu', {
                                body: {
                                    name: itemName.value.trim(),
                                    description: itemDesc.value.trim(),
                                    category: itemCategory.value.trim(),
                                    price: parseFloat(itemPrice.value) || 0,
                                    photo: itemPhoto.value.trim() || null,
                                    available: true
                                }
                            });
                            itemName.value = itemPrice.value = itemDesc.value = itemCategory.value = itemPhoto.value = '';
                            UI.toast('Item added', 'ok');
                            await drawMenu();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Add to menu')));
        };

        // ---------------- 3. Restaurant settings ----------------
        const settingsCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Restaurant'));
        const openBtn = UI.el('button', {});

        const toggleOpen = async (open) => {
            try {
                const r = await API.call('/restaurant/status', { method: 'PATCH', body: { open } });
                UI.toast(r.open ? 'Restaurant is open' : 'Restaurant is closed', 'ok');
                syncOpenButton(r.open);
            } catch (err) { UI.error(err); }
        };
        const syncOpenButton = (open) => {
            openBtn.replaceChildren(open ? 'Close the restaurant' : 'Open the restaurant');
            openBtn.className = open ? 'btn-danger' : 'btn-ok';
            openBtn.onclick = () => toggleOpen(!open);
        };
        syncOpenButton(true);

        const day = UI.el('select', {}, ...['SATURDAY', 'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
            .map(d => UI.el('option', { value: d }, d)));
        const openTime = UI.el('input', { type: 'time', value: '09:00' });
        const closeTime = UI.el('input', { type: 'time', value: '23:00' });

        settingsCard.append(
            UI.el('p', { class: 'muted' }, 'Closed restaurants reject checkouts with a clear reason.'),
            openBtn,
            UI.el('h3', { style: 'margin-top:16px' }, 'Operating hours (replaces the whole week)'),
            UI.el('div', { class: 'row' }, UI.el('div', {}, day), UI.el('div', {}, openTime), UI.el('div', {}, closeTime)),
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                class: 'btn-ghost',
                onclick: async () => {
                    const hours = [{ day: day.value, openTime: openTime.value, closeTime: closeTime.value }];
                    try {
                        await API.call('/restaurant/hours', { method: 'PUT', body: hours });
                        UI.toast('Hours saved', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Save hours')));

        // ---------------- 4. Broadcast ----------------
        const broadcastCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Promotional broadcast'));
        const bTitle = UI.el('input', { type: 'text', placeholder: 'Title' });
        const bBody = UI.el('input', { type: 'text', placeholder: 'Message' });
        const bChannels = UI.el('select', {},
            UI.el('option', { value: 'PUSH' }, 'Push only'),
            UI.el('option', { value: 'PUSH,EMAIL' }, 'Push + email'),
            UI.el('option', { value: 'PUSH,EMAIL,SMS' }, 'Push + email + SMS'));
        broadcastCard.append(bTitle, bBody, bChannels,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                onclick: async () => {
                    try {
                        const res = await API.call('/notifications/broadcast', {
                            body: {
                                title: bTitle.value.trim(),
                                body: bBody.value.trim(),
                                channels: bChannels.value.split(',')
                            }
                        });
                        UI.toast('Campaign ' + res.campaignId + ' queued', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Send campaign')));

        // ---------------- 5. Riders ----------------
        const ridersCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Riders'));
        const drawRiders = async () => {
            const riders = await API.call('/deliveries/riders');
            ridersCard.replaceChildren(UI.el('h2', {}, 'Riders (' + riders.length + ')'));
            if (!riders.length) ridersCard.append(UI.el('p', { class: 'muted' }, 'No riders registered yet.'));
            for (const r of riders) {
                ridersCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {}, UI.el('b', {}, r.displayName || r.id),
                        UI.el('div', { class: 'muted' }, r.vehicleType + ' · ' + (r.phone || '') +
                            ' · ' + r.completedDeliveries + ' deliveries done')),
                    UI.chip(r.status)));
            }
        };

        UI.render(
            UI.el('h1', {}, 'Kitchen & admin'),
            queueCard,
            UI.el('div', { class: 'row' }, menuCard, UI.el('div', {}, settingsCard, broadcastCard, ridersCard))
        );

        await Promise.all([drawQueue(), drawMenu(), drawRiders()]);
        const stop = UI.poll(async () => { await drawQueue(); }, 5000);
        return stop;
    }
});
