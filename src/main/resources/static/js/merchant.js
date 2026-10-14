/* Merchant dashboard - single page with hash-based tabs. */
App.requireRole('MERCHANT');
App.renderNav('/merchant.html');
const { esc, money, api } = App;
const view = document.getElementById('view');
const modal = new bootstrap.Modal('#formModal');
let refreshTimer;
/* Records referenced from inline handlers by key, so user data is never embedded in HTML attributes. */
const reg = {};
const keep = (obj) => { const k = 'r' + Object.keys(reg).length; reg[k] = obj; return k; };

// Local calendar date (toISOString would give the UTC date, which is "yesterday" after midnight in IST).
const localDate = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const today = () => localDate(new Date());
const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return localDate(d); };
const spinner = '<div class="text-center py-5"><div class="spinner-border text-secondary"></div></div>';

/* ---------- Generic modal form ---------- */
let onModalSubmit = null;
function field(f, value) {
    const v = value ?? f.value ?? '';
    const col = `col-md-${f.col || 6}`;
    const req = f.required ? 'required' : '';
    if (f.type === 'select') {
        return `<div class="${col}"><label class="form-label small">${f.label}</label><select name="${f.name}" class="form-select" ${req}>
            ${f.options.map(([val, text]) => `<option value="${esc(val)}" ${String(val) === String(v) ? 'selected' : ''}>${esc(text)}</option>`).join('')}</select></div>`;
    }
    if (f.type === 'checkbox') {
        return `<div class="${col} d-flex align-items-end"><div class="form-check form-switch"><input class="form-check-input" type="checkbox" name="${f.name}" id="f_${f.name}" ${v === true || (v === '' && f.default) ? 'checked' : ''}>
            <label class="form-check-label" for="f_${f.name}">${f.label}</label></div></div>`;
    }
    if (f.type === 'textarea') {
        return `<div class="${col}"><label class="form-label small">${f.label}</label><textarea name="${f.name}" class="form-control" rows="2" ${req}>${esc(v)}</textarea></div>`;
    }
    return `<div class="${col}"><label class="form-label small">${f.label}</label><input name="${f.name}" type="${f.type || 'text'}" ${f.step ? `step="${f.step}"` : ''} class="form-control" value="${esc(v)}" ${req} ${f.placeholder ? `placeholder="${esc(f.placeholder)}"` : ''}></div>`;
}
function openForm(title, fields, data, submit) {
    document.getElementById('formTitle').textContent = title;
    document.getElementById('formBody').innerHTML = fields.map(f => field(f, data ? data[f.name] : undefined)).join('');
    const form = document.getElementById('modalForm');
    form.querySelector('.form-alert').classList.add('d-none');
    onModalSubmit = async () => {
        const d = App.formData(form);
        fields.forEach(f => {
            if (f.type === 'number') d[f.name] = d[f.name] === '' ? null : Number(d[f.name]);
            else if (f.type !== 'checkbox' && d[f.name] === '') d[f.name] = null;
        });
        await submit(d);
    };
    modal.show();
}
document.getElementById('modalForm').onsubmit = async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type=submit]');
    btn.disabled = true;
    try { await onModalSubmit(); modal.hide(); }
    catch (err) { App.showFormErrors(e.target, err); }
    finally { btn.disabled = false; }
};

/* ---------- Router ---------- */
const routes = { dashboard, orders, menu, coupons, customers, feedback, reports, payments, notifications, staff, profile };
function route() {
    clearInterval(refreshTimer);
    const tab = (location.hash || '#dashboard').slice(1).split('?')[0];
    document.querySelectorAll('#tabs .nav-link').forEach(a => a.classList.toggle('active', a.getAttribute('href') === '#' + tab));
    view.innerHTML = spinner;
    (routes[tab] || dashboard)().catch(e => { view.innerHTML = `<div class="alert alert-danger">${esc(e.message)}</div>`; });
}
window.addEventListener('hashchange', route);
api('/api/merchant/restaurant').then(r => document.getElementById('restName').textContent = r.name).catch(() => {});
route();

/* ---------- Dashboard ---------- */
async function dashboard() {
    const [rep, active, fb] = await Promise.all([
        api('/api/reports/daily'), api('/api/kitchen/orders'), api('/api/reports/feedback')]);
    const stat = (title, value, icon, color) => `<div class="col-6 col-xl-3"><div class="card stat-card h-100"><div class="card-body d-flex justify-content-between align-items-center">
        <div><div class="small text-muted">${title}</div><div class="fs-4 fw-bold">${value}</div></div><i class="bi bi-${icon} icon text-${color}"></i></div></div></div>`;
    view.innerHTML = `
      <h4 class="mb-3">Today's overview <small class="text-muted fs-6">${App.dateOnly(rep.date)}</small></h4>
      <div class="row g-3 mb-4">
        ${stat('Total orders', rep.totalOrders, 'receipt', 'primary')}
        ${stat('Gross sales', money(rep.grossSales), 'currency-rupee', 'success')}
        ${stat('Collected', money(rep.amountCollected), 'cash-stack', 'info')}
        ${stat('Pending payment', money(rep.pendingAmount), 'hourglass-split', 'warning')}
        ${stat('Active in kitchen', rep.activeOrders, 'fire', 'danger')}
        ${stat('Completed', rep.completedOrders, 'check2-all', 'success')}
        ${stat('Avg order value', money(rep.averageOrderValue), 'graph-up', 'secondary')}
        ${stat('Avg rating', fb.averageRating ? fb.averageRating + ' \u2605' : '-', 'star', 'warning')}
      </div>
      <div class="row g-3">
        <div class="col-lg-7"><div class="card h-100"><div class="card-body">
          <div class="d-flex justify-content-between"><h6>Live orders</h6><a href="#orders" class="small">View all</a></div>
          ${active.length ? `<table class="table table-sm small align-middle"><tbody>${active.slice(0, 8).map(o => `<tr>
            <td><a href="/order.html?id=${o.id}">#${esc(o.orderNumber.slice(-6))}</a></td><td>${esc(o.customerName)}</td>
            <td>${money(o.totalAmount)}</td><td>${App.statusBadge(o.status)}</td><td>${App.payBadge(o.paymentStatus)}</td></tr>`).join('')}</tbody></table>`
            : '<p class="text-muted small">No active orders right now.</p>'}
        </div></div></div>
        <div class="col-lg-5"><div class="card h-100"><div class="card-body">
          <h6>Popular items today</h6>
          ${rep.popularItems.length ? `<ol class="small mb-0">${rep.popularItems.map(p => `<li class="d-flex justify-content-between"><span>${esc(p.name)}</span><span class="text-muted">${p.quantity} sold &middot; ${money(p.revenue)}</span></li>`).join('')}</ol>`
            : '<p class="text-muted small">No sales yet today.</p>'}
          <hr><h6>Complaints</h6>
          <p class="small mb-0"><span class="badge text-bg-danger">${fb.openComplaints} open</span> <span class="badge text-bg-success">${fb.resolvedComplaints} resolved</span>
          <a href="#feedback" class="ms-2">Manage</a></p>
        </div></div></div>
      </div>`;
    refreshTimer = setInterval(() => { if ((location.hash || '#dashboard') === '#dashboard') dashboard(); }, 20000);
}

/* ---------- Orders & billing ---------- */
async function orders() {
    const status = new URLSearchParams(location.hash.split('?')[1] || '').get('status') || '';
    const list = await api('/api/merchant/orders' + (status ? '?status=' + status : ''));
    const filters = ['', 'PENDING', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED'];
    view.innerHTML = `
      <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
        <h4 class="mb-0">Orders &amp; billing</h4>
        <div class="btn-group btn-group-sm">${filters.map(f => `<button class="btn btn-outline-dark ${f === status ? 'active' : ''}" data-f="${f}">${f ? App.label(f) : 'All'}</button>`).join('')}</div>
      </div>
      <div class="card"><div class="table-responsive"><table class="table table-hover align-middle small mb-0">
        <thead class="table-light"><tr><th>Order</th><th>Customer</th><th>Items</th><th>Bill</th><th>Status</th><th>Payment</th><th>Actions</th></tr></thead>
        <tbody>${list.length ? list.map(o => `<tr>
          <td><strong>#${esc(o.orderNumber)}</strong><div class="text-muted">${App.dt(o.createdAt)}</div><div class="text-muted">${App.label(o.orderType)}${o.tableNumber ? ' &middot; T' + esc(o.tableNumber) : ''}</div></td>
          <td>${esc(o.customerName)}<div class="text-muted">${esc(o.customerMobile)}</div></td>
          <td>${o.items.map(i => esc(i.itemName) + ' x' + i.quantity).join('<br>')}</td>
          <td><strong>${money(o.totalAmount)}</strong><div class="text-muted">Sub ${money(o.subtotal)} | Tax ${money(o.taxAmount)}</div>
            ${Number(o.couponDiscount) + Number(o.loyaltyDiscount) > 0 ? `<div class="text-success">Disc ${money(Number(o.couponDiscount) + Number(o.loyaltyDiscount))}</div>` : ''}</td>
          <td>${App.statusBadge(o.status)}</td><td>${App.payBadge(o.paymentStatus)}</td>
          <td><div class="d-flex flex-wrap gap-1">
            ${o.nextStatuses.map(s => `<button class="btn btn-sm ${s === 'CANCELLED' ? 'btn-outline-danger' : 'btn-outline-primary'}" onclick="setOrderStatus(${o.id},'${s}')">${App.label(s)}</button>`).join('')}
            ${o.status !== 'CANCELLED' && !['PAID', 'REFUNDED'].includes(o.paymentStatus) ? `<button class="btn btn-sm btn-success" onclick="recordPayment(${o.id}, '${o.totalAmount}')"><i class="bi bi-cash"></i> Collect</button>` : ''}
            <a class="btn btn-sm btn-outline-dark" href="/order.html?id=${o.id}"><i class="bi bi-printer"></i> Bill</a>
          </div></td></tr>`).join('') : '<tr><td colspan="7" class="text-center text-muted py-4">No orders</td></tr>'}</tbody></table></div></div>`;
    view.querySelectorAll('[data-f]').forEach(b => b.onclick = () => location.hash = 'orders' + (b.dataset.f ? '?status=' + b.dataset.f : ''));
    refreshTimer = setInterval(() => { if (location.hash.startsWith('#orders')) orders(); }, 20000);
}
window.setOrderStatus = async (id, status) => {
    if (status === 'CANCELLED' && !confirm('Cancel this order? Any payment will be marked refunded.')) return;
    try { await api(`/api/orders/${id}/status`, { method: 'PUT', body: { status } }); App.toast('Order ' + App.label(status)); route(); }
    catch (e) { App.error(e); }
};
window.recordPayment = (id, amount) => openForm('Collect payment - ' + money(amount),
    [{ name: 'method', label: 'Payment method', type: 'select', col: 12, options: [['CASH', 'Cash'], ['CARD', 'Card (POS)'], ['UPI', 'UPI (QR at counter)']] }],
    null, async (d) => { await api(`/api/orders/${id}/payments`, { method: 'POST', body: { method: d.method } }); App.toast('Payment recorded'); route(); });

/* ---------- Menu ---------- */
async function menu() {
    const [cats, items] = await Promise.all([api('/api/merchant/categories'), api('/api/merchant/menu-items')]);
    view.innerHTML = `
      <div class="row g-4">
        <div class="col-xl-4"><div class="card"><div class="card-body">
          <div class="d-flex justify-content-between align-items-center mb-2"><h5 class="mb-0">Categories</h5>
            <button class="btn btn-sm btn-brand" id="addCat"><i class="bi bi-plus"></i> Add</button></div>
          <ul class="list-group list-group-flush">${cats.map(c => `<li class="list-group-item d-flex justify-content-between align-items-center px-0">
            <div>${esc(c.name)} ${c.active ? '' : '<span class="badge text-bg-secondary">Hidden</span>'}<div class="small text-muted">${c.itemCount} items &middot; order ${c.displayOrder}</div></div>
            <div><button class="btn btn-sm btn-light" onclick="editCat(reg['${keep(c)}'])"><i class="bi bi-pencil"></i></button>
            <button class="btn btn-sm btn-light text-danger" onclick="delCat(${c.id})"><i class="bi bi-trash"></i></button></div></li>`).join('') || '<li class="list-group-item text-muted small">No categories yet</li>'}</ul>
        </div></div></div>
        <div class="col-xl-8"><div class="card"><div class="card-body">
          <div class="d-flex justify-content-between align-items-center mb-2"><h5 class="mb-0">Menu items</h5>
            <button class="btn btn-sm btn-brand" id="addItem" ${cats.length ? '' : 'disabled title="Add a category first"'}><i class="bi bi-plus"></i> Add item</button></div>
          <div class="table-responsive"><table class="table table-sm align-middle small">
            <thead><tr><th>Item</th><th>Category</th><th class="text-end">Price</th><th>Available</th><th></th></tr></thead>
            <tbody>${items.map(i => `<tr><td>${App.vegDot(i.vegetarian)} ${esc(i.name)}<div class="text-muted">${esc(i.description || '')}</div></td>
              <td>${esc(i.categoryName)}</td><td class="text-end">${money(i.price)}</td>
              <td><div class="form-check form-switch"><input class="form-check-input" type="checkbox" ${i.available ? 'checked' : ''} onchange="toggleItem(${i.id}, this.checked)"></div></td>
              <td class="text-end text-nowrap"><button class="btn btn-sm btn-light" onclick="editItem(reg['${keep(i)}'])"><i class="bi bi-pencil"></i></button>
              <button class="btn btn-sm btn-light text-danger" onclick="delItem(${i.id})"><i class="bi bi-trash"></i></button></td></tr>`).join('') || '<tr><td colspan="5" class="text-muted">No items yet</td></tr>'}</tbody>
          </table></div>
        </div></div></div>
      </div>`;
    const catFields = [
        { name: 'name', label: 'Name', required: true }, { name: 'displayOrder', label: 'Display order', type: 'number', value: 0 },
        { name: 'description', label: 'Description', col: 12 }, { name: 'active', label: 'Visible to customers', type: 'checkbox', default: true }];
    const itemFields = [
        { name: 'name', label: 'Item name', required: true },
        { name: 'categoryId', label: 'Category', type: 'select', options: cats.map(c => [c.id, c.name]) },
        { name: 'price', label: 'Price (\u20B9)', type: 'number', step: '0.01', required: true },
        { name: 'imageUrl', label: 'Image URL (optional)', placeholder: 'https://...' },
        { name: 'description', label: 'Description', type: 'textarea', col: 12 },
        { name: 'vegetarian', label: 'Vegetarian', type: 'checkbox', default: true },
        { name: 'available', label: 'Available', type: 'checkbox', default: true }];
    document.getElementById('addCat').onclick = () => openForm('Add category', catFields, null, async d => { await api('/api/merchant/categories', { method: 'POST', body: d }); App.toast('Category added'); menu(); });
    window.editCat = (c) => openForm('Edit category', catFields, c, async d => { await api('/api/merchant/categories/' + c.id, { method: 'PUT', body: d }); App.toast('Category updated'); menu(); });
    window.delCat = async (id) => { if (!confirm('Delete this category?')) return; try { await api('/api/merchant/categories/' + id, { method: 'DELETE' }); menu(); } catch (e) { App.error(e); } };
    document.getElementById('addItem').onclick = () => openForm('Add menu item', itemFields, null, async d => { await api('/api/merchant/menu-items', { method: 'POST', body: d }); App.toast('Item added'); menu(); });
    window.editItem = (i) => openForm('Edit menu item', itemFields, i, async d => { await api('/api/merchant/menu-items/' + i.id, { method: 'PUT', body: d }); App.toast('Item updated'); menu(); });
    window.delItem = async (id) => { if (!confirm('Remove this item from the menu?')) return; try { await api('/api/merchant/menu-items/' + id, { method: 'DELETE' }); menu(); } catch (e) { App.error(e); } };
    window.toggleItem = async (id, available) => { try { await api(`/api/merchant/menu-items/${id}/availability`, { method: 'PATCH', body: { available } }); App.toast(available ? 'Item available' : 'Item marked unavailable'); } catch (e) { App.error(e); menu(); } };
}

/* ---------- Coupons ---------- */
async function coupons() {
    const list = await api('/api/merchant/coupons');
    view.innerHTML = `
      <div class="d-flex justify-content-between align-items-center mb-3"><h4 class="mb-0">Coupons</h4><button class="btn btn-brand btn-sm" id="addCoupon"><i class="bi bi-plus"></i> New coupon</button></div>
      <div class="card"><div class="table-responsive"><table class="table align-middle small mb-0">
        <thead class="table-light"><tr><th>Code</th><th>Discount</th><th>Min order</th><th>Validity</th><th>Usage</th><th>Status</th><th></th></tr></thead>
        <tbody>${list.map(c => `<tr><td><strong>${esc(c.code)}</strong><div class="text-muted">${esc(c.description || '')}</div></td>
          <td>${c.discountType === 'PERCENT' ? Number(c.discountValue) + '%' + (c.maxDiscount ? ' (max ' + money(c.maxDiscount) + ')' : '') : money(c.discountValue)}</td>
          <td>${money(c.minOrderAmount)}</td><td>${App.dateOnly(c.validFrom)} &rarr; ${App.dateOnly(c.validTo)}</td>
          <td>${c.usedCount}${c.usageLimit ? ' / ' + c.usageLimit : ''}${c.perCustomerLimit ? `<div class="text-muted">${c.perCustomerLimit}/customer</div>` : ''}</td>
          <td>${c.currentlyValid ? '<span class="badge text-bg-success">Valid</span>' : '<span class="badge text-bg-secondary">Inactive/expired</span>'}</td>
          <td class="text-nowrap"><button class="btn btn-sm btn-light" onclick="editCoupon(reg['${keep(c)}'])"><i class="bi bi-pencil"></i></button>
            <button class="btn btn-sm btn-light text-danger" onclick="delCoupon(${c.id})"><i class="bi bi-trash"></i></button></td></tr>`).join('') || '<tr><td colspan="7" class="text-center text-muted py-4">No coupons</td></tr>'}</tbody>
      </table></div></div>`;
    const fields = [
        { name: 'code', label: 'Code', required: true, placeholder: 'e.g. DIWALI20' },
        { name: 'discountType', label: 'Type', type: 'select', options: [['PERCENT', 'Percentage'], ['FLAT', 'Flat amount']] },
        { name: 'discountValue', label: 'Discount value', type: 'number', step: '0.01', required: true },
        { name: 'maxDiscount', label: 'Max discount (for %)', type: 'number', step: '0.01' },
        { name: 'minOrderAmount', label: 'Minimum order amount', type: 'number', step: '0.01', value: 0 },
        { name: 'description', label: 'Description' },
        { name: 'validFrom', label: 'Valid from', type: 'date', value: today(), required: true },
        { name: 'validTo', label: 'Valid to', type: 'date', required: true },
        { name: 'usageLimit', label: 'Total usage limit (blank = unlimited)', type: 'number' },
        { name: 'perCustomerLimit', label: 'Per-customer limit', type: 'number' },
        { name: 'active', label: 'Active', type: 'checkbox', default: true }];
    document.getElementById('addCoupon').onclick = () => openForm('New coupon', fields, null, async d => { await api('/api/merchant/coupons', { method: 'POST', body: d }); App.toast('Coupon created'); coupons(); });
    window.editCoupon = (c) => openForm('Edit coupon', fields, c, async d => { await api('/api/merchant/coupons/' + c.id, { method: 'PUT', body: d }); App.toast('Coupon updated'); coupons(); });
    window.delCoupon = async (id) => { if (!confirm('Delete this coupon?')) return; try { await api('/api/merchant/coupons/' + id, { method: 'DELETE' }); coupons(); } catch (e) { App.error(e); } };
}

/* ---------- Customers ---------- */
async function customers() {
    const list = await api('/api/merchant/customers');
    view.innerHTML = `<h4 class="mb-3">Customers</h4>
      <div class="card"><div class="table-responsive"><table class="table align-middle small mb-0">
        <thead class="table-light"><tr><th>Name</th><th>Contact</th><th class="text-end">Orders</th><th class="text-end">Total spent</th><th class="text-end">Loyalty pts</th><th>Last order</th></tr></thead>
        <tbody>${list.map(c => `<tr><td>${esc(c.name)}</td><td>${esc(c.email)}<div class="text-muted">${esc(c.mobile)}</div></td>
          <td class="text-end">${c.totalOrders}</td><td class="text-end">${money(c.totalSpent)}</td><td class="text-end">${c.loyaltyPoints}</td><td>${App.dt(c.lastOrderAt)}</td></tr>`).join('')
          || '<tr><td colspan="6" class="text-center text-muted py-4">No customers yet</td></tr>'}</tbody></table></div></div>`;
}

/* ---------- Feedback ---------- */
async function feedback() {
    const [list, rep] = await Promise.all([api('/api/merchant/feedback'), api('/api/reports/feedback')]);
    const max = Math.max(1, ...Object.values(rep.ratingDistribution));
    view.innerHTML = `<h4 class="mb-3">Feedback &amp; complaints</h4>
      <div class="row g-3 mb-3">
        <div class="col-md-4"><div class="card h-100"><div class="card-body text-center"><div class="display-5 fw-bold">${rep.averageRating ?? '-'}</div>
          <div>${App.stars(Math.round(rep.averageRating || 0))}</div><div class="small text-muted">${rep.totalReviews} reviews</div></div></div></div>
        <div class="col-md-4"><div class="card h-100"><div class="card-body small">${[5, 4, 3, 2, 1].map(s => `<div class="d-flex align-items-center gap-2 mb-1">
          <span>${s}\u2605</span><div class="progress flex-grow-1" style="height:8px"><div class="progress-bar bg-warning" style="width:${rep.ratingDistribution[s] / max * 100}%"></div></div><span>${rep.ratingDistribution[s]}</span></div>`).join('')}</div></div></div>
        <div class="col-md-4"><div class="card h-100"><div class="card-body"><div class="small text-muted">Complaints</div><div class="fs-3 fw-bold">${rep.totalComplaints}</div>
          <span class="badge text-bg-danger">${rep.openComplaints} open</span> <span class="badge text-bg-success">${rep.resolvedComplaints} resolved</span></div></div></div>
      </div>
      <div class="card"><div class="card-body">${list.map(f => `
        <div class="border-bottom py-2"><div class="d-flex justify-content-between flex-wrap">
          <div><span class="badge ${f.type === 'COMPLAINT' ? 'text-bg-danger' : 'text-bg-success'}">${App.label(f.type)}</span>
            <strong>${esc(f.customerName)}</strong> ${f.orderNumber ? `<a class="small" href="/order.html?id=${f.orderId}">#${esc(f.orderNumber)}</a>` : ''}
            <span class="small text-muted">&middot; ${App.dt(f.createdAt)}</span> ${App.stars(f.rating)}</div>
          <div><span class="badge text-bg-light">${App.label(f.status)}</span>
            <button class="btn btn-sm btn-outline-secondary" onclick="respond(reg['${keep({ id: f.id, status: f.status, reply: f.merchantReply })}'])"><i class="bi bi-reply"></i> Respond</button></div></div>
          <div class="small mt-1">${esc(f.comment || '')}</div>
          ${f.merchantReply ? `<div class="small ms-3 fst-italic text-muted"><i class="bi bi-reply"></i> ${esc(f.merchantReply)}</div>` : ''}
        </div>`).join('') || '<p class="text-muted small mb-0">No feedback yet.</p>'}</div></div>`;
    window.respond = (f) => openForm('Respond to feedback', [
        { name: 'status', label: 'Status', type: 'select', options: [['OPEN', 'Open'], ['IN_PROGRESS', 'In progress'], ['RESOLVED', 'Resolved']] },
        { name: 'reply', label: 'Reply to customer', type: 'textarea', col: 12 }], f,
        async d => { await api('/api/merchant/feedback/' + f.id, { method: 'PUT', body: d }); App.toast('Response saved'); feedback(); });
}

/* ---------- Reports ---------- */
async function reports() {
    const qs = new URLSearchParams(location.hash.split('?')[1] || '');
    const from = qs.get('from') || daysAgo(6), to = qs.get('to') || today(), day = qs.get('day') || today();
    const [sales, daily] = await Promise.all([api(`/api/reports/sales?from=${from}&to=${to}`), api(`/api/reports/daily?date=${day}`)]);
    const max = Math.max(1, ...sales.daily.map(d => Number(d.sales)));
    const showBars = sales.daily.length <= 31;
    view.innerHTML = `
      <div class="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2 no-print">
        <h4 class="mb-0">Sales reports</h4>
        <form class="d-flex gap-2 align-items-center" id="rangeForm"><input type="date" name="from" class="form-control form-control-sm" value="${from}">
          <span>to</span><input type="date" name="to" class="form-control form-control-sm" value="${to}"><button class="btn btn-sm btn-dark">Apply</button>
          <button type="button" class="btn btn-sm btn-outline-dark" onclick="window.print()"><i class="bi bi-printer"></i></button></form>
      </div>
      <div class="row g-3 mb-3">
        ${[['Orders', sales.totalOrders], ['Gross sales', money(sales.grossSales)], ['Tax collected', money(sales.totalTax)], ['Discounts given', money(sales.totalDiscounts)], ['Avg order value', money(sales.averageOrderValue)], ['Cancelled', sales.cancelledOrders]]
            .map(([t, v]) => `<div class="col-6 col-md-4 col-xl-2"><div class="card"><div class="card-body p-2"><div class="small text-muted">${t}</div><div class="fw-bold">${v}</div></div></div></div>`).join('')}
      </div>
      <div class="row g-3">
        <div class="col-lg-8"><div class="card h-100"><div class="card-body">
          <h6>Daily sales (${App.dateOnly(sales.from)} - ${App.dateOnly(sales.to)})</h6>
          ${showBars ? `<div class="bar-chart">${sales.daily.map(d => `<div class="bar" style="height:${Number(d.sales) / max * 100}%" title="${d.date}: ${money(d.sales)} (${d.orders} orders)">${sales.daily.length <= 14 && Number(d.sales) ? `<span>${Math.round(d.sales)}</span>` : ''}</div>`).join('')}</div>
            <div class="bar-labels">${sales.daily.map(d => `<div>${sales.daily.length <= 14 ? d.date.slice(5) : ''}</div>`).join('')}</div>` : ''}
          <table class="table table-sm small mt-3 mb-0"><thead><tr><th>Date</th><th class="text-end">Orders</th><th class="text-end">Sales</th></tr></thead>
            <tbody>${sales.daily.slice().reverse().map(d => `<tr><td>${App.dateOnly(d.date)}</td><td class="text-end">${d.orders}</td><td class="text-end">${money(d.sales)}</td></tr>`).join('')}</tbody></table>
        </div></div></div>
        <div class="col-lg-4"><div class="card h-100"><div class="card-body">
          <h6>Top selling items</h6>
          <table class="table table-sm small"><thead><tr><th>Item</th><th class="text-end">Qty</th><th class="text-end">Revenue</th></tr></thead>
            <tbody>${sales.popularItems.map(p => `<tr><td>${esc(p.name)}</td><td class="text-end">${p.quantity}</td><td class="text-end">${money(p.revenue)}</td></tr>`).join('') || '<tr><td colspan="3" class="text-muted">No data</td></tr>'}</tbody></table>
        </div></div></div>
      </div>
      <div class="card mt-3"><div class="card-body">
        <div class="d-flex justify-content-between align-items-center flex-wrap gap-2"><h6 class="mb-0">Daily report</h6>
          <input type="date" id="dayPick" class="form-control form-control-sm no-print" style="max-width:180px" value="${day}"></div>
        <div class="row g-3 mt-1 small">
          <div class="col-md-4"><table class="table table-sm mb-0">
            <tr><td>Total orders</td><td class="text-end">${daily.totalOrders}</td></tr>
            <tr><td>Completed</td><td class="text-end">${daily.completedOrders}</td></tr>
            <tr><td>Cancelled</td><td class="text-end">${daily.cancelledOrders}</td></tr>
            <tr><td>Gross sales</td><td class="text-end">${money(daily.grossSales)}</td></tr>
            <tr><td>Collected</td><td class="text-end">${money(daily.amountCollected)}</td></tr>
            <tr><td>Pending</td><td class="text-end">${money(daily.pendingAmount)}</td></tr>
            <tr><td>Tax</td><td class="text-end">${money(daily.totalTax)}</td></tr>
            <tr><td>Discounts</td><td class="text-end">${money(daily.totalDiscounts)}</td></tr></table></div>
          <div class="col-md-4"><strong>Payment methods</strong><table class="table table-sm">${Object.entries(daily.paymentMethodBreakdown).map(([k, v]) => `<tr><td>${App.label(k)}</td><td class="text-end">${money(v)}</td></tr>`).join('') || '<tr><td class="text-muted">No payments</td></tr>'}</table>
            <strong>Orders by hour</strong><table class="table table-sm">${Object.entries(daily.ordersByHour).map(([h, c]) => `<tr><td>${String(h).padStart(2, '0')}:00</td><td class="text-end">${c}</td></tr>`).join('') || '<tr><td class="text-muted">No orders</td></tr>'}</table></div>
          <div class="col-md-4"><strong>Popular items</strong><ol>${daily.popularItems.map(p => `<li>${esc(p.name)} - ${p.quantity}</li>`).join('') || '<li class="text-muted">None</li>'}</ol></div>
        </div>
      </div></div>`;
    document.getElementById('rangeForm').onsubmit = (e) => { e.preventDefault(); const d = App.formData(e.target); location.hash = `reports?from=${d.from}&to=${d.to}&day=${day}`; };
    document.getElementById('dayPick').onchange = (e) => location.hash = `reports?from=${from}&to=${to}&day=${e.target.value}`;
}

/* ---------- Payments ---------- */
async function payments() {
    const list = await api('/api/merchant/payments');
    view.innerHTML = `<h4 class="mb-3">Payments</h4>
      <div class="card"><div class="table-responsive"><table class="table align-middle small mb-0">
        <thead class="table-light"><tr><th>Date</th><th>Order</th><th>Method</th><th>Gateway</th><th>Reference</th><th>Status</th><th>Recorded by</th><th class="text-end">Amount</th></tr></thead>
        <tbody>${list.map(p => `<tr><td>${App.dt(p.createdAt)}</td><td><a href="/order.html?id=${p.orderId}">#${esc(p.orderNumber)}</a></td><td>${App.label(p.method)}</td>
          <td>${esc(p.gateway || '-')}</td><td><code>${esc(p.transactionRef || '-')}</code></td>
          <td><span class="badge ${p.status === 'SUCCESS' ? 'text-bg-success' : p.status === 'FAILED' ? 'text-bg-danger' : 'text-bg-secondary'}">${App.label(p.status)}</span>${p.failureReason ? `<div class="text-danger">${esc(p.failureReason)}</div>` : ''}</td>
          <td>${esc(p.recordedBy || '-')}</td><td class="text-end">${money(p.amount)}</td></tr>`).join('') || '<tr><td colspan="8" class="text-center text-muted py-4">No payments yet</td></tr>'}</tbody></table></div></div>`;
}

/* ---------- Notifications ---------- */
async function notifications() {
    const list = await api('/api/merchant/notifications');
    view.innerHTML = `<h4 class="mb-1">Notification log</h4><p class="small text-muted">Email/SMS updates sent to customers. "Simulated" means no SMTP/SMS provider is configured, so the message was written to the server log.</p>
      <div class="card"><div class="table-responsive"><table class="table align-middle small mb-0">
        <thead class="table-light"><tr><th>Time</th><th>Channel</th><th>Recipient</th><th>Order</th><th>Message</th><th>Status</th></tr></thead>
        <tbody>${list.map(n => `<tr><td class="text-nowrap">${App.dt(n.createdAt)}</td><td>${App.label(n.channel)}</td><td>${esc(n.recipient)}</td><td>#${esc(n.orderNumber || '-')}</td>
          <td><strong>${esc(n.subject || '')}</strong><div class="text-muted">${esc(n.message || '')}</div></td><td>${App.label(n.status)}</td></tr>`).join('') || '<tr><td colspan="6" class="text-center text-muted py-4">No notifications yet</td></tr>'}</tbody></table></div></div>`;
}

/* ---------- Kitchen staff ---------- */
async function staff() {
    const list = await api('/api/merchant/staff');
    view.innerHTML = `<div class="d-flex justify-content-between align-items-center mb-3"><h4 class="mb-0">Kitchen staff</h4><button class="btn btn-brand btn-sm" id="addStaff"><i class="bi bi-plus"></i> Add staff</button></div>
      <div class="card"><div class="table-responsive"><table class="table align-middle small mb-0">
        <thead class="table-light"><tr><th>Name</th><th>Email</th><th>Mobile</th><th>Account</th></tr></thead>
        <tbody>${list.map(s => `<tr><td>${esc(s.name)}</td><td>${esc(s.email)}</td><td>${esc(s.mobile)}</td>
          <td><div class="form-check form-switch"><input class="form-check-input" type="checkbox" ${s.enabled ? 'checked' : ''} onchange="toggleStaff(${s.id}, this.checked)"><label class="form-check-label">${s.enabled ? 'Enabled' : 'Disabled'}</label></div></td></tr>`).join('')
          || '<tr><td colspan="4" class="text-center text-muted py-4">No kitchen staff accounts</td></tr>'}</tbody></table></div></div>`;
    document.getElementById('addStaff').onclick = () => openForm('Add kitchen staff', [
        { name: 'name', label: 'Name', required: true }, { name: 'email', label: 'Email', type: 'email', required: true },
        { name: 'mobile', label: 'Mobile', required: true }, { name: 'password', label: 'Password', type: 'password', required: true }], null,
        async d => { await api('/api/merchant/staff', { method: 'POST', body: d }); App.toast('Staff account created'); staff(); });
    window.toggleStaff = async (id, enabled) => { try { await api('/api/merchant/staff/' + id, { method: 'PATCH', body: { enabled } }); staff(); } catch (e) { App.error(e); } };
}

/* ---------- Restaurant profile ---------- */
async function profile() {
    const r = await api('/api/merchant/restaurant');
    const fields = [
        { name: 'name', label: 'Restaurant name', required: true }, { name: 'cuisine', label: 'Cuisine' },
        { name: 'phone', label: 'Phone' }, { name: 'email', label: 'Email', type: 'email' },
        { name: 'address', label: 'Address', col: 12 }, { name: 'description', label: 'Description', type: 'textarea', col: 12 },
        { name: 'gstNumber', label: 'GSTIN' }, { name: 'taxPercent', label: 'Tax / GST %', type: 'number', step: '0.01', required: true },
        { name: 'active', label: 'Restaurant active (visible to customers)', type: 'checkbox' },
        { name: 'acceptingOrders', label: 'Accepting orders', type: 'checkbox' }];
    view.innerHTML = `<h4 class="mb-3">Restaurant profile</h4><div class="card"><div class="card-body">
        <form id="profileForm" novalidate><div class="alert alert-danger d-none form-alert small"></div><div class="row g-3">${fields.map(f => field(f, r[f.name])).join('')}</div>
        <button class="btn btn-brand mt-3">Save changes</button></form></div></div>`;
    const form = document.getElementById('profileForm');
    form.onsubmit = async (e) => {
        e.preventDefault();
        const d = App.formData(form);
        d.taxPercent = d.taxPercent === '' ? null : Number(d.taxPercent);
        try {
            const updated = await api('/api/merchant/restaurant', { method: 'PUT', body: d });
            document.getElementById('restName').textContent = updated.name;
            App.toast('Restaurant profile saved');
        } catch (err) { App.showFormErrors(form, err); }
    };
}
