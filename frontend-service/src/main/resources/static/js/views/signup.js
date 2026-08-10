/** Signup screen - role decides which extra fields appear. */
'use strict';

App.register('/signup', {
    auth: false,

    async render() {
        const name = UI.el('input', { type: 'text' });
        const email = UI.el('input', { type: 'email' });
        const phone = UI.el('input', { type: 'text', placeholder: '+8801...' });
        const password = UI.el('input', { type: 'password' });
        const role = UI.el('select', {},
            UI.el('option', { value: 'CUSTOMER' }, 'Customer'),
            UI.el('option', { value: 'DELIVERYMAN' }, 'Delivery rider'),
            UI.el('option', { value: 'ADMIN' }, 'Admin / kitchen')
        );

        // Rider-only fields, shown/hidden with the role select: secret key first,
        // then vehicle and plate.
        const riderKey = UI.el('input', { type: 'password', autocomplete: 'off' });
        const vehicleType = UI.el('select', {},
            UI.el('option', { value: 'BIKE' }, 'Bike'),
            UI.el('option', { value: 'MOTORCYCLE' }, 'Motorcycle'),
            UI.el('option', { value: 'BICYCLE' }, 'Bicycle')
        );
        const licenseNumber = UI.el('input', { type: 'text' });
        const riderFields = UI.el('div', {},
            UI.el('label', {}, 'Rider secret key'), riderKey,
            UI.el('label', {}, 'Vehicle type'), vehicleType,
            UI.el('label', {}, 'Number plate'), licenseNumber
        );
        // Admin-only field: the secret key proves the signer may join as staff.
        const adminKey = UI.el('input', { type: 'password', autocomplete: 'off' });
        const adminFields = UI.el('div', {},
            UI.el('label', {}, 'Admin secret key'), adminKey);
        const syncRiderFields = () => {
            riderFields.style.display = role.value === 'DELIVERYMAN' ? '' : 'none';
            adminFields.style.display = role.value === 'ADMIN' ? '' : 'none';
        };
        role.addEventListener('change', syncRiderFields);
        syncRiderFields();

        const submit = UI.el('button', { type: 'submit' }, 'Create account');

        const form = UI.el('form', {
            onsubmit: async (e) => {
                e.preventDefault();
                submit.disabled = true;
                try {
                    const payload = {
                        name: name.value.trim(),
                        email: email.value.trim(),
                        phone: phone.value.trim(),
                        password: password.value,
                        role: role.value
                    };
                    if (role.value === 'DELIVERYMAN') {
                        payload.riderKey = riderKey.value;
                        payload.vehicleType = vehicleType.value;
                        payload.licenseNumber = licenseNumber.value.trim();
                    }
                    if (role.value === 'ADMIN') {
                        payload.adminKey = adminKey.value;
                    }
                    await Auth.signup(payload);
                    UI.toast('Account created - welcome!', 'ok');
                    location.hash = Auth.home();
                } catch (err) {
                    UI.error(err);
                    submit.disabled = false;
                }
            }
        },
            UI.el('label', {}, 'Full name'), name,
            UI.el('label', {}, 'Email'), email,
            UI.el('label', {}, 'Phone'), phone,
            UI.el('label', {}, 'Password'), password,
            UI.el('label', {}, 'I am a'), role,
            riderFields,
            adminFields,
            UI.el('div', { class: 'form-actions' }, submit)
        );

        UI.render(UI.el('div', { class: 'auth-wrap' },
            UI.el('div', { class: 'card' },
                UI.el('h2', {}, 'Sign up'),
                form,
                UI.el('p', { class: 'muted' },
                    'Already registered? ', UI.el('a', { href: '#/login' }, 'Log in'))
            )
        ));
    }
});
