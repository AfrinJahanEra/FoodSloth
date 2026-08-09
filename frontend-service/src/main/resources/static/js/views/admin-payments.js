/**
 * Payments (admin) - the platform ledger.
 *   - Lists every payment on the platform, newest first (GET /payments),
 *     each row with a Details button that opens the full record.
 *   - Look up the payment behind any order id, inspect it and refund it.
 * Amounts are stored in the smallest unit (poisha); shown here as taka (/100).
 */
'use strict';

App.register('/admin-payments', {
    roles: ['ADMIN'],

    async render() {
        /** Payment ids whose Details panel is open. */
        const expanded = new Set();

        /** Stored amounts are poisha - the ledger talks taka. */
        const money = (p) => UI.money((p.amount || 0) / 100, p.currency);

        const row = (label, ...content) => UI.el('div', { class: 'ticket-items' },
            UI.el('b', {}, label + ': '), ...content);

        /** Friendly order label: the sequential #number when known, else a UUID prefix. */
        const orderRef = (orderNo, id) => orderNo
            ? 'Order #' + orderNo
            : 'Order ' + String(id || '').slice(0, 8) + '…';

        /** Full record, shared by the list and the lookup. */
        const detailOf = (p) => UI.el('div', { class: 'ticket-detail' },
            row('Payment id', p.id),
            row('Order', p.orderNo ? '#' + p.orderNo + ' (' + (p.orderId || '-') + ')' : (p.orderId || '-')),
            row('Customer', p.userId || '-'),
            row('Amount', money(p)),
            row('Method', p.paymentMethod || '-'),
            row('Status', UI.chip(p.status)),
            p.failureReason ? row('Failure reason', p.failureReason) : null,
            row('Created', UI.time(p.createdAt)),
            row('Updated', p.updatedAt ? UI.time(p.updatedAt) : '-'));

        const refundBtn = (p, after) => p.status === 'SUCCEEDED' ? UI.el('button', {
            class: 'btn-danger btn-small',
            onclick: async () => {
                if (!confirm('Refund ' + money(p) + '?')) return;
                try {
                    const refunded = await API.call('/payments/' + p.id + '/refund', { method: 'POST', body: {} });
                    UI.toast('Refunded - payment now ' + refunded.status, 'ok');
                    after(refunded);
                } catch (err) { UI.error(err); }
            }
        }, 'Refund') : null;

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Payments'),
                UI.el('div', { class: 'sub' }, 'Every payment on the platform - look it up, inspect it, refund it')));

        // ---------------- Look up by order id ----------------

        const orderIdInput = UI.el('input', { type: 'text', placeholder: 'Paste an order id (from the Orders screen)' });
        const lookupBox = UI.el('div', {});

        const showLookup = (p) => {
            lookupBox.replaceChildren(
                UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, 'Payment ' + p.id.slice(0, 8) + '… '), UI.chip(p.status),
                        UI.el('div', { class: 'muted' },
                            money(p) + ' · ' + (p.paymentMethod || '-') + ' · ' + UI.time(p.createdAt))),
                    UI.el('div', { style: 'display:flex;gap:8px' }, refundBtn(p, showLookup))),
                detailOf(p));
        };

        const lookupCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Look up by order'),
            UI.el('label', {}, 'Order id'),
            orderIdInput,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                onclick: async () => {
                    const orderId = orderIdInput.value.trim();
                    if (!orderId) return;
                    try {
                        showLookup(await API.call('/payments/order/' + orderId));
                    } catch (err) { UI.error(err); }
                }
            }, 'Look up payment')),
            lookupBox);

        // ---------------- All payments, newest first ----------------

        const listCard = UI.el('div', { class: 'card' });

        const drawList = async () => {
            const all = await API.call('/payments');
            listCard.replaceChildren(UI.el('h2', {}, 'All payments (' + all.length + ')'));

            if (!all.length) {
                listCard.append(UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('creditcard', 34)), 'No payments yet.'));
                return;
            }

            for (const p of all) {
                const isOpen = expanded.has(p.id);
                listCard.append(UI.el('div', {},
                    UI.el('div', { class: 'line-item' },
                        UI.el('div', {},
                            UI.el('b', {}, 'Payment ' + p.id.slice(0, 8) + '… '), UI.chip(p.status),
                            UI.el('div', { class: 'muted' },
                                money(p) + ' · ' + (p.paymentMethod || '-') +
                                ' · ' + orderRef(p.orderNo, p.orderId) + ' · ' + UI.time(p.createdAt))),
                        UI.el('div', { style: 'display:flex;gap:8px' },
                            refundBtn(p, () => drawList().catch(UI.error)),
                            UI.el('button', {
                                class: 'btn-ghost btn-small',
                                onclick: () => {
                                    if (isOpen) expanded.delete(p.id); else expanded.add(p.id);
                                    drawList().catch(UI.error);
                                }
                            }, isOpen ? 'Hide details' : 'Details'))),
                    isOpen ? detailOf(p) : null));
            }
        };

        UI.render(head, lookupCard, listCard);
        await drawList().catch(UI.error);
    }
});
