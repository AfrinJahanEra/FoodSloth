/**
 * Checkout: pick an address, payment method, leave a note. The backend mints the
 * orderId and the pipeline takes over - this screen then hands off to Orders.
 */
'use strict';

App.register('/checkout', {
    roles: ['CUSTOMER'],

    async render() {
        UI.render(UI.el('h1', {}, 'Checkout'), UI.loading('Loading your addresses...'));

        /** Re-runs after an address is added so the new one is selectable immediately. */
        const draw = async () => {
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
            const cardHint = UI.el('p', { class: 'muted', style: 'margin:-4px 0 12px' },
                'Stripe test mode - use card 4242 4242 4242 4242 with any future expiry and any CVC.');
            paymentMethod.addEventListener('change', () => {
                cardHint.style.display = paymentMethod.value === 'CARD' ? '' : 'none';
            });
            const note = UI.el('textarea', { rows: '2', placeholder: 'e.g. extra chili, no onions' });
            const submit = UI.el('button', { type: 'submit' }, 'Place order');

            // ---- inline "new address" form (same fields as Profile) ----
            const aLabel = UI.el('input', { type: 'text', placeholder: 'Label (Home, Office...)' });
            const aStreet = UI.el('input', { type: 'text', placeholder: 'Street / house' });
            const aArea = UI.el('input', { type: 'text', placeholder: 'Area' });
            const aCity = UI.el('input', { type: 'text', placeholder: 'City' });
            const aLat = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Latitude (e.g. 23.8103)' });
            const aLng = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Longitude (e.g. 90.4125)' });
            const aDefault = UI.el('input', { type: 'checkbox' }); aDefault.checked = !addresses.length;
            const addressForm = UI.el('div', { style: 'display:none' },
                UI.el('h3', {}, 'New delivery address'),
                UI.el('div', { class: 'row' }, UI.el('div', {}, aLabel), UI.el('div', {}, aStreet)),
                UI.el('div', { class: 'row' }, UI.el('div', {}, aArea), UI.el('div', {}, aCity)),
                UI.el('div', { class: 'row' }, UI.el('div', {}, aLat), UI.el('div', {}, aLng)),
                UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Set as default address'), aDefault),
                UI.el('div', { class: 'form-actions' },
                    UI.el('button', {
                        type: 'button',
                        onclick: async () => {
                            try {
                                await API.call('/users/me/addresses', {
                                    body: {
                                        label: aLabel.value.trim(), street: aStreet.value.trim(),
                                        area: aArea.value.trim(), city: aCity.value.trim(),
                                        latitude: parseFloat(aLat.value) || 0, longitude: parseFloat(aLng.value) || 0,
                                        defaultAddress: aDefault.checked
                                    }
                                });
                                UI.toast('Address saved', 'ok');
                                await draw();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Save address')));
            const toggleAddressForm = UI.el('button', {
                type: 'button', class: 'btn-ghost btn-small',
                onclick: () => {
                    addressForm.style.display = addressForm.style.display === 'none' ? '' : 'none';
                }
            }, '+ Add new address');

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
                                contactPhone: Auth.user?.phone || null,
                                paymentMethod: paymentMethod.value,
                                note: note.value.trim() || null
                            }
                        });
                        UI.toast('Order placed - id ' + res.orderId, 'ok');
                        location.hash = '#/orders/' + res.orderId;
                    } catch (err) {
                        UI.error(err);
                        submit.disabled = false;
                    }
                }
            },
                UI.el('label', {}, 'Deliver to'), address,
                UI.el('div', { class: 'form-actions' }, toggleAddressForm,
                    UI.el('a', { class: 'btn-ghost btn-small', href: '#/profile' }, 'Manage addresses')),
                addressForm,
                UI.el('label', {}, 'Payment method'), paymentMethod, cardHint,
                UI.el('label', {}, 'Note for the kitchen (optional)'), note,
                UI.el('div', { class: 'form-actions' }, submit)
            );

            UI.render(UI.el('h1', {}, 'Checkout'), UI.el('div', { class: 'card' }, form));
        };

        await draw();
    }
});
