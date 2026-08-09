/**
 * Orders screen: history list + live detail panel.
 * The selected order is polled every 3s while it is still moving, so the
 * customer watches PENDING_PAYMENT -> CONFIRMED -> PREPARING -> OUT_FOR_DELIVERY
 * -> DELIVERED without refreshing. Pay / retry buttons open the Stripe link.
 */
'use strict';

App.register('/orders', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Your orders'), UI.loading());

        const list = await API.call('/orders/me');
        if (!list.length) {
            UI.render(UI.el('h1', {}, 'Your orders'),
                UI.el('div', { class: 'card' },
                    UI.el('p', { class: 'muted' }, 'No orders yet.'),
                    UI.el('a', { class: 'btn', href: '#/' }, 'Order something')));
            return;
        }

        const detailCard = UI.el('div', { class: 'card' }, UI.el('p', { class: 'muted' }, 'Select an order to see it live.'));
        const table = UI.el('table', {},
            UI.el('tr', {}, UI.el('th', {}, 'Order'), UI.el('th', {}, 'Placed'),
                UI.el('th', {}, 'Total'), UI.el('th', {}, 'Status'), UI.el('th', {}, '')));

        let stopPolling = null;

        const openOrder = async (orderId) => {
            if (stopPolling) { stopPolling(); stopPolling = null; }

            const drawDetail = async () => {
                const order = await API.call('/orders/' + orderId);
                detailCard.replaceChildren(...orderDetail(order));

                // Keep polling while the order can still move.
                const live = !['DELIVERED', 'CANCELLED', 'REJECTED', 'PAYMENT_FAILED'].includes(order.status);
                if (live && !stopPolling) stopPolling = UI.poll(drawDetail, 3000);
                if (!live && stopPolling) { stopPolling(); stopPolling = null; }
            };

            const orderDetail = (order) => {
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
                                location.hash = '#/orders';
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Reorder'));
                }
                if (['OUT_FOR_DELIVERY', 'DELIVERED'].includes(order.status)) {
                    actions.push(UI.el('a', { class: 'btn btn-ghost', href: '#/track/' + order.id }, 'Track rider'));
                }

                return [
                    UI.el('div', { class: 'line-item' },
                        UI.el('b', {}, 'Order ' + order.id), UI.chip(order.status)),
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
                    UI.el('div', { class: 'form-actions' }, ...actions)
                ];
            };

            await drawDetail();
        };

        for (const order of list) {
            const button = UI.el('button', { class: 'btn-ghost btn-small', onclick: () => openOrder(order.id) }, 'Open');
            table.append(UI.el('tr', {},
                UI.el('td', {}, order.id.slice(0, 8) + '…'),
                UI.el('td', {}, UI.time(order.createdAt)),
                UI.el('td', {}, UI.money(order.grandTotal, order.currency)),
                UI.el('td', {}, UI.chip(order.status)),
                UI.el('td', {}, button)));
        }

        UI.render(
            UI.el('h1', {}, 'Your orders'),
            UI.el('div', { class: 'card' }, table),
            detailCard
        );

        // Jump straight into the newest order so a fresh checkout is visible immediately.
        await openOrder(list[0].id);

        return () => { if (stopPolling) stopPolling(); };
    }
});
