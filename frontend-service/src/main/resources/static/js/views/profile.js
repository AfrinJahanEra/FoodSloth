/**
 * Profile screen, by role:
 *  - ADMIN: the restaurant profile only - open/close switch, profile fields
 *    and operating hours. No personal account details.
 *  - Everyone else: edit identity (PUT /users/me), food preferences
 *    (PUT /users/me/preferences) and full delivery-address CRUD
 *    (GET/POST/PUT/DELETE /users/me/addresses + set-default). Every mutation
 *    returns the fresh UserResponse, pushed back into the local session cache.
 */
'use strict';

App.register('/profile', {
    // Delivery men have no profile here - their shift and vehicle details live
    // on the rider dashboard (Delivery Service), not in User Service.
    roles: ['CUSTOMER', 'ADMIN'],

    async render() {
        if (Auth.role === 'ADMIN') return renderRestaurantProfile();

        UI.render(UI.el('h1', {}, 'Profile & addresses'), UI.loading());

        const me = await API.call('/users/me');

        /** Every user-service mutation answers the updated user - keep the session in sync. */
        const saveUser = (updated) => { API.setSession(API.token(), updated); App.drawUserBox(); };
        const splitTags = (s) => s.split(',').map(x => x.trim()).filter(Boolean);

        // ---------------- 1. Identity ----------------
        const name = UI.el('input', { type: 'text', value: me.name || '' });
        const phone = UI.el('input', { type: 'text', value: me.phone || '' });
        const photo = UI.el('input', { type: 'text', value: me.photo || '', placeholder: 'Photo URL (optional)' });

        const identityCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Account'),
            UI.el('p', { class: 'muted' }, (me.email || '') + ' · ' + (me.role || '') +
                (me.code ? ' · ID ' + me.code : '')),
            UI.el('label', {}, 'Name'), name,
            UI.el('label', {}, 'Phone'), phone,
            UI.el('label', {}, 'Photo URL'), photo,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                onclick: async () => {
                    try {
                        const updated = await API.call('/users/me', {
                            method: 'PUT',
                            body: { name: name.value.trim(), phone: phone.value.trim(), photo: photo.value.trim() || null }
                        });
                        saveUser(updated);
                        UI.toast('Profile saved', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Save profile')));

        // ---------------- 2. Preferences ----------------
        const foodPrefs = UI.el('input', { type: 'text', value: (me.foodPreferences || []).join(', '), placeholder: 'e.g. spicy, less oil' });
        const dietTags = UI.el('input', { type: 'text', value: (me.dietaryTags || []).join(', '), placeholder: 'e.g. vegetarian, halal' });
        const payMethod = UI.el('select', {},
            UI.el('option', { value: 'CARD', selected: me.defaultPaymentMethod === 'CARD' ? 'selected' : null }, 'Card'),
            UI.el('option', { value: 'CASH_ON_DELIVERY', selected: me.defaultPaymentMethod === 'CASH_ON_DELIVERY' ? 'selected' : null }, 'Cash on delivery'));

        const prefsCard = UI.el('div', { class: 'card' },
            UI.el('h2', {}, 'Preferences'),
            UI.el('label', {}, 'Food preferences (comma separated)'), foodPrefs,
            UI.el('label', {}, 'Dietary tags (comma separated)'), dietTags,
            UI.el('label', {}, 'Default payment method'), payMethod,
            UI.el('div', { class: 'form-actions' }, UI.el('button', {
                onclick: async () => {
                    try {
                        const updated = await API.call('/users/me/preferences', {
                            method: 'PUT',
                            body: {
                                foodPreferences: splitTags(foodPrefs.value),
                                dietaryTags: splitTags(dietTags.value),
                                defaultPaymentMethod: payMethod.value
                            }
                        });
                        saveUser(updated);
                        UI.toast('Preferences saved', 'ok');
                    } catch (err) { UI.error(err); }
                }
            }, 'Save preferences')));

        // ---------------- 3. Addresses ----------------
        const addressesCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Delivery addresses'));

        const formTitle = UI.el('h3', {}, 'Add address');
        const label = UI.el('input', { type: 'text', placeholder: 'Label (Home, Office...)' });
        const street = UI.el('input', { type: 'text', placeholder: 'Street / house' });
        const area = UI.el('input', { type: 'text', placeholder: 'Area' });
        const city = UI.el('input', { type: 'text', placeholder: 'City' });
        const lat = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Latitude (e.g. 23.8103)' });
        const lng = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Longitude (e.g. 90.4125)' });
        const isDefault = UI.el('input', { type: 'checkbox' });
        let editingId = null;

        const clearForm = () => {
            editingId = null;
            formTitle.replaceChildren('Add address');
            label.value = street.value = area.value = city.value = lat.value = lng.value = '';
            isDefault.checked = false;
        };

        const drawAddresses = async () => {
            const list = await API.call('/users/me/addresses');
            addressesCard.replaceChildren(UI.el('h2', {}, 'Delivery addresses'));
            if (!list.length) addressesCard.append(UI.el('p', { class: 'muted' }, 'No saved addresses yet - add one below.'));

            for (const a of list) {
                addressesCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, (a.label || 'Address') + (a.defaultAddress ? ' (default)' : '')),
                        UI.el('div', { class: 'muted' },
                            `${a.street}, ${a.area || ''} ${a.city || ''} · ${a.latitude}, ${a.longitude}`)),
                    UI.el('div', { style: 'display:flex;gap:8px' },
                        a.defaultAddress ? null : UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: async () => {
                                try {
                                    saveUser(await API.call(`/users/me/addresses/${a.id}/default`, { method: 'PUT', body: {} }));
                                    await drawAddresses();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Make default'),
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: () => {
                                editingId = a.id;
                                formTitle.replaceChildren('Edit address');
                                label.value = a.label || ''; street.value = a.street || '';
                                area.value = a.area || ''; city.value = a.city || '';
                                lat.value = a.latitude; lng.value = a.longitude;
                                isDefault.checked = !!a.defaultAddress;
                            }
                        }, 'Edit'),
                        UI.el('button', {
                            class: 'btn-danger btn-small',
                            onclick: async () => {
                                if (!confirm('Delete this address?')) return;
                                try {
                                    saveUser(await API.call(`/users/me/addresses/${a.id}`, { method: 'DELETE' }));
                                    await drawAddresses();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Delete'))));
            }

            addressesCard.append(
                formTitle,
                UI.el('div', { class: 'row' }, UI.el('div', {}, label), UI.el('div', {}, street)),
                UI.el('div', { class: 'row' }, UI.el('div', {}, area), UI.el('div', {}, city)),
                UI.el('div', { class: 'row' }, UI.el('div', {}, lat), UI.el('div', {}, lng)),
                UI.el('div', { class: 'line-item' }, UI.el('span', {}, 'Set as default address'), isDefault),
                UI.el('div', { class: 'form-actions' },
                    UI.el('button', {
                        onclick: async () => {
                            const body = {
                                label: label.value.trim(), street: street.value.trim(),
                                area: area.value.trim(), city: city.value.trim(),
                                latitude: parseFloat(lat.value) || 0, longitude: parseFloat(lng.value) || 0,
                                defaultAddress: isDefault.checked
                            };
                            try {
                                const updated = editingId
                                    ? await API.call(`/users/me/addresses/${editingId}`, { method: 'PUT', body })
                                    : await API.call('/users/me/addresses', { body });
                                saveUser(updated);
                                clearForm();
                                await drawAddresses();
                                UI.toast('Address saved', 'ok');
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Save address'),
                    UI.el('button', { class: 'btn-ghost', onclick: clearForm }, 'Clear')));
        };

        UI.render(UI.el('h1', {}, 'Profile & addresses'),
            UI.el('div', { class: 'row' }, identityCard, prefsCard),
            addressesCard);
        await drawAddresses();
    }
});

/* ---------- Admin: restaurant profile only ---------- */

async function renderRestaurantProfile() {
    UI.render(UI.el('h1', {}, 'Restaurant profile'), UI.loading());

    // ---------------- 1. Settings ----------------
    const settingsCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Restaurant'));
    const openBtn = UI.el('button', {});
    const rName = UI.el('input', { type: 'text', placeholder: 'Restaurant name' });
    const rPhone = UI.el('input', { type: 'text', placeholder: 'Phone' });
    const rDesc = UI.el('input', { type: 'text', placeholder: 'Description' });
    const rPhoto = UI.el('input', { type: 'text', placeholder: 'Photo URL' });
    const rFile = UI.el('input', { type: 'file', accept: 'image/*' });
    const rAddr = UI.el('input', { type: 'text', placeholder: 'Street address' });
    const rLat = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Latitude' });
    const rLng = UI.el('input', { type: 'number', step: '0.0001', placeholder: 'Longitude' });

    const loadRestaurant = async () => {
        try {
            const r = await API.call('/restaurant');
            rName.value = r.name || ''; rPhone.value = r.phone || '';
            rDesc.value = r.description || ''; rPhoto.value = r.photo || '';
            rAddr.value = r.address || ''; rLat.value = r.latitude ?? ''; rLng.value = r.longitude ?? '';
            syncOpenButton(!!r.open);
        } catch { /* first-run: restaurant document not created yet */ }
    };

    const toggleOpen = async (open) => {
        try {
            const r = await API.call('/restaurant/status', { method: 'PATCH', body: { open } });
            UI.toast(r.open ? 'Restaurant is open' : 'Restaurant is closed', 'ok');
            syncOpenButton(r.open);
        } catch (err) { UI.error(err); }
    };
    const syncOpenButton = (open) => {
        openBtn.replaceChildren(open ? 'Close the restaurant' : 'Open the restaurant');
        openBtn.className = open ? 'btn-danger' : 'btn-ok';
        openBtn.onclick = () => toggleOpen(!open);
    };
    syncOpenButton(true);

    const day = UI.el('select', {}, ...['SATURDAY', 'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY']
        .map(d => UI.el('option', { value: d }, d)));
    const openTime = UI.el('input', { type: 'time', value: '09:00' });
    const closeTime = UI.el('input', { type: 'time', value: '23:00' });

    settingsCard.append(
        UI.el('p', { class: 'muted' }, 'Closed restaurants reject checkouts with a clear reason.'),
        openBtn,
        UI.el('h3', { style: 'margin-top:16px' }, 'Restaurant profile'),
        UI.el('div', { class: 'row' }, UI.el('div', {}, rName), UI.el('div', {}, rPhone)),
        rDesc, rPhoto,
        UI.el('label', {}, 'Upload a new photo (replaces the URL)'),
        rFile,
        rAddr,
        UI.el('div', { class: 'row' }, UI.el('div', {}, rLat), UI.el('div', {}, rLng)),
        UI.el('div', { class: 'form-actions' }, UI.el('button', {
            class: 'btn-ghost',
            onclick: async () => {
                try {
                    let photo = rPhoto.value.trim() || null;
                    if (rFile.files && rFile.files[0]) {
                        UI.toast('Uploading photo…');
                        photo = await Cloudinary.upload(rFile.files[0]);
                    }
                    await API.call('/restaurant', {
                        method: 'PUT',
                        body: {
                            name: rName.value.trim(), description: rDesc.value.trim(),
                            photo, address: rAddr.value.trim(),
                            latitude: parseFloat(rLat.value) || null, longitude: parseFloat(rLng.value) || null,
                            phone: rPhone.value.trim()
                        }
                    });
                    rFile.value = '';
                    UI.toast('Restaurant profile saved', 'ok');
                } catch (err) { UI.error(err); }
            }
        }, 'Save profile')),
        UI.el('h3', { style: 'margin-top:16px' }, 'Operating hours (replaces the whole week)'),
        UI.el('div', { class: 'row' }, UI.el('div', {}, day), UI.el('div', {}, openTime), UI.el('div', {}, closeTime)),
        UI.el('div', { class: 'form-actions' }, UI.el('button', {
            class: 'btn-ghost',
            onclick: async () => {
                const hours = [{ day: day.value, openTime: openTime.value, closeTime: closeTime.value }];
                try {
                    await API.call('/restaurant/hours', { method: 'PUT', body: hours });
                    UI.toast('Hours saved', 'ok');
                } catch (err) { UI.error(err); }
            }
        }, 'Save hours')));

    UI.render(
    UI.el('div', { class: 'admin-head' },
        UI.el('div', {},
            UI.el('h1', {}, 'Restaurant profile'),
            UI.el('div', { class: 'sub' }, 'How the restaurant appears to every customer'))),
    settingsCard);
    await loadRestaurant();
}
