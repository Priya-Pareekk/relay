// Relay Dashboard Client Application

const state = {
    currentPage: 0,
    pageSize: 15,
    totalPages: 0,
    totalElements: 0,
    statusFilter: '',
    typeFilter: '',
    searchQuery: '',
    autoRefreshInterval: null,
    selectedJobId: null
};

// DOM Elements
const elements = {
    jobsTableBody: document.getElementById('jobsTableBody'),
    statusFilter: document.getElementById('statusFilter'),
    typeFilter: document.getElementById('typeFilter'),
    searchInput: document.getElementById('searchInput'),
    prevPageBtn: document.getElementById('prevPageBtn'),
    nextPageBtn: document.getElementById('nextPageBtn'),
    currentPageTag: document.getElementById('currentPageTag'),
    paginationInfo: document.getElementById('paginationInfo'),
    refreshBtn: document.getElementById('refreshBtn'),
    
    // Metrics
    metricTotal: document.getElementById('metricTotal'),
    metricPending: document.getElementById('metricPending'),
    metricProcessing: document.getElementById('metricProcessing'),
    metricRetrying: document.getElementById('metricRetrying'),
    metricCompleted: document.getElementById('metricCompleted'),
    metricDeadLetter: document.getElementById('metricDeadLetter'),
    metricSuccessRate: document.getElementById('metricSuccessRate'),

    // Modals
    submitJobModal: document.getElementById('submitJobModal'),
    openSubmitJobModalBtn: document.getElementById('openSubmitJobModalBtn'),
    submitJobForm: document.getElementById('submitJobForm'),
    jobTypeSelect: document.getElementById('jobTypeSelect'),
    idempotencyKeyInput: document.getElementById('idempotencyKeyInput'),
    maxAttemptsInput: document.getElementById('maxAttemptsInput'),
    payloadEditor: document.getElementById('payloadEditor'),
    loadEmailTemplateBtn: document.getElementById('loadEmailTemplateBtn'),
    loadReportTemplateBtn: document.getElementById('loadReportTemplateBtn'),
    loadFailingTemplateBtn: document.getElementById('loadFailingTemplateBtn'),

    jobDetailModal: document.getElementById('jobDetailModal'),
    jobDetailBody: document.getElementById('jobDetailBody'),

    definitionsModal: document.getElementById('definitionsModal'),
    openDefinitionsModalBtn: document.getElementById('openDefinitionsModalBtn'),
    definitionsList: document.getElementById('definitionsList'),
    showNewDefinitionFormBtn: document.getElementById('showNewDefinitionFormBtn'),
    newDefinitionPanel: document.getElementById('newDefinitionPanel'),
    newDefinitionForm: document.getElementById('newDefinitionForm'),
    cancelDefBtn: document.getElementById('cancelDefBtn'),

    toastContainer: document.getElementById('toastContainer')
};

// Templates
const templates = {
    email: JSON.stringify({
        to: "developer@relay.io",
        subject: "Async Task Processed",
        body: "Hello! Your distributed job has successfully executed."
    }, null, 2),
    report: JSON.stringify({
        reportName: "Quarterly Analytics",
        format: "PDF",
        generatedBy: "System"
    }, null, 2),
    fail: JSON.stringify({
        to: "fail@error.com",
        fail: true,
        reason: "Simulate service failure for retry and DLQ demo"
    }, null, 2)
};

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    setTemplate('email');
    loadMetrics();
    loadJobs();
    startAutoRefresh();
});

function setupEventListeners() {
    elements.refreshBtn.addEventListener('click', () => {
        loadMetrics();
        loadJobs();
        showToast('Dashboard refreshed');
    });

    elements.statusFilter.addEventListener('change', (e) => {
        state.statusFilter = e.target.value;
        state.currentPage = 0;
        loadJobs();
    });

    elements.typeFilter.addEventListener('change', (e) => {
        state.typeFilter = e.target.value;
        state.currentPage = 0;
        loadJobs();
    });

    let searchTimeout;
    elements.searchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            state.searchQuery = e.target.value.trim();
            state.currentPage = 0;
            loadJobs();
        }, 300);
    });

    elements.prevPageBtn.addEventListener('click', () => {
        if (state.currentPage > 0) {
            state.currentPage--;
            loadJobs();
        }
    });

    elements.nextPageBtn.addEventListener('click', () => {
        if (state.currentPage < state.totalPages - 1) {
            state.currentPage++;
            loadJobs();
        }
    });

    // Modals
    elements.openSubmitJobModalBtn.addEventListener('click', () => openModal(elements.submitJobModal));
    elements.openDefinitionsModalBtn.addEventListener('click', () => {
        openModal(elements.definitionsModal);
        loadDefinitions();
    });

    document.querySelectorAll('[data-close]').forEach(btn => {
        btn.addEventListener('click', () => {
            const modalId = btn.getAttribute('data-close');
            closeModal(document.getElementById(modalId));
        });
    });

    // Templates
    elements.jobTypeSelect.addEventListener('change', (e) => {
        if (e.target.value === 'EMAIL_NOTIFICATION') {
            setTemplate('email');
        } else {
            setTemplate('report');
        }
    });

    elements.loadEmailTemplateBtn.addEventListener('click', () => {
        elements.jobTypeSelect.value = 'EMAIL_NOTIFICATION';
        setTemplate('email');
    });

    elements.loadReportTemplateBtn.addEventListener('click', () => {
        elements.jobTypeSelect.value = 'REPORT_GENERATION';
        setTemplate('report');
    });

    elements.loadFailingTemplateBtn.addEventListener('click', () => {
        setTemplate('fail');
    });

    // Form Submissions
    elements.submitJobForm.addEventListener('submit', handleSubmitJob);
    elements.showNewDefinitionFormBtn.addEventListener('click', () => {
        elements.newDefinitionPanel.style.display = 'block';
    });
    elements.cancelDefBtn.addEventListener('click', () => {
        elements.newDefinitionPanel.style.display = 'none';
    });
    elements.newDefinitionForm.addEventListener('submit', handleCreateDefinition);
}

function setTemplate(type) {
    elements.payloadEditor.value = templates[type] || '{}';
}

function openModal(modal) {
    modal.classList.add('active');
}

function closeModal(modal) {
    modal.classList.remove('active');
}

// Fetch Metrics
async function loadMetrics() {
    try {
        const res = await fetch('/api/metrics/summary');
        if (!res.ok) return;
        const data = await res.json();

        elements.metricTotal.textContent = data.totalJobs.toLocaleString();
        elements.metricPending.textContent = data.pendingJobs.toLocaleString();
        elements.metricProcessing.textContent = data.processingJobs.toLocaleString();
        elements.metricRetrying.textContent = data.retryingJobs.toLocaleString();
        elements.metricCompleted.textContent = data.completedJobs.toLocaleString();
        elements.metricDeadLetter.textContent = data.deadLetterJobs.toLocaleString();

        const successRate = data.successRatePercentLast24h || 0;
        elements.metricSuccessRate.textContent = `${successRate.toFixed(1)}% 24h success rate (${data.completedLast24h}/${data.completedLast24h + data.deadLetterLast24h})`;
    } catch (err) {
        console.error('Failed to load metrics', err);
    }
}

// Fetch Jobs
async function loadJobs() {
    try {
        let url = `/api/jobs?page=${state.currentPage}&size=${state.pageSize}&sort=createdAt,desc`;
        if (state.statusFilter) url += `&status=${state.statusFilter}`;
        if (state.typeFilter) url += `&jobType=${state.typeFilter}`;

        const res = await fetch(url);
        if (!res.ok) return;
        const data = await res.json();

        state.totalPages = data.totalPages;
        state.totalElements = data.totalElements;

        renderJobsTable(data.content || []);
        updatePagination();
    } catch (err) {
        console.error('Failed to load jobs', err);
        elements.jobsTableBody.innerHTML = `<tr><td colspan="8" class="empty-state text-danger">Error connecting to Relay API.</td></tr>`;
    }
}

function renderJobsTable(jobs) {
    if (!jobs || jobs.length === 0) {
        elements.jobsTableBody.innerHTML = `<tr><td colspan="8" class="empty-state">No jobs found matching criteria.</td></tr>`;
        return;
    }

    // Filter locally if search query is active
    let filteredJobs = jobs;
    if (state.searchQuery) {
        const q = state.searchQuery.toLowerCase();
        filteredJobs = jobs.filter(j => 
            j.id.toLowerCase().includes(q) || 
            (j.idempotencyKey && j.idempotencyKey.toLowerCase().includes(q))
        );
    }

    elements.jobsTableBody.innerHTML = filteredJobs.map(job => {
        const shortId = job.id.substring(0, 8) + '...';
        const createdAt = new Date(job.createdAt).toLocaleTimeString();
        
        let retryOrError = '-';
        if (job.status === 'RETRYING' && job.nextRetryAt) {
            const retryTime = new Date(job.nextRetryAt).toLocaleTimeString();
            retryOrError = `<span class="next-retry-snippet">Retry at ${retryTime}</span>`;
        } else if (job.lastError) {
            retryOrError = `<span class="error-snippet" title="${escapeHtml(job.lastError)}">${escapeHtml(job.lastError)}</span>`;
        }

        const idemKey = job.idempotencyKey 
            ? `<span class="idempotency-tag">${escapeHtml(job.idempotencyKey)}</span>` 
            : '<span class="text-muted">-</span>';

        return `
            <tr>
                <td class="code-cell">
                    <a href="javascript:void(0)" onclick="viewJobDetail('${job.id}')" style="color: #a5b4fc; text-decoration: none;">
                        ${shortId}
                    </a>
                </td>
                <td><span style="font-size: 12px; font-weight: 500;">${job.jobType}</span></td>
                <td><span class="status-badge status-${job.status}">${job.status}</span></td>
                <td><strong>${job.attemptCount}</strong> / ${job.maxAttempts}</td>
                <td>${idemKey}</td>
                <td style="color: var(--text-secondary); font-size: 12px;">${createdAt}</td>
                <td>${retryOrError}</td>
                <td>
                    <div style="display: flex; gap: 6px;">
                        <button class="btn btn-outline btn-sm" onclick="viewJobDetail('${job.id}')">View</button>
                        ${job.status === 'DEAD_LETTER' ? `<button class="btn btn-primary btn-sm" onclick="replayJob('${job.id}')">Replay</button>` : ''}
                        ${job.status === 'PENDING' ? `<button class="btn btn-outline btn-sm text-danger" onclick="cancelJob('${job.id}')">Cancel</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function updatePagination() {
    elements.prevPageBtn.disabled = state.currentPage === 0;
    elements.nextPageBtn.disabled = state.currentPage >= state.totalPages - 1 || state.totalPages === 0;
    elements.currentPageTag.textContent = `Page ${state.currentPage + 1} of ${Math.max(state.totalPages, 1)}`;
    elements.paginationInfo.textContent = `Showing page ${state.currentPage + 1} (${state.totalElements} total jobs)`;
}

// Submit Job
async function handleSubmitJob(e) {
    e.preventDefault();

    let payload;
    try {
        payload = JSON.parse(elements.payloadEditor.value);
    } catch (err) {
        showToast('Invalid JSON in payload editor', 'error');
        return;
    }

    const body = {
        jobType: elements.jobTypeSelect.value,
        payload: payload,
        idempotencyKey: elements.idempotencyKeyInput.value.trim() || null,
        maxAttempts: parseInt(elements.maxAttemptsInput.value, 10) || 5
    };

    try {
        const res = await fetch('/api/jobs', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Submission failed');
        }

        const data = await res.json();
        closeModal(elements.submitJobModal);
        showToast(`Job submitted successfully! (ID: ${data.id.substring(0, 8)}...)`, 'success');
        
        loadMetrics();
        loadJobs();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// View Job Detail
window.viewJobDetail = async function(id) {
    openModal(elements.jobDetailModal);
    elements.jobDetailBody.innerHTML = `<div class="loading-state">Loading details for ${id}...</div>`;

    try {
        const res = await fetch(`/api/jobs/${id}`);
        if (!res.ok) throw new Error('Job not found');
        const job = await res.json();

        const attemptsHtml = (job.attempts || []).map(att => {
            const started = new Date(att.startedAt).toLocaleTimeString();
            const finished = new Date(att.finishedAt).toLocaleTimeString();
            return `
                <div class="timeline-item">
                    <div class="timeline-header">
                        <strong>Attempt #${att.attemptNumber}</strong>
                        <span class="status-badge status-${att.status}">${att.status}</span>
                    </div>
                    <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 4px;">
                        ${started} &rarr; ${finished}
                    </div>
                    ${att.errorMessage ? `<div class="text-danger" style="font-size: 12px; font-family: monospace;">${escapeHtml(att.errorMessage)}</div>` : ''}
                </div>
            `;
        }).join('');

        elements.jobDetailBody.innerHTML = `
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px;">
                <div>
                    <h3 style="font-size: 16px; margin-bottom: 4px; font-family: monospace;">${job.id}</h3>
                    <span class="badge-tag">${job.jobType}</span>
                </div>
                <span class="status-badge status-${job.status}" style="font-size: 13px;">${job.status}</span>
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 18px; font-size: 13px;">
                <div><strong>Attempts:</strong> ${job.attemptCount} / ${job.maxAttempts}</div>
                <div><strong>Idempotency Key:</strong> ${job.idempotencyKey || 'None'}</div>
                <div><strong>Created At:</strong> ${new Date(job.createdAt).toLocaleString()}</div>
                <div><strong>Next Retry:</strong> ${job.nextRetryAt ? new Date(job.nextRetryAt).toLocaleString() : 'N/A'}</div>
            </div>

            ${job.lastError ? `
                <div style="background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 6px; padding: 10px; margin-bottom: 18px;">
                    <strong class="text-danger" style="font-size: 12px;">Last Error:</strong>
                    <div style="font-size: 12px; font-family: monospace; color: #fca5a5; margin-top: 4px;">${escapeHtml(job.lastError)}</div>
                </div>
            ` : ''}

            <div style="margin-bottom: 18px;">
                <label style="font-size: 12px; font-weight: 600; color: var(--text-secondary);">Job Payload</label>
                <pre style="background: #060911; padding: 10px; border-radius: 6px; font-size: 12px; color: #a5f3fc; overflow-x: auto; margin-top: 4px;">${JSON.stringify(job.payload, null, 2)}</pre>
            </div>

            <div>
                <label style="font-size: 12px; font-weight: 600; color: var(--text-secondary);">Execution Audit Trail (${(job.attempts || []).length} attempts)</label>
                <div class="audit-timeline">
                    ${attemptsHtml || '<div class="text-muted" style="font-size: 12px;">No attempts recorded yet.</div>'}
                </div>
            </div>

            <div class="modal-footer" style="margin-top: 24px;">
                ${job.status === 'DEAD_LETTER' ? `<button class="btn btn-primary" onclick="replayJob('${job.id}')">Replay Dead-Letter Job</button>` : ''}
                ${job.status === 'PENDING' ? `<button class="btn btn-outline text-danger" onclick="cancelJob('${job.id}')">Cancel Job</button>` : ''}
                <button class="btn btn-outline" data-close="jobDetailModal">Close</button>
            </div>
        `;

        elements.jobDetailBody.querySelectorAll('[data-close]').forEach(b => {
            b.addEventListener('click', () => closeModal(elements.jobDetailModal));
        });

    } catch (err) {
        elements.jobDetailBody.innerHTML = `<div class="empty-state text-danger">${err.message}</div>`;
    }
};

// Replay Job
window.replayJob = async function(id) {
    try {
        const res = await fetch(`/api/jobs/${id}/replay`, { method: 'POST' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Replay failed');
        }
        showToast('Job replayed and republished to queue!', 'success');
        closeModal(elements.jobDetailModal);
        loadMetrics();
        loadJobs();
    } catch (err) {
        showToast(err.message, 'error');
    }
};

// Cancel Job
window.cancelJob = async function(id) {
    if (!confirm('Are you sure you want to cancel this pending job?')) return;
    try {
        const res = await fetch(`/api/jobs/${id}/cancel`, { method: 'POST' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Cancel failed');
        }
        showToast('Job marked CANCELLED', 'success');
        closeModal(elements.jobDetailModal);
        loadMetrics();
        loadJobs();
    } catch (err) {
        showToast(err.message, 'error');
    }
};

// Job Definitions (Cron)
async function loadDefinitions() {
    try {
        const res = await fetch('/api/job-definitions');
        if (!res.ok) return;
        const definitions = await res.json();

        if (definitions.length === 0) {
            elements.definitionsList.innerHTML = `<div class="empty-state">No recurring job schedules configured yet.</div>`;
            return;
        }

        elements.definitionsList.innerHTML = definitions.map(d => {
            const lastTrigger = d.lastTriggeredAt ? new Date(d.lastTriggeredAt).toLocaleString() : 'Never';
            const nextTrigger = d.nextExecutionTime ? new Date(d.nextExecutionTime).toLocaleString() : 'Pending calc';
            return `
                <div class="timeline-item" style="margin-bottom: 12px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                        <div>
                            <strong style="font-size: 14px;">${escapeHtml(d.name)}</strong>
                            <span class="badge-tag" style="margin-left: 8px;">${d.jobType}</span>
                        </div>
                        <div style="display: flex; gap: 8px; align-items: center;">
                            <label style="font-size: 12px; cursor: pointer;">
                                <input type="checkbox" ${d.enabled ? 'checked' : ''} onchange="toggleDefinition('${d.id}', this.checked)">
                                ${d.enabled ? '<span style="color:#34d399">Enabled</span>' : '<span style="color:#9ca3af">Disabled</span>'}
                            </label>
                            <button class="btn btn-text text-danger" onclick="deleteDefinition('${d.id}')">Delete</button>
                        </div>
                    </div>
                    <div style="font-size: 12px; color: var(--text-secondary); display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 6px;">
                        <div><strong>Cron:</strong> <code style="color: #a5f3fc;">${escapeHtml(d.cronExpression)}</code></div>
                        <div><strong>Next Due:</strong> ${nextTrigger}</div>
                        <div><strong>Last Triggered:</strong> ${lastTrigger}</div>
                    </div>
                </div>
            `;
        }).join('');
    } catch (err) {
        console.error('Failed to load definitions', err);
    }
}

async function handleCreateDefinition(e) {
    e.preventDefault();
    let payload;
    try {
        payload = JSON.parse(document.getElementById('defPayload').value);
    } catch (err) {
        showToast('Invalid JSON in payload template', 'error');
        return;
    }

    const body = {
        name: document.getElementById('defName').value.trim(),
        jobType: document.getElementById('defType').value,
        cronExpression: document.getElementById('defCron').value.trim(),
        payloadTemplate: payload,
        enabled: true
    };

    try {
        const res = await fetch('/api/job-definitions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || 'Failed to save definition');
        }
        showToast('Recurring job definition created!', 'success');
        elements.newDefinitionPanel.style.display = 'none';
        elements.newDefinitionForm.reset();
        loadDefinitions();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

window.toggleDefinition = async function(id, enabled) {
    try {
        await fetch(`/api/job-definitions/${id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ enabled })
        });
        showToast(`Schedule ${enabled ? 'enabled' : 'disabled'}`, 'success');
        loadDefinitions();
    } catch (err) {
        showToast(err.message, 'error');
    }
};

window.deleteDefinition = async function(id) {
    if (!confirm('Are you sure you want to delete this recurring schedule?')) return;
    try {
        await fetch(`/api/job-definitions/${id}`, { method: 'DELETE' });
        showToast('Schedule deleted', 'success');
        loadDefinitions();
    } catch (err) {
        showToast(err.message, 'error');
    }
};

// Auto Refresh
function startAutoRefresh() {
    state.autoRefreshInterval = setInterval(() => {
        loadMetrics();
        loadJobs();
    }, 3000);
}

// Toast
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    elements.toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
