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

        // Rider-only fields, shown/hidden with the role select.
        const vehicleType = UI.el('select', {},
            UI.el('option', { value: 'BIKE' }, 'Bike'),
            UI.el('option', { value: 'MOTORCYCLE' }, 'Motorcycle'),
            UI.el('option', { value: 'BICYCLE' }, 'Bicycle')
        );
        const licenseNumber = UI.el('input', { type: 'text' });
        const riderFields = UI.el('div', {},
            UI.el('label', {}, 'Vehicle type'), vehicleType,
            UI.el('label', {}, 'License number'), licenseNumber
        );
        const syncRiderFields = () => {
            riderFields.style.display = role.value === 'DELIVERYMAN' ? '' : 'none';
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
                        payload.vehicleType = vehicleType.value;
                        payload.licenseNumber = licenseNumber.value.trim();
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
