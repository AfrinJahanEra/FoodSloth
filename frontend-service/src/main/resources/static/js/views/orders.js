/**
 * Orders screen: history list + live detail panel.
 * The selected order is polled every 3s while it is still moving, so the
 * customer watches PENDING_PAYMENT -> CONFIRMED -> PREPARING -> OUT_FOR_DELIVERY
 * -> DELIVERED without refreshing. Pay / retry buttons open the Stripe link.
 */
'use strict';

App.register('/orders', {
    roles: ['CUSTOMER'],

    async render(focusOrderId) {
        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Your orders'),
                UI.el('div', { class: 'sub' }, 'Live status, deliveries on the road and payment history')));
        UI.render(head, UI.loading());

        let list = await API.call('/orders/me');
        // The pipeline is event-driven, so a just-placed order lands a moment after checkout.
        // Keep asking for it briefly instead of showing a list without it.
        for (let i = 0; i < 10 && focusOrderId && !list.some(o => o.id === focusOrderId); i++) {
            await new Promise(r => setTimeout(r, 1500));
            list = await API.call('/orders/me').catch(() => list);
        }
        if (!list.length) {
            UI.render(head,
                UI.el('div', { class: 'card' },
                    UI.el('div', { class: 'empty' },
                        UI.el('div', { class: 'big' }, Icon.of('utensils', 34)), 'No orders yet.'),
                    UI.el('div', { class: 'center' },
                        UI.el('a', { class: 'btn', href: '#/' }, 'Order something'))));
            // A checkout made moments ago arrives asynchronously - refresh the moment it lands.
            const stop = UI.poll(async () => {
                const fresh = await API.call('/orders/me').catch(() => []);
                if (fresh.length) { stop(); App.views['/orders'].render(); }
            }, 2000);
            return stop;
        }

        const detailCard = UI.el('div', { class: 'card' }, UI.el('p', { class: 'muted' }, 'Select an order to see it live.'));
        const deliveriesCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Your deliveries'));
        const paymentsCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Payment history'));
        const table = UI.el('table', {},
            UI.el('tr', {}, UI.el('th', {}, 'Order'), UI.el('th', {}, 'Placed'),
                UI.el('th', {}, 'Total'), UI.el('th', {}, 'Status'), UI.el('th', {}, '')));

        /** Friendly order label: the sequential #number when known, else a UUID prefix. */
        const orderRef = (orderNo, id) => orderNo
            ? 'Order #' + orderNo
            : 'Order ' + String(id || '').slice(0, 8) + '…';

        let stopPolling = null;

        const openOrder = async (orderId) => {
            if (stopPolling) { stopPolling(); stopPolling = null; }

            const drawDetail = async () => {
                const order = await API.call('/orders/' + orderId);
                const payment = await API.call('/payments/order/' + order.id).catch(() => null);
                detailCard.replaceChildren(...orderDetail(order, payment));

                // Keep polling while the order can still move.
                const live = !['DELIVERED', 'CANCELLED', 'REJECTED', 'PAYMENT_FAILED'].includes(order.status);
                if (live && !stopPolling) stopPolling = UI.poll(drawDetail, 3000);
                if (!live && stopPolling) { stopPolling(); stopPolling = null; }
            };

            const orderDetail = (order, payment) => {
                const rows = (order.items || []).map(line => UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, `${line.quantity} × ${line.name || line.itemId}`),
                    UI.el('b', {}, UI.money(line.lineTotal !== undefined ? line.lineTotal : line.price * line.quantity, order.currency))));

                const actions = [];
                if (['PENDING_PAYMENT', 'PAYMENT_FAILED'].includes(order.status)) {
                    actions.push(UI.el('button', {
                        onclick: async () => {
                            try {
                                if (order.status === 'PAYMENT_FAILED') {
                                    await API.call(`/payments/order/${order.id}/retry`, { method: 'POST', body: {} });
                                }
                                const payment = await API.call('/payments/order/' + order.id);
                                if (payment && payment.checkoutUrl) window.open(payment.checkoutUrl, '_blank');
                                else UI.toast('No checkout link yet - try again in a moment');
                            } catch (err) { UI.error(err); }
                        }
                    }, order.status === 'PAYMENT_FAILED' ? 'Retry payment' : 'Pay now'));
                }
                if (['PENDING_PAYMENT', 'CONFIRMED'].includes(order.status)) {
                    actions.push(UI.el('button', {
                        class: 'btn-danger',
                        onclick: async () => {
                            try {
                                await API.call(`/orders/${order.id}/cancel`, { method: 'PATCH', body: {} });
                                UI.toast('Order cancelled', 'ok');
                                await drawDetail();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Cancel'));
                }
                if (['DELIVERED', 'CANCELLED', 'REJECTED', 'PAYMENT_FAILED'].includes(order.status)) {
                    actions.push(UI.el('button', {
                        class: 'btn-ghost',
                        onclick: async () => {
                            try {
                                const res = await API.call(`/orders/${order.id}/reorder`, { method: 'POST', body: {} });
                                UI.toast('Reordered - new order ' + (res.orderId || ''), 'ok');
                                location.hash = '#/orders/' + (res.orderId || '');
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Reorder'));
                }
                if (['OUT_FOR_DELIVERY', 'DELIVERED'].includes(order.status)) {
                    actions.push(UI.el('a', { class: 'btn btn-ghost', href: '#/track/' + order.id }, 'Track rider'));
                }

                return [
                    UI.el('div', { class: 'line-item' },
                        UI.el('b', {}, orderRef(order.orderNo, order.id)), UI.chip(order.status)),
                    ...rows,
                    UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Subtotal'),
                        UI.el('span', {}, UI.money(order.subtotal, order.currency))),
                    UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Delivery'),
                        UI.el('span', {}, UI.money(order.deliveryCharge, order.currency))),
                    UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Tax'),
                        UI.el('span', {}, UI.money(order.tax, order.currency))),
                    UI.el('div', { class: 'line-item' }, UI.el('b', {}, 'Grand total'),
                        UI.el('b', {}, UI.money(order.grandTotal, order.currency))),
                    UI.el('p', { class: 'muted' },
                        'To: ' + (order.deliveryAddress || '-') + ' · ' + order.paymentMethod),
                    payment ? UI.el('p', { class: 'muted' },
                        'Payment: ', UI.chip(payment.status),
                        payment.failureReason ? ' · ' + payment.failureReason : '') : null,
                    UI.el('div', { class: 'form-actions' }, ...actions)
                ];
            };

            await drawDetail();
        };

        for (const order of list) {
            const button = UI.el('button', { class: 'btn-ghost btn-small', onclick: () => openOrder(order.id) }, 'Open');
            table.append(UI.el('tr', {},
                UI.el('td', {}, UI.el('b', {}, order.orderNo ? '#' + order.orderNo : order.id.slice(0, 8) + '…')),
                UI.el('td', {}, UI.time(order.createdAt)),
                UI.el('td', {}, UI.money(order.grandTotal, order.currency)),
                UI.el('td', {}, UI.chip(order.status)),
                UI.el('td', {}, button)));
        }

        UI.render(
            head,
            UI.el('div', { class: 'card' }, UI.el('h2', {}, 'All orders'), table),
            detailCard,
            deliveriesCard,
            paymentsCard
        );

        // ---------------- My deliveries (GET /deliveries/me) ----------------
        try {
            const deliveries = await API.call('/deliveries/me');
            deliveriesCard.replaceChildren(UI.el('h2', {}, 'Your deliveries (' + deliveries.length + ')'));
            if (!deliveries.length) deliveriesCard.append(UI.el('p', { class: 'muted' }, 'Nothing on the road yet.'));
            for (const d of deliveries) {
                deliveriesCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, orderRef(d.orderNo, d.orderId) + ' '), UI.chip(d.status),
                        UI.el('div', { class: 'muted' }, (d.dropAddressLabel || '-') +
                            ' · rider: ' + (d.riderDisplayName || 'unassigned') + ' · ' + UI.time(d.createdAt))),
                    ['ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED'].includes(d.status)
                        ? UI.el('a', { class: 'btn btn-ghost btn-small', href: '#/track/' + d.orderId }, 'Track')
                        : null));
            }
        } catch { /* deliveries appear only after the kitchen marks the food ready */ }

        // ---------------- Payment history (GET /payments/me) ----------------
        try {
            const pays = await API.call('/payments/me');
            paymentsCard.replaceChildren(UI.el('h2', {}, 'Payment history (' + pays.length + ')'));
            if (!pays.length) paymentsCard.append(UI.el('p', { class: 'muted' }, 'No payments yet.'));
            for (const p of pays) {
                paymentsCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, 'Payment ' + p.id.slice(0, 8) + '… '), UI.chip(p.status),
                        UI.el('div', { class: 'muted' },
                            UI.money(p.amount, p.currency) + ' · ' + p.paymentMethod +
                            ' · ' + orderRef(p.orderNo, p.orderId) + ' · ' + UI.time(p.createdAt) +
                            (p.failureReason ? ' · ' + p.failureReason : '')))));
            }
        } catch { /* payment history is best-effort */ }

        // Jump straight into the newest order so a fresh checkout is visible immediately.
        const focus = list.some(o => o.id === focusOrderId) ? focusOrderId : list[0].id;
        await openOrder(focus);

        return () => { if (stopPolling) stopPolling(); };
    }
});
