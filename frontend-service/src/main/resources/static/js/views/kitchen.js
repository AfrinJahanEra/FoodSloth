/**
 * Orders screen (admin) - modular, two tabs:
 *   1. Orders   - the live kitchen queue (accept / reject / ready). Once an order
 *                 is READY, the admin hands it over to one of the delivery men.
 *   2. Delivery - every delivery with its rider, filterable by status
 *                 (waiting / on the way / delivered / cancelled), plus the roster.
 * Menu management, restaurant profile and users live on their own screens.
 */
'use strict';

/** Friendly, customer-facing words for the delivery states. */
const DELIVERY_LABEL = {
    PENDING_ASSIGNMENT: 'Waiting for rider',
    ASSIGNED: 'On the way',
    ACCEPTED: 'On the way',
    PICKED_UP: 'On the way',
    DELIVERED: 'Delivered',
    CANCELLED: 'Cancelled'
};

function deliveryChip(status) {
    const tone = status === 'DELIVERED' ? 'ok'
        : status === 'CANCELLED' ? 'err'
            : status === 'PENDING_ASSIGNMENT' ? 'warn' : 'info';
    return UI.el('span', { class: 'chip ' + tone }, DELIVERY_LABEL[status] || status);
}

/**
 * Riders the admin may hand a job to: online and with at least one of today's
 * slots still free. A rider who is already on a job keeps appearing here until
 * every slot for the day is booked (slots left = 0).
 */
function assignableRiders(riders) {
    return riders.filter(r => r.status !== 'OFFLINE' && r.slotsRemainingToday > 0);
}

/** Ticket left-border accent by meaning. */
const TICKET_ACCENT = {
    QUEUED: 's-new', AWAITING_PAYMENT: 's-new',
    ACCEPTED: 's-prep',
    READY: 's-ready',
    REJECTED: 's-done', CANCELLED: 's-done'
};

const DELIVERY_ACCENT = {
    PENDING_ASSIGNMENT: 's-prep',
    ASSIGNED: 's-new', ACCEPTED: 's-new', PICKED_UP: 's-new',
    DELIVERED: 's-ready',
    CANCELLED: 's-done'
};

/** One small KPI card: big number, caption, tinted SVG icon. */
function statCard(value, caption, icon, tone) {
    return UI.el('div', { class: 'stat-card' },
        UI.el('div', { class: 'dot ' + tone }, Icon.of(icon, 18)),
        UI.el('div', {},
            UI.el('div', { class: 'num' }, String(value)),
            UI.el('div', { class: 'cap' }, caption)));
}

/** Initials for the avatar circle, e.g. "Arif Hossain" -> "AH". */
function initials(name) {
    return (name || '?').trim().split(/\s+/).slice(0, 2).map(w => w[0].toUpperCase()).join('') || '?';
}

/** Friendly order label: the sequential #number when the server knows it, else a UUID prefix. */
function orderRef(orderNo, id) {
    return orderNo ? 'Order #' + orderNo : 'Order ' + String(id || '').slice(0, 8) + '…';
}

App.register('/kitchen', {
    roles: ['ADMIN'],

    async render() {
        let tab = 'orders';
        let statusFilter = '';
        let deliveryFilter = 'ALL';

        /** Order ids whose "Show details" panel is open - survives the 5s refresh. */
        const expanded = new Set();

        const content = UI.el('div', {});
        const ordersBtn = UI.el('button', {}, Icon.of('receipt', 15), 'Orders');
        const deliveryBtn = UI.el('button', {}, Icon.of('bike', 15), 'Delivery');
        const seg = UI.el('div', { class: 'seg' }, ordersBtn, deliveryBtn);

        const paintTabs = () => {
            ordersBtn.className = tab === 'orders' ? 'on' : '';
            deliveryBtn.className = tab === 'delivery' ? 'on' : '';
        };
        ordersBtn.onclick = () => { tab = 'orders'; paintTabs(); refresh().catch(UI.error); };
        deliveryBtn.onclick = () => { tab = 'delivery'; paintTabs(); refresh().catch(UI.error); };

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Orders'),
                UI.el('div', { class: 'sub' }, 'Kitchen queue, rider hand-over and live delivery status')),
            UI.el('span', { class: 'live-dot' }, 'Live · refreshes every 5s'));

        // ---------------- Tab 1: order queue + rider hand-over ----------------

        const drawOrders = async () => {
            const [tickets, deliveries, riders] = await Promise.all([
                API.call(statusFilter
                    ? '/restaurant/kitchen/orders/status/' + statusFilter
                    : '/restaurant/kitchen/orders'),
                API.call('/deliveries'),
                API.call('/deliveries/riders')
            ]);

            const onWay = deliveries.filter(d => ['ASSIGNED', 'ACCEPTED', 'PICKED_UP'].includes(d.status)).length;
            const stats = UI.el('div', { class: 'stats-row' },
                statCard(tickets.filter(t => t.status === 'QUEUED').length, 'New orders', 'receipt', 'info'),
                statCard(tickets.filter(t => t.status === 'ACCEPTED').length, 'Cooking', 'flame', 'warn'),
                statCard(tickets.filter(t => t.status === 'READY').length, 'Ready to hand over', 'package', 'ok'),
                statCard(onWay, 'On the way', 'bike', 'brand'));

            const pills = UI.el('div', { class: 'pills' });
            for (const s of ['', 'AWAITING_PAYMENT', 'QUEUED', 'ACCEPTED', 'READY', 'REJECTED', 'CANCELLED']) {
                pills.append(UI.el('button', {
                    class: s === statusFilter ? 'on' : '',
                    onclick: () => { statusFilter = s; refresh().catch(UI.error); }
                }, s ? s.replace(/_/g, ' ') : 'All'));
            }

            const list = UI.el('div', {});
            if (!tickets.length) {
                list.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('receipt', 34)), 'No tickets in this view - the queue is clear.'));
            }

            for (const t of tickets) {
                const lines = (t.items || []).map(i => `${i.quantity} × ${i.name || i.itemId}`).join(', ');
                const foot = [];

                // Every ticket can be opened to see the full order details.
                const isOpen = expanded.has(t.id);
                foot.push(UI.el('button', {
                    class: 'btn-small',
                    onclick: () => {
                        if (isOpen) expanded.delete(t.id); else expanded.add(t.id);
                        refresh().catch(UI.error);
                    }
                }, isOpen ? 'Hide details' : 'Show details'));

                if (t.status === 'QUEUED') {
                    foot.push(UI.el('button', {
                        class: 'btn-ok btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/accept`, { method: 'PATCH', body: {} });
                                await refresh();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Accept'));
                    foot.push(UI.el('button', {
                        class: 'btn-danger btn-small',
                        onclick: async () => {
                            const reason = prompt('Rejection reason:', 'Out of stock');
                            if (reason === null) return;
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/reject`, { method: 'PATCH', body: { reason } });
                                await refresh();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Reject'));
                }

                if (t.status === 'ACCEPTED') {
                    foot.push(UI.el('button', {
                        class: 'btn-small',
                        onclick: async () => {
                            try {
                                await API.call(`/restaurant/kitchen/orders/${t.id}/ready`, { method: 'PATCH', body: {} });
                                UI.toast('Food ready - hand it over to a delivery man below', 'ok');
                                await refresh();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Mark ready'));
                }

                // READY: the order is packed - hand it over to one of the delivery men.
                if (t.status === 'READY') {
                    const delivery = deliveries.find(d => d.orderId === t.id);
                    if (!delivery) {
                        foot.push(UI.el('span', { class: 'muted' }, 'Preparing hand-over…'));
                    } else if (delivery.status === 'PENDING_ASSIGNMENT') {
                        const candidates = assignableRiders(riders);
                        if (!candidates.length) {
                            foot.push(UI.el('span', { class: 'muted' }, 'No free delivery man right now'));
                        } else {
                            const pick = UI.el('select', {},
                                ...candidates.map(r => UI.el('option', { value: r.id },
                                    (r.displayName || r.id.slice(0, 8) + '…') + ' · ' + r.slotsRemainingToday + ' slot(s) left')));
                            foot.push(pick, UI.el('button', {
                                class: 'btn-ok btn-small',
                                onclick: async () => {
                                    try {
                                        await API.call(`/deliveries/${delivery.id}/assign`, {
                                            method: 'POST', body: { riderId: pick.value }
                                        });
                                        const rider = candidates.find(r => r.id === pick.value);
                                        UI.toast('Handed over to ' + (rider ? rider.displayName || 'the rider' : 'the rider')
                                            + ' - on the way', 'ok');
                                        await refresh();
                                    } catch (err) { UI.error(err); }
                                }
                            }, 'Hand over'));
                        }
                    } else {
                        foot.push(deliveryChip(delivery.status));
                        if (delivery.riderDisplayName) {
                            foot.push(UI.el('span', { class: 'avatar sm' }, initials(delivery.riderDisplayName)),
                                UI.el('span', { class: 'muted' }, delivery.riderDisplayName));
                        }
                    }
                }

                // Expandable details: line items with prices, money split, drop and timeline.
                const detail = [];
                if (isOpen) {
                    for (const i of t.items || []) {
                        detail.push(UI.el('div', { class: 'ticket-items' },
                            i.quantity + ' × ' + (i.name || i.itemId) + ' @ ' + UI.money(i.unitPrice, t.currency) +
                            ' = ' + UI.money(i.lineTotal, t.currency)));
                    }
                    detail.push(UI.el('div', { class: 'ticket-items' },
                        'Items ' + UI.money(t.itemsTotal, t.currency) +
                        ' · Delivery ' + UI.money(t.deliveryFee, t.currency) +
                        ' · Tax ' + UI.money(t.tax, t.currency)));
                    detail.push(UI.el('div', { class: 'ticket-items' },
                        'Drop: ' + (t.dropAddressLabel || '-') +
                        (t.customerPhone ? ' · Phone: ' + t.customerPhone : '')));
                    detail.push(UI.el('div', { class: 'ticket-items muted' },
                        'Placed ' + UI.time(t.createdAt) +
                        (t.acceptedAt ? ' · Accepted ' + UI.time(t.acceptedAt) : '') +
                        (t.readyAt ? ' · Ready ' + UI.time(t.readyAt) : '')));
                    if (t.statusReason) {
                        detail.push(UI.el('div', { class: 'ticket-items muted' }, 'Reason: ' + t.statusReason));
                    }
                }

                list.append(UI.el('div', { class: 'ticket ' + (TICKET_ACCENT[t.status] || '') },
                    UI.el('div', { class: 'ticket-head' },
                        UI.el('span', { class: 'oid' }, orderRef(t.orderNo, t.id)),
                        UI.chip(t.status),
                        UI.el('span', { class: 'when' }, UI.time(t.createdAt))),
                    UI.el('div', { class: 'ticket-items' }, lines || 'no items'),
                    UI.el('div', { class: 'ticket-items' },
                        UI.el('b', {}, UI.money(t.grandTotal, t.currency)) + ' · ' + (t.note || 'no note')),
                    isOpen ? UI.el('div', { class: 'ticket-detail' }, ...detail) : null,
                    foot.length ? UI.el('div', { class: 'ticket-foot' }, ...foot) : null));
            }
            content.replaceChildren(stats, pills, list);
        };

        // ---------------- Tab 2: delivery tracking ----------------

        const FILTERS = [
            ['ALL', () => true],
            ['WAITING', d => d.status === 'PENDING_ASSIGNMENT'],
            ['ON THE WAY', d => ['ASSIGNED', 'ACCEPTED', 'PICKED_UP'].includes(d.status)],
            ['DELIVERED', d => d.status === 'DELIVERED'],
            ['CANCELLED', d => d.status === 'CANCELLED']
        ];

        const drawDelivery = async () => {
            const [deliveries, riders] = await Promise.all([
                API.call('/deliveries'),
                API.call('/deliveries/riders')
            ]);

            const stats = UI.el('div', { class: 'stats-row' },
                statCard(deliveries.filter(d => d.status === 'PENDING_ASSIGNMENT').length, 'Waiting for rider', 'clock', 'warn'),
                statCard(deliveries.filter(d => ['ASSIGNED', 'ACCEPTED', 'PICKED_UP'].includes(d.status)).length, 'On the way', 'bike', 'info'),
                statCard(deliveries.filter(d => d.status === 'DELIVERED').length, 'Delivered', 'checkcircle', 'ok'),
                statCard(riders.filter(r => r.status !== 'OFFLINE').length, 'Riders online', 'user', 'brand'));

            // Roster: every delivery man the admin can hand orders to.
            const rosterCard = UI.el('div', { class: 'card' },
                UI.el('h2', {}, 'Delivery men (' + riders.length + ')'));
            if (!riders.length) {
                rosterCard.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('bike', 34)), 'No delivery man has gone online yet.'));
            } else {
                const grid = UI.el('div', { class: 'rider-grid' });
                for (const r of riders) {
                    const free = r.status !== 'OFFLINE' && !r.activeDeliveryId && r.slotsRemainingToday > 0;
                    const state = r.status === 'OFFLINE' ? 'OFFLINE'
                        : r.activeDeliveryId ? 'ON A JOB'
                            : r.slotsRemainingToday <= 0 ? 'SLOTS FULL' : 'FREE';
                    const total = r.slotsRemainingToday + r.slotsUsedToday;
                    const usedPct = total ? Math.round((r.slotsUsedToday / total) * 100) : 0;
                    grid.append(UI.el('div', { class: 'rider-card' },
                        UI.el('div', { class: 'rider-top' },
                            UI.el('div', { class: 'avatar' }, initials(r.displayName)),
                            UI.el('div', {},
                                UI.el('b', {}, r.displayName || r.id.slice(0, 8) + '…'),
                                UI.el('span', { class: 'muted' }, r.phone || 'no phone'))),
                        UI.el('div', {},
                            UI.el('div', { class: 'slot-bar' },
                                UI.el('span', { style: 'width:' + usedPct + '%' })),
                            UI.el('div', { class: 'rider-meta' },
                                UI.el('span', {}, r.slotsRemainingToday + ' of ' + total + ' slots free'),
                                UI.chip(free ? 'FREE' : state)))));
                }
                rosterCard.append(grid);
            }

            // Deliveries, filterable by friendly status.
            const matches = FILTERS.find(f => f[0] === deliveryFilter) || FILTERS[0];
            const list = deliveries.filter(matches[1]);

            const pills = UI.el('div', { class: 'pills' });
            for (const [label] of FILTERS) {
                pills.append(UI.el('button', {
                    class: label === deliveryFilter ? 'on' : '',
                    onclick: () => { deliveryFilter = label; refresh().catch(UI.error); }
                }, label));
            }

            const listWrap = UI.el('div', {});
            if (!list.length) {
                listWrap.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('package', 34)), 'No deliveries in this view.'));
            }

            for (const d of list) {
                const controls = [];
                if (d.status === 'PENDING_ASSIGNMENT') {
                    const candidates = assignableRiders(riders);
                    if (!candidates.length) {
                        controls.push(UI.el('span', { class: 'muted' }, 'No free delivery man right now'));
                    } else {
                        const pick = UI.el('select', {},
                            ...candidates.map(r => UI.el('option', { value: r.id },
                                (r.displayName || r.id.slice(0, 8) + '…') + ' · ' + r.slotsRemainingToday + ' slot(s) left')));
                        controls.push(pick, UI.el('button', {
                            class: 'btn-ok btn-small',
                            onclick: async () => {
                                try {
                                    await API.call(`/deliveries/${d.id}/assign`, {
                                        method: 'POST', body: { riderId: pick.value }
                                    });
                                    UI.toast('Delivery man assigned - on the way', 'ok');
                                    await refresh();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Assign'));
                    }
                }

                // Admin-only cancel: frees the rider's slot so they can be assigned again.
                // Riders have no cancel button anywhere - only the admin can call this.
                if (d.status !== 'DELIVERED' && d.status !== 'CANCELLED') {
                    controls.push(UI.el('button', {
                        class: 'btn-danger btn-small',
                        onclick: async () => {
                            if (!confirm('Cancel ' + orderRef(d.orderNo, d.orderId) + '? The rider\'s slot will be freed.')) return;
                            try {
                                await API.call(`/deliveries/${d.id}/cancel`, { method: 'PATCH', body: {} });
                                UI.toast('Delivery cancelled - the slot is free again', 'ok');
                                await refresh();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Cancel'));
                }

                listWrap.append(UI.el('div', { class: 'ticket ' + (DELIVERY_ACCENT[d.status] || '') },
                    UI.el('div', { class: 'ticket-head' },
                        UI.el('span', { class: 'oid' }, orderRef(d.orderNo, d.orderId || d.id)),
                        deliveryChip(d.status),
                        UI.el('span', { class: 'when' }, UI.time(d.createdAt))),
                    UI.el('div', { class: 'ticket-items' },
                        (d.riderDisplayName ? 'Rider: ' + d.riderDisplayName : 'No rider assigned yet') +
                        ' · Drop: ' + (d.dropAddressLabel || '-')),
                    controls.length ? UI.el('div', { class: 'ticket-foot' }, ...controls) : null));
            }

            content.replaceChildren(stats, rosterCard,
                UI.el('div', { class: 'card' },
                    UI.el('h2', {}, 'Deliveries (' + list.length + ')'), pills, listWrap));
        };

        const refresh = async () => {
            if (tab === 'orders') await drawOrders();
            else await drawDelivery();
        };

        UI.render(head, seg, content);
        paintTabs();
        await refresh();
        return UI.poll(() => refresh().catch(() => { }), 5000);
    }
});
