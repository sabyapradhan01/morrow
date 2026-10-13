/*

 * Morrow Cloud — Java/Spring + JDBC + PostgreSQL frontend.

 * Authentication, wallet balances, top-ups, transfers and transaction history

 * are handled by server endpoints. Top-ups are simulated credits.

 */

(() => {

  'use strict';

  const $ = (selector, root = document) => root.querySelector(selector);   const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  const money = value => new Intl.NumberFormat('en-IN', {

    style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2

  }).format(Number(value || 0));

  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({

    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'

  }[char]));

  class ApiError extends Error {

    constructor(message, status) { super(message); this.status = status; }

  }

  async function api(path, options = {}) {

    const headers = { Accept: 'application/json', ...(options.headers || {}) };

    const request = { method: options.method || 'GET', credentials: 'same-origin', headers };

    if (options.body !== undefined) {

      headers['Content-Type'] = 'application/json';

      headers['X-Requested-With'] = 'XMLHttpRequest';

      request.body = JSON.stringify(options.body);

    } else if (request.method !== 'GET') {

      headers['X-Requested-With'] = 'XMLHttpRequest';

    }

    let response;

    try {

      response = await fetch(path, request);

    } catch (_) {

      throw new ApiError('Could not reach Morrow. Check that the Spring Boot server is running.', 0);

    }

    if (response.status === 204) return null;

    const contentType = response.headers.get('content-type') || '';

    const body = contentType.includes('application/json') ? await response.json() : null;

    if (!response.ok) throw new ApiError(body?.message || `Request failed (${response.status}).`, response.status);

    return body;

  }

  let user = null;

  let balance = 0;

  let transactions = [];

  let activeFilter = 'All';

  let searchTerm = '';

  let balanceHidden = false;

  let toastTimer = null;

  let busy = false;

  const loginView = $('#login-view');

  const walletApp = $('#wallet-app');

  const loginForm = $('#login-form');

  const registerForm = $('#register-form');

  const loginEmail = $('#login-email');

  const loginPassword = $('#login-password');

  const loginError = $('#login-error');

  const registerError = $('#register-error');

  const toast = $('#toast');

  const toastMessage = $('#toast-message');

  function notify(message) {

    toastMessage.textContent = message;

    toast.hidden = false;

    window.clearTimeout(toastTimer);

    toastTimer = window.setTimeout(() => { toast.hidden = true; }, 4000);

  }

  function initialsFor(value) {

    return String(value || 'Morrow User').trim().split(/\s+/).filter(Boolean)

      .slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'MW';

  }

  function reportApiError(error) {

    if (error.status === 401 && user) {

      user = null;

      balance = 0;

      transactions = [];

      walletApp.hidden = true;

      loginView.hidden = false;

      showAuthMode('login');

      $('#login-error span').textContent = 'Your session has expired. Please sign in again.';

      loginError.hidden = false;

      return;

    }

    notify(error.message);

  }

  function showModal(id) {

    const modal = document.getElementById(id);

    if (!modal) return;

    modal.hidden = false;

    const focusable = $('input:not([type="hidden"]),select,button', modal);

    if (focusable) window.setTimeout(() => focusable.focus(), 50);

  }

  function closeModal(id) {

    const modal = document.getElementById(id);

    if (modal) modal.hidden = true;

  }

  function showAuthMode(mode) {

    const registering = mode === 'register';

    loginForm.hidden = registering;

    registerForm.hidden = !registering;

    $('#auth-kicker').textContent = registering ? 'JOIN MORROW' : 'WELCOME TO MORROW';

    $('#auth-title').textContent = registering ? 'A fresh start.' : 'Good to see you.';

    $('#auth-subtitle').textContent = registering

      ? 'Create an account to open your own database-backed wallet.'

      : 'Sign in to access your database-backed wallet.';

    loginError.hidden = true;

    registerError.hidden = true;

    window.setTimeout(() => (registering ? $('#register-name') : loginEmail).focus(), 100);

  }

  function gotoPage(pageName) {

    const validPages = ['overview', 'transfer', 'transactions', 'cards', 'settings'];

    const page = validPages.includes(pageName) ? pageName : 'overview';

    $$('.page-view').forEach(view => view.classList.toggle('active', view.dataset.view === page));$$

('.nav-item[data-page]').forEach(button => button.classList.toggle('active', button.dataset.page === page));

    if (page === 'transactions') renderTransactions();

    if (page === 'overview') renderRecent();

    window.scrollTo({ top: 0, behavior: 'smooth' });

  }

  function normalizedTransaction(transaction) {

    const amount = Number(transaction.amount || 0);

    const createdAt = transaction.createdAt ? new Date(transaction.createdAt) : new Date();

    const date = Number.isNaN(createdAt.getTime()) ? 'Recently' : createdAt.toLocaleString('en-IN', {

      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'

    });

    return {

      ...transaction,

      amount,

      id: transaction.referenceCode || transaction.id || 'MRW',

      date,

      initials: initialsFor(transaction.name),

      status: String(transaction.status || 'COMPLETED').replace(/^./, first => first.toUpperCase())

    };

  }

  function filteredTransactions() {

    const needle = searchTerm.trim().toLocaleLowerCase();

    return transactions.filter(transaction => {

      const searchable = [transaction.name, transaction.detail, transaction.category, transaction.id].join(' ').toLocaleLowerCase();

      const matchesSearch = !needle || searchable.includes(needle);

      const matchesFilter = activeFilter === 'All'

        || (activeFilter === 'Money in' && transaction.amount > 0)

        || (activeFilter === 'Money out' && transaction.amount < 0)

        || (activeFilter === 'Pending' && transaction.status === 'Pending');

      return matchesSearch && matchesFilter;

    });

  }

  function transactionRow(transaction) {

    const positive = transaction.amount > 0;

    const initials = escapeHtml(transaction.initials || initialsFor(transaction.name));

    const statusClass = transaction.status === 'Pending' ? ' pending' : '';

    return `<div class="tx-row">

      <div class="tx-identity">

        <span class="avatar${positive ? ' positive' : ''}">${initials}</span>

        <span class="tx-copy">

          <strong>${escapeHtml(transaction.name)}</strong>

          <small>${escapeHtml(transaction.id)}</small>

        </span>

      </div>

      <span class="tx-category">${escapeHtml(transaction.category)}</span>

      <span class="tx-date">${escapeHtml(transaction.date)}</span>

      <span><i class="status-pill${statusClass}">${escapeHtml(transaction.status)}</i></span>

      <strong class="tx-amount${positive ? ' positive' : ''}">${positive ? '+ ' : '− '}${money(Math.abs(transaction.amount))}</strong>

    </div>`;

  }

  function emptyState() {

    const hasSearch = Boolean(searchTerm.trim()) || activeFilter !== 'All';

    return `<div class="empty-state">

      <svg class="icon"><use href="#i-search"></use></svg>

      <strong>${hasSearch ? 'No matching transactions' : 'Your activity starts here'}</strong>

      <span>${hasSearch ? 'Try another name or filter.' : 'When you add simulated funds or send a transfer, it will appear here.'}</span>

    </div>`;

  }

  function renderRecent() {

    const target = $('#recent-rows');

    if (!target) return;

    const recent = transactions.slice(0, 4);

    target.innerHTML = `<div class="table-head"><span>TRANSACTION</span><span>CATEGORY</span><span>DATE</span><span>STATUS</span><span>AMOUNT</span></div>`

      + (recent.length ? recent.map(transactionRow).join('') : emptyState());

  }

  function renderTransactions() {

    const target = $('#all-rows');

    if (!target) return;

    const entries = filteredTransactions();

    target.innerHTML = `<div class="table-head"><span>TRANSACTION</span><span>CATEGORY</span><span>DATE</span><span>STATUS</span><span>AMOUNT</span></div>`

      + (entries.length ? entries.map(transactionRow).join('') : emptyState());

    $('#showing-count').textContent = `SHOWING ${entries.length} OF ${transactions.length} TRANSACTIONS`;

    $('#transaction-count').textContent = transactions.length.toLocaleString('en-IN');

    $('#total-received').textContent = money(user?.totalReceived || 0);

    $('#total-sent').textContent = money(user?.totalSent || 0);

    $('#monthly-sent').textContent = money(user?.totalSent || 0);

  }

  function renderBalance() {

    $('#balance-value').textContent = balanceHidden ? '₹ ••••••••' : money(balance);

    $('#balance-toggle').setAttribute('aria-label', balanceHidden ? 'Show balance' : 'Hide balance');

    $('#balance-toggle').innerHTML = balanceHidden ? '<svg class="icon"><use href="#i-eye-off"/></svg>' : '<svg class="icon"><use href="#i-eye"/></svg>';

    $('#available-balance').textContent = `AVAILABLE  ${money(balance)}`;

    $('#settings-balance-toggle').classList.toggle('on', balanceHidden);

    updateTransferPreview();

  }

  function updateTransferPreview() {

    const amount = Number($('#transfer-amount').value || 0);

    $('#preview-amount').textContent = money(Number.isFinite(amount) && amount >= 0 ? amount : 0);

    $('#preview-recipient').textContent = $('#recipient').value.trim() || 'Recipient email';

  }

  function updateIdentity(summary) {

    user = summary;

    balance = Number(summary.balance || 0);

    const fullName = summary.fullName || 'Morrow User';

    const firstName = fullName.trim().split(/\s+/)[0] || 'there';

    const initials = initialsFor(fullName);

    $('#profile-name').textContent = fullName;

    $('#greeting-name').textContent = firstName;

    $('#settings-name').textContent = fullName;

    $('#settings-email').textContent = summary.email || '';

    $('#mini-avatar').textContent = initials;

    $('#card-holder').textContent = fullName.toUpperCase();

    $('#wallet-reference').textContent = `MRW · ${String(summary.walletId).padStart(4, '0').slice(-4)}`;

    $('.profile-card .avatar').textContent = initials;

    $('.settings-profile .avatar').textContent = initials;

    $('#wallet-owner-label').textContent = `${firstName}'s Morrow INR wallet`;

    $('.topbar-date').textContent = new Date().toLocaleDateString('en-IN', {

      weekday: 'long', day: '2-digit', month: 'long'

    }).toUpperCase();

    loginView.hidden = true;

    walletApp.hidden = false;

    $('.demo-banner').hidden = false;

    renderBalance();

  }

  async function loadTransactions() {

    const payload = await api('/api/wallet/transactions');

    transactions = payload.map(normalizedTransaction);

    renderRecent();

    renderTransactions();

  }

  async function refreshWallet() {

    const summary = await api('/api/wallet/summary');

    updateIdentity(summary);

    await loadTransactions();

    return summary;

  }

  async function completeSignIn(summary, message) {

    updateIdentity(summary);

    await loadTransactions();

    gotoPage('overview');

    loginPassword.value = '';

    $('#register-password').value = '';

    $('#register-confirm-password').value = '';

    notify(message);

  }

  function setButtonBusy(button, isBusy, busyText) {

    if (isBusy) {

      button.dataset.originalHtml = button.innerHTML;

      button.disabled = true;

      button.dataset.busyText = busyText || 'Please wait…';

      button.textContent = button.dataset.busyText;

    } else {

      button.disabled = false;

      if (button.dataset.originalHtml) button.innerHTML = button.dataset.originalHtml;

      delete button.dataset.originalHtml;

      delete button.dataset.busyText;

    }

  }

  loginForm.addEventListener('submit', async event => {

    event.preventDefault();

    if (busy) return;

    const email = loginEmail.value.trim();

    const password = loginPassword.value;

    if (!loginEmail.validity.valid || !email || !password) {

      $('#login-error span').textContent = 'Enter a valid email address and your password.';

      loginError.hidden = false;

      (!email || !loginEmail.validity.valid ? loginEmail : loginPassword).focus();

      return;

    }

    const button = $('button[type="submit"]', loginForm);

    busy = true;

    loginError.hidden = true;

    setButtonBusy(button, true, 'Signing in…');

    try {

      const summary = await api('/api/auth/login', { method: 'POST', body: { email, password } });

      await completeSignIn(summary, `Welcome back, ${summary.fullName.split(/\s+/)[0]}.`);

    } catch (error) {

      $('#login-error span').textContent = error.message;

      loginError.hidden = false;

    } finally {

      setButtonBusy(button, false);

      busy = false;

    }

  });

  registerForm.addEventListener('submit', async event => {

    event.preventDefault();

    if (busy) return;

    const fullName = $('#register-name').value.trim();

    const email = $('#register-email').value.trim();

    const password = $('#register-password').value;

    const confirmPassword = $('#register-confirm-password').value;

    const button = $('button[type="submit"]', registerForm);

    if (!fullName || fullName.length < 2 || !$('#register-email').validity.valid || password.length < 8) {

      $('#register-error span').textContent = 'Enter your name, a valid email, and a password with at least 8 characters.';

      registerError.hidden = false;

      return;

    }

    if (password !== confirmPassword) {

      $('#register-error span').textContent = 'The passwords do not match.';

      registerError.hidden = false;

      $('#register-confirm-password').focus();

      return;

    }

    busy = true;

    registerError.hidden = true;

    setButtonBusy(button, true, 'Creating account…');

    try {

      const summary = await api('/api/auth/register', { method: 'POST', body: { fullName, email, password } });

      await completeSignIn(summary, 'Your premium account and INR wallet have been created.');

      registerForm.reset();

    } catch (error) {

      $('#register-error span').textContent = error.message;

      registerError.hidden = false;

    } finally {

      setButtonBusy(button, false);

      busy = false;

    }

  });

  $('#show-register').addEventListener('click', () => showAuthMode('register'));

  $('#show-login').addEventListener('click', () => showAuthMode('login'));

  $('#toggle-password').addEventListener('click', () => {

    const isPassword = loginPassword.type === 'password';

    loginPassword.type = isPassword ? 'text' : 'password';

    $('#toggle-password').setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');

  });

  $('#logout-button').addEventListener('click', async () => {

    try {

      await api('/api/auth/logout', { method: 'POST' });

      user = null;

      balance = 0;

      transactions = [];

      loginView.hidden = false;

      walletApp.hidden = true;

      loginForm.reset();

      registerForm.reset();

      loginPassword.value = '';

      showAuthMode('login');

      notify('You have been securely signed out.');

    } catch (error) {

      notify(error.message);

    }

  });

  $$('.nav-item[data-page]').forEach(button => button.addEventListener('click', () => gotoPage(button.dataset.page)));$$

('[data-go]').forEach(button => button.addEventListener('click', () => gotoPage(button.dataset.go)));

  $$('[data-action="add-money"]').forEach(button => button.addEventListener('click', () => showModal('add-money-modal')));   $$

('[data-action="request-money"]').forEach(button => button.addEventListener('click', () => notify('Money requests are not part of this PAT 4 release yet.')));

  $('#help-button').addEventListener('click', () => notify('For the project demo, use a registered email address for transfers.'));

  $('#notifications-button').addEventListener('click', () => notify('You are all caught up.'));

  $('#spend-more').addEventListener('click', () => notify(`Total recorded transfers sent: ${money(user?.totalSent || 0)}.`));

  $('#dismiss-banner').addEventListener('click', () => { $('.demo-banner').hidden = true; });$('#dismiss-toast').addEventListener('click', () => { toast.hidden = true; window.clearTimeout(toastTimer); });

  $('#balance-toggle').addEventListener('click', () => { balanceHidden = !balanceHidden; renderBalance(); });

  $('#settings-balance-toggle').addEventListener('click', () => { balanceHidden = !balanceHidden; renderBalance(); });

  $('#global-search').addEventListener('input', event => {

    searchTerm = event.target.value;

    $('#transaction-search').value = searchTerm;

    if (searchTerm) gotoPage('transactions');

    renderTransactions();

  });

  $('#transaction-search').addEventListener('input', event => {

    searchTerm = event.target.value;

    $('#global-search').value = searchTerm;

    renderTransactions();

  });

  $$('[data-filter]').forEach(button => button.addEventListener('click', () => {     activeFilter = button.dataset.filter;     $$

('[data-filter]').forEach(item => item.classList.toggle('selected', item === button));

    renderTransactions();

  }));

  $('#recipient').addEventListener('input', updateTransferPreview);

  $('#transfer-amount').addEventListener('input', updateTransferPreview);   $$('[data-amount]').forEach(button => button.addEventListener('click', () => {$('#transfer-amount').value = button.dataset.amount;

    updateTransferPreview();

  }));

  $('#review-transfer').addEventListener('click', () => {

    const recipientEmail = $('#recipient').value.trim();

    const amount = Number($('#transfer-amount').value);

    if (!recipientEmail || !$('#recipient').validity.valid) {

      notify('Enter the registered email address of the recipient.');

      $('#recipient').focus();

      return;

    }

    if (!Number.isFinite(amount) || amount <= 0) {

      notify('Enter an amount greater than zero.');

      $('#transfer-amount').focus();

      return;

    }

    if (amount > balance) {

      notify('Your available balance is too low for this transfer.');

      $('#transfer-amount').focus();

      return;

    }

    $('#review-recipient').textContent = recipientEmail;

    $('#review-amount').textContent = money(amount);

    $('#review-note').textContent = $('#transfer-note').value.trim() || '—';

    showModal('transfer-review-modal');

  });

  $('#confirm-transfer').addEventListener('click', async () => {

    if (busy) return;

    const button = $('#confirm-transfer');

    const requestBody = {

      recipientEmail: $('#recipient').value.trim(),

      amount: Number($('#transfer-amount').value),

      description: $('#transfer-note').value.trim()

    };

    busy = true;

    setButtonBusy(button, true, 'Recording transfer…');

    try {

      const summary = await api('/api/wallet/transfers', { method: 'POST', body: requestBody });

      updateIdentity(summary);

      await loadTransactions();

      closeModal('transfer-review-modal');

      $('#transfer-amount').value = '';

      $('#transfer-note').value = '';

      $('#recipient').value = '';

      updateTransferPreview();

      gotoPage('transactions');

      notify(`${money(requestBody.amount)} transfer recorded securely in the database.`);

    } catch (error) {

      reportApiError(error);

    } finally {

      setButtonBusy(button, false);

      busy = false;

    }

  });

  $('#confirm-topup').addEventListener('click', async () => {

    if (busy) return;

    const amount = Number($('#top-up-amount').value);

    if (!Number.isFinite(amount) || amount < 1 || amount > 1000000) {

      notify('Enter an amount between ₹1 and ₹10,00,000.');

      $('#top-up-amount').focus();

      return;

    }

    const button = $('#confirm-topup');

    busy = true;

    setButtonBusy(button, true, 'Saving top-up…');

    try {

      const summary = await api('/api/wallet/top-up', { method: 'POST', body: { amount } });

      updateIdentity(summary);

      await loadTransactions();

      closeModal('add-money-modal');

      notify(`${money(amount)} simulated credit securely saved in the database.`);

    } catch (error) {

      reportApiError(error);

    } finally {

      setButtonBusy(button, false);

      busy = false;

    }

  });

  $$('[data-topup]').forEach(button => button.addEventListener('click', () => { $('#top-up-amount').value = button.dataset.topup; }));   $$('[data-close-modal]').forEach(button => button.addEventListener('click', () => closeModal(button.dataset.closeModal)));

  $$('.modal-backdrop').forEach(backdrop => backdrop.addEventListener('mousedown', event => { if (event.target === backdrop) backdrop.hidden = true; }));   document.addEventListener('keydown', event => {     if (event.key === 'Escape') $$

('.modal-backdrop:not([hidden])').forEach(modal => { modal.hidden = true; });

    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k' && !walletApp.hidden) {

      event.preventDefault();

      $('#global-search').focus();

    }

  });

  $('#export-csv').addEventListener('click', () => {

    const header = ['Reference', 'Counterparty', 'Category', 'Date', 'Status', 'Amount (INR)'];

    const lines = [header, ...filteredTransactions().map(t => [t.id, t.name, t.category, t.date, t.status, t.amount])];

    const csv = lines.map(row => row.map(value => `"${String(value).replace(/"/g, '""')}"`).join(',')).join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });

    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');

    link.href = url;

    link.download = 'morrow-wallet-statement.csv';

    document.body.appendChild(link);

    link.click();

    link.remove();

    URL.revokeObjectURL(url);

    notify('Exported the currently visible database-backed transactions as CSV.');

  });

  $('#manage-card').addEventListener('click', () => notify('This is a beautiful visual concept only; no bank card is issued.'));

  async function bootstrap() {

    try {

      const summary = await api('/api/auth/me');

      if (summary.role === 'ADMIN') {
        await api('/api/auth/logout', { method: 'POST' });
        loginView.hidden = false;
        walletApp.hidden = true;
        showAuthMode('login');
        $('#login-error span').textContent = 'This is an administrator account. Please sign in at /admin.';
        loginError.hidden = false;
        return;
      }

      updateIdentity(summary);

      await loadTransactions();

      gotoPage('overview');

    } catch (error) {

      loginView.hidden = false;

      walletApp.hidden = true;

      if (error.status !== 401) {

        $('#login-error span').textContent = error.message;

        loginError.hidden = false;

      }

    }

  }

  renderBalance();

  renderRecent();

  renderTransactions();

  showAuthMode('login');

  bootstrap();

})();
