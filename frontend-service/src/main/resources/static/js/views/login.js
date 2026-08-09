/** Login screen - identifier is email or phone. */
'use strict';

App.register('/login', {
    auth: false,

    async render() {
        const identifier = UI.el('input', { type: 'text', placeholder: 'you@email.com or +8801...' });
        const password = UI.el('input', { type: 'password', placeholder: 'password' });
        const submit = UI.el('button', { type: 'submit' }, 'Log in');

        const form = UI.el('form', {
            onsubmit: async (e) => {
                e.preventDefault();
                submit.disabled = true;
                try {
                    await Auth.login(identifier.value.trim(), password.value);
                    UI.toast('Welcome back, ' + (Auth.user?.name || 'user'), 'ok');
                    location.hash = Auth.home();
                } catch (err) {
                    UI.error(err);
                    submit.disabled = false;
                }
            }
        },
            UI.el('label', {}, 'Email or phone'), identifier,
            UI.el('label', {}, 'Password'), password,
            UI.el('div', { class: 'form-actions' }, submit)
        );

        UI.render(UI.el('div', { class: 'auth-wrap' },
            UI.el('div', { class: 'card' },
                UI.el('h2', {}, 'Log in'),
                form,
                UI.el('p', { class: 'muted' },
                    'No account? ', UI.el('a', { href: '#/signup' }, 'Sign up'))
            )
        ));
    }
});
