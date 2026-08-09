/**
 * Rider app: shift toggle, GPS sharing (browser geolocation), current job
 * actions and history. Polls the current assignment every 5s while online.
 */
'use strict';

App.register('/rider', {
    roles: ['DELIVERYMAN'],

    async render() {
        UI.render(UI.el('h1', {}, 'Rider'), UI.loading());

        const profile = await API.call('/deliveries/riders/me').catch(() => null);

        // ---------------- Shift ----------------
        const shiftCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Shift'));
        const displayName = UI.el('input', { type: 'text', value: profile?.displayName || (Auth.user?.name || '') });
        const phone = UI.el('input', { type: 'text', value: profile?.phone || (Auth.user?.phone || '') });
        const vehicleType = UI.el('select', {},
            ...['BIKE', 'MOTORCYCLE', 'BICYCLE'].map(v => UI.el('option', { value: v }, v)));
        if (profile?.vehicleType) vehicleType.value = profile.vehicleType;

        const getPosition = () => new Promise((resolve, reject) => {
            if (!navigator.geolocation) return reject(new Error('Browser geolocation unavailable - enter coordinates via Postman'));
            navigator.geolocation.getCurrentPosition(
                pos => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
                err => reject(new Error('Location denied: ' + err.message)),
                { enableHighAccuracy: true, timeout: 8000 });
        });

        let locationTimer = null;
        const sendLocation = async () => {
            try {
                const pos = await getPosition();
                await API.call('/deliveries/riders/location', { method: 'PUT', body: pos });
                return pos;
            } catch (err) { UI.error(err); return null; }
        };

        const drawShift = (online) => {
            shiftCard.replaceChildren(
                UI.el('h2', {}, 'Shift'),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Status'), UI.chip(online ? 'ONLINE' : 'OFFLINE')));
            if (!online) {
                shiftCard.append(
                    UI.el('label', {}, 'Display name'), displayName,
                    UI.el('label', {}, 'Phone'), phone,
                    UI.el('label', {}, 'Vehicle'), vehicleType,
                    UI.el('div', { class: 'form-actions' }, UI.el('button', {
                        class: 'btn-ok',
                        onclick: async () => {
                            try {
                                const pos = await getPosition();
                                await API.call('/deliveries/riders/online', {
                                    body: {
                                        displayName: displayName.value.trim(),
                                        phone: phone.value.trim(),
                                        vehicleType: vehicleType.value,
                                        latitude: pos.latitude,
                                        longitude: pos.longitude
                                    }
                                });
                                UI.toast('You are online', 'ok');
                                // Keep sharing location while online - this is what drives the ETA.
                                locationTimer = setInterval(sendLocation, 10000);
                                drawShift(true);
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Go online')));
            } else {
                shiftCard.append(
                    UI.el('p', { class: 'muted' }, 'Sharing your GPS every 10 seconds while online.'),
                    UI.el('div', { class: 'form-actions' },
                        UI.el('button', { class: 'btn-ghost', onclick: sendLocation }, 'Send location now'),
                        UI.el('button', {
                            class: 'btn-danger',
                            onclick: async () => {
                                try {
                                    await API.call('/deliveries/riders/offline', { method: 'POST', body: {} });
                                    clearInterval(locationTimer); locationTimer = null;
                                    UI.toast('You are offline');
                                    drawShift(false);
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Go offline')));
            }
        };
        drawShift(profile?.status === 'ONLINE');
        if (profile?.status === 'ONLINE') locationTimer = setInterval(sendLocation, 10000);

        // ---------------- Current job ----------------
        const jobCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Current job'));
        const drawJob = async () => {
            const job = await API.call('/deliveries/riders/me/current'); // 204 -> null
            jobCard.replaceChildren(UI.el('h2', {}, 'Current job'));
            if (!job || !job.id) {
                jobCard.append(UI.el('p', { class: 'muted' },
                    'No job right now. Stay online - assignments arrive automatically.'));
                return;
            }
            const actions = [];
            if (job.status === 'ASSIGNED') {
                actions.push(UI.el('button', {
                    class: 'btn-ok',
                    onclick: async () => {
                        try {
                            await API.call(`/deliveries/${job.id}/accept`, { method: 'PATCH', body: {} });
                            await drawJob();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Accept'));
                actions.push(UI.el('button', {
                    class: 'btn-danger',
                    onclick: async () => {
                        try {
                            await API.call(`/deliveries/${job.id}/decline`, { method: 'PATCH', body: {} });
                            await drawJob();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Decline'));
            }
            if (job.status === 'ACCEPTED') {
                actions.push(UI.el('button', {
                    onclick: async () => {
                        try {
                            await API.call(`/deliveries/${job.id}/picked-up`, { method: 'PATCH', body: {} });
                            await drawJob();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Picked up the food'));
            }
            if (job.status === 'PICKED_UP') {
                actions.push(UI.el('button', {
                    class: 'btn-ok',
                    onclick: async () => {
                        try {
                            await API.call(`/deliveries/${job.id}/delivered`, { method: 'PATCH', body: {} });
                            UI.toast('Delivered - nice work!', 'ok');
                            await drawJob();
                        } catch (err) { UI.error(err); }
                    }
                }, 'Mark delivered'));
            }

            jobCard.append(
                UI.el('div', { class: 'line-item' },
                    UI.el('b', {}, 'Order ' + job.orderId.slice(0, 8) + '…'), UI.chip(job.status)),
                UI.el('p', { class: 'muted' }, 'Drop: ' + (job.dropAddressLabel || '-')),
                UI.el('p', { class: 'muted' },
                    job.etaMinutes != null ? 'ETA ' + job.etaMinutes + ' min · ' : '',
                    job.remainingDistanceKm != null ? job.remainingDistanceKm.toFixed(1) + ' km left' : ''),
                UI.el('div', { class: 'form-actions' }, ...actions));
        };

        // ---------------- History ----------------
        const historyCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'History'));
        const drawHistory = async () => {
            const history = await API.call('/deliveries/riders/me/history');
            historyCard.replaceChildren(UI.el('h2', {}, 'History (' + history.length + ')'));
            if (!history.length) historyCard.append(UI.el('p', { class: 'muted' }, 'Nothing delivered yet.'));
            for (const job of history) {
                historyCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Order ' + job.orderId.slice(0, 8) + '… · ' + UI.time(job.deliveredAt || job.updatedAt)),
                    UI.chip(job.status)));
            }
        };

        UI.render(UI.el('h1', {}, 'Rider'), shiftCard, jobCard, historyCard);
        await Promise.all([drawJob().catch(UI.error), drawHistory().catch(UI.error)]);

        const stop = UI.poll(async () => { await drawJob(); }, 5000);
        return () => { stop(); clearInterval(locationTimer); };
    }
});
