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
        UI.render(UI.el('h1', {}, 'Track order ' + orderId.slice(0, 8) + '…'), card);

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

            const progress = UI.el('div', { class: 'progress' },
                ...steps.map((step, i) => UI.el('div', {
                    class: 'step' + (i <= doneIndex ? ' done' : '')
                }, stepLabels[step])));

            card.replaceChildren(
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
                        UI.el('div', { class: 'label' }, 'Rider phone'))),
                UI.el('p', { class: 'muted' },
                    'Delivering to: ' + (track.dropAddressLabel || '-') +
                    ' · Last GPS fix: ' + UI.time(track.riderLocationUpdatedAt) +
                    (track.riderLatitude != null ? ` at ${track.riderLatitude.toFixed(4)}, ${track.riderLongitude.toFixed(4)}` : ''))
            );
            return status !== 'DELIVERED' && status !== 'CANCELLED';
        };

        let keepGoing = await draw();
        const stop = UI.poll(async () => { keepGoing = await draw(); if (!keepGoing) stop(); }, 5000);
        return stop;
    }
});
