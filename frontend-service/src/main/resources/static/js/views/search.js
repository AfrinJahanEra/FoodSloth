/**
 * Food search (customer) - opened by the magnifier in the top bar.
 * Type a dish name and the matching menu items appear with their photo,
 * name and price; available dishes can be added straight to the cart.
 * The whole menu is fetched once and filtered in the browser as you type.
 */
'use strict';

App.register('/search', {
    roles: ['CUSTOMER'],

    async render() {
        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Search food'),
                UI.el('div', { class: 'sub' }, 'Find a dish by name - results show the photo, name and price')));

        const input = UI.el('input', {
            type: 'text',
            placeholder: 'Search for a dish, e.g. biriyani…',
            style: 'max-width:480px'
        });

        const results = UI.el('div', {}, UI.loading('Loading menu...'));

        /** Filter the cached menu and redraw; the card (photo + name + price) is reused from the storefront. */
        const draw = (menu, q) => {
            if (!q) {
                results.replaceChildren(UI.el('p', { class: 'muted' }, 'Start typing to search the menu.'));
                return;
            }
            const matches = menu.filter(i => (i.name || '').toLowerCase().includes(q));
            results.replaceChildren(matches.length
                ? UI.el('div', { class: 'grid' }, matches.map(item => menuCard(item, true)))
                : UI.el('div', { class: 'empty' },
                    UI.el('div', { class: 'big' }, Icon.of('search', 34)), 'No dish matches "' + q + '".'));
        };

        UI.render(head, UI.el('div', { class: 'card' },
            UI.el('label', {}, 'Dish name'), input), results);
        input.focus();

        try {
            const menu = await API.call('/restaurant/menu');
            input.oninput = () => draw(menu, input.value.trim().toLowerCase());
            draw(menu, '');
        } catch (err) {
            results.replaceChildren(UI.el('p', { class: 'muted' }, 'The menu could not be loaded right now.'));
            UI.error(err);
        }
    }
});
