/**
 * Payments (admin) - the platform ledger.
 *   - Lists every payment on the platform, newest first (GET /payments),
 *     each row with a Details button that opens the full record.
 *   - Look up the payment behind any order number and inspect it.
 * Amounts are stored in the smallest unit (poisha); shown here as taka (/100).
 * Only human-facing references are shown - Payment #n, Order #n and customer
 * codes - never database ids.
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

        const payRef = (p) => p.paymentNo ? 'Payment #' + p.paymentNo : 'Payment';
        const orderRef = (orderNo) => orderNo ? 'Order #' + orderNo : 'Order';
        /** Cash on delivery stays PENDING on the ledger until the rider delivers - say so. */
        const codHint = (p) => (p.paymentMethod === 'CASH_ON_DELIVERY' && p.status === 'PENDING')
            ? ' · cash on delivery - confirms when the rider delivers' : '';

        // Customer codes (user id -> code) so the ledger shows C-3 instead of a database id.
        const userCodes = {};
        API.call('/users').then(users => {
            for (const u of users || []) if (u.id && u.code) userCodes[u.id] = u.code;
        }).catch(() => { /* the ledger falls back to a plain label */ });
        const customerRef = (userId) => userCodes[userId] || 'Customer';

        /** Full record, shared by the list and the lookup. */
        const detailOf = (p) => UI.el('div', { class: 'ticket-detail' },
            row('Payment', payRef(p)),
            row('Order', orderRef(p.orderNo)),
            row('Customer', customerRef(p.userId)),
            row('Amount', money(p)),
            row('Method', p.paymentMethod || '-'),
            row('Status', UI.chip(p.status), codHint(p)),
            p.failureReason ? row('Failure reason', p.failureReason) : null,
            row('Created', UI.time(p.createdAt)),
            row('Updated', p.updatedAt ? UI.time(p.updatedAt) : '-'));

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Payments'),
                UI.el('div', { class: 'sub' }, 'Every payment on the platform - look it up and inspect it')));

        // ---------------- Look up by order number ----------------

        const orderNoInput = UI.el('input', { type: 'text', placeholder: 'Order number (e.g. 4)' });
        const lookupBox = UI.el('div', {});

        const showLookup = (p) => {
            lookupBox.replaceChildren(
                UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, payRef(p) + ' '), UI.chip(p.status),
                        UI.el('div', { class: 'muted' },
                            money(p) + ' · ' + (p.paymentMethod || '-') + ' · ' +
                            orderRef(p.orderNo) + ' · ' + UI.time(p.createdAt) + codHint(p)))),
                detailOf(p));
        };

        const lookupCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Look up by order'),
            UI.el('label', {}, 'Order number'),
            orderNoInput,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                onclick: async () => {
                    const n = Number(orderNoInput.value.trim());
                    if (!n) return;
                    try {
                        const all = await API.call('/payments');
                        const hit = all.find(x => x.orderNo === n);
                        if (!hit) { UI.toast('No payment found for order #' + n); return; }
                        showLookup(hit);
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
                            UI.el('b', {}, payRef(p) + ' '), UI.chip(p.status),
                            UI.el('div', { class: 'muted' },
                                money(p) + ' · ' + (p.paymentMethod || '-') +
                                ' · ' + orderRef(p.orderNo) + ' · ' + UI.time(p.createdAt) + codHint(p))),
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: () => {
                                if (isOpen) expanded.delete(p.id); else expanded.add(p.id);
                                drawList().catch(UI.error);
                            }
                        }, isOpen ? 'Hide details' : 'Details')),
                    isOpen ? detailOf(p) : null));
            }
        };

        UI.render(head, lookupCard, listCard);
        await drawList().catch(UI.error);
    }
});
