const DEFAULT_TENANT = "tenant_local_dev";
const state = {
    currentView: "dashboard",
    pageSize: 20,
    uploadHistory: []
};

const elements = {
    tenantLabel: document.getElementById("tenantLabel"),
    viewTitle: document.getElementById("viewTitle"),
    refreshCurrentView: document.getElementById("refreshCurrentView"),
    latestEventsFeed: document.getElementById("latestEventsFeed"),
    cameraRanking: document.getElementById("cameraRanking"),
    statTotalEvents: document.getElementById("statTotalEvents"),
    statDeviceCount: document.getElementById("statDeviceCount"),
    statUserCount: document.getElementById("statUserCount"),
    statLatestEvent: document.getElementById("statLatestEvent"),
    eventsFilterForm: document.getElementById("eventsFilterForm"),
    resetEventsFilter: document.getElementById("resetEventsFilter"),
    eventsTableWrap: document.getElementById("eventsTableWrap"),
    eventsMeta: document.getElementById("eventsMeta"),
    searchForm: document.getElementById("searchForm"),
    parsedIntentPanel: document.getElementById("parsedIntentPanel"),
    searchRequestPanel: document.getElementById("searchRequestPanel"),
    searchResultsGrid: document.getElementById("searchResultsGrid"),
    searchMeta: document.getElementById("searchMeta"),
    uploadForm: document.getElementById("uploadForm"),
    fillUploadDefaults: document.getElementById("fillUploadDefaults"),
    uploadRequestPanel: document.getElementById("uploadRequestPanel"),
    uploadResponsePanel: document.getElementById("uploadResponsePanel"),
    uploadHistory: document.getElementById("uploadHistory"),
    eventDetailDialog: document.getElementById("eventDetailDialog"),
    eventDetailBody: document.getElementById("eventDetailBody"),
    detailTitle: document.getElementById("detailTitle"),
    closeDetailDialog: document.getElementById("closeDetailDialog")
};

document.addEventListener("DOMContentLoaded", () => {
    elements.tenantLabel.textContent = DEFAULT_TENANT;
    bindNavigation();
    bindDashboardActions();
    bindEventFilters();
    bindSearchForm();
    bindUploadForm();
    bindDetailDialog();
    loadCurrentView();
});

function bindNavigation() {
    document.querySelectorAll(".nav-link").forEach((button) => {
        button.addEventListener("click", () => switchView(button.dataset.view));
    });

    elements.refreshCurrentView.addEventListener("click", () => loadCurrentView());
}

function bindDashboardActions() {
    document.querySelectorAll("[data-jump]").forEach((button) => {
        button.addEventListener("click", () => switchView(button.dataset.jump));
    });
}

function bindEventFilters() {
    elements.eventsFilterForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        await loadEvents();
    });

    elements.resetEventsFilter.addEventListener("click", async () => {
        elements.eventsFilterForm.reset();
        await loadEvents();
    });
}

function bindSearchForm() {
    elements.searchForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        await runSearchDebug();
    });
}

function bindUploadForm() {
    elements.fillUploadDefaults.addEventListener("click", () => {
        const now = new Date();
        elements.uploadForm.elements.event_id.value = `evt_upload_${Date.now()}`;
        if (!elements.uploadForm.elements.camera_id.value) {
            elements.uploadForm.elements.camera_id.value = "cam_front_door_01";
        }
        elements.uploadForm.elements.timestamp.value = toDatetimeLocal(now);
    });

    elements.uploadForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        await uploadAndIngest();
    });
}

function bindDetailDialog() {
    elements.closeDetailDialog.addEventListener("click", () => {
        elements.eventDetailDialog.close();
    });
}

function switchView(view) {
    state.currentView = view;

    document.querySelectorAll(".nav-link").forEach((button) => {
        button.classList.toggle("active", button.dataset.view === view);
    });

    document.querySelectorAll(".view").forEach((section) => {
        section.classList.toggle("active", section.id === `${view}View`);
    });

    const titles = {
        dashboard: "总览",
        events: "事件浏览",
        search: "搜索调试",
        upload: "上传调试"
    };
    elements.viewTitle.textContent = titles[view] || "SearchPic Console";
    loadCurrentView();
}

async function loadCurrentView() {
    if (state.currentView === "dashboard") {
        await loadDashboard();
        return;
    }
    if (state.currentView === "events") {
        await loadEvents();
        return;
    }
    if (state.currentView === "search") {
        if (!elements.searchRequestPanel.dataset.loaded) {
            renderJson(elements.searchRequestPanel, { tenant_id: DEFAULT_TENANT, message: "等待输入查询" });
            renderJson(elements.parsedIntentPanel, { message: "等待一次搜索..." });
            elements.searchRequestPanel.dataset.loaded = "true";
        }
        return;
    }
    if (state.currentView === "upload") {
        if (!elements.uploadRequestPanel.dataset.loaded) {
            renderJson(elements.uploadRequestPanel, { tenant_id: DEFAULT_TENANT, message: "等待一次上传" });
            renderJson(elements.uploadResponsePanel, { message: "等待一次上传..." });
            elements.uploadRequestPanel.dataset.loaded = "true";
        }
        renderUploadHistory();
    }
}

async function loadDashboard() {
    try {
        const [overview, latest, cameras, users] = await Promise.all([
            apiGet("/api/v1/events/stats/overview"),
            apiGet("/api/v1/events/latest?limit=6"),
            apiGet("/api/v1/events/stats/by-camera?limit=6"),
            apiGet("/api/v1/admin/users")
        ]);

        elements.statTotalEvents.textContent = formatNumber(overview.total_events);
        elements.statDeviceCount.textContent = formatNumber(overview.unique_camera_count);
        elements.statUserCount.textContent = formatNumber(users.length);
        elements.statLatestEvent.textContent = formatTimestamp(overview.latest_event_timestamp);

        renderLatestFeed(latest.items || []);
        renderCameraRanking(cameras.items || []);
    } catch (error) {
        renderErrorState(elements.latestEventsFeed, error);
        renderErrorState(elements.cameraRanking, error);
        elements.statTotalEvents.textContent = "!";
        elements.statDeviceCount.textContent = "!";
        elements.statUserCount.textContent = "!";
        elements.statLatestEvent.textContent = "加载失败";
    }
}

function renderLatestFeed(items) {
    if (!items.length) {
        elements.latestEventsFeed.className = "feed-list empty-state";
        elements.latestEventsFeed.textContent = "暂无最新事件";
        return;
    }

    elements.latestEventsFeed.className = "feed-list";
    elements.latestEventsFeed.innerHTML = items.map((item) => `
        <article class="feed-item">
            <strong>${escapeHtml(item.event_id || "-")}</strong>
            <div>设备：${escapeHtml(item.camera_id || "-")}</div>
            <div>用户：${escapeHtml(item.user_id || "-")}</div>
            <div>${formatTimestamp(item.timestamp)}</div>
            <button class="link-button" data-event-detail="${escapeHtml(item.event_id || "")}">查看详情</button>
        </article>
    `).join("");
    bindDetailButtons(elements.latestEventsFeed);
}

function renderCameraRanking(items) {
    if (!items.length) {
        elements.cameraRanking.className = "ranking-list empty-state";
        elements.cameraRanking.textContent = "暂无设备统计";
        return;
    }

    const maxCount = Math.max(...items.map((item) => Number(item.event_count || 0)), 1);
    elements.cameraRanking.className = "ranking-list";
    elements.cameraRanking.innerHTML = items.map((item) => {
        const width = Math.round((Number(item.event_count || 0) / maxCount) * 100);
        return `
            <article class="ranking-item">
                <strong>${escapeHtml(item.camera_id || "-")}</strong>
                <div>事件数：${formatNumber(item.event_count)}</div>
                <div>最近时间：${formatTimestamp(item.latest_event_timestamp)}</div>
                <div class="ranking-bar"><span style="width:${width}%"></span></div>
            </article>
        `;
    }).join("");
}

async function loadEvents() {
    elements.eventsMeta.textContent = "加载中...";
    elements.eventsTableWrap.innerHTML = '<div class="empty-state">正在加载事件数据...</div>';

    try {
        const formData = new FormData(elements.eventsFilterForm);
        const params = new URLSearchParams({
            page: "0",
            size: String(state.pageSize)
        });

        if (formData.get("user_id")) {
            params.set("user_id", formData.get("user_id"));
        }
        if (formData.get("camera_id")) {
            params.set("camera_id", formData.get("camera_id"));
        }

        const startTimestamp = toEpochMs(formData.get("start_time"));
        const endTimestamp = toEpochMs(formData.get("end_time"));
        if (startTimestamp) {
            params.set("start_timestamp", String(startTimestamp));
        }
        if (endTimestamp) {
            params.set("end_timestamp", String(endTimestamp));
        }

        const data = await apiGet(`/api/v1/events?${params.toString()}`);
        renderEventsTable(data.items || []);
        elements.eventsMeta.textContent = `共 ${formatNumber(data.total)} 条，当前展示 ${formatNumber((data.items || []).length)} 条`;
    } catch (error) {
        elements.eventsMeta.textContent = "加载失败";
        renderErrorState(elements.eventsTableWrap, error);
    }
}

function renderEventsTable(items) {
    if (!items.length) {
        elements.eventsTableWrap.innerHTML = '<div class="empty-state">当前筛选条件下没有事件</div>';
        return;
    }

    elements.eventsTableWrap.innerHTML = `
        <table>
            <thead>
                <tr>
                    <th>图片</th>
                    <th>事件信息</th>
                    <th>实体摘要</th>
                    <th>操作</th>
                </tr>
            </thead>
            <tbody>
                ${items.map((item) => `
                    <tr>
                        <td><img class="thumbnail" src="${escapeAttr(item.image_url || "")}" alt="${escapeAttr(item.event_id || "event")}"></td>
                        <td>
                            <strong>${escapeHtml(item.event_id || "-")}</strong><br>
                            用户：${escapeHtml(item.user_id || "-")}<br>
                            设备：${escapeHtml(item.camera_id || "-")}<br>
                            时间：${formatTimestamp(item.timestamp)}
                        </td>
                        <td>${escapeHtml(item.entities_text || "-")}</td>
                        <td><button class="link-button" data-event-detail="${escapeHtml(item.event_id || "")}">查看详情</button></td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
    `;
    bindDetailButtons(elements.eventsTableWrap);
}

async function runSearchDebug() {
    elements.searchMeta.textContent = "搜索中...";
    elements.searchResultsGrid.className = "result-grid";
    elements.searchResultsGrid.innerHTML = '<div class="empty-state">正在调用搜索调试接口...</div>';

    try {
        const formData = new FormData(elements.searchForm);
        const cameraIds = String(formData.get("camera_ids") || "")
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean);

        const payload = {
            query: String(formData.get("query") || "").trim(),
            timezone: String(formData.get("timezone") || "Asia/Shanghai").trim() || "Asia/Shanghai",
            camera_ids: cameraIds
        };

        const data = await apiPost("/api/v1/search/debug", payload);
        renderJson(elements.searchRequestPanel, data.request || payload);
        renderJson(elements.parsedIntentPanel, data.parsed_intent || {});
        renderSearchResults(data.results || []);
        elements.searchMeta.textContent = `命中 ${formatNumber((data.results || []).length)} 条`;
    } catch (error) {
        elements.searchMeta.textContent = "搜索失败";
        renderJson(elements.parsedIntentPanel, { error: error.message });
        renderErrorState(elements.searchResultsGrid, error);
    }
}

function renderSearchResults(items) {
    if (!items.length) {
        elements.searchResultsGrid.className = "result-grid empty-state";
        elements.searchResultsGrid.textContent = "没有找到匹配结果";
        return;
    }

    elements.searchResultsGrid.className = "result-grid";
    elements.searchResultsGrid.innerHTML = items.map((item) => `
        <article class="search-result-card">
            <img src="${escapeAttr(item.image_url || "")}" alt="${escapeAttr(item.event_id || "result")}">
            <div class="result-content">
                <span class="score-pill">Score ${formatScore(item.relevance_score)}</span>
                <strong>${escapeHtml(item.event_id || "-")}</strong>
                <div>设备：${escapeHtml(item.camera_id || "-")}</div>
                <div>用户：${escapeHtml(item.user_id || "-")}</div>
                <div>时间：${formatTimestamp(item.timestamp)}</div>
                <div>${escapeHtml(item.entities_text || "-")}</div>
                <button class="link-button" data-event-detail="${escapeHtml(item.event_id || "")}">查看详情</button>
            </div>
        </article>
    `).join("");
    bindDetailButtons(elements.searchResultsGrid);
}

async function uploadAndIngest() {
    const formData = new FormData(elements.uploadForm);
    const file = formData.get("file");
    if (!(file instanceof File) || !file.name) {
        renderJson(elements.uploadResponsePanel, { error: "请选择一张图片后再上传" });
        return;
    }

    const payload = new FormData();
    payload.append("file", file);
    payload.append("event_id", String(formData.get("event_id") || "").trim());
    payload.append("camera_id", String(formData.get("camera_id") || "").trim());

    const userId = String(formData.get("user_id") || "").trim();
    if (userId) {
        payload.append("user_id", userId);
    }

    const timestamp = toEpochMs(formData.get("timestamp"));
    if (timestamp) {
        payload.append("timestamp", String(timestamp));
    }

    renderJson(elements.uploadRequestPanel, {
        tenant_id: DEFAULT_TENANT,
        event_id: payload.get("event_id"),
        camera_id: payload.get("camera_id"),
        user_id: userId || null,
        timestamp: timestamp || null,
        file_name: file.name,
        file_size: file.size
    });
    renderJson(elements.uploadResponsePanel, { message: "上传中..." });

    try {
        const data = await apiFormPost("/api/v1/events/ingest-upload", payload);
        renderJson(elements.uploadResponsePanel, data);

        state.uploadHistory.unshift({
            event_id: data.event_id,
            camera_id: data.camera_id,
            user_id: data.user_id,
            timestamp: data.timestamp,
            image_url: data.image_url,
            received_at: data.received_at
        });
        state.uploadHistory = state.uploadHistory.slice(0, 8);
        renderUploadHistory();
    } catch (error) {
        renderJson(elements.uploadResponsePanel, { error: error.message });
    }
}

function renderUploadHistory() {
    if (!state.uploadHistory.length) {
        elements.uploadHistory.className = "feed-list empty-state";
        elements.uploadHistory.textContent = "还没有上传记录";
        return;
    }

    elements.uploadHistory.className = "feed-list";
    elements.uploadHistory.innerHTML = state.uploadHistory.map((item) => `
        <article class="feed-item">
            <strong>${escapeHtml(item.event_id || "-")}</strong>
            <div>设备：${escapeHtml(item.camera_id || "-")}</div>
            <div>用户：${escapeHtml(item.user_id || "-")}</div>
            <div>事件时间：${formatTimestamp(item.timestamp)}</div>
            <div>接收时间：${formatTimestamp(item.received_at)}</div>
            <button class="link-button" data-event-detail="${escapeHtml(item.event_id || "")}">查看详情</button>
        </article>
    `).join("");
    bindDetailButtons(elements.uploadHistory);
}

function bindDetailButtons(container) {
    container.querySelectorAll("[data-event-detail]").forEach((button) => {
        button.addEventListener("click", async () => {
            await openEventDetail(button.dataset.eventDetail);
        });
    });
}

async function openEventDetail(eventId) {
    if (!eventId) {
        return;
    }

    elements.detailTitle.textContent = `事件详情 · ${eventId}`;
    elements.eventDetailBody.innerHTML = '<div class="empty-state">正在加载详情...</div>';
    if (!elements.eventDetailDialog.open) {
        elements.eventDetailDialog.showModal();
    }

    try {
        const detail = await apiGet(`/api/v1/events/${encodeURIComponent(eventId)}`);
        elements.eventDetailBody.innerHTML = `
            <div class="detail-layout">
                <div>
                    <img class="detail-image" src="${escapeAttr(detail.image_url || "")}" alt="${escapeAttr(detail.event_id || "detail")}">
                </div>
                <div class="kv-grid">
                    ${renderKv("事件 ID", detail.event_id)}
                    ${renderKv("租户", detail.tenant_id)}
                    ${renderKv("用户", detail.user_id)}
                    ${renderKv("设备", detail.camera_id)}
                    ${renderKv("时间", formatTimestamp(detail.timestamp))}
                    ${renderKv("向量维度", detail.caption_vector_size)}
                </div>
            </div>
            <div class="kv-item">
                <span>实体摘要</span>
                <div>${escapeHtml(detail.entities_text || "-")}</div>
            </div>
        `;
    } catch (error) {
        renderErrorState(elements.eventDetailBody, error);
    }
}

function renderKv(label, value) {
    return `
        <div class="kv-item">
            <span>${escapeHtml(label)}</span>
            <div>${escapeHtml(value == null ? "-" : String(value))}</div>
        </div>
    `;
}

async function apiGet(url) {
    const response = await fetch(url, {
        headers: {
            "Accept": "application/json"
        }
    });
    return unwrapResponse(response);
}

async function apiPost(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "Accept": "application/json"
        },
        body: JSON.stringify(payload)
    });
    return unwrapResponse(response);
}

async function apiFormPost(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        body: payload
    });
    return unwrapResponse(response);
}

async function unwrapResponse(response) {
    const raw = await response.json().catch(() => null);
    if (!response.ok || !raw || raw.code !== 200) {
        const message = raw?.message || `Request failed with status ${response.status}`;
        throw new Error(message);
    }
    return raw.data;
}

function renderJson(element, value) {
    element.textContent = JSON.stringify(value, null, 2);
}

function renderErrorState(element, error) {
    element.classList.add("empty-state");
    element.innerHTML = `<span class="error-text">${escapeHtml(error.message || "加载失败")}</span>`;
}

function formatTimestamp(value) {
    if (!value) {
        return "-";
    }
    try {
        return new Date(Number(value)).toLocaleString("zh-CN", { hour12: false });
    } catch (_) {
        return String(value);
    }
}

function formatNumber(value) {
    return Number(value || 0).toLocaleString("zh-CN");
}

function formatScore(value) {
    if (value == null || Number.isNaN(Number(value))) {
        return "-";
    }
    return Number(value).toFixed(3);
}

function toEpochMs(value) {
    if (!value) {
        return null;
    }
    const ms = new Date(value).getTime();
    return Number.isNaN(ms) ? null : ms;
}

function toDatetimeLocal(date) {
    const pad = (value) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function escapeAttr(value) {
    return escapeHtml(value);
}
