/**
 * Live rider tracking - polls GET /deliveries/order/{orderId}/track every 5s
 * while the delivery is moving.
 */
'use strict';

App.register('/track', {
    roles: ['CUSTOMER'],

    async render(orderId) {
        if (!orderId) { location.hash = '#/orders'; return; }

        UI.render(UI.el('h1', {}, 'Track order'), UI.loading('Finding the delivery...'));

        const card = UI.el('div', { class: 'card' });
        const mapCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Live map'));
        const notesCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Order updates'));
        const title = UI.el('h1', {}, 'Track order');
        UI.render(
            UI.el('div', { class: 'page-head' },
                UI.el('button', { class: 'btn-ghost btn-small', onclick: () => location.hash = '#/orders' }, '← Orders'),
                title),
            card, mapCard, notesCard);

        const has = (lat, lng) => lat != null && lng != null
            && !(Math.abs(lat) < 0.01 && Math.abs(lng) < 0.01);
        let lastMapUrl = null;

        /** Route map: from the rider's live position (or the restaurant before
         *  the first GPS fix) to the customer's area. Rebuilt only when it changes.
         *  Without a drop pin the rider's live position is shown on its own, so the
         *  customer still sees the rider move in real time. */
        const drawMap = (track) => {
            const r3 = (v) => Math.round(v * 1000) / 1000;
            const riderLive = has(track.riderLatitude, track.riderLongitude);

            if (!has(track.dropLatitude, track.dropLongitude)) {
                if (riderLive) {
                    const url = 'https://maps.google.com/maps?daddr='
                        + r3(track.riderLatitude) + ',' + r3(track.riderLongitude) + '&output=embed';
                    if (url === lastMapUrl) return;
                    lastMapUrl = url;
                    mapCard.replaceChildren(
                        UI.el('h2', {}, 'Live map'),
                        UI.el('iframe', { class: 'map-frame', src: url, loading: 'lazy', title: 'Rider live position map' }),
                        UI.el('div', { class: 'map-legend muted' },
                            UI.el('span', {}, 'The rider\u2019s live position - your address has no GPS pin, the rider will call if needed.')));
                } else if (lastMapUrl !== 'none') {
                    lastMapUrl = 'none';
                    mapCard.replaceChildren(UI.el('h2', {}, 'Live map'),
                        UI.el('p', { class: 'muted' }, 'This address has no map coordinates - the rider will call you if needed.'));
                }
                return;
            }
            const drop = track.dropLatitude + ',' + track.dropLongitude;
            const from = riderLive
                ? r3(track.riderLatitude) + ',' + r3(track.riderLongitude)
                : track.pickupLatitude + ',' + track.pickupLongitude;
            const url = 'https://maps.google.com/maps?saddr=' + from + '&daddr=' + drop + '&output=embed';
            if (url === lastMapUrl) return;
            lastMapUrl = url;
            mapCard.replaceChildren(
                UI.el('h2', {}, 'Live map'),
                UI.el('iframe', { class: 'map-frame', src: url, loading: 'lazy', title: 'Delivery route map' }),
                UI.el('div', { class: 'map-legend muted' },
                    UI.el('span', {}, 'Route: restaurant → ' + (track.dropAddressLabel || 'your address')),
                    UI.el('span', {}, riderLive
                        ? 'Start point follows the rider\u2019s live GPS.'
                        : 'Rider GPS not live yet - showing the restaurant as start.')));
        };

        const steps = ['ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED'];
        const stepLabels = { ASSIGNED: 'Rider assigned', ACCEPTED: 'To restaurant', PICKED_UP: 'To you', DELIVERED: 'Delivered' };

        const draw = async () => {
            let track;
            try {
                track = await API.call(`/deliveries/order/${orderId}/track`);
            } catch (err) {
                card.replaceChildren(UI.el('p', { class: 'muted' },
                    'No delivery yet - it appears when the kitchen marks the food ready. ' + err.message));
                return true; // keep polling
            }

            const status = track.status || 'PENDING_ASSIGNMENT';
            const doneIndex = steps.indexOf(status);

            // Show the friendly #number once Delivery Service knows it.
            if (track.orderNo) title.replaceChildren('Track order #' + track.orderNo);

            const progress = UI.el('div', { class: 'progress' },
                ...steps.map((step, i) => UI.el('div', {
                    class: 'step' + (i <= doneIndex ? ' done' : '')
                }, stepLabels[step])));

            const cardKids = [
                UI.el('div', { class: 'line-item' },
                    UI.el('b', {}, track.riderName || 'Looking for a rider...'),
                    UI.chip(status)),
                progress,
                UI.el('div', { class: 'track-stats' },
                    UI.el('div', { class: 'stat' },
                        UI.el('div', { class: 'value' }, track.etaMinutes != null ? track.etaMinutes + ' min' : '—'),
                        UI.el('div', { class: 'label' }, 'Estimated arrival')),
                    UI.el('div', { class: 'stat' },
                        UI.el('div', { class: 'value' }, track.remainingDistanceKm != null ? track.remainingDistanceKm.toFixed(1) + ' km' : '—'),
                        UI.el('div', { class: 'label' }, 'Remaining')),
                    UI.el('div', { class: 'stat' },
                        UI.el('div', { class: 'value' }, track.riderPhone || '—'),
                        UI.el('div', { class: 'label' }, 'Rider phone')))
            ];
            if (!has(track.dropLatitude, track.dropLongitude)) {
                cardKids.push(UI.el('p', { class: 'muted' },
                    'Distance and ETA show as dashes because this address has no GPS pin - ' +
                    'the map still follows the rider live. Pin the address (Profile → Edit → ' +
                    '“Use my current location”) and future orders get full estimates.'));
            }
            cardKids.push(UI.el('p', { class: 'muted' },
                'Delivering to: ' + (track.dropAddressLabel || '-') +
                ' · Last GPS fix: ' + UI.time(track.riderLocationUpdatedAt) +
                (has(track.riderLatitude, track.riderLongitude) ? ` at ${track.riderLatitude.toFixed(4)}, ${track.riderLongitude.toFixed(4)}` : '')));
            card.replaceChildren(...cardKids);

            drawMap(track);

            // Order-related notifications (GET /notifications/order/{orderId})
            const notes = await API.call(`/notifications/order/${orderId}`).catch(() => []);
            notesCard.replaceChildren(UI.el('h2', {}, 'Order updates (' + notes.length + ')'));
            if (!notes.length) {
                notesCard.append(UI.el('p', { class: 'muted' }, 'Status changes land here as notifications.'));
            }
            for (const n of notes) {
                notesCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, n.title || n.type || 'Update'),
                        UI.el('div', { class: 'muted' }, n.body || ''),
                        UI.el('div', { class: 'muted' }, UI.time(n.createdAt) + ' · ' + (n.channel || '')))));
            }
            return status !== 'DELIVERED' && status !== 'CANCELLED';
        };

        let keepGoing = await draw();
        const stop = UI.poll(async () => { keepGoing = await draw(); if (!keepGoing) stop(); }, 5000);
        return stop;
    }
});
