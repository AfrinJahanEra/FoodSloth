/**
 * '/' is the storefront - hero banner, menu and footer - for everyone.
 * Guests browse with a "Sign up to order" call to action; logged-in customers
 * get the same layout with live "Add to cart" buttons instead.
 */
'use strict';

App.register('/', {
    async render() {
        const canOrder = Auth.loggedIn && Auth.role === 'CUSTOMER';

        // Full-bleed layout: hero and footer stretch edge to edge.
        document.body.classList.add('landing');
        const cleanup = () => document.body.classList.remove('landing');

        // Order Now: guests go to login, customers scroll to the menu.
        // The primary button is solid brand, the secondary one frosted glass.
        const actions = canOrder
            ? [
                UI.el('button', {
                    class: 'btn btn-hero',
                    onclick: () => document.getElementById('menu-section')
                        .scrollIntoView({ behavior: 'smooth' })
                }, Icon.of('utensils', 16), 'Order Now'),
                UI.el('a', { class: 'btn btn-hero ghost', href: '#/cart' }, Icon.of('cart', 16), 'View cart')
            ]
            : [
                UI.el('a', { class: 'btn btn-hero', href: '#/login' }, 'Order Now'),
                UI.el('a', { class: 'btn btn-hero ghost', href: '#/signup' }, 'Create free account')
            ];

        const hero = UI.el('section', { class: 'hero' },
            UI.el('img', { class: 'hero-banner', src: '/assets/hero-banner.png', alt: 'FoodSloth - good food, delivered slow' }),
            UI.el('div', { class: 'hero-overlay' },
                UI.el('div', { class: 'hero-copy' },
                    UI.el('span', { class: 'hero-eyebrow' }, 'Fresh kitchen · open daily'),
                    UI.el('h1', { class: 'hero-title' }, 'FoodSloth'),
                    UI.el('p', { class: 'hero-tagline' }, 'Good Food, Delivered Slow.'),
                    UI.el('p', { class: 'hero-sub' },
                        'Honest recipes and fair prices, cooked to order and brought to your door - follow every step from pan to porch.'),
                    UI.el('div', { class: 'hero-actions' }, ...actions),
                    UI.el('div', { class: 'hero-features' },
                        UI.el('span', {}, Icon.of('clock', 15), 'Cooked to order'),
                        UI.el('span', {}, Icon.of('bike', 15), 'Live rider tracking'),
                        UI.el('span', {}, Icon.of('creditcard', 15), 'Cash or card'))))
        );

        const section = UI.el('section', { class: 'landing-section', id: 'menu-section' },
            UI.el('h2', {}, 'Our menu'),
            UI.loading('Loading menu...')
        );

        const footer = UI.el('footer', { class: 'site-footer' },
            UI.el('div', { class: 'foot-inner' },
                UI.el('span', { class: 'brand' }, 'FoodSloth',
                    UI.el('span', { class: 'brand-dot' }, '.')),
                UI.el('span', {}, 'Fresh food, honest prices, fast delivery.'),
                UI.el('span', {}, '\u00A9 2026 FoodSloth')
            )
        );

        UI.render(hero, section, footer);

        // Guests and customers alike always see the menu; only ordering needs a session.
        const loadMenu = async () => {
            section.replaceChildren(UI.el('h2', {}, 'Our menu'), UI.loading('Loading menu...'));
            try {
                const menu = await API.call('/restaurant/menu');
                const body = menu.length
                    ? UI.el('div', { class: 'grid' }, menu.map(item => menuCard(item, canOrder)))
                    : UI.el('p', { class: 'muted' }, 'The menu is being updated - check back soon.');
                section.replaceChildren(UI.el('h2', {}, 'Our menu'), body);
            } catch {
                section.replaceChildren(
                    UI.el('h2', {}, 'Our menu'),
                    UI.el('p', { class: 'muted' }, 'The menu could not be loaded right now.'),
                    UI.el('div', {}, UI.el('button', {
                        class: 'btn-ghost btn-small', onclick: loadMenu
                    }, 'Try again'))
                );
            }
        };
        await loadMenu();
        return cleanup;
    }
});

/** Landscape card: guests see a signup call to action, customers an Add button. */
function menuCard(item, canOrder) {
    const footAction = canOrder
        ? soldOutButton(item)
        : UI.el('a', { class: 'btn btn-small', href: '#/signup' }, 'Sign up to order');

    return UI.el('div', { class: 'card menu-item' },
        thumbOf(item),
        item.category ? UI.el('span', { class: 'chip' }, item.category) : null,
        UI.el('h3', {}, item.name),
        UI.el('p', { class: 'muted' }, item.description || ''),
        UI.el('div', { class: 'card-foot' },
            UI.el('b', {}, UI.money(item.price)),
            footAction
        )
    );
}

function soldOutButton(item) {
    const add = UI.el('button', { class: 'btn-small', disabled: item.available === false },
        item.available === false ? 'Sold out' : 'Add to cart');
    if (item.available !== false) {
        add.onclick = async () => {
            add.disabled = true;
            try {
                await API.call('/carts/' + Auth.userId + '/items', {
                    body: { itemId: item.id, quantity: 1 }
                });
                UI.toast(item.name + ' added to cart', 'ok');
            } catch (err) {
                UI.error(err);
            } finally {
                add.disabled = false;
            }
        };
    }
    return add;
}

function thumbOf(item) {
    if (!item.photo) return UI.el('div', { class: 'thumb thumb-empty' }, Icon.of('utensils', 30));
    const img = UI.el('img', { class: 'thumb', src: item.photo, alt: item.name });
    // Dead photo URLs (old placeholders) should degrade to the empty tile, not a broken icon.
    img.addEventListener('error', () => {
        img.replaceWith(UI.el('div', { class: 'thumb thumb-empty' }, Icon.of('utensils', 30)));
    });
    return img;
}
