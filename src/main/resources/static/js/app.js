/* Shared helpers: API client, auth storage, navbar, formatting, toasts. */
const App = (() => {
    const TOKEN_KEY = 'rms_token';
    const USER_KEY = 'rms_user';

    const getToken = () => localStorage.getItem(TOKEN_KEY);
    const getUser = () => { try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; } };
    const setSession = (auth) => {
        localStorage.setItem(TOKEN_KEY, auth.token);
        localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
    };
    const setUser = (user) => localStorage.setItem(USER_KEY, JSON.stringify(user));
    const logout = () => {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        window.location.href = '/login.html';
    };

    async function api(path, { method = 'GET', body, auth = true } = {}) {
        const headers = { 'Accept': 'application/json' };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const token = getToken();
        if (auth && token) headers['Authorization'] = 'Bearer ' + token;
        const res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
        if (res.status === 401 && auth && token) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
            window.location.href = '/login.html?expired=1';
            throw new Error('Session expired');
        }
        if (res.status === 204) return null;
        const text = await res.text();
        const data = text ? JSON.parse(text) : null;
        if (!res.ok) {
            const err = new Error((data && data.message) || ('Request failed (' + res.status + ')'));
            err.status = res.status;
            err.fieldErrors = data && data.fieldErrors;
            throw err;
        }
        return data;
    }

    /** Redirects to login if the user is not signed in with one of the allowed roles. */
    function requireRole(...roles) {
        const user = getUser();
        if (!getToken() || !user || (roles.length && !roles.includes(user.role))) {
            window.location.href = '/login.html?next=' + encodeURIComponent(location.pathname + location.search);
            throw new Error('redirecting');
        }
        return user;
    }

    function homeFor(role) {
        return { MERCHANT: '/merchant.html', KITCHEN: '/kitchen.html', CUSTOMER: '/restaurants.html' }[role] || '/';
    }

    const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    const money = (v) => '\u20B9' + Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const dt = (v) => v ? new Date(v).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '-';
    const dateOnly = (v) => v ? new Date(v).toLocaleDateString('en-IN', { dateStyle: 'medium' }) : '-';
    const timeAgo = (v) => {
        const s = Math.floor((Date.now() - new Date(v).getTime()) / 1000);
        if (s < 60) return s + 's ago';
        if (s < 3600) return Math.floor(s / 60) + ' min ago';
        if (s < 86400) return Math.floor(s / 3600) + ' h ago';
        return Math.floor(s / 86400) + ' d ago';
    };
    const ACRONYMS = { UPI: 'UPI', SMS: 'SMS', MOCK_GATEWAY: 'Online Gateway' };
    const label = (v) => ACRONYMS[v] || String(v || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
    const statusBadge = (s) => `<span class="badge status-${esc(s)}">${esc(label(s))}</span>`;
    const payBadge = (s) => `<span class="badge pay-${esc(s)}">${esc(label(s))}</span>`;
    const vegDot = (veg) => `<span class="${veg ? 'veg-dot' : 'nonveg-dot'}" title="${veg ? 'Veg' : 'Non-veg'}"></span>`;
    const stars = (n) => n ? Array.from({ length: 5 }, (_, i) => `<i class="bi ${i < n ? 'bi-star-fill' : 'bi-star'} star"></i>`).join('') : '';

    function toast(message, type = 'success') {
        let c = document.getElementById('toast-container');
        if (!c) { c = document.createElement('div'); c.id = 'toast-container'; document.body.appendChild(c); }
        const el = document.createElement('div');
        el.className = `toast align-items-center text-bg-${type} border-0 show mb-2`;
        el.setAttribute('role', 'alert');
        el.innerHTML = `<div class="d-flex"><div class="toast-body">${esc(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto"></button></div>`;
        el.querySelector('button').onclick = () => el.remove();
        c.appendChild(el);
        setTimeout(() => el.remove(), 4000);
    }
    const error = (e) => toast(e.message || String(e), 'danger');

    /** Shows server-side validation errors next to inputs (inputs must have name = field). */
    function showFormErrors(form, err) {
        form.querySelectorAll('.is-invalid').forEach(i => i.classList.remove('is-invalid'));
        form.querySelectorAll('.invalid-feedback.dyn').forEach(i => i.remove());
        if (err.fieldErrors) {
            Object.entries(err.fieldErrors).forEach(([field, msg]) => {
                const input = form.querySelector(`[name="${field}"]`);
                if (input) {
                    input.classList.add('is-invalid');
                    const fb = document.createElement('div');
                    fb.className = 'invalid-feedback dyn';
                    fb.textContent = msg;
                    input.insertAdjacentElement('afterend', fb);
                }
            });
        }
        const alert = form.querySelector('.form-alert');
        if (alert) { alert.textContent = err.message; alert.classList.remove('d-none'); }
        else error(err);
    }

    function formData(form) {
        const obj = {};
        new FormData(form).forEach((v, k) => { obj[k] = typeof v === 'string' ? v.trim() : v; });
        form.querySelectorAll('input[type=checkbox][name]').forEach(cb => { obj[cb.name] = cb.checked; });
        return obj;
    }

    function renderNav(active) {
        const user = getUser();
        const links = [];
        if (!user) {
            links.push(['/restaurants.html', 'Restaurants', 'shop']);
        } else if (user.role === 'CUSTOMER') {
            links.push(['/restaurants.html', 'Order Food', 'shop'], ['/orders.html', 'My Orders', 'receipt'],
                ['/profile.html', 'Profile & Rewards', 'person-circle']);
        } else if (user.role === 'MERCHANT') {
            links.push(['/merchant.html', 'Dashboard', 'speedometer2'], ['/kitchen.html', 'Kitchen', 'fire']);
        } else if (user.role === 'KITCHEN') {
            links.push(['/kitchen.html', 'Kitchen Board', 'fire']);
        }
        const nav = document.createElement('nav');
        nav.className = 'navbar navbar-expand-lg navbar-dark bg-dark sticky-top shadow-sm';
        nav.innerHTML = `
          <div class="container-fluid px-lg-4">
            <a class="navbar-brand" href="/"><i class="bi bi-fire"></i> RestoBill</a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav">
              <span class="navbar-toggler-icon"></span></button>
            <div class="collapse navbar-collapse" id="mainNav">
              <ul class="navbar-nav me-auto">
                ${links.map(([href, text, icon]) => `<li class="nav-item"><a class="nav-link ${active === href ? 'active' : ''}" href="${href}">
                  <i class="bi bi-${icon}"></i> ${text}</a></li>`).join('')}
              </ul>
              <div class="d-flex align-items-center gap-2">
                ${user ? `<span class="text-light small"><i class="bi bi-person"></i> ${esc(user.name)}
                    <span class="badge bg-secondary">${esc(label(user.role))}</span></span>
                    <button class="btn btn-outline-light btn-sm" id="logoutBtn"><i class="bi bi-box-arrow-right"></i> Logout</button>`
                  : `<a class="btn btn-outline-light btn-sm" href="/login.html">Sign in</a>
                     <a class="btn btn-brand btn-sm" href="/signup.html">Sign up</a>`}
              </div>
            </div>
          </div>`;
        document.body.prepend(nav);
        const btn = document.getElementById('logoutBtn');
        if (btn) btn.onclick = logout;

        const footer = document.createElement('footer');
        footer.className = 'bg-dark text-secondary text-center py-3 mt-5 small';
        footer.innerHTML = '&copy; ' + new Date().getFullYear() + ' RestoBill &mdash; Restaurant Management &amp; Billing System';
        document.body.appendChild(footer);
    }

    return { api, getToken, getUser, setSession, setUser, logout, requireRole, homeFor, esc, money, dt, dateOnly,
        timeAgo, label, statusBadge, payBadge, vegDot, stars, toast, error, showFormErrors, formData, renderNav };
})();
