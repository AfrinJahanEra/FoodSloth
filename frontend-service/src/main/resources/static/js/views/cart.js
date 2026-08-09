/**
 * Cart screen. The cart stores only item ids and quantities - names and prices
 * are merged in from the menu here in the browser, so nothing is duplicated
 * server-side.
 */
'use strict';

App.register('/cart', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Your cart'), UI.loading());

        const [cart, menu] = await Promise.all([
            API.call(`/carts/${Auth.userId}`),
            API.call('/restaurant/menu')
        ]);
        const byId = Object.fromEntries(menu.map(item => [item.id, item]));

        const wrap = UI.el('div');
        const head = UI.el('h1', {}, 'Your cart');
        const card = UI.el('div', { class: 'card' });
        wrap.append(head, card);
        UI.render(wrap);

        const draw = () => {
            card.replaceChildren();
            if (!cart.items || !cart.items.length) {
                card.append(UI.el('p', { class: 'muted' }, 'Your cart is empty.'),
                    UI.el('a', { class: 'btn btn-ghost', href: '#/' }, 'Browse the menu'));
                return;
            }
            for (const line of cart.items) {
                const item = byId[line.itemId];
                const qty = UI.el('input', {
                    type: 'number', min: '1', value: line.quantity,
                    style: 'width:80px'
                });
                qty.addEventListener('change', async () => {
                    const value = Math.max(1, parseInt(qty.value, 10) || 1);
                    try {
                        const updated = await API.call(`/carts/${Auth.userId}/items/${line.itemId}`, {
                            method: 'PUT', body: { quantity: value }
                        });
                        cart.items = updated.items;
                        draw();
                    } catch (err) { UI.error(err); }
                });

                card.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, item ? item.name : line.itemId),
                        item ? UI.el('div', { class: 'muted' }, UI.money(item.price) + ' each') : null),
                    UI.el('div', { style: 'display:flex;gap:8px;align-items:center' },
                        qty,
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: async () => {
                                try {
                                    const updated = await API.call(
                                        `/carts/${Auth.userId}/items/${line.itemId}`, { method: 'DELETE' });
                                    cart.items = updated.items;
                                    draw();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Remove'))
                ));
            }
            card.append(UI.el('div', { class: 'form-actions' },
                UI.el('button', {
                    class: 'btn-danger btn-small',
                    onclick: async () => {
                        try {
                            await API.call(`/carts/${Auth.userId}`, { method: 'DELETE' });
                            cart.items = [];
                            draw();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Clear cart'),
                UI.el('a', { class: 'btn', href: '#/checkout' }, 'Checkout')));
        };
        draw();
    }
});
