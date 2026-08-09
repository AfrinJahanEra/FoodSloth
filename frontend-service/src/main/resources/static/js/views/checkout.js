/**
 * Checkout: pick an address, payment method, leave a note. The backend mints the
 * orderId and the pipeline takes over - this screen then hands off to Orders.
 */
'use strict';

App.register('/checkout', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Checkout'), UI.loading('Loading your addresses...'));

        const me = await API.call('/users/me');
        const addresses = me.addresses || [];

        const address = UI.el('select', {});
        for (const a of addresses) {
            address.append(UI.el('option', {
                value: a.id,
                selected: a.defaultAddress ? 'selected' : null
            }, `${a.label || 'Address'} - ${a.street}, ${a.area || ''} ${a.city || ''}`));
        }
        if (!addresses.length) {
            address.append(UI.el('option', { value: '' }, 'No saved addresses'));
        }

        const paymentMethod = UI.el('select', {},
            UI.el('option', { value: 'CARD' }, 'Card (Stripe checkout)'),
            UI.el('option', { value: 'CASH_ON_DELIVERY' }, 'Cash on delivery')
        );
        const note = UI.el('textarea', { rows: '2', placeholder: 'e.g. extra chili, no onions' });
        const submit = UI.el('button', { type: 'submit' }, 'Place order');

        const form = UI.el('form', {
            onsubmit: async (e) => {
                e.preventDefault();
                const chosen = addresses.find(a => a.id === address.value);
                if (!chosen) {
                    UI.toast('Add a delivery address first', 'err');
                    return;
                }
                submit.disabled = true;
                try {
                    const res = await API.call(`/carts/${Auth.userId}/checkout`, {
                        body: {
                            deliveryAddress: `${chosen.street}, ${chosen.area || ''} ${chosen.city || ''}`.trim(),
                            deliveryLatitude: chosen.latitude,
                            deliveryLongitude: chosen.longitude,
                            paymentMethod: paymentMethod.value,
                            note: note.value.trim() || null
                        }
                    });
                    UI.toast('Order placed - id ' + res.orderId, 'ok');
                    location.hash = '#/orders';
                } catch (err) {
                    UI.error(err);
                    submit.disabled = false;
                }
            }
        },
            UI.el('label', {}, 'Deliver to'), address,
            addresses.length
                ? null
                : UI.el('p', { class: 'muted' }, 'You have no addresses yet - add one via POST /users/me/addresses (see Postman guide).'),
            UI.el('label', {}, 'Payment method'), paymentMethod,
            UI.el('label', {}, 'Note for the kitchen (optional)'), note,
            UI.el('div', { class: 'form-actions' }, submit)
        );

        UI.render(UI.el('h1', {}, 'Checkout'), UI.el('div', { class: 'card' }, form));
    }
});
