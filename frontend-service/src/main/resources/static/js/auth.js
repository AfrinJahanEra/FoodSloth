/**
 * Session handling: login, signup, logout and role checks.
 * The JWT comes from user-service (via the gateway) and lives in localStorage;
 * api.js attaches it to every request.
 */
'use strict';

const Auth = {
    get user() { return API.user(); },
    get role() { const u = API.user(); return u ? u.role : null; },
    get userId() { const u = API.user(); return u ? u.id : null; },
    get loggedIn() { return !!API.token(); },

    async login(identifier, password) {
        const res = await API.call('/users/login', { body: { identifier, password } });
        API.setSession(res.token, res.user);
    },

    async signup(payload) {
        const res = await API.call('/users/signup', { body: payload });
        API.setSession(res.token, res.user);
    },

    logout() {
        API.clearSession();
        location.hash = '#/login';
    },

    /** Where a user lands after signing in, by role. */
    home() {
        switch (Auth.role) {
            case 'ADMIN': return '#/kitchen';
            case 'DELIVERYMAN': return '#/rider';
            default: return '#/';
        }
    }
};
