(function () {

// ═══════════════════════════════════════════════════════════════
//  상수
// ═══════════════════════════════════════════════════════════════

var STORAGE_KEYS = {
    TOKEN:               'token',
    NAME:                'name',
    CURRENT_MONTH:       'currentMonth',
    SIMULATION_ID:       'medsim_simulation_id',
    ONBOARDING_SHOWN:    'onboardingShown',
    HISTORY:             'medsim_history',
    EVENTS:              'medsim_events',
    // ── 시뮬레이션 진행 데이터 (백엔드 응답 누적) ──
    MONTHLY_DATA:        'medsim_monthly_data',
    NEXT_EVENTS:         'medsim_next_events',
    AVAILABLE_DECISIONS: 'medsim_available_decisions',
    // ── start1~5 입력값 (기존 유지) ──
    OWNED_CASH:          'medsim_owned_cash',
    LOAN_AMOUNT:         'medsim_loan_amount',
    FIXED_COST:          'medsim_fixed_cost',
    INTEREST_RATE:       'medsim_interest_rate',
    MONTHLY_INTEREST:    'medsim_monthly_interest',
    SIGUNGU:             'medsim_sigungu',
    FLOATING_POPULATION: 'medsim_floating_population',
    COMPETITOR_COUNT:    'medsim_competitor_count',
    PYEONG:              'medsim_pyeong',
    MARKETING_COST:      'medsim_marketing_cost',
    INTERIOR_COST:       'medsim_interior_cost',
    MONTHLY_RENT:        'medsim_monthly_rent',
    EQUIPMENT_TOTAL:     'medsim_equipment_total',
    CLINIC_STYLE:        'medsim_clinic_style',
    OPERATION_HOURS:     'medsim_operation_hours',
    OPERATION_STYLE:     'medsim_operation_style',
    STAFF_COUNTS:        'medsim_staff_counts',
    SERVICES:            'medsim_services',
    REPAYMENT_STYLE:     'medsim_repayment_style',
    REP_PROGRAMS:        'medsim_rep_programs',
};

var CHART_COLORS = {
    blue:      '#1A6BFF',
    green:     '#22C97A',
    red:       '#FF3B3B',
    orange:    '#FF8C42',
    navy:      '#0D1B2A',
    gray:      '#C8D0DC',
    blueBg:    'rgba(26,107,255,0.07)',
    greenBg:   'rgba(34,201,122,0.07)',
};

var CHART_FONT = { family: 'Pretendard', size: 12, weight: '600' };

var SIDEBAR_MENU = [
    { id: 'hospital',   label: '병원 현황', href: 'dashboard.html',
      svg: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>' },
    { id: 'finance',    label: '재무 분석', href: 'finance.html',
      svg: '<line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>' },
    { id: 'patient',    label: '환자 분석', href: 'patient.html',
      svg: '<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>' },
    { id: 'market',     label: '상권 분석', href: 'market.html',
      svg: '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>' },
    { id: 'reputation', label: '평판 관리', href: 'reputation.html',
      svg: '<polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>' },
    { id: 'risk',       label: '리스크 관리', href: 'risk.html',
      svg: '<path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>' },
    { id: 'staff',      label: '직원 관리', href: 'staff.html',
      svg: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16"/>' },
    { id: 'ai-coach',   label: 'AI 코치',   href: 'ai-coach.html',
      svg: '<rect x="4" y="4" width="16" height="16" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1" x2="9" y2="4"/><line x1="15" y1="1" x2="15" y2="4"/><line x1="9" y1="20" x2="9" y2="23"/><line x1="15" y1="20" x2="15" y2="23"/><line x1="20" y1="9" x2="23" y2="9"/><line x1="20" y1="14" x2="23" y2="14"/><line x1="1" y1="9" x2="4" y2="9"/><line x1="1" y1="14" x2="4" y2="14"/>' },
    { id: 'report',     label: '보고서',    href: 'report.html',
      svg: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>' },
    { id: 'settings',   label: '설정',      href: 'settings.html',
      svg: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/>' },
];

// ═══════════════════════════════════════════════════════════════
//  시뮬레이션 데이터 접근
// ═══════════════════════════════════════════════════════════════

function getSimulationId() {
    return localStorage.getItem(STORAGE_KEYS.SIMULATION_ID);
}

function getCurrentMonth() {
    return parseInt(localStorage.getItem(STORAGE_KEYS.CURRENT_MONTH) || '1');
}

function getConfig() {
    var ls = function (k) { return localStorage.getItem(k); };
    var cc = ls(STORAGE_KEYS.COMPETITOR_COUNT);
    return {
        ownedCash:          parseInt(ls(STORAGE_KEYS.OWNED_CASH))          || 0,
        loanAmount:         parseInt(ls(STORAGE_KEYS.LOAN_AMOUNT))          || 0,
        fixedCost:          parseInt(ls(STORAGE_KEYS.FIXED_COST))           || 5028,
        interestRate:       parseFloat(ls(STORAGE_KEYS.INTEREST_RATE))      || 4.5,
        monthlyInterest:    parseInt(ls(STORAGE_KEYS.MONTHLY_INTEREST))     || 0,
        sigungu:            ls(STORAGE_KEYS.SIGUNGU)                        || '',
        floatingPopulation: parseInt(ls(STORAGE_KEYS.FLOATING_POPULATION))  || 0,
        competitorCount:    cc !== null ? parseInt(cc) : null,
        pyeong:             parseInt(ls(STORAGE_KEYS.PYEONG))               || 0,
        marketingCost:      parseInt(ls(STORAGE_KEYS.MARKETING_COST))       || 0,
        interiorCost:       parseInt(ls(STORAGE_KEYS.INTERIOR_COST))        || 0,
        monthlyRent:        parseInt(ls(STORAGE_KEYS.MONTHLY_RENT))         || 0,
        equipmentTotal:     parseInt(ls(STORAGE_KEYS.EQUIPMENT_TOTAL))      || 0,
        clinicStyle:        ls(STORAGE_KEYS.CLINIC_STYLE)                   || 'BALANCED',
        operationHours:     ls(STORAGE_KEYS.OPERATION_HOURS)                || 'STANDARD',
        operationStyle:     ls(STORAGE_KEYS.OPERATION_STYLE)                || 'BALANCED',
        staffCounts:        JSON.parse(ls(STORAGE_KEYS.STAFF_COUNTS)        || 'null') || {},
        services:           JSON.parse(ls(STORAGE_KEYS.SERVICES)            || '[]'),
        repaymentStyle:     ls(STORAGE_KEYS.REPAYMENT_STYLE)                || 'BALANCED',
        reputationPrograms: JSON.parse(ls(STORAGE_KEYS.REP_PROGRAMS)        || '[]'),
    };
}

function getAllMonthlyData() {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.MONTHLY_DATA) || '[]');
}

function getMonthlyData(month) {
    return getAllMonthlyData().find(function (h) { return h.month === month; }) || null;
}

function getLatestMonthData() {
    var all = getAllMonthlyData();
    return all.length > 0 ? all[all.length - 1] : null;
}

function getNextEvents() {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.NEXT_EVENTS) || '[]');
}

function getAvailableDecisions() {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.AVAILABLE_DECISIONS) || '[]');
}

function appendMonthlyData(newData) {
    var all = getAllMonthlyData();
    all.push(newData);
    localStorage.setItem(STORAGE_KEYS.MONTHLY_DATA, JSON.stringify(all));
}

function resetSimulationData() {
    localStorage.removeItem(STORAGE_KEYS.MONTHLY_DATA);
    localStorage.removeItem(STORAGE_KEYS.CURRENT_MONTH);
    localStorage.removeItem(STORAGE_KEYS.NEXT_EVENTS);
    localStorage.removeItem(STORAGE_KEYS.AVAILABLE_DECISIONS);
}

function hasSimulationData() {
    return getAllMonthlyData().length > 0;
}

function getSimulationState() {
    var history = getAllMonthlyData();
    return {
        currentMonth: getCurrentMonth(),
        simulationId: localStorage.getItem(STORAGE_KEYS.SIMULATION_ID),
        config:       getConfig(),
        monthlyData:  history,
        events:       JSON.parse(localStorage.getItem(STORAGE_KEYS.EVENTS) || '[]'),
        history:      history,
    };
}

// ═══════════════════════════════════════════════════════════════
//  숫자/통화 포맷팅
// ═══════════════════════════════════════════════════════════════

function formatKRW(v) {
    var abs = Math.abs(v), sign = v < 0 ? '-' : '';
    if (abs >= 100000000) {
        var eok = Math.floor(abs / 100000000);
        var man = Math.round((abs % 100000000) / 10000);
        return sign + eok + '억' + (man > 0 ? ' ' + man.toLocaleString() + '만원' : '원');
    }
    if (abs >= 10000) return sign + Math.round(abs / 10000).toLocaleString() + '만원';
    return sign + abs.toLocaleString() + '원';
}

function formatManwon(v) {
    return Math.round(Math.abs(v) / 10000).toLocaleString() + '만원';
}

function formatPercent(value, decimals) {
    decimals = (decimals === undefined) ? 1 : decimals;
    return (value * 100).toFixed(decimals) + '%';
}

function formatNumber(num) {
    return Number(num).toLocaleString();
}

function formatDelta(value, decimals) {
    decimals = (decimals === undefined) ? 1 : decimals;
    var pos = value >= 0;
    return '<span style="color:' + (pos ? 'var(--green,#22C97A)' : 'var(--red,#FF3B3B)') + ';font-weight:700">' +
        (pos ? '+' : '-') + Math.abs(value).toFixed(decimals) + '%</span>';
}

// ═══════════════════════════════════════════════════════════════
//  인증
// ═══════════════════════════════════════════════════════════════

function requireAuth() {
    if (!localStorage.getItem(STORAGE_KEYS.TOKEN)) {
        window.location.replace('/login.html');
    }
}

function getAuthHeader() {
    var token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    return token ? { Authorization: 'Bearer ' + token } : {};
}

function logout() {
    localStorage.clear();
    window.location.href = '/index.html';
}

// ═══════════════════════════════════════════════════════════════
//  API 헬퍼
// ═══════════════════════════════════════════════════════════════

function apiGet(path) {
    return fetch(path, {
        method: 'GET',
        headers: Object.assign({ 'Content-Type': 'application/json' }, getAuthHeader()),
    }).then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
    });
}

function apiPost(path, body) {
    return fetch(path, {
        method: 'POST',
        headers: Object.assign({ 'Content-Type': 'application/json' }, getAuthHeader()),
        body: JSON.stringify(body),
    }).then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
    });
}

// ═══════════════════════════════════════════════════════════════
//  Chart.js 헬퍼
// ═══════════════════════════════════════════════════════════════

function _baseChartOptions(extra) {
    return Object.assign({
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                position: 'top',
                labels: {
                    font: { family: CHART_FONT.family, weight: CHART_FONT.weight, size: CHART_FONT.size },
                    padding: 14,
                    usePointStyle: true,
                }
            }
        }
    }, extra || {});
}

function _destroyExisting(canvas) {
    if (canvas._chartInst) {
        canvas._chartInst.destroy();
        canvas._chartInst = null;
    }
}

function createLineChart(canvasId, labels, datasets, options) {
    var canvas = document.getElementById(canvasId);
    if (!canvas) return null;
    _destroyExisting(canvas);
    var inst = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: { labels: labels, datasets: datasets },
        options: _baseChartOptions(Object.assign({
            interaction: { mode: 'index', intersect: false },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { font: { family: CHART_FONT.family, size: CHART_FONT.size }, color: '#7A8699' }
                },
                y: {
                    grid: { color: '#E8ECF2' },
                    ticks: {
                        font: { family: CHART_FONT.family, size: CHART_FONT.size },
                        color: '#7A8699',
                        callback: function (v) { return v.toLocaleString(); }
                    }
                }
            }
        }, options || {})),
    });
    canvas._chartInst = inst;
    return inst;
}

function createDoughnutChart(canvasId, labels, data, colors, options) {
    var canvas = document.getElementById(canvasId);
    if (!canvas) return null;
    _destroyExisting(canvas);
    var inst = new Chart(canvas.getContext('2d'), {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: colors || [CHART_COLORS.blue, CHART_COLORS.gray],
                borderWidth: 0,
                hoverOffset: 4,
            }]
        },
        options: Object.assign({
            responsive: true,
            maintainAspectRatio: false,
            cutout: '65%',
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: function (ctx) { return ' ' + ctx.label + ': ' + ctx.parsed + '%'; } } }
            }
        }, options || {}),
    });
    canvas._chartInst = inst;
    return inst;
}

function createBarChart(canvasId, labels, data, options) {
    var canvas = document.getElementById(canvasId);
    if (!canvas) return null;
    _destroyExisting(canvas);
    var colors = (options && options.colors) ? options.colors : [CHART_COLORS.blue];
    var inst = new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: Array.isArray(colors) ? colors : [colors],
                borderRadius: 4,
                borderSkipped: false,
            }]
        },
        options: _baseChartOptions(Object.assign({
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { font: { family: CHART_FONT.family, size: CHART_FONT.size }, color: '#7A8699' }
                },
                y: {
                    grid: { color: '#E8ECF2' },
                    ticks: {
                        font: { family: CHART_FONT.family, size: CHART_FONT.size },
                        color: '#7A8699',
                        callback: function (v) { return v.toLocaleString(); }
                    }
                }
            }
        }, options || {})),
    });
    canvas._chartInst = inst;
    return inst;
}

// ═══════════════════════════════════════════════════════════════
//  사이드바 CSS 주입 (sidebar-root를 사용하는 페이지용)
// ═══════════════════════════════════════════════════════════════

function _injectSidebarCss() {
    if (document.getElementById('medsim-sidebar-css')) return;
    var style = document.createElement('style');
    style.id = 'medsim-sidebar-css';
    style.textContent =
        ':root{--blue:#1A6BFF;--blue-dark:#0F4FCC;--blue-bg:#EFF5FF;--navy:#0D1B2A;' +
        '--gray-1:#F5F7FA;--gray-2:#E8ECF2;--gray-3:#C8D0DC;--gray-5:#7A8699;--gray-7:#3D4F63;' +
        '--green:#22C97A;--orange:#FF8C42;--red:#FF3B3B;' +
        '--font:\'Pretendard\',-apple-system,sans-serif;--sb:160px;}' +

        '.sidebar{width:var(--sb);flex-shrink:0;position:fixed;top:0;left:0;height:100vh;' +
        'background:white;border-right:1px solid var(--gray-2);display:flex;flex-direction:column;' +
        'overflow-y:auto;z-index:40;}' +

        '.sb-logo{padding:17px 14px 13px;display:flex;align-items:center;gap:8px;' +
        'border-bottom:1px solid var(--gray-2);flex-shrink:0;}' +
        '.sb-logo-icon{width:22px;height:22px;background:var(--blue);border-radius:5px;' +
        'display:flex;align-items:center;justify-content:center;}' +
        '.sb-logo-icon svg{width:12px;height:12px;fill:white;}' +
        '.sb-logo-type{font-size:14px;font-weight:800;color:var(--navy);}' +

        '.sb-icons{display:flex;gap:4px;padding:10px 12px;border-bottom:1px solid var(--gray-2);flex-shrink:0;}' +
        '.sb-icon-btn{flex:1;height:28px;border:1px solid var(--gray-2);border-radius:6px;' +
        'display:flex;align-items:center;justify-content:center;cursor:pointer;background:white;' +
        'color:var(--gray-5);transition:background .15s,color .15s;}' +
        '.sb-icon-btn:hover{background:var(--gray-1);color:var(--blue);}' +
        '.sb-icon-btn svg{width:12px;height:12px;}' +

        '.sb-search{padding:9px 12px;border-bottom:1px solid var(--gray-2);flex-shrink:0;}' +
        '.sb-search-wrap{display:flex;align-items:center;gap:5px;background:var(--gray-1);' +
        'border-radius:6px;padding:5px 9px;border:1px solid var(--gray-2);}' +
        '.sb-search-wrap svg{width:11px;height:11px;flex-shrink:0;color:var(--gray-3);}' +
        '.sb-search-wrap input{border:none;background:transparent;font-size:11px;color:var(--navy);' +
        'font-family:var(--font);width:100%;outline:none;}' +
        '.sb-search-wrap input::placeholder{color:var(--gray-3);}' +

        '.sb-nav{flex:1;padding:6px 0;overflow-y:auto;}' +
        '.sb-menu-item{display:flex;align-items:center;gap:8px;padding:8px 14px;font-size:12px;' +
        'font-weight:500;color:var(--gray-5);cursor:pointer;text-decoration:none;' +
        'transition:background .13s,color .13s;white-space:nowrap;}' +
        '.sb-menu-item:hover{background:var(--gray-1);color:var(--navy);}' +
        '.sb-menu-item.active{background:var(--blue-bg);color:var(--blue);font-weight:700;}' +
        '.sb-menu-item svg{width:14px;height:14px;flex-shrink:0;}' +

        '.sb-bottom{padding:12px 14px;border-top:1px solid var(--gray-2);flex-shrink:0;}' +
        '.sb-month-badge{background:var(--blue-bg);border-radius:8px;padding:8px 10px;text-align:center;}' +
        '.sb-month-num{font-size:16px;font-weight:800;color:var(--blue);}' +
        '.sb-month-lbl{font-size:10px;color:var(--blue);opacity:.7;margin-top:1px;}';
    document.head.appendChild(style);
}

// ═══════════════════════════════════════════════════════════════
//  사이드바 렌더링
// ═══════════════════════════════════════════════════════════════

function renderSidebar(activePageId) {
    _injectSidebarCss();
    var root = document.getElementById('sidebar-root');
    if (!root) return;

    var month = getCurrentMonth();

    var navHtml = SIDEBAR_MENU.map(function (item) {
        var active = item.id === activePageId ? ' active' : '';
        return '<a class="sb-menu-item' + active + '" href="' + item.href + '">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
            'stroke-linecap="round" stroke-linejoin="round">' + item.svg + '</svg>' +
            '<span>' + item.label + '</span>' +
            '</a>';
    }).join('');

    root.innerHTML =
        '<aside class="sidebar">' +
            '<div class="sb-logo">' +
                '<div class="sb-logo-icon">' +
                    '<svg viewBox="0 0 20 20"><rect x="3" y="9" width="14" height="2" rx="1"/>' +
                    '<rect x="9" y="3" width="2" height="14" rx="1"/></svg>' +
                '</div>' +
                '<span class="sb-logo-type">MEDSIM</span>' +
            '</div>' +
            '<div class="sb-icons">' +
                '<button class="sb-icon-btn" title="알림">' +
                    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9"/>' +
                    '<path d="M13.73 21a2 2 0 01-3.46 0"/></svg>' +
                '</button>' +
                '<button class="sb-icon-btn" title="도움말">' +
                    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3"/>' +
                    '<line x1="12" y1="17" x2="12.01" y2="17"/></svg>' +
                '</button>' +
                '<button class="sb-icon-btn" title="프로필">' +
                    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                    '<path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/>' +
                    '<circle cx="12" cy="7" r="4"/></svg>' +
                '</button>' +
            '</div>' +
            '<div class="sb-search"><div class="sb-search-wrap">' +
                '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">' +
                '<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>' +
                '<input type="text" placeholder="검색..."/>' +
            '</div></div>' +
            '<nav class="sb-nav">' + navHtml + '</nav>' +
            '<div class="sb-bottom"><div class="sb-month-badge">' +
                '<div class="sb-month-num" id="sb-month-num">M' + month + '</div>' +
                '<div class="sb-month-lbl">진행 중</div>' +
            '</div></div>' +
        '</aside>';
}

// ═══════════════════════════════════════════════════════════════
//  상단 KPI 바
// ═══════════════════════════════════════════════════════════════

function renderTopKpiBar(containerId, cards) {
    var el = document.getElementById(containerId);
    if (!el) return;
    el.innerHTML =
        '<div style="display:grid;grid-template-columns:repeat(' + cards.length + ',1fr);gap:14px;margin-bottom:18px;">' +
        cards.map(function (c) {
            return '<div style="background:white;border:1px solid var(--gray-2,#E8ECF2);border-radius:12px;padding:18px 20px;">' +
                '<div style="font-size:11px;font-weight:700;color:var(--gray-5,#7A8699);letter-spacing:.5px;' +
                'margin-bottom:10px;display:flex;align-items:center;justify-content:space-between;">' +
                c.label + (c.badgeHtml || '') +
                '</div>' +
                '<div style="font-size:22px;font-weight:800;color:var(--navy,#0D1B2A);line-height:1.2;margin-bottom:5px;">' +
                c.value +
                '</div>' +
                '<div style="font-size:12px;color:var(--gray-5,#7A8699);">' + (c.sub || '') + '</div>' +
                '</div>';
        }).join('') +
        '</div>';
}

// ═══════════════════════════════════════════════════════════════
//  네비게이션 (상단 공개 페이지용 — index, login 등)
// ═══════════════════════════════════════════════════════════════

function updateNav() {
    var navRight = document.querySelector('.nav-right');
    if (!navRight) return;

    var token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    var rawName = localStorage.getItem(STORAGE_KEYS.NAME);
    var name = (rawName && rawName !== 'null' && rawName !== 'undefined') ? rawName : '회원';

    if (token) {
        navRight.innerHTML =
            '<span style="font-size:13px;font-weight:600;color:var(--navy,#0D1B2A);">' + name + '님</span>' +
            '<a class="btn-outline" href="/start/start1.html">시뮬레이션</a>' +
            '<a class="btn-solid" href="#" onclick="logout();return false;">로그아웃</a>';
    } else {
        navRight.innerHTML =
            '<a class="btn-outline" href="/signup.html">회원가입</a>' +
            '<a class="btn-solid" href="/login.html">로그인</a>';
    }
}

// ═══════════════════════════════════════════════════════════════
//  전역 공개
// ═══════════════════════════════════════════════════════════════

window.STORAGE_KEYS    = STORAGE_KEYS;
window.CHART_COLORS    = CHART_COLORS;
window.CHART_FONT      = CHART_FONT;
window.SIDEBAR_MENU    = SIDEBAR_MENU;

window.getSimulationState      = getSimulationState;
window.getSimulationId         = getSimulationId;
window.getCurrentMonth         = getCurrentMonth;
window.getMonthlyData          = getMonthlyData;
window.getAllMonthlyData        = getAllMonthlyData;
window.getLatestMonthData      = getLatestMonthData;
window.getNextEvents           = getNextEvents;
window.getAvailableDecisions   = getAvailableDecisions;
window.appendMonthlyData       = appendMonthlyData;
window.resetSimulationData     = resetSimulationData;
window.hasSimulationData       = hasSimulationData;
window.getConfig               = getConfig;

window.formatKRW      = formatKRW;
window.formatManwon   = formatManwon;
window.formatPercent  = formatPercent;
window.formatNumber   = formatNumber;
window.formatDelta    = formatDelta;

window.requireAuth    = requireAuth;
window.getAuthHeader  = getAuthHeader;
window.logout         = logout;

window.apiGet         = apiGet;
window.apiPost        = apiPost;

window.createLineChart     = createLineChart;
window.createDoughnutChart = createDoughnutChart;
window.createBarChart      = createBarChart;

window.renderSidebar    = renderSidebar;
window.renderTopKpiBar  = renderTopKpiBar;
window.updateNav        = updateNav;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', updateNav);
} else {
    updateNav();
}

})();
