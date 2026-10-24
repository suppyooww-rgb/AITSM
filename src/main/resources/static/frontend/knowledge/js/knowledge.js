let knowledgeArticles = [];
let editingArticleId = null;
let knowledgeCandidates = [];
function normalizeListResponse(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
}

async function fetchKnowledge() {
  const res = await fetch('/api/knowledge', { headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || 'Unable to load knowledge articles');
  return normalizeListResponse(data);
}

function filterKnowledge(items) {
  const query = (document.getElementById('search')?.value || '').trim().toLowerCase();
  const category = (document.getElementById('knowledgeCategory')?.value || '').trim().toLowerCase();

  return items.filter(a => {
    const matchesQuery = !query || [a.title, a.content, a.category].some(v => (v || '').toLowerCase().includes(query));
    const matchesCategory = !category || (a.category || '').toLowerCase() === category;
    return matchesQuery && matchesCategory;
  });
}

function formatKnowledgeDate(value) {
  const date = new Date(value || '');
  return Number.isNaN(date.getTime()) ? (value || '-') : date.toLocaleString();
}

function ensureKnowledgeModal() {
  if (document.getElementById('knowledgeArticleModal')) return;
  const modal = document.createElement('div');
  modal.id = 'knowledgeArticleModal';
  modal.className = 'modal-overlay hidden';
  modal.innerHTML = `
    <div class='modal-card' style='max-width:760px'>
      <div class='card-head'>
        <div>
          <h3 id='knowledgeModalTitle' class='section-title'>Knowledge Article</h3>
          <p id='knowledgeModalMeta' class='section-subtitle'></p>
        </div>
        <button type='button' class='btn btn-ghost' onclick='closeKnowledgeModal()'>Close</button>
      </div>
      <div id='knowledgeModalContent' class='small' style='white-space:pre-wrap;max-height:60vh;overflow:auto'></div>
    </div>`;
  document.body.appendChild(modal);
  modal.addEventListener('click', (event) => {
    if (event.target?.id === 'knowledgeArticleModal') closeKnowledgeModal();
  });
}

function openKnowledgeModal(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!article) return alert('Unable to open the selected article.');
  ensureKnowledgeModal();
  document.getElementById('knowledgeModalTitle').textContent = article.title || 'Untitled';
  document.getElementById('knowledgeModalMeta').textContent = `${article.category || 'General'} • ${formatKnowledgeDate(article.createdAt)}`;
  document.getElementById('knowledgeModalContent').textContent = article.content || '';
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('hidden');
  modal?.classList.add('show');
}

function closeKnowledgeModal() {
  const modal = document.getElementById('knowledgeArticleModal');
  modal?.classList.remove('show');
  modal?.classList.add('hidden');
}

function renderKnowledge(items) {
  const host = document.getElementById('knowledgeList');
  if (!host) return;

  if (!items.length) {
    renderTableEmptyState(host, 4, 'No matching articles found.');
    return;
  }

  host.innerHTML = items.map(a => `
    <tr>
      <td><button type='button' class='btn btn-ghost' onclick='openKnowledgeModal(${Number(a.id)})'>${aitsmEscapeHtml(a.title || 'Untitled')}</button></td>
      <td>${aitsmEscapeHtml(a.category || 'General')}</td>
      <td>${aitsmEscapeHtml(formatKnowledgeDate(a.createdAt))}</td>
      <td>
        <div class='table-actions'>
          <button class='btn btn-ghost' type='button' onclick='startKnowledgeEdit(${Number(a.id)})'>Edit</button>
          <button class='btn btn-ghost text-danger' type='button' onclick='deleteArticle(${Number(a.id)})'>Delete</button>
        </div>
      </td>
    </tr>
  `).join('');
}

async function loadKnowledge() {
  const host = document.getElementById('knowledgeList');
  if (host) showTableSkeleton(host, { rowCount: 6, columnCount: 4, hasActions: true });

  try {
    knowledgeArticles = await fetchKnowledge();
    if (host) clearTableSkeleton(host);
    renderKnowledge(filterKnowledge(knowledgeArticles));
  } catch (error) {
    if (host) renderTableErrorState(host, 4, error.message);
  } finally {
    if (host) clearTableSkeleton(host);
  }
}

async function loadKnowledgeCandidates() {
  const section = document.getElementById('knowledgeCandidateSection');
  const host = document.getElementById('knowledgeCandidateList');
  if (!section || !host || currentRole() !== 'superadmin') return;
  section.classList.remove('hidden');
  host.innerHTML = '<p class="small">Loading recurring-issue drafts…</p>';
  try {
    const response = await fetch('/api/knowledge/candidates', { headers: authHeaders() });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Unable to load recurring-issue drafts');
    knowledgeCandidates = Array.isArray(data) ? data : [];
    renderKnowledgeCandidates();
  } catch (error) {
    host.innerHTML = `<p class='small text-danger'>${aitsmEscapeHtml(error.message)}</p>`;
  }
}

function renderKnowledgeCandidates() {
  const host = document.getElementById('knowledgeCandidateList');
  if (!host) return;
  if (!knowledgeCandidates.length) {
    host.innerHTML = '<p class="small">No recurring issues are waiting for review.</p>';
    return;
  }
  host.innerHTML = knowledgeCandidates.map(candidate => {
    const id = Number(candidate.id);
    const sourceLinks = (candidate.sourceTicketIds || []).map(ticketId =>
      `<a href='/ticket-management?ticketId=${Number(ticketId)}'>#${Number(ticketId)}</a>`
    ).join(', ');
    return `<article class='card' style='margin-top:12px;background:var(--surface-muted,#f8fafc)'>
      <div class='card-head'>
        <div>
          <h4 class='section-title'>${Number(candidate.occurrenceCount)} similar resolved tickets</h4>
          <p class='section-subtitle'>Sources: ${sourceLinks || 'Unavailable'}</p>
        </div>
        <span class='badge in-progress'>Awaiting Super Admin review</span>
      </div>
      <div class='form-grid'>
        <div class='form-group'><label for='candidateTitle${id}'>Suggested title</label><input id='candidateTitle${id}' value='${aitsmEscapeHtml(candidate.title || '')}' maxlength='200'></div>
        <div class='form-group'><label for='candidateCategory${id}'>Category</label><input id='candidateCategory${id}' value='${aitsmEscapeHtml(candidate.category || 'General')}' maxlength='100'></div>
        <div class='form-group' style='grid-column:1/-1'><label for='candidateContent${id}'>Suggested article</label><textarea id='candidateContent${id}' maxlength='10000'>${aitsmEscapeHtml(candidate.content || '')}</textarea></div>
      </div>
      <div class='inline-actions' style='margin-top:12px'>
        <button class='btn btn-primary' type='button' onclick='publishKnowledgeCandidate(${id})'>Publish reviewed article</button>
        <button class='btn btn-ghost text-danger' type='button' onclick='dismissKnowledgeCandidate(${id})'>Dismiss suggestion</button>
      </div>
    </article>`;
  }).join('');
}

async function publishKnowledgeCandidate(id) {
  const body = {
    title: (document.getElementById(`candidateTitle${id}`)?.value || '').trim(),
    category: (document.getElementById(`candidateCategory${id}`)?.value || 'General').trim(),
    content: (document.getElementById(`candidateContent${id}`)?.value || '').trim()
  };
  if (body.title.length < 5 || body.content.length < 10) return alert('Review the draft and provide a title and article content before publishing.');
  if (!window.confirm('Publish this reviewed draft to the Knowledge Base?')) return;
  const response = await fetch(`/api/knowledge/candidates/${Number(id)}/publish`, {
    method: 'POST', headers: authHeaders(), body: JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) return alert(data.error || 'Unable to publish the recurring-issue draft');
  await Promise.all([loadKnowledgeCandidates(), loadKnowledge()]);
  alert('Reviewed article published successfully.');
}

async function dismissKnowledgeCandidate(id) {
  if (!window.confirm('Dismiss this recurring-issue suggestion? Its source-ticket history will remain recorded.')) return;
  const response = await fetch(`/api/knowledge/candidates/${Number(id)}/dismiss`, { method: 'POST', headers: authHeaders() });
  const data = await response.json();
  if (!response.ok) return alert(data.error || 'Unable to dismiss the recurring-issue draft');
  await loadKnowledgeCandidates();
}

function resetKnowledgeForm() {
  editingArticleId = null;
  document.getElementById('knowledgeFormTitle').textContent = 'Create Article';
  document.getElementById('knowledgeSaveBtn').textContent = 'Publish Article';
  document.getElementById('knowledgeCancelBtn').classList.add('hidden');
  document.getElementById('articleTitle').value = '';
  document.getElementById('articleCategory').value = '';
  document.getElementById('articleContent').value = '';
}

function startKnowledgeEdit(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!article) return alert('Unable to load the selected article.');
  editingArticleId = id;
  document.getElementById('knowledgeFormTitle').textContent = 'Edit Article';
  document.getElementById('knowledgeSaveBtn').textContent = 'Save Changes';
  document.getElementById('knowledgeCancelBtn').classList.remove('hidden');
  document.getElementById('articleTitle').value = article.title || '';
  document.getElementById('articleCategory').value = article.category || '';
  document.getElementById('articleContent').value = article.content || '';
  window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
}

async function saveArticle() {
  const body = {
    title: (document.getElementById('articleTitle')?.value || '').trim(),
    category: (document.getElementById('articleCategory')?.value || 'General').trim(),
    content: (document.getElementById('articleContent')?.value || '').trim()
  };

  if (!body.title || !body.content) return alert('Title and content are required.');

  const endpoint = editingArticleId ? `/api/knowledge/${editingArticleId}` : '/api/knowledge';
  const method = editingArticleId ? 'PUT' : 'POST';
  const res = await fetch(endpoint, {
    method,
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to save article');

  const wasEditing = Boolean(editingArticleId);
  resetKnowledgeForm();
  await loadKnowledge();
  alert(wasEditing ? 'Article updated successfully.' : 'Article published successfully.');
}

async function deleteArticle(id) {
  const article = knowledgeArticles.find(item => item.id === id);
  if (!window.confirm(`Delete article "${article?.title || `#${id}`}"?`)) return;

  const res = await fetch(`/api/knowledge/${id}`, { method: 'DELETE', headers: authHeaders() });
  const data = await res.json();
  if (!res.ok) return alert(data.error || 'Unable to delete article');

  if (editingArticleId === id) resetKnowledgeForm();
  closeKnowledgeModal();
  await loadKnowledge();
  alert(data.message || 'Article deleted');
}

document.addEventListener('DOMContentLoaded', async () => {
  await window.aitsmReady;
  ensureKnowledgeModal();
  loadKnowledge();
  loadKnowledgeCandidates();
});
