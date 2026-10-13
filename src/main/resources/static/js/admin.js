(() => {
  'use strict';
  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const loginView = $('#admin-login-view');
  const dashboard = $('#admin-dashboard');
  const loginForm = $('#admin-login-form');
  const loginError = $('#admin-login-error');
  const toast = $('#admin-toast');
  let toastTimer = null;
  let page = 'overview';
  let dataRows = [];
  let searchValue = '';
  let editorSpec = null;
  let sqlWorkbenchEnabled = false;

  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('en-IN', {style:'currency',currency:'INR',minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(value || 0));

  async function api(path, options = {}) {
    const headers = {Accept:'application/json', ...(options.headers || {})};
    const request = {method: options.method || 'GET', credentials:'same-origin', headers};
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      headers['X-Requested-With'] = 'XMLHttpRequest';
      request.body = JSON.stringify(options.body);
    } else if (request.method !== 'GET') headers['X-Requested-With'] = 'XMLHttpRequest';
    let response;
    try { response = await fetch(path, request); }
    catch (_) { throw new Error('Cannot reach Morrow. Check that the Spring Boot server is running.'); }
    if (response.status === 204) return null;
    const type = response.headers.get('content-type') || '';
    const body = type.includes('application/json') ? await response.json() : null;
    if (!response.ok) throw new Error(body?.message || `Request failed (${response.status}).`);
    return body;
  }

  function notify(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle('error', isError);
    toast.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => { toast.hidden = true; }, 4200);
  }

  function showLoginError(message) { loginError.textContent = message; loginError.hidden = !message; }
  function setBusy(button, yes, text) {
    if (yes) { button.dataset.oldText = button.textContent; button.disabled = true; button.textContent = text; }
    else { button.disabled = false; if (button.dataset.oldText) button.textContent = button.dataset.oldText; delete button.dataset.oldText; }
  }
  function showAdmin(summary) {
    if (!summary || summary.role !== 'ADMIN') {
      loginView.hidden = false; dashboard.hidden = true;
      showLoginError('This account is not an active administrator. Use the user login for USER accounts.');
      return;
    }
    loginView.hidden = true; dashboard.hidden = false;
    $('#admin-name').textContent = summary.fullName || 'Administrator';
    $('#admin-email-label').textContent = summary.email || '';
    showLoginError('');
    (async () => {
      await configureAdminFeatures();
      await loadPage('overview');
    })();
  }

  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    const email = $('#admin-email').value.trim();
    const password = $('#admin-password').value;
    if (!email || !$('#admin-email').validity.valid || !password) { showLoginError('Enter a valid email address and password.'); return; }
    const button = $('#admin-login-submit');
    setBusy(button, true, 'Verifying access…'); showLoginError('');
    try {
      const summary = await api('/api/auth/admin-login', {method:'POST', body:{email,password}});
      $('#admin-password').value = '';
      if (summary.role !== 'ADMIN') throw new Error('This account is not assigned the ADMIN role.');
      showAdmin(summary);
      notify('Administrator session established.');
    } catch (error) { showLoginError(error.message); }
    finally { setBusy(button, false); }
  });

  $('#admin-logout').addEventListener('click', async () => {
    try { await api('/api/auth/logout', {method:'POST'}); }
    catch (_) { /* Clear local view even if the session already expired. */ }
    dashboard.hidden = true; loginView.hidden = false; loginForm.reset(); showLoginError('You have signed out.');
  });

  async function configureAdminFeatures() {
    try {
      const features = await api('/api/admin/features');
      sqlWorkbenchEnabled = features.sqlWorkbenchEnabled === true;
    } catch (_) {
      // Fail closed if the feature flag cannot be confirmed.
      sqlWorkbenchEnabled = false;
    }
    const sqlNav = $('.nav-link[data-admin-page="sql"]');
    if (sqlNav) sqlNav.hidden = !sqlWorkbenchEnabled;
    const warning = $('.admin-warning');
    if (warning && !sqlWorkbenchEnabled) {
      warning.textContent = 'ADMIN ACCESS — Hosted safety mode: raw SQL execution is disabled. Use the local academic setup for DDL/DML demonstrations. Top-ups are simulated.';
    }
  }

  const pageMeta = {
    overview:{title:'System overview'}, users:{title:'User management',heading:'Users',desc:'Create, edit, role-assign, suspend or close accounts.',url:'/api/admin/users'},
    wallets:{title:'Wallet management',heading:'Wallets',desc:'Review wallet ownership and adjust balances with a corresponding audit transaction and ledger entry.',url:'/api/admin/wallets'},
    transactions:{title:'Transaction management',heading:'Transactions',desc:'Edit transaction descriptions or remove a transaction and its ledger rows.',url:'/api/admin/transactions'},
    ledger:{title:'Ledger management',heading:'Ledger',desc:'Inspect and carefully edit low-level credit/debit records.',url:'/api/admin/ledger'},
    sql:{title:'SQL workbench'}
  };

  async function loadPage(next) {
    if (next === 'sql' && !sqlWorkbenchEnabled) { notify('The raw SQL workbench is disabled on this hosted instance. Use local SQL*Plus for DDL/DML demos.', true); next = 'overview'; }
    page = pageMeta[next] ? next : 'overview';
    $$('.nav-link').forEach(btn => btn.classList.toggle('active', btn.dataset.adminPage === page));
    $('#admin-page-title').textContent = pageMeta[page].title;
    $('#admin-overview-panel').hidden = page !== 'overview';
    $('#admin-data-panel').hidden = !['users','wallets','transactions','ledger'].includes(page);
    $('#admin-sql-panel').hidden = page !== 'sql';
    $('#show-create-user').hidden = page !== 'users';
    $('#create-user-form').hidden = true;
    $('#table-search').value = '';
    searchValue = '';
    if (page === 'overview') await loadOverview();
    else if (pageMeta[page].url) {
      $('#data-heading').textContent = pageMeta[page].heading;
      $('#data-description').textContent = pageMeta[page].desc;
      $('#data-eyebrow').textContent = 'LIVE ORACLE RECORDS';
      await loadRecords();
    }
  }

  $$('.nav-link').forEach(btn => btn.addEventListener('click', () => loadPage(btn.dataset.adminPage)));
  $('#admin-refresh').addEventListener('click', async () => {
    if (page === 'overview') await loadOverview();
    else if (pageMeta[page]?.url) await loadRecords();
    else notify('SQL results remain visible until you run another statement.');
  });

  async function loadOverview() {
    try {
      const data = await api('/api/admin/overview');
      $('#metric-users').textContent = data.users;
      $('#metric-active-users').textContent = `${data.activeUsers} active`;
      $('#metric-wallets').textContent = data.wallets;
      $('#metric-transactions').textContent = data.transactions;
      $('#metric-ledger').textContent = data.ledgerEntries;
      $('#metric-total-balance').textContent = money(data.totalBalance);
      $('#snapshot-time').textContent = `REFRESHED ${new Date().toLocaleTimeString('en-IN')}`;
    } catch (error) { notify(error.message, true); }
  }

  async function loadRecords() {
    try { dataRows = await api(pageMeta[page].url); renderRecords(); }
    catch (error) { dataRows = []; renderRecords(); notify(error.message, true); }
  }
  $('#table-search').addEventListener('input', event => { searchValue = event.target.value.toLowerCase().trim(); renderRecords(); });

  function columnsFor(current) {
    if (current === 'users') return [
      ['USER_ID','ID'],['FULL_NAME','Full name'],['EMAIL','Email'],['ROLE','Role'],['STATUS','Status'],['WALLET_ID','Wallet'],['BALANCE','Balance'],['CREATED_AT','Created'],['ACTIONS','Actions']
    ];
    if (current === 'wallets') return [['WALLET_ID','Wallet ID'],['USER_ID','User ID'],['FULL_NAME','Owner'],['EMAIL','Email'],['CURRENCY_CODE','Currency'],['BALANCE','Balance'],['CREATED_AT','Created'],['ACTIONS','Actions']];
    if (current === 'transactions') return [['TRANSACTION_ID','ID'],['REFERENCE_CODE','Reference'],['TRANSACTION_TYPE','Type'],['SENDER_WALLET_ID','Sender'],['RECEIVER_WALLET_ID','Receiver'],['AMOUNT','Amount'],['STATUS','Status'],['DESCRIPTION','Description'],['CREATED_AT','Created'],['ACTIONS','Actions']];
    return [['LEDGER_ID','ID'],['TRANSACTION_ID','Transaction'],['WALLET_ID','Wallet'],['EMAIL','Owner email'],['ENTRY_TYPE','Entry'],['AMOUNT','Amount'],['BALANCE_AFTER','Balance after'],['CREATED_AT','Created'],['ACTIONS','Actions']];
  }
  function tableValue(key, value) {
    if (value === null || value === undefined) return '<span class="muted">—</span>';
    if (key === 'BALANCE' || key === 'AMOUNT' || key === 'BALANCE_AFTER') return escapeHtml(money(value));
    if (key === 'ROLE') return `<span class="role-chip ${String(value)==='ADMIN'?'admin':''}">${escapeHtml(value)}</span>`;
    if (key === 'STATUS') return `<span class="status-chip ${['CLOSED','SUSPENDED'].includes(String(value))?'closed':''}">${escapeHtml(value)}</span>`;
    if (String(key).endsWith('_AT')) return escapeHtml(String(value).replace('T',' ').slice(0,19));
    return escapeHtml(value);
  }
  function actionButtons(row) {
    const id = Number(page === 'users' ? row.USER_ID : page === 'wallets' ? row.WALLET_ID : page === 'transactions' ? row.TRANSACTION_ID : row.LEDGER_ID);
    if (page === 'users') return `<div class="row-actions"><button class="mini-action" data-action="edit-user" data-id="${id}">Edit</button><button class="mini-action danger" data-action="close-user" data-id="${id}" ${row.STATUS==='CLOSED'?'disabled':''}>Close</button></div>`;
    if (page === 'wallets') return `<div class="row-actions"><button class="mini-action" data-action="edit-wallet" data-id="${id}" data-value="${escapeHtml(row.BALANCE)}">Set balance</button></div>`;
    if (page === 'transactions') return `<div class="row-actions"><button class="mini-action" data-action="edit-transaction" data-id="${id}" data-value="${escapeHtml(row.DESCRIPTION || '')}">Edit</button><button class="mini-action danger" data-action="delete-transaction" data-id="${id}">Delete</button></div>`;
    return `<div class="row-actions"><button class="mini-action" data-action="edit-ledger" data-id="${id}" data-type="${escapeHtml(row.ENTRY_TYPE)}" data-value="${escapeHtml(row.AMOUNT)}">Edit</button><button class="mini-action danger" data-action="delete-ledger" data-id="${id}">Delete</button></div>`;
  }
  function renderRecords() {
    const filtered = dataRows.filter(row => !searchValue || Object.entries(row).some(([k,v]) => k !== 'PASSWORD_HASH' && String(v ?? '').toLowerCase().includes(searchValue)));
    const columns = columnsFor(page);
    $('#admin-table-head').innerHTML = `<tr>${columns.map(([key,label])=>`<th>${escapeHtml(label)}</th>`).join('')}</tr>`;
    $('#admin-table-body').innerHTML = filtered.length ? filtered.map(row => `<tr>${columns.map(([key])=>`<td>${key==='ACTIONS'?actionButtons(row):tableValue(key,row[key])}</td>`).join('')}</tr>`).join('') : `<tr><td class="empty-cell" colspan="${columns.length}">No matching Oracle records.</td></tr>`;
    $('#table-record-count').textContent = `${filtered.length} displayed / ${dataRows.length} records`;
  }

  $('#show-create-user').addEventListener('click', () => { $('#create-user-form').hidden = false; $('#create-user-form input[name="fullName"]').focus(); });
  $('#hide-create-user').addEventListener('click', () => { $('#create-user-form').hidden = true; });
  $('#create-user-form').addEventListener('submit', async event => {
    event.preventDefault(); const form = event.currentTarget; const data = Object.fromEntries(new FormData(form).entries());
    const button = $('button[type="submit"]', form); setBusy(button,true,'Creating…');
    try { await api('/api/admin/users',{method:'POST',body:data}); form.reset(); form.hidden=true; notify('User and INR wallet created in Oracle.'); await loadRecords(); await loadOverview(); }
    catch(error){notify(error.message,true);} finally{setBusy(button,false);}
  });

  function openEditor(title, fields, onSave) {
    editorSpec = onSave;
    $('#dialog-title').textContent = title;
    $('#dialog-fields').innerHTML = fields.map(field => {
      const label = escapeHtml(field.label); const name = escapeHtml(field.name); const value = escapeHtml(field.value ?? '');
      if (field.type === 'select') return `<label class="${field.span?'span-two':''}">${label}<select name="${name}" ${field.required?'required':''}>${field.options.map(option=>`<option value="${escapeHtml(option.value)}" ${String(option.value)===String(field.value)?'selected':''}>${escapeHtml(option.label)}</option>`).join('')}</select></label>`;
      return `<label class="${field.span?'span-two':''}">${label}<input name="${name}" type="${field.type||'text'}" value="${value}" ${field.required?'required':''} ${field.minlength?`minlength="${field.minlength}"`:''} ${field.maxlength?`maxlength="${field.maxlength}"`:''} ${field.placeholder?`placeholder="${escapeHtml(field.placeholder)}"`:''}></label>`;
    }).join('');
    $('#record-dialog').showModal();
  }
  $('#record-dialog-form').addEventListener('submit', async event => {
    event.preventDefault(); if (!editorSpec) return;
    const values = Object.fromEntries(new FormData(event.currentTarget).entries());
    const button = $('#dialog-save'); setBusy(button,true,'Saving…');
    try { await editorSpec(values); $('#record-dialog').close(); await loadRecords(); await loadOverview(); notify('Change saved to Oracle.'); }
    catch(error){notify(error.message,true);} finally{setBusy(button,false);}
  });
  $('#dialog-close').addEventListener('click',()=>$('#record-dialog').close());
  $('#dialog-cancel').addEventListener('click',()=>$('#record-dialog').close());

  $('#admin-table-body').addEventListener('click', async event => {
    const button = event.target.closest('[data-action]'); if (!button) return;
    const id = Number(button.dataset.id); const action = button.dataset.action; const row = dataRows.find(item => Number(page === 'users' ? item.USER_ID : page === 'wallets' ? item.WALLET_ID : page === 'transactions' ? item.TRANSACTION_ID : item.LEDGER_ID) === id);
    if (!row) return;
    try {
      if(action==='edit-user') openEditor(`Edit user #${id}`,[
        {name:'fullName',label:'Full name',value:row.FULL_NAME,required:true,minlength:2,maxlength:100},
        {name:'email',label:'Email',type:'email',value:row.EMAIL,required:true,maxlength:255},
        {name:'role',label:'Role',type:'select',value:row.ROLE,options:[{value:'USER',label:'USER'},{value:'ADMIN',label:'ADMIN'}]},
        {name:'status',label:'Status',type:'select',value:row.STATUS,options:[{value:'ACTIVE',label:'ACTIVE'},{value:'SUSPENDED',label:'SUSPENDED'},{value:'CLOSED',label:'CLOSED'}]},
        {name:'newPassword',label:'Reset password (optional)',type:'password',value:'',maxlength:72,placeholder:'Leave blank to keep current',span:true}
      ], values=>api(`/api/admin/users/${id}`,{method:'PUT',body:values}));
      else if(action==='close-user') {
        if(!confirm(`Soft-close ${row.EMAIL}? Financial history is retained; the user can no longer sign in.`)) return;
        await api(`/api/admin/users/${id}`,{method:'DELETE'}); notify('User closed; financial history preserved.'); await loadRecords(); await loadOverview();
      }
      else if(action==='edit-wallet') openEditor(`Set wallet #${id} balance`,[
        {name:'balance',label:'New balance (INR)',type:'number',value:row.BALANCE,required:true,placeholder:'0.00'}
      ], values=>api(`/api/admin/wallets/${id}`,{method:'PUT',body:{balance:values.balance}}));
      else if(action==='edit-transaction') openEditor(`Edit transaction #${id}`,[
        {name:'description',label:'Description',value:row.DESCRIPTION||'',maxlength:250,span:true}
      ], values=>api(`/api/admin/transactions/${id}`,{method:'PUT',body:values}));
      else if(action==='delete-transaction') {
        if(!confirm(`Delete transaction #${id}, remove its associated ledger entries, and recompute affected wallet balances from the remaining ledger? This cannot be undone.`)) return;
        await api(`/api/admin/transactions/${id}`,{method:'DELETE'}); notify('Transaction and linked ledger entries removed; balances recomputed.'); await loadRecords(); await loadOverview();
      }
      else if(action==='edit-ledger') openEditor(`Edit ledger entry #${id}`,[
        {name:'entryType',label:'Entry type',type:'select',value:row.ENTRY_TYPE,options:[{value:'CREDIT',label:'CREDIT'},{value:'DEBIT',label:'DEBIT'}]},
        {name:'amount',label:'Amount',type:'number',value:row.AMOUNT,required:true}
      ], values=>api(`/api/admin/ledger/${id}`,{method:'PUT',body:values}));
      else if(action==='delete-ledger') {
        if(!confirm(`Delete ledger entry #${id} and recompute wallet #${row.WALLET_ID}? The parent transaction record remains. Low-level ledger edits can change accounting history.`)) return;
        await api(`/api/admin/ledger/${id}`,{method:'DELETE'}); notify('Ledger entry deleted; wallet balance recomputed.'); await loadRecords(); await loadOverview();
      }
    } catch(error){notify(error.message,true);}
  });

  $$('[data-sql]').forEach(btn=>btn.addEventListener('click',()=>{$('#sql-input').value=btn.dataset.sql;$('#sql-input').focus();}));
  $('#run-sql').addEventListener('click', async () => {
    const sql = $('#sql-input').value.trim(); if (!sql) {notify('Enter a SQL statement first.',true);return;}
    const first = (sql.match(/^([A-Za-z]+)/) || [null, ''])[1].toUpperCase();
    if (!['SELECT','WITH'].includes(first) && !confirm(`Execute this ${first || 'SQL'} statement against the live Oracle schema?\n\nDDL can commit implicitly. DML will modify real project records. Continue only if you understand the effect.`)) return;
    const button=$('#run-sql'); setBusy(button,true,'Executing…'); $('#sql-result-status').textContent='Executing…';
    try { const result=await api('/api/admin/sql',{method:'POST',body:{sql}}); renderSqlResult(result); $('#sql-result-status').textContent=`SUCCESS · ${result.command}`; $('#sql-result-status').className='sql-status-success'; }
    catch(error){ $('#sql-result').innerHTML=`<pre>${escapeHtml(error.message)}</pre>`; $('#sql-result-status').textContent='FAILED'; $('#sql-result-status').className='sql-status-error'; }
    finally {setBusy(button,false);}
  });
  function renderSqlResult(result) {
    if (result.rows) {
      const rows=result.rows;
      if (!rows.length) {$('#sql-result').innerHTML='<div class="empty-cell">Query ran successfully; no rows returned.</div>';return;}
      const cols=Object.keys(rows[0]);
      $('#sql-result').innerHTML=`<div style="padding:10px 15px;color:#777b6c;font:10px var(--mono)">${result.rowCount} row(s) returned${result.truncated?' · display capped at 200':''}</div><div class="table-wrap"><table class="result-table"><thead><tr>${cols.map(k=>`<th>${escapeHtml(k)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${cols.map(k=>`<td>${escapeHtml(row[k])}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
    } else $('#sql-result').innerHTML=`<pre>${escapeHtml(result.message || JSON.stringify(result,null,2))}</pre>`;
  }

  async function bootstrap() {
    try { const current = await api('/api/auth/me'); if (current.role === 'ADMIN') showAdmin(current); }
    catch (_) { /* No existing admin session: show admin login. */ }
  }
  bootstrap();
})();
