/**
 * The only place that talks to the backend. Every screen goes through api(),
 * so the gateway URL, the JWT header and error handling exist exactly once.
 */
'use strict';

const API = {
    /** All REST calls go to api-gateway; it verifies the JWT and routes onward. */
    BASE: 'http://localhost:8080',

    token() { return localStorage.getItem('fs_token'); },
    user() {
        try { return JSON.parse(localStorage.getItem('fs_user')); }
        catch { return null; }
    },

    setSession(token, user) {
        localStorage.setItem('fs_token', token);
        localStorage.setItem('fs_user', JSON.stringify(user));
    },

    clearSession() {
        localStorage.removeItem('fs_token');
        localStorage.removeItem('fs_user');
    },

    /**
     * api('/orders/me')                          -> GET
     * api('/carts/x/items', { method:'POST', body:{...} })
     * Throws Error(serverMessage) on any non-2xx response.
     */
    async call(path, opts = {}) {
        const headers = { 'Content-Type': 'application/json' };
        const token = API.token();
        if (token) headers['Authorization'] = 'Bearer ' + token;

        const method = opts.method || (opts.body ? 'POST' : 'GET');
        const send = () => fetch(API.BASE + path, {
            method,
            headers,
            body: opts.body ? JSON.stringify(opts.body) : undefined
        });

        let res = await send();
        // A 5xx usually means a service was mid-restart when the gateway routed the call.
        // GETs are safe to repeat, so give the backend a moment and try once more.
        if (method === 'GET' && res.status >= 500) {
            await new Promise(r => setTimeout(r, 1200));
            res = await send();
        }

        if (res.status === 204) return null;

        let data = null;
        try { data = await res.json(); } catch { /* empty body */ }

        if (!res.ok) {
            if (res.status === 401) {
                API.clearSession();
                location.hash = '#/login';
            }
            const message = data && (data.message || data.error) || res.statusText;
            throw new Error(message);
        }
        return data;
    }
};
