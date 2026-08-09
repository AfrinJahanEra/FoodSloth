/**
 * Menu management (admin) - add / edit / toggle availability / delete items.
 * Photos go straight to Cloudinary when a file is picked; a pasted URL works too.
 */
'use strict';

App.register('/admin-menu', {
    roles: ['ADMIN'],

    async render() {
        UI.render(UI.el('h1', {}, 'Menu'), UI.loading());

        const head = UI.el('div', { class: 'admin-head' },
            UI.el('div', {},
                UI.el('h1', {}, 'Menu'),
                UI.el('div', { class: 'sub' }, 'Add, edit, price and stock every dish the restaurant sells')));

        const menuCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Menu'));
        const formCard = UI.el('div', { class: 'card' });
        const itemName = UI.el('input', { type: 'text', placeholder: 'Name' });
        const itemDesc = UI.el('input', { type: 'text', placeholder: 'Description' });
        const itemCategory = UI.el('input', { type: 'text', placeholder: 'Category' });
        const itemPrice = UI.el('input', { type: 'number', step: '0.01', placeholder: 'Price (taka)' });
        const itemFile = UI.el('input', { type: 'file', accept: 'image/*' });
        const itemPhoto = UI.el('input', { type: 'text', placeholder: '…or paste an image URL' });
        let editingItemId = null;
        const menuFormTitle = UI.el('h2', {}, 'Add item');
        const clearMenuForm = () => {
            editingItemId = null;
            menuFormTitle.replaceChildren('Add item');
            menuSubmit.replaceChildren('Add to menu');
            itemName.value = itemPrice.value = itemDesc.value = itemCategory.value = itemPhoto.value = '';
            itemFile.value = '';
        };

        const drawMenu = async () => {
            const menu = await API.call('/restaurant/menu');
            menuCard.replaceChildren(UI.el('h2', {}, 'Menu (' + menu.length + ')'));
            if (!menu.length) {
                menuCard.append(UI.el('p', { class: 'muted' }, 'The menu is empty - add the first item below.'));
            }
            for (const item of menu) {
                menuCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', { style: 'display:flex;align-items:center;gap:12px' },
                        item.photo ? UI.el('img', {
                            class: 'thumb-mini', src: item.photo, alt: item.name,
                            onerror: (e) => e.target.replaceWith(UI.el('div', { class: 'thumb-mini thumb-empty' }, Icon.of('utensils', 18)))
                        }) : UI.el('div', { class: 'thumb-mini thumb-empty' }, Icon.of('utensils', 18)),
                        UI.el('div', {},
                            UI.el('div', { style: 'display:flex;align-items:center;gap:8px' },
                                UI.el('b', {}, item.name),
                                item.available === false ? UI.el('span', { class: 'chip err' }, 'Sold out') : null),
                            UI.el('div', { class: 'muted' }, (item.category || '-') + ' · ' + UI.money(item.price)))),
                    UI.el('div', { style: 'display:flex;gap:8px' },
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: async () => {
                                try {
                                    const full = await API.call('/restaurant/menu/' + item.id);
                                    editingItemId = full.id;
                                    menuFormTitle.replaceChildren('Edit item');
                                    menuSubmit.replaceChildren('Update item');
                                    itemName.value = full.name || ''; itemDesc.value = full.description || '';
                                    itemCategory.value = full.category || ''; itemPrice.value = full.price ?? '';
                                    itemPhoto.value = full.photo || ''; itemFile.value = '';
                                    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Edit'),
                        UI.el('button', {
                            class: 'btn-ghost btn-small',
                            onclick: async () => {
                                try {
                                    await API.call('/restaurant/menu/' + item.id, {
                                        method: 'PUT',
                                        body: { ...item, available: !item.available }
                                    });
                                    await drawMenu();
                                } catch (err) { UI.error(err); }
                            }
                        }, item.available === false ? 'Restock' : 'Sold out'),
                        UI.el('button', {
                            class: 'btn-danger btn-small',
                            onclick: async () => {
                                if (!confirm('Delete ' + item.name + ' from the menu?')) return;
                                try {
                                    await API.call('/restaurant/menu/' + item.id, { method: 'DELETE' });
                                    await drawMenu();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Delete'))));
            }
        };

        const menuSubmit = UI.el('button', {
            onclick: async () => {
                const body = {
                    name: itemName.value.trim(),
                    description: itemDesc.value.trim(),
                    category: itemCategory.value.trim(),
                    price: parseFloat(itemPrice.value) || 0,
                    photo: itemPhoto.value.trim() || null,
                    available: true
                };
                try {
                    // A picked file wins over a pasted URL; it goes straight to Cloudinary.
                    if (itemFile.files && itemFile.files[0]) {
                        UI.toast('Uploading photo…');
                        body.photo = await Cloudinary.upload(itemFile.files[0]);
                    }
                    if (editingItemId) {
                        await API.call('/restaurant/menu/' + editingItemId, { method: 'PUT', body });
                        UI.toast('Item updated', 'ok');
                    } else {
                        await API.call('/restaurant/menu', { body });
                        UI.toast('Item added', 'ok');
                    }
                    clearMenuForm();
                    await drawMenu();
                } catch (err) { UI.error(err); }
            }
        }, 'Add to menu');

        formCard.append(
            menuFormTitle,
            UI.el('div', { class: 'row' },
                UI.el('div', {}, itemName),
                UI.el('div', {}, itemPrice)),
            UI.el('div', { class: 'row' },
                UI.el('div', {}, itemDesc),
                UI.el('div', {}, itemCategory)),
            UI.el('label', {}, 'Photo'),
            itemFile,
            itemPhoto,
            UI.el('div', { class: 'form-actions' }, menuSubmit, UI.el('button', {
                class: 'btn-ghost', onclick: clearMenuForm
            }, 'Clear')));

        UI.render(head, menuCard, formCard);
        await drawMenu();
    }
});
