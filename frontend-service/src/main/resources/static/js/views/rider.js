/**
 * Rider app - page-based, like a real delivery app:
 *   Home     shift toggle, current-job teaser, recent history
 *   Job      order & customer info on the left, live GPS route map on the right
 *   History  everything handled so far
 * Every sub-page has a back button. The current job is polled every 5s.
 *
 * Flow: the admin hands the job to an online rider (mandatory); the rider accepts,
 * follows the route to the restaurant, picks up, follows the route to the customer
 * and marks it delivered.
 */
'use strict';

App.register('/rider', {
    roles: ['DELIVERYMAN'],

    async render() {
        let page = 'home';
        let autoOpenedJob = false;
        let lastJobSig = null;
        let isOnline = false;
        const root = UI.el('div', {});
        UI.render(root);

        const profile = await API.call('/deliveries/riders/me').catch(() => null);

        // ---------------- shared helpers ----------------

        const hasCoords = (p) => !!p && p.latitude != null && p.longitude != null
            && !(Math.abs(p.latitude) < 0.01 && Math.abs(p.longitude) < 0.01);

        const getPosition = () => new Promise((resolve, reject) => {
            if (!navigator.geolocation) return reject(new Error('Browser geolocation unavailable'));
            navigator.geolocation.getCurrentPosition(
                pos => resolve({ latitude: pos.coords.latitude, longitude: pos.coords.longitude, accuracy: pos.coords.accuracy }),
                err => reject(new Error('Location denied: ' + err.message)),
                { enableHighAccuracy: true, maximumAge: 0, timeout: 8000 });
        });

        const go = (p) => { page = p; lastJobSig = null; draw().catch(UI.error); };

        /** Friendly order label: the sequential #number when known; never a database id. */
        const orderRef = (job) => job.orderNo
            ? 'Order #' + job.orderNo
            : 'Order';

        /** Renders whatever the server just returned, so actions never wait on the next poll. */
        const renderJob = (job) => { lastJobSig = null; return drawJob(job); };

        const backBtn = (label) => UI.el('button', {
            class: 'btn-ghost btn-small',
            onclick: () => go('home')
        }, '← ' + label);

        const JOB_STEPS = ['ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'DELIVERED'];
        const JOB_LABELS = { ASSIGNED: 'Assigned', ACCEPTED: 'To restaurant', PICKED_UP: 'To customer', DELIVERED: 'Delivered' };

        const progressOf = (status) => {
            const doneIndex = JOB_STEPS.indexOf(status);
            return UI.el('div', { class: 'progress' },
                ...JOB_STEPS.map((step, i) => UI.el('div', {
                    class: 'step' + (i <= doneIndex ? ' done' : '')
                }, JOB_LABELS[step])));
        };

        /** Live route map: from the rider's real-time position to the next stop - the
         *  restaurant until the food is picked up, the customer afterwards. When no live
         *  fix has arrived yet the restaurant stands in as the start point. */
        const mapOf = (job) => {
            const box = UI.el('div', {});
            const live = hasCoords(job.riderLocation) ? job.riderLocation : null;
            const pickedUp = job.status === 'PICKED_UP' || job.status === 'DELIVERED';
            const dropText = (job.dropAddressLabel || '').trim();

            // Destination: the restaurant on the first leg, the customer after pickup.
            const destPoint = pickedUp ? job.drop : job.pickup;
            const dest = hasCoords(destPoint) ? destPoint.latitude + ',' + destPoint.longitude
                : pickedUp && dropText ? encodeURIComponent(dropText) : null;

            if (!dest) {
                box.append(
                    UI.el('p', { class: 'muted' }, 'No map coordinates for this delivery.'),
                    UI.el('p', {}, UI.el('b', {}, dropText || 'Address not specified')),
                    UI.el('p', { class: 'muted' }, 'Call the customer to find the exact spot.'));
                return box;
            }

            const origin = live ? live.latitude + ',' + live.longitude
                : hasCoords(job.pickup) ? job.pickup.latitude + ',' + job.pickup.longitude : null;
            // Never draw a from == to route: a single marker is enough then.
            const from = origin && origin !== dest ? origin : null;
            const embed = 'https://maps.google.com/maps?'
                + (from ? 'saddr=' + from + '&' : '')
                + 'daddr=' + dest + '&output=embed';
            const navUrl = 'https://www.google.com/maps/dir/?api=1'
                + (from ? '&origin=' + from : '')
                + '&destination=' + dest + '&travelmode=driving';

            const stopName = pickedUp ? (dropText || 'the customer') : 'the restaurant';
            box.append(
                UI.el('iframe', { class: 'map-frame', src: embed, loading: 'lazy', title: 'Route map' }),
                UI.el('div', { class: 'map-legend muted' },
                    UI.el('span', {}, live ? 'Live route: your position → ' + stopName
                        : 'Route: the restaurant → ' + stopName)),
                !live ? UI.el('p', { class: 'muted' },
                    'Waiting for your GPS - the start point jumps to your live position once it reports.') : null,
                pickedUp && !hasCoords(job.drop) && dropText ? UI.el('p', { class: 'muted' },
                    'The customer address has no GPS pin - navigating to the written address instead.') : null,
                UI.el('div', { class: 'form-actions' },
                    UI.el('a', {
                        href: navUrl, target: '_blank', rel: 'noopener',
                        class: 'btn btn-ok btn-small'
                    }, 'Navigate with Google Maps')));
            return box;
        };

        // ---------------- live GPS stream ----------------

        // While online, the browser position is watched continuously and pushed to Delivery
        // Service every 10s; the open job mirrors it, so the rider map and the customer's
        // tracking page both follow the ride in real time. The rider can also hit
        // "Send live location" any time to push a fresh fix immediately.
        let lastFix = null;
        let gpsWatch = null;
        let lastSentAt = null;
        let lastAccuracy = null;
        const startGps = () => {
            if (gpsWatch != null || !navigator.geolocation) return;
            gpsWatch = navigator.geolocation.watchPosition(
                pos => {
                    lastFix = { latitude: pos.coords.latitude, longitude: pos.coords.longitude };
                    lastAccuracy = pos.coords.accuracy != null ? Math.round(pos.coords.accuracy) : null;
                },
                () => { /* denied or unavailable - keep the last known fix */ },
                { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 });
        };

        /** One manual push right now - the customer's tracking page picks it up on its next poll. */
        const sendLocationNow = async () => {
            try {
                const pos = lastFix || await getPosition();
                lastFix = { latitude: pos.latitude, longitude: pos.longitude };
                if (pos.accuracy != null) lastAccuracy = Math.round(pos.accuracy);
                await API.call('/deliveries/riders/me/location', { method: 'POST', body: lastFix });
                lastSentAt = new Date();
                UI.toast('Live location sent - the customer can follow you on the map', 'ok');
            } catch (err) { UI.error(err); }
        };

        /** The manual send button plus a hint about the automatic 10s sharing. */
        const liveLocationRow = () => UI.el('div', { class: 'form-actions', style: 'margin:0' },
            UI.el('button', { class: 'btn-ok btn-small', onclick: sendLocationNow }, 'Send live location'),
            UI.el('span', { class: 'muted' }, lastSentAt
                ? 'Last sent ' + lastSentAt.toLocaleTimeString() +
                (lastAccuracy != null ? ' (±' + lastAccuracy + ' m)' : '') + ' · auto-shares every 10s while online' +
                (lastAccuracy != null && lastAccuracy > 200 ? ' · network-based fix on this device - open on a phone for exact GPS' : '')
                : 'Shares automatically every 10s while online'));

        // ---------------- Home page ----------------

        /**
         * Sidebar: identity, shift state and the slot progress bar. The bar mirrors
         * slotsUsedToday from Delivery Service, which reserves a slot the moment the admin
         * assigns a delivery (delivery.assigned) and releases it when the job is done - so
         * polling it keeps the bar in step with every assignment the rider accepts.
         */
        const sidebarOf = (rider, job) => {
            const used = rider?.slotsUsedToday || 0;
            const total = rider ? used + (rider.slotsRemainingToday || 0) : 0;
            const pct = total > 0 ? Math.min(100, Math.round(used / total * 100)) : 0;
            const name = rider?.displayName || (Auth.user?.name || 'Rider');
            const initials = name.trim().split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
            const full = total > 0 && used >= total;

            const side = UI.el('div', { class: 'card rider-sidebar' },
                UI.el('div', { class: 'rider-id' },
                    UI.el('div', { class: 'avatar' }, initials),
                    UI.el('div', {},
                        UI.el('b', {}, name),
                        UI.el('div', { class: 'muted' },
                            (rider?.vehicleType || 'No vehicle') + (rider?.status ? ' · ' + rider.status : '')))));

            side.append(UI.el('div', { class: 'line-item' },
                UI.el('span', {}, 'Shift'),
                UI.chip(rider && rider.status !== 'OFFLINE' ? 'ONLINE' : 'OFFLINE')));

            side.append(
                UI.el('h2', { style: 'margin-top:12px' }, "Today's slots"),
                UI.el('div', { class: 'slot-bar big' },
                    UI.el('span', { style: 'width:' + pct + '%;background:' + (full ? 'var(--brand)' : 'var(--ok)') })),
                UI.el('div', { class: 'rider-meta' },
                    UI.el('span', { class: 'muted' }, used + ' of ' + (total || 0) + ' used'),
                    UI.el('b', {}, total > 0 ? (total - used) + ' left' : '—')),
                UI.el('p', { class: 'muted' },
                    'The bar grows when the admin assigns you a delivery and shrinks when a job is done.'));

            side.append(UI.el('div', { class: 'line-item' },
                UI.el('span', {}, 'Current job'),
                job && job.id ? UI.chip(job.status) : UI.el('span', { class: 'muted' }, 'None')));
            return side;
        };

        const drawHome = async () => {
            const rider = await API.call('/deliveries/riders/me').catch(() => null);
            const online = !!rider && rider.status !== 'OFFLINE';
            isOnline = online;
            if (online) startGps();

            const shiftCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Shift'));
            const displayName = UI.el('input', { type: 'text', value: rider?.displayName || (Auth.user?.name || '') });
            const phone = UI.el('input', { type: 'text', value: rider?.phone || (Auth.user?.phone || '') });
            const vehicleType = UI.el('select', {},
                ...['BIKE', 'MOTORCYCLE', 'BICYCLE'].map(v => UI.el('option', { value: v }, v)));
            if (rider?.vehicleType) vehicleType.value = rider.vehicleType;

            shiftCard.append(UI.el('div', { class: 'line-item' },
                UI.el('span', {}, 'Status'), UI.chip(online ? 'ONLINE' : 'OFFLINE')));

            if (!online) {
                shiftCard.append(
                    UI.el('p', { class: 'muted' }, 'Go online to receive deliveries. The admin assigns jobs to free riders - accepting is mandatory.'),
                    UI.el('label', {}, 'Display name'), displayName,
                    UI.el('label', {}, 'Phone'), phone,
                    UI.el('label', {}, 'Vehicle'), vehicleType,
                    UI.el('div', { class: 'form-actions' }, UI.el('button', {
                        class: 'btn-ok',
                        onclick: async () => {
                            try {
                                const pos = await getPosition();
                                const r = await API.call('/deliveries/riders/online', {
                                    body: {
                                        displayName: displayName.value.trim(),
                                        phone: phone.value.trim(),
                                        vehicleType: vehicleType.value,
                                        latitude: pos.latitude,
                                        longitude: pos.longitude
                                    }
                                });
                                UI.toast('You are online - ' + r.slotsRemainingToday + ' slot(s) left today', 'ok');
                                await drawHome();
                            } catch (err) { UI.error(err); }
                        }
                    }, 'Go online')));
            } else {
                shiftCard.append(
                    UI.el('div', { class: 'line-item' },
                        UI.el('span', {}, 'Slots left today'),
                        UI.el('span', {}, UI.el('b', {}, String(rider.slotsRemainingToday)),
                            ' of ' + (rider.slotsRemainingToday + rider.slotsUsedToday))),
                    UI.el('div', { class: 'form-actions' },
                        UI.el('button', {
                            class: 'btn-danger',
                            onclick: async () => {
                                try {
                                    await API.call('/deliveries/riders/offline', { method: 'POST', body: {} });
                                    UI.toast('You are offline');
                                    await drawHome();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Go offline')));
            }

            // Current job teaser with the primary action right here, so a new assignment can
            // always be acted on even without opening the full job page.
            const job = await API.call('/deliveries/riders/me/current').catch(() => null);
            const jobCard = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'Current job'));
            if (job && job.id) {
                jobCard.append(
                    UI.el('div', { class: 'line-item' },
                        UI.el('div', {},
                            UI.el('b', {}, orderRef(job) + ' '), UI.chip(job.status),
                            UI.el('div', { class: 'muted' }, 'Drop: ' + (job.dropAddressLabel || '-'))),
                        UI.el('button', { class: 'btn-small', onclick: () => go('job') }, 'Open job')));
                if (job.status === 'ASSIGNED') {
                    jobCard.append(
                        UI.el('p', { class: 'muted', style: 'color:var(--brand);font-weight:600' },
                            'New delivery assigned to you - accept it to start.'),
                        UI.el('div', { class: 'form-actions', style: 'margin:0' }, UI.el('button', {
                            class: 'btn-ok',
                            onclick: async () => {
                                try {
                                    await API.call(`/deliveries/${job.id}/accept`, { method: 'PATCH', body: {} });
                                    UI.toast('Accepted - order is now on the way', 'ok');
                                    await drawHome();
                                } catch (err) { UI.error(err); }
                            }
                        }, 'Accept assignment')));
                }
            } else {
                jobCard.append(UI.el('p', { class: 'muted' },
                    'No job right now. Stay online - the admin assigns deliveries to free riders.'));
            }

            // Recent history teaser.
            const history = await API.call('/deliveries/riders/me/history').catch(() => []);
            const historyCard = UI.el('div', { class: 'card' },
                UI.el('h2', {}, 'Recent deliveries'),
                UI.el('div', { class: 'form-actions', style: 'margin:0 0 8px' },
                    UI.el('button', { class: 'btn-ghost btn-small', onclick: () => go('history') }, 'View all')));
            if (!history.length) {
                historyCard.append(UI.el('p', { class: 'muted' }, 'Nothing delivered yet.'));
            }
            for (const h of history.slice(0, 3)) {
                historyCard.append(UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, orderRef(h) + ' · ' + UI.time(h.deliveredAt || h.updatedAt)),
                    UI.chip(h.status)));
            }

            root.replaceChildren(
                UI.el('div', { class: 'page-head' }, UI.el('h1', {}, 'Rider dashboard')),
                UI.el('div', { class: 'rider-layout' },
                    sidebarOf(rider, job),
                    UI.el('div', { class: 'rider-main' },
                        shiftCard,
                        UI.el('div', { class: 'row' }, jobCard, historyCard))));

            // A fresh assignment demands attention: open the job page directly (once).
            if (job && job.id && job.status === 'ASSIGNED' && page === 'home' && !autoOpenedJob) {
                autoOpenedJob = true;
                go('job');
            }
        };

        // ---------------- Job page (split view) ----------------

        const drawJob = async (prefetched) => {
            let jobError = null;
            let job = prefetched || null;
            if (!job) {
                try {
                    job = await API.call('/deliveries/riders/me/current');
                } catch (err) { jobError = err; }
            }
            if (!job || !job.id) {
                // Only bounce home when there is genuinely nothing to do; a fetch failure
                // shows an error card instead, so a network blip can never hide the job.
                if (!jobError) {
                    UI.toast('No active job right now');
                    page = 'home';
                    return drawHome();
                }
                root.replaceChildren(
                    UI.el('div', { class: 'page-head' }, backBtn('Dashboard'), UI.el('h1', {}, 'Current job')),
                    UI.el('div', { class: 'card' },
                        UI.el('p', { class: 'muted' }, 'Could not load your current job: ' + jobError.message),
                        UI.el('div', { class: 'form-actions' }, UI.el('button', {
                            onclick: () => { lastJobSig = null; drawJob().catch(UI.error); }
                        }, 'Try again'))));
                return;
            }

            // Skip re-render while nothing meaningful changed (keeps the map iframe steady).
            const r3 = (v) => v != null ? Math.round(v * 1000) / 1000 : null;
            const sig = [job.status, r3(job.riderLocation?.latitude), r3(job.riderLocation?.longitude),
                job.etaMinutes, job.remainingDistanceKm].join('|');
            if (sig === lastJobSig) return;
            lastJobSig = sig;

            const infoCard = UI.el('div', { class: 'card' },
                UI.el('h2', {}, 'Order info'),
                progressOf(job.status),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Order'), UI.el('b', {}, orderRef(job))),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Status'), UI.chip(job.status)),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Drop address'),
                    UI.el('span', {}, job.dropAddressLabel || '-')),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Customer phone'),
                    UI.el('span', {}, job.customerPhone || '-')),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'ETA'),
                    UI.el('span', {}, job.etaMinutes != null ? job.etaMinutes + ' min' : '—')),
                UI.el('div', { class: 'line-item' },
                    UI.el('span', {}, 'Distance left'),
                    UI.el('span', {}, job.remainingDistanceKm != null ? job.remainingDistanceKm.toFixed(1) + ' km' : '—')),
                liveLocationRow());

            const mapCard = UI.el('div', { class: 'card' },
                UI.el('h2', {}, (job.status === 'ASSIGNED' || job.status === 'ACCEPTED')
                    ? 'Route to the restaurant' : 'Route to the customer'),
                mapOf(job));

            const actions = [];
            if (job.status === 'ASSIGNED') {
                actions.push(UI.el('button', {
                    class: 'btn-ok',
                    onclick: async () => {
                        try {
                            const updated = await API.call(`/deliveries/${job.id}/accept`, { method: 'PATCH', body: {} });
                            UI.toast('Accepted - order is now on the way', 'ok');
                            await renderJob(updated && updated.id ? updated : null);
                        } catch (err) { UI.error(err); }
                    }
                }, 'Accept assignment'));
            }
            if (job.status === 'ACCEPTED') {
                actions.push(UI.el('button', {
                    onclick: async () => {
                        try {
                            const updated = await API.call(`/deliveries/${job.id}/picked-up`, { method: 'PATCH', body: {} });
                            UI.toast('Picked up - head to the customer', 'ok');
                            await renderJob(updated && updated.id ? updated : null);
                        } catch (err) { UI.error(err); }
                    }
                }, 'Picked up the food'));
            }
            if (job.status === 'PICKED_UP') {
                actions.push(UI.el('button', {
                    class: 'btn-ok',
                    onclick: async () => {
                        try {
                            const updated = await API.call(`/deliveries/${job.id}/delivered`, { method: 'PATCH', body: {} });
                            UI.toast('Delivered - nice work!', 'ok');
                            await renderJob(updated && updated.id ? updated : null);
                        } catch (err) { UI.error(err); }
                    }
                }, 'Mark delivered'));
            }

            root.replaceChildren(
                UI.el('div', { class: 'page-head' },
                    backBtn('Dashboard'),
                    UI.el('h1', {}, job.orderNo ? 'Delivery #' + job.orderNo : 'Delivery'),
                    UI.chip(job.status)),
                UI.el('div', { class: 'job-grid' }, infoCard, mapCard),
                UI.el('div', { class: 'form-actions', style: 'margin-top:16px' }, ...actions));
        };

        // ---------------- History page ----------------

        const drawHistory = async () => {
            const history = await API.call('/deliveries/riders/me/history');
            const card = UI.el('div', { class: 'card' }, UI.el('h2', {}, 'History (' + history.length + ')'));
            if (!history.length) card.append(UI.el('p', { class: 'muted' }, 'Nothing delivered yet.'));
            for (const job of history) {
                card.append(UI.el('div', { class: 'line-item' },
                    UI.el('div', {},
                        UI.el('b', {}, orderRef(job) + ' '), UI.chip(job.status),
                        UI.el('div', { class: 'muted' },
                            (job.dropAddressLabel || '-') + ' · ' + UI.time(job.deliveredAt || job.updatedAt))),
                    UI.el('span', { class: 'muted' },
                        job.remainingDistanceKm != null ? job.remainingDistanceKm.toFixed(1) + ' km' : '')));
            }
            root.replaceChildren(
                UI.el('div', { class: 'page-head' }, backBtn('Dashboard'), UI.el('h1', {}, 'Delivery history')),
                card);
        };

        const draw = async () => {
            if (page === 'job') return drawJob();
            if (page === 'history') return drawHistory();
            return drawHome();
        };

        await draw();

        // While on the job page, keep the map and info fresh; while online on the dashboard,
        // keep the sidebar slot bar in step with new assignments and accepts.
        // Push the latest GPS fix while online; failures (service down, GPS off) are silent.
        const pushTimer = setInterval(() => {
            if (!isOnline || !lastFix) return;
            API.call('/deliveries/riders/me/location', { method: 'POST', body: lastFix })
                .then(() => { lastSentAt = new Date(); }).catch(() => { });
        }, 10000);

        const stopPoll = UI.poll(async () => {
            if (page === 'job') await drawJob();
            else if (page === 'home' && isOnline) await drawHome();
        }, 5000);

        return () => {
            clearInterval(pushTimer);
            if (gpsWatch != null && navigator.geolocation) navigator.geolocation.clearWatch(gpsWatch);
            stopPoll();
        };
    }
});
