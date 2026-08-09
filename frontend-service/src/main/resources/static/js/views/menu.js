/** Menu browse + add to cart. Home screen for customers. */
'use strict';

App.register('/', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Menu'), UI.loading('Loading the menu...'));

        const menu = await API.call('/restaurant/menu');
        if (!menu.length) {
            UI.render(UI.el('h1', {}, 'Menu'),
                UI.el('div', { class: 'card' },
                    UI.el('p', { class: 'muted' }, 'The menu is empty. An admin can add items from the Kitchen screen.')));
            return;
        }

        const grid = UI.el('div', { class: 'grid' });
        for (const item of menu) {
            const soldOut = item.available === false;
            const add = UI.el('button', {
                class: 'btn-small',
                disabled: soldOut,
                onclick: async () => {
                    try {
                        await API.call(`/carts/${Auth.userId}/items`, {
                            body: { itemId: item.id, quantity: 1 }
                        });
                        UI.toast(item.name + ' added to cart', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, soldOut ? 'Sold out' : 'Add to cart');

            grid.append(UI.el('div', { class: 'card menu-item' },
                item.photo ? UI.el('img', { class: 'thumb', src: item.photo, alt: item.name }) : null,
                UI.el('h3', {}, item.name),
                UI.el('p', { class: 'muted' }, item.description || ''),
                UI.el('div', { class: 'line-item' },
                    UI.el('b', {}, UI.money(item.price)),
                    add)
            ));
        }
        UI.render(UI.el('h1', {}, 'Menu'), grid);
    }
});
