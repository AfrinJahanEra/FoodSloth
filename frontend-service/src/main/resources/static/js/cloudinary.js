/**
 * Cloudinary photo uploads. The API secret never reaches the browser: we ask
 * restaurant-service for a short-lived signed payload, then POST the file
 * straight to Cloudinary and keep the returned secure_url.
 */
'use strict';

const Cloudinary = {
    MAX_MB: 5,

    /** Uploads an image file, resolves to its Cloudinary URL. Throws friendly errors. */
    async upload(file) {
        if (!file || !file.type || !file.type.startsWith('image/')) {
            throw new Error('Choose an image file (jpg, png, webp...)');
        }
        if (file.size > Cloudinary.MAX_MB * 1024 * 1024) {
            throw new Error('Image is too large - keep it under ' + Cloudinary.MAX_MB + ' MB');
        }

        const sig = await API.call('/restaurant/images/upload-signature');

        const form = new FormData();
        form.append('file', file);
        form.append('api_key', sig.apiKey);
        form.append('timestamp', sig.timestamp);
        form.append('signature', sig.signature);
        form.append('folder', sig.folder);

        const res = await fetch(sig.uploadUrl, { method: 'POST', body: form });
        let data = null;
        try { data = await res.json(); } catch { /* non-JSON failure */ }

        if (!res.ok) {
            throw new Error(data && data.error && data.error.message
                ? 'Cloudinary: ' + data.error.message
                : 'Cloudinary upload failed (' + res.status + ')');
        }
        return data.secure_url;
    }
};
