(function () {
  "use strict";

  var FE = window.FE_DATA || {};
  var LS_KEY = "fefw.planner.v2";
  var ROUTES = FE.routeKeys || [];
  var STATS = FE.stats || [];
  var CLASS_BY_ID = {};
  var REC_BY_CLASS = {};
  var MATERIALS = FE.materials || {};
  var CUSTOM_TIERS = ["初级", "中级", "上级", "最上级", "神将"];
  var MOUNT_TYPES = ["马", "飞鸵", "天马", "飞龙", "战象"];
  var MOUNT_UNIT = /骑兵|飞行/;
  var DIFF_RANK = { "低": 0, "中": 1, "高": 2, "超高": 3 };
  var KIND_LABEL = { story: "剧情加入", no: "不可挖角", scout: "可挖角" };

  (FE.classes || []).forEach(function (c) { CLASS_BY_ID[c.id] = c; });
  (FE.recommend || []).forEach(function (r) {
    (REC_BY_CLASS[r.classId] = REC_BY_CLASS[r.classId] || []).push(r);
  });

  var store, state;

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (m) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[m];
    });
  }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function uid() { return "s" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }
  function nowISO() { return new Date().toISOString(); }
  function fmtTime(iso) {
    try { return new Date(iso).toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }); }
    catch (e) { return esc(iso || ""); }
  }
  function statLabel(key) {
    var s = STATS.find(function (x) { return x[0] === key; });
    return s ? s[1] : key;
  }

  // 每个槽位记住离开时的界面状态（标签页 + 挖角筛选），reload 或切槽后原样恢复。
  function defaultUi() {
    return { tab: "recruit", sort: "rep", repMin: 0, onlyPlanned: false, q: "" };
  }
  function defaultData() {
    return {
      route: (FE.recommendedOrder && FE.recommendedOrder[0]) || ROUTES[0] || "",
      recruits: {}, paths: {}, mounts: {}, notes: {},
      onboarded: false, ui: defaultUi()
    };
  }
  function withDefaults(d) {
    d = d || {};
    if (!d.route) d.route = defaultData().route;
    ["recruits", "paths", "mounts", "notes"].forEach(function (k) { if (!d[k]) d[k] = {}; });
    // 只补缺失的 ui 子键，绝不覆盖用户已有值；onboarded 不在这里补，交给 loadStore 迁移判断。
    d.ui = d.ui || {};
    var def = defaultUi();
    Object.keys(def).forEach(function (k) { if (d.ui[k] === undefined) d.ui[k] = def[k]; });
    if (d.ui.tab === "settings") d.ui.tab = "recruit"; // 旧存档的设定页已并入常驻主角线切换条
    return d;
  }
  function newSlot(name) {
    return { id: uid(), name: name || ("存档 " + (store ? store.slots.length + 1 : 1)), createdAt: nowISO(), updatedAt: nowISO(), data: defaultData() };
  }
  function loadStore() {
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (raw) {
        var s = JSON.parse(raw);
        if (s && Array.isArray(s.slots) && s.slots.length) {
          s.slots.forEach(function (sl) {
            // 老存档没有 onboarded 字段：只要已选过主角线就视为完成引导，避免老用户被强行重新选择。
            if (sl.data && sl.data.onboarded === undefined) sl.data.onboarded = true;
            sl.data = withDefaults(sl.data);
          });
          if (!s.slots.some(function (x) { return x.id === s.activeId; })) s.activeId = s.slots[0].id;
          return s;
        }
      }
    } catch (e) { console.warn("存档读取失败", e); }
    return null;
  }
  function activeSlot() { return store.slots.find(function (x) { return x.id === state.activeId; }); }
  function data() { var sl = activeSlot(); return sl ? sl.data : defaultData(); }
  function save() {
    try {
      store.activeId = state.activeId;
      var sl = activeSlot();
      if (sl) sl.updatedAt = nowISO();
      localStorage.setItem(LS_KEY, JSON.stringify(store));
    } catch (e) { reportError("保存失败：" + e.message); }
  }
  function reportError(msg) { state.error = msg; }

  function boot() {
    store = loadStore();
    if (!store) {
      store = { activeId: null, slots: [] };
      var s = newSlot("我的规划");
      store.slots.push(s); store.activeId = s.id;
    }
    state = {
      activeId: store.activeId, tab: "recruit", error: "", scrollTo: null, charsScrollTo: null, charsFocus: null, showAllPaths: false, rulesOpen: false,
      filters: { q: "", onlyPlanned: false, sort: "rep", repMin: 0, repMax: 99 },
      chars: { q: "" },
      expandCmp: {}, expandItem: {}, expandClass: {}, expandChar: {},
    };
    restoreUi();
    save();
    var app = document.getElementById("app");
    app.addEventListener("click", onClick);
    app.addEventListener("change", onChange);
    app.addEventListener("input", onInput);
    render();
  }

  function syncUi() {
    var d = data();
    if (!d.ui) d.ui = {};
    d.ui.tab = state.tab === "start" ? "recruit" : state.tab; // start 是引导态，不写进 ui.tab，避免已引导用户被再次拉回引导页
    d.ui.sort = state.filters.sort;
    d.ui.repMin = state.filters.repMin;
    d.ui.onlyPlanned = state.filters.onlyPlanned;
    d.ui.q = state.filters.q;
  }

  // boot 与切槽时调用：未引导过的槽位强制回引导页，其余恢复其保存的标签页与筛选。
  function restoreUi() {
    var d = data() || {};
    var ui = d.ui || {};
    var tab = ui.tab === "settings" ? "recruit" : (ui.tab || "recruit");
    state.tab = d.onboarded === true ? tab : "start";
    state.filters.sort = ui.sort || "rep";
    state.filters.repMin = ui.repMin == null ? 0 : ui.repMin;
    state.filters.onlyPlanned = !!ui.onlyPlanned;
    state.filters.q = ui.q || "";
  }

  function render() {
    document.getElementById("app").innerHTML =
      topbarHTML() + routeBarHTML() + tabsHTML() + "<main class=\"view\">" + viewHTML() + "</main>" +
      (state.error ? "<div class=\"toast\" data-act=\"dismiss-error\">" + esc(state.error) + "（点击关闭）</div>" : "");
    if (state.scrollTo) {
      var el = document.getElementById(state.scrollTo);
      if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
      state.scrollTo = null;
    }
    if (state.charsScrollTo) {
      var el2 = document.getElementById("char-" + state.charsScrollTo);
      if (el2) el2.scrollIntoView({ block: "center", behavior: "smooth" });
      state.charsScrollTo = null;
    }
    state.error = "";
  }

  function topbarHTML() {
    var opts = store.slots.map(function (s) {
      return "<option value=\"" + s.id + "\"" + (s.id === state.activeId ? " selected" : "") + ">" + esc(s.name) + "</option>";
    }).join("");
    return "<header class=\"topbar\"><div class=\"brand\"><span class=\"logo\">◇</span>" +
      "<h1>火焰纹章 · 万缕千丝 <small>游玩规划器</small></h1></div>" +
      "<div class=\"topbar-actions\"><label class=\"slot\">存档 <select data-act=\"switch-slot\">" + opts + "</select></label>" +
      "<button class=\"btn-ghost\" data-act=\"export-cur\">导出</button>" +
      "<button class=\"btn-ghost\" data-act=\"import\">导入</button></div></header>" +
      "<input type=\"file\" id=\"import-file\" accept=\".json,application/json\" hidden data-act=\"import-file\">";
  }

  function tabsHTML() {
    if (state.tab === "start") return "";
    var tabs = [["recruit", "挖角规划"], ["class", "转职规划"], ["mounts", "坐骑规划"], ["chars", "角色信息"], ["saves", "存档"]];
    return "<nav class=\"tabs\">" + tabs.map(function (t) {
      return "<button class=\"tab" + (state.tab === t[0] ? " active" : "") + "\" data-act=\"tab\" data-tab=\"" + t[0] + "\">" + t[1] + "</button>";
    }).join("") + "</nav>";
  }

  // 主角线切换条：除引导首页外每个视图都常驻，避免再进设定页换线。说明面板复用规则与解锁职业。
  function routeBarHTML() {
    if (state.tab === "start") return "";
    var cur = data().route;
    var chips = (FE.routes || []).map(function (r) {
      return "<button class=\"route-chip" + (r.key === cur ? " active" : "") + "\" data-act=\"route\" data-route=\"" + esc(r.key) + "\">" +
        "<span class=\"rdot\" style=\"background:" + esc(r.color) + "\"></span>" + esc(r.key) + "</button>";
    }).join("");
    return "<div class=\"route-bar\"><span class=\"rb-label\">本篇主角</span>" + chips +
      "<button class=\"route-help" + (state.rulesOpen ? " open" : "") + "\" data-act=\"toggle-rules\">说明 " + (state.rulesOpen ? "▲" : "▼") + "</button></div>" +
      (state.rulesOpen ? "<div class=\"route-rules panel\">" + rulesHTML() + unlockedClassesHTML(cur) + "</div>" : "");
  }

  function viewHTML() {
    switch (state.tab) {
      case "start": return startHTML();
      case "recruit": return recruitHTML();
      case "class": return classHTML();
      case "mounts": return mountsHTML();
      case "chars": return charsHTML();
      case "saves": return savesHTML();
    }
    return "";
  }

  // 设定页与引导首页共用同一套主角线卡片标记（.routeCard/.route-grid），保证样式一致。
  function routeCardsHTML(cur) {
    return (FE.routes || []).map(function (r) {
      return "<button class=\"routeCard" + (r.key === cur ? " active" : "") + "\" data-act=\"route\" data-route=\"" + esc(r.key) + "\">" +
        "<div class=\"routeHead\"><span class=\"dot\" style=\"background:" + esc(r.color) + "\"></span><strong>" + esc(r.key) + "</strong></div>" +
        "<p>" + esc(r.feature) + "</p>" +
        "<div class=\"tags\">" + (r.unlocks || []).map(function (u) { return "<span class=\"tag\">" + esc(u) + "</span>"; }).join("") + "</div>" +
        "<p class=\"muted\">名声来源：" + esc(r.fameSource) + "</p>" +
        "<p class=\"note\">" + esc(r.note) + "</p></button>";
    }).join("");
  }

  function startHTML() {
    var cur = data().route;
    var order = FE.recommendedOrder || [];
    var orderLine = "<p class=\"note\">推荐通关顺序：" + order.map(function (k) {
      return k === cur ? "<b>" + esc(k) + "</b>" : esc(k);
    }).join(" → ") + "</p>";
    return "<div class=\"start-hero\"><h2 class=\"start-title\">选择本篇主角</h2>" +
      "<p class=\"start-sub muted\">先选定要操作的主角线，规划器会据此显示本线可招募角色、名声与条件。</p>" +
      orderLine + "</div>" +
      "<div class=\"route-grid\">" + routeCardsHTML(cur) + "</div>" +
      "<p class=\"muted start-note\">进入后可用顶部常驻的主角线切换条随时换线。</p>";
  }

  function rulesHTML() {
    var rules = [
      "名声 LV2 的角色送礼即可免费加入。",
      "四位主角在其它主角篇的第 1 部不可招募。",
      "战争篇以后不能挖角，第 1 部 12 章结束前挖完。",
      "外传通关给大量名声值（如谭利穆恩外传 150），优先提升名声可解锁更多可挖角色。"
    ];
    return "<p class=\"muted\">攻略摘要：名声达标 → 支援达标 → 帝都交涉 → 满足附加条件。</p><ul>" +
      rules.map(function (r) { return "<li>" + esc(r) + "</li>"; }).join("") + "</ul>";
  }

  function unlockedClassesHTML(route) {
    var byUnlock = (FE.classes || []).filter(function (c) { return c.unlock && c.unlock.indexOf(route) >= 0; });
    var names = byUnlock.map(function (c) { return c.name; });
    var routeObj = (FE.routes || []).find(function (r) { return r.key === route; }) || {};
    (routeObj.unlocks || []).forEach(function (n) { if (names.indexOf(n) < 0) names.push(n); });
    return "<h3>本篇解锁职业</h3><div class=\"tags\">" +
      (names.length ? names.map(function (n) { return "<span class=\"tag\">" + esc(n) + "</span>"; }).join("") : "<span class=\"muted\">无</span>") + "</div>";
  }

  function recruitHTML() {
    var route = data().route;
    var pool = recruitPool(route);
    var scoutAll = pool.filter(function (x) { return x.r && x.r.kind === "scout"; }).length;
    var planned = pool.filter(function (x) { return (data().recruits[x.char.id] || {}).planned; }).length;
    return "<div class=\"split\"><section class=\"col-main\">" + toolbarHTML() +
      "<div id=\"recruit-list\">" + recruitListHTML(route) + "</div></section>" +
      "<aside class=\"drawer\" id=\"drawer-panel\">" + drawerHTML(route, planned, scoutAll) + "</aside></div>";
  }

  function toolbarHTML(filters) {
    filters = filters || state.filters;
    var repOpts = [0, 2, 4, 6, 8, 10, 12].map(function (n) { return "<option value=\"" + n + "\"" + (filters.repMin === n ? " selected" : "") + ">≥" + n + "</option>"; }).join("");
    var sortSel = [["rep", "名声LV"], ["sup", "支援LV"], ["name", "名字"]].map(function (s) { return "<option value=\"" + s[0] + "\"" + (filters.sort === s[0] ? " selected" : "") + ">" + s[1] + "</option>"; }).join("");
    return "<div class=\"toolbar\">" +
      "<input type=\"text\" class=\"grow\" id=\"recruit-search\" placeholder=\"搜索角色名…\" value=\"" + esc(filters.q) + "\">" +
      "<label>名声 <select data-act=\"rep-min\">" + repOpts + "</select></label>" +
      "<label>排序 <select data-act=\"sort\">" + sortSel + "</select></label>" +
      "<button class=\"chk" + (filters.onlyPlanned ? " plan" : "") + "\" aria-pressed=\"" + !!filters.onlyPlanned + "\" data-act=\"toggle-only-planned\"><span class=\"box\">" + (filters.onlyPlanned ? "✓" : "") + "</span>只看已计划</button>" +
      "</div>";
  }

  function recruitPool(route) {
    return (FE.characters || []).filter(function (c) { return !c.late; }).map(function (c) {
      return { char: c, r: (c.recruitByRoute || {})[route] || null };
    });
  }

  // 「属于某条线」的判定：本线剧情固定出场或可挖角。用白名单（story/scout）而非「非 no」，
  // 因为 recruits 是全局的、不区分路线，若不按本线可用性过滤，切换路线后别的线角色（含其它主角）会残留。
  function availableOnRoute(c, route) {
    var r = (c.recruitByRoute || {})[route];
    return !!r && (r.kind === "story" || r.kind === "scout");
  }

  function recruitListHTML(route) {
    var f = state.filters;
    var shown = recruitPool(route).filter(function (x) { return matchFilter(x, f); });
    var story = shown.filter(function (x) { return x.r && x.r.kind === "story"; });
    var scouts = shown.filter(function (x) { return x.r && x.r.kind === "scout"; });
    var no = shown.filter(function (x) { return !x.r || x.r.kind === "no"; });
    var parts = [];
    // 固定出场 / 不可招募没有「计划/已招募」状态，故「只看已计划」时整组不显示。
    if (!f.onlyPlanned && story.length) parts.push(storyGroupHTML(story));
    if (scouts.length) parts.push(scoutGroupHTML(scouts, f.sort, route));
    if (!f.onlyPlanned && no.length) parts.push(noGroupHTML(no));
    if (!parts.length) return "<div class=\"empty\">没有符合筛选条件的角色。</div>";
    return parts.join("");
  }

  // 章节串（如「1部3章2月」）取前两段数字，供固定出场按章节排序；无法解析的沉底。
  function chapterKey(ch) {
    var m = String(ch || "").match(/(\d+)\D+(\d+)/);
    return m ? [parseInt(m[1], 10), parseInt(m[2], 10)] : [99, 99];
  }

  function groupHeadHTML(title, hint) {
    return "<div class=\"group-head\"><h3>" + esc(title) + "</h3>" +
      (hint ? "<span class=\"muted\">" + esc(hint) + "</span>" : "") + "</div>";
  }

  // A. 固定出场：剧情自动加入，用紧凑行展示，不提供计划/已招募按钮。
  function storyGroupHTML(rows) {
    rows.sort(function (a, b) {
      var ka = chapterKey(a.r && a.r.ch), kb = chapterKey(b.r && b.r.ch);
      return ka[0] - kb[0] || ka[1] - kb[1] || a.char.name.localeCompare(b.char.name, "zh");
    });
    return groupHeadHTML("本线固定出场 · " + rows.length + " 人", "剧情加入，无需交涉。") +
      rows.map(function (x) { return fixedRowHTML(x); }).join("");
  }

  function fixedRowHTML(x) {
    var c = x.char, r = x.r;
    var ch = r && r.ch ? "<span class=\"row-sub\">加入 " + esc(r.ch) + "</span>" : "";
    return "<div class=\"row compact\">" +
      "<img class=\"portrait\" src=\"" + c.portrait + "\" alt=\"" + esc(c.name) + "\">" +
      "<div class=\"row-body\"><div class=\"row-head\"><span class=\"name\">" + esc(c.name) + "</span>" +
      rowBadgeHTML(c, r) + ch + "</div></div></div>";
  }

  // B. 可挖角：默认按名声等级升序分子组；按支援/名字排序时退化为扁平列表。
  function scoutGroupHTML(rows, sort, route) {
    var titles = { rep: "可挖角 · 按名声等级", sup: "可挖角 · 按支援等级", name: "可挖角 · 按名字" };
    if (sort === "rep") return groupHeadHTML(titles.rep) + repGroupedHTML(rows, route);
    rows.sort(sorterFor(sort));
    return groupHeadHTML(titles[sort] || "可挖角") + rows.map(function (x) { return recruitRowHTML(x, route); }).join("");
  }

  function repGroupedHTML(rows, route) {
    var groups = {};
    rows.forEach(function (x) {
      var rep = (x.r && x.r.rep != null) ? x.r.rep : "?";
      (groups[rep] = groups[rep] || []).push(x);
    });
    var keys = Object.keys(groups).sort(function (a, b) { return (parseInt(a, 10) || 99) - (parseInt(b, 10) || 99); });
    return keys.map(function (k) {
      var list = groups[k];
      list.sort(function (a, b) {
        return (a.r.sup == null ? 99 : a.r.sup) - (b.r.sup == null ? 99 : b.r.sup) || a.char.name.localeCompare(b.char.name, "zh");
      });
      return "<div class=\"rank-head\"><span class=\"rep-pill\">名声 LV" + esc(k) + "</span><span class=\"muted\">" + list.length + " 人</span></div>" +
        list.map(function (x) { return recruitRowHTML(x, route); }).join("");
    }).join("");
  }

  // C. 不可招募：默认折叠收纳，避免干扰主列表。
  function noGroupHTML(rows) {
    rows.sort(function (a, b) { return a.char.name.localeCompare(b.char.name, "zh"); });
    var items = rows.map(function (x) {
      return "<div class=\"no-row\"><span class=\"name\">" + esc(x.char.name) + "</span>" + rowBadgeHTML(x.char, x.r) + "</div>";
    }).join("");
    return "<details class=\"no-group\"><summary>本篇不可招募 · " + rows.length + " 人</summary>" +
      "<div class=\"no-list\">" + items + "</div></details>";
  }

  function matchFilter(x, f) {
    var c = x.char, rec = data().recruits[c.id] || {};
    if (f.q && c.name.indexOf(f.q) < 0 && c.id.indexOf(f.q) < 0) return false;
    if (f.onlyPlanned && !rec.planned && !rec.done) return false;
    if (x.r && x.r.kind === "scout" && typeof x.r.rep === "number" && x.r.rep < f.repMin) return false;
    return true;
  }

  function sorterFor(sort) {
    return function (a, b) {
      var ra = a.r || {}, rb = b.r || {};
      if (sort === "rep") return (ra.rep == null ? 99 : ra.rep) - (rb.rep == null ? 99 : rb.rep) || a.char.name.localeCompare(b.char.name, "zh");
      if (sort === "sup") return (ra.sup == null ? 99 : ra.sup) - (rb.sup == null ? 99 : rb.sup) || a.char.name.localeCompare(b.char.name, "zh");
      return a.char.name.localeCompare(b.char.name, "zh");
    };
  }

  function recruitRowHTML(x, route) {
    var c = x.char, r = x.r, rec = data().recruits[c.id] || {};
    var body = r && r.kind === "scout" ? scoutBodyHTML(x, route) : nonScoutBodyHTML(c, r);
    var actions = (!r || r.kind === "no") ? "" :
      "<div class=\"row-actions\">" + planBtnHTML(c.id, rec) + doneBtnHTML(c.id, rec) + "</div>";
    var cmp = r && r.kind === "scout" ? compareToggleHTML(c, route) : "";
    return "<div class=\"row" + (rec.done ? " done" : "") + "\">" +
      "<img class=\"portrait\" src=\"" + c.portrait + "\" alt=\"" + esc(c.name) + "\">" +
      "<div class=\"row-body\">" + rowHeadHTML(c, r) + body + cmp + "</div>" + actions + "</div>";
  }

  function rowHeadHTML(c, r) {
    return "<div class=\"row-head\"><span class=\"name\">" + esc(c.name) + "</span>" + rowBadgeHTML(c, r) +
      "<span class=\"row-sub\">" + esc(c.startClass || "") + "</span></div>";
  }

  function rowBadgeHTML(c, r) {
    var hero = r && r.hero;
    if (r && r.kind === "story") return "<span class=\"badge " + (hero ? "hero\">本篇主角·剧情加入" : "story\">剧情加入") + "</span>";
    if (r && r.kind === "scout") return "<span class=\"badge scout\">可挖角</span>";
    if (r && r.kind === "no" && c.isHero) return "<span class=\"badge no\">其它主角本篇不可招募</span>";
    return "<span class=\"badge no\">不可挖角</span>";
  }

  function scoutBodyHTML(x, route) {
    var c = x.char, r = x.r;
    var ch = r.ch ? "<span>最早 <b>" + esc(r.ch) + "</b></span>" : "";
    return "<div class=\"stat-line\">" +
      "<span>名声 <b>" + (r.rep == null ? "?" : r.rep) + "</b></span>" +
      "<span>支援 <b>" + (r.sup == null ? "?" : r.sup) + "</b></span>" +
      "<span>难度 <b>" + esc(r.diff || "?") + "</b></span>" + ch + "</div>" +
      condChipsHTML(r.cond) +
      ((r.hard || r.minCh || r.minAt) ? "<div class=\"hard\">⚠ " + esc(r.hard || r.minAt || ("最早 " + r.minCh + " 章")) + "</div>" : "");
  }

  function nonScoutBodyHTML(c, r) {
    if (r && r.kind === "story") return "<div class=\"stat-line\"><span>剧情加入 <b>" + esc(r.ch || (c.joinChapter || [])[0] || "") + "</b></span></div>";
    return "<div class=\"stat-line\"><span class=\"muted\">" + esc(c.recruit || "本篇不可招募") + "</span></div>";
  }

  function condChipsHTML(cond) {
    if (!cond || !cond.length) return "";
    return "<div class=\"cond-list\">" + cond.map(function (raw) {
      var p = parseCond(raw);
      return "<span class=\"cond" + (p.type === "other" ? " other" : "") + "\">" + esc(raw) + "</span>";
    }).join("") + "</div>";
  }

  function planBtnHTML(id, rec) {
    return "<button class=\"chk plan\" aria-pressed=\"" + !!rec.planned + "\" data-act=\"plan\" data-char=\"" + id + "\"><span class=\"box\">" + (rec.planned ? "☆" : "") + "</span>计划招募</button>";
  }
  function doneBtnHTML(id, rec) {
    return "<button class=\"chk\" aria-pressed=\"" + !!rec.done + "\" data-act=\"done\" data-char=\"" + id + "\"><span class=\"box\">" + (rec.done ? "✓" : "") + "</span>" + (rec.done ? "已招募" : "已招募") + "</button>";
  }

  // 条件是非结构化中文文本，用一组正则按优先级识别；未命中的一律原样进入「其它条件」，
  // 保证新增或异常写法不会静默丢失（SPEC 要求容错）。
  function parseCond(raw) {
    var s = String(raw || "").trim(), m;
    if ((m = s.match(/交涉时付[ 　]*([0-9]+)G/))) return { type: "money", amount: parseInt(m[1], 10), raw: s };
    if ((m = s.match(/交涉时交出[ 　]*(.+?)×([0-9]+)/))) return { type: "item", name: m[1].trim(), qty: parseInt(m[2], 10), raw: s };
    if ((m = s.match(/先通关(.+?)外传/))) return { type: "paralogue", name: m[1].trim(), raw: s };
    if ((m = s.match(/先做完.*?支线「(.+?)」/)) || (m = s.match(/做完.*?支线「(.+?)」/))) return { type: "quest", name: m[1].trim(), raw: s };
    return { type: "other", raw: s };
  }

  function compareToggleHTML(c, route) {
    var open = !!state.expandCmp[c.id];
    return "<div><button class=\"chip-btn btn-sm\" data-act=\"cmp\" data-char=\"" + c.id + "\">" + (open ? "▲ 收起四线对比" : "▼ 四线对比") + "</button>" +
      (open ? fourCompareHTML(c, route) : "") + "</div>";
  }

  // 「哪条线最好挖」的判断：先比名声LV、再比支援LV、最后比难度；数值越低越省事。
  function fourCompareHTML(c, route) {
    var by = c.recruitByRoute || {};
    var scouts = ROUTES.map(function (k) { return by[k]; }).filter(function (r) { return r && r.kind === "scout"; });
    var best = null;
    scouts.forEach(function (r) {
      if (!best || cmpScore(r) < cmpScore(best)) best = r;
    });
    var rows = ROUTES.map(function (k) {
      var r = by[k] || { kind: "no" };
      var label = r.kind === "scout" ? ("名声" + (r.rep == null ? "?" : r.rep) + " · 支援" + (r.sup == null ? "?" : r.sup) + " · " + (r.diff || "?"))
        : (r.kind === "story" ? "剧情加入" : "不可挖");
      var isBest = best && r.kind === "scout" && cmpScore(r) === cmpScore(best);
      var isCur = k === route;
      return "<tr class=\"" + (isBest ? "best" : "") + "\"><td" + (isCur ? " class=\"cur\"" : "") + ">" + esc(k) + (isCur ? " ●" : "") + "</td><td>" + esc(label) + "</td><td class=\"bestflag\">" + (isBest ? "最好挖" : "") + "</td></tr>";
    }).join("");
    return "<div class=\"cmp\"><table><thead><tr><th>主角篇</th><th>要求</th><th></th></tr></thead><tbody>" + rows + "</tbody></table></div>";
  }

  function cmpScore(r) {
    return (r.rep == null ? 99 : r.rep) * 1000 + (r.sup == null ? 99 : r.sup) * 10 + (DIFF_RANK[r.diff] == null ? 9 : DIFF_RANK[r.diff]);
  }

  function drawerHTML(route, planned, scoutAll) {
    var plannedChars = (FE.characters || []).filter(function (c) { return (data().recruits[c.id] || {}).planned; });
    var scoutPlanned = plannedChars.filter(function (c) { var r = (c.recruitByRoute || {})[route]; return r && r.kind === "scout"; });
    var head = "<div class=\"drawerHead\"><h2>材料与条件清单</h2><span class=\"counter\">已计划 " + planned + " 人 / 可挖 " + scoutAll + " 人</span></div>";
    if (!scoutPlanned.length) {
      return head + "<div class=\"empty\">" + (plannedChars.length
        ? "已计划角色在本线均非可挖角（剧情加入），无需交涉材料。"
        : "在左侧把角色标记为「☆ 计划招募」，这里会自动汇总所需资金、道具与前置条件。") + "</div>";
    }
    var agg = aggregateConditions(scoutPlanned, route);
    // 只统计「已计划招募」且该条线可挖的角色，聚合所有附加条件。
    return head + moneyBlock(agg) + itemsBlock(agg) + listBlock("前置外传", agg.paralogues) +
      listBlock("前置支线", agg.quests) + repBlock(scoutPlanned, route) + othersBlock(agg) +
      "<p class=\"muted\">共 " + scoutPlanned.length + " 名已计划角色参与统计。</p>";
  }

  function aggregateConditions(chars, route) {
    var agg = { money: 0, moneyRows: [], items: {}, paralogues: {}, quests: {}, others: [] };
    chars.forEach(function (c) {
      var r = (c.recruitByRoute || {})[route];
      if (!r || r.kind !== "scout") return;
      (r.cond || []).forEach(function (raw) {
        var p = parseCond(raw);
        if (p.type === "money") { agg.money += p.amount; agg.moneyRows.push({ name: c.name, amount: p.amount }); }
        else if (p.type === "item") addItem(agg.items, p, c.name);
        else if (p.type === "paralogue") addUnique(agg.paralogues, p.name, c.name);
        else if (p.type === "quest") addUnique(agg.quests, p.name, c.name);
        else agg.others.push({ cond: p.raw, char: c.name });
      });
    });
    return agg;
  }

  // 同一道具被多个角色要求时按名字合并数量，这样清单能直接告诉玩家"总共要几个"。
  function addItem(map, p, charName) {
    var it = map[p.name] || (map[p.name] = { name: p.name, qty: 0, chars: [] });
    it.qty += p.qty;
    it.chars.push(charName);
  }
  function addUnique(map, name, charName) {
    (map[name] = map[name] || []).push(charName);
  }

  function moneyBlock(agg) {
    if (!agg.moneyRows.length) return "";
    var detail = agg.moneyRows.map(function (r) { return "<div class=\"agg-row\"><span class=\"left\">" + esc(r.name) + "</span><span class=\"right\">" + r.amount + " G</span></div>"; }).join("");
    return "<div class=\"block\"><h3>资金需求 <span class=\"count\">" + agg.moneyRows.length + " 笔</span></h3>" +
      "<div class=\"money-total\">共 " + agg.money.toLocaleString("en-US") + " G</div>" + detail + "</div>";
  }

  function itemsBlock(agg) {
    var names = Object.keys(agg.items);
    if (!names.length) return "";
    var rows = names.map(function (name) {
      var it = agg.items[name], open = !!state.expandItem[name];
      return "<div class=\"agg-row item-name\"><span class=\"left\"><b>" + esc(name) + "</b> ×" + it.qty + "</span>" +
        "<span class=\"right\">" + esc(it.chars.join("、")) + " <button class=\"link\" data-act=\"item\" data-item=\"" + esc(name) + "\">" + (open ? "收起" : "获取") + "</button></span></div>" +
        (open ? obtainHTML(name) : "");
    }).join("");
    return "<div class=\"block\"><h3>道具需求 <span class=\"count\">" + names.length + " 种</span></h3>" + rows + "</div>";
  }

  function obtainHTML(name) {
    var mat = MATERIALS[name];
    if (!mat || !(mat.obtain || []).length) return "<div class=\"agg-sub gray\">未找到「" + esc(name) + "」的获取资料。</div>";
    return "<div class=\"agg-sub\">获取：" + mat.obtain.map(esc).join("；") + "</div>";
  }

  function listBlock(title, map) {
    var names = Object.keys(map);
    if (!names.length) return "";
    var rows = names.map(function (n) {
      return "<div class=\"agg-row\"><span class=\"left\">" + esc(n) + "</span><span class=\"right\">" + esc(map[n].join("、")) + "</span></div>";
    }).join("");
    return "<div class=\"block\"><h3>" + title + " <span class=\"count\">" + names.length + " 项</span></h3>" + rows + "</div>";
  }

  function repBlock(chars, route) {
    var groups = {};
    chars.forEach(function (c) {
      var r = c.recruitByRoute[route];
      var key = r.rep == null ? "?" : r.rep;
      (groups[key] = groups[key] || []).push(c.name);
    });
    var keys = Object.keys(groups).sort(function (a, b) { return (parseInt(a, 10) || 99) - (parseInt(b, 10) || 99); });
    var rows = keys.map(function (k) {
      return "<div class=\"rep-row\"><span class=\"rep-pill\">名声 LV" + esc(k) + "</span><span>" + groups[k].length + " 人：</span><span class=\"muted\">" + esc(groups[k].join("、")) + "</span></div>";
    }).join("");
    return "<div class=\"block\"><h3>名声门槛汇总</h3><p class=\"muted\">优先把名声冲到最高门槛，可一次解锁多名角色。</p>" + rows + "</div>";
  }

  function othersBlock(agg) {
    if (!agg.others.length) return "";
    var rows = agg.others.map(function (o) {
      return "<div class=\"agg-row\"><span class=\"left\">" + esc(o.cond) + "</span><span class=\"right\">" + esc(o.char) + "</span></div>";
    }).join("");
    return "<div class=\"block\"><h3>对话 / 其它条件 <span class=\"count\">" + agg.others.length + " 项</span></h3>" + rows + "</div>";
  }

  function classHTML() {
    var d = data();
    var route = d.route;
    var chars = (FE.characters || []).filter(function (c) {
      if (c.late) return false;
      // 先按「本线是否可用」过滤，否则切换路线后非本线角色（含其它三线主角）会残留在此页。
      if (!availableOnRoute(c, route)) return false;
      var r = (c.recruitByRoute || {})[route];
      // 剧情固定出场的角色在挖角页没有计划/招募按钮（视为自动入队），故默认也一并展示，
      // 否则连本线主角都不会出现在转职规划里。
      if (r.kind === "story") return true;
      if (state.showAllPaths) return (c.paths || []).length > 0;
      var rec = d.recruits[c.id] || {};
      return rec.planned || rec.done;
    });
    var toggle = "<div class=\"toolbar\"><button class=\"chk" + (state.showAllPaths ? " plan" : "") + "\" aria-pressed=\"" + state.showAllPaths + "\" data-act=\"toggle-all-paths\"><span class=\"box\">" + (state.showAllPaths ? "✓" : "") + "</span>显示全部角色</button>" +
      "<span class=\"muted\">只显示本线登场角色的转职规划。</span></div>";
    if (!chars.length) return toggle + "<div class=\"empty\">本线暂无可规划的角色。</div>";
    return toggle + chars.map(charPathHTML).join("");
  }

  function charPathHTML(c) {
    var pd = data().paths[c.id] || {};
    return "<div class=\"char-card\"><div class=\"char-head\"><img class=\"portrait link\" data-act=\"goto-chars\" data-char=\"" + esc(c.id) + "\" src=\"" + c.portrait + "\" alt=\"" + esc(c.name) + "\" title=\"前往角色信息\">"
      + "<div><div class=\"row-head\"><span class=\"name link\" data-act=\"goto-chars\" data-char=\"" + esc(c.id) + "\">" + esc(c.name) + "</span><span class=\"badge\">" + esc(c.startClass || "") + "</span></div>" +
      growthMiniHTML(c) + "</div></div>" +
      pathPickerHTML(c, pd) + stepsForCharHTML(c, pd) +
      "<textarea data-act=\"note\" data-char=\"" + c.id + "\" rows=\"2\" placeholder=\"备注（可选）…\">" + esc(data().notes[c.id] || "") + "</textarea></div>";
  }

  function growthMiniHTML(c) {
    var g = c.growth || {};
    return "<div class=\"growth-grid\">" + STATS.map(function (s) {
      return "<span>" + s[1] + " " + (g[s[0]] == null ? "-" : g[s[0]]) + "</span>";
    }).join("") + "</div>";
  }

  function pathPickerHTML(c, pd) {
    var idx = pd.srcPathIndex == null ? 0 : pd.srcPathIndex;
    var opts = (c.paths || []).map(function (p, i) {
      return "<span class=\"radio" + (idx === i ? " active" : "") + "\" data-act=\"path-src\" data-char=\"" + c.id + "\" data-idx=\"" + i + "\">" + esc(p.src || ("方案" + (i + 1))) + "</span>";
    }).join("");
    opts += "<span class=\"radio" + (idx === -1 ? " active" : "") + "\" data-act=\"path-src\" data-char=\"" + c.id + "\" data-idx=\"-1\">自定义</span>";
    return "<div class=\"path-picker\">" + (opts || "<span class=\"muted\">该角色暂无推荐路线</span>") + "</div>";
  }

  function stepsForCharHTML(c, pd) {
    var idx = pd.srcPathIndex == null ? 0 : pd.srcPathIndex;
    if (idx === -1) return customBuilderHTML(c, pd) + stepsHTML(c, customSteps(pd.custom), pd);
    var p = (c.paths || [])[idx];
    if (!p) return "<div class=\"empty\">请选择一条推荐路线。</div>";
    return stepsHTML(c, p.steps || [], pd);
  }

  function customSteps(custom) {
    return (custom || []).map(function (id, i) {
      var cls = CLASS_BY_ID[id];
      return cls ? { tier: CUSTOM_TIERS[i] || cls.tier, name: cls.name, classId: id } : null;
    }).filter(Boolean);
  }

  function customBuilderHTML(c, pd) {
    var custom = pd.custom || [];
    var sels = CUSTOM_TIERS.map(function (tier, i) {
      var list = (FE.classes || []).filter(function (cl) { return cl.tier === tier; });
      var cur = custom[i] || "";
      return "<label>" + tier + "<select data-act=\"custom-sel\" data-char=\"" + c.id + "\" data-tier=\"" + i + "\">" +
        "<option value=\"\">— 不选 —</option>" +
        list.map(function (cl) { return "<option value=\"" + cl.id + "\"" + (cl.id === cur ? " selected" : "") + ">" + esc(cl.name) + "</option>"; }).join("") + "</select></label>";
    }).join("");
    return "<div class=\"custom-builder\">" + sels + "</div>";
  }

  // 转职路线改为横向流程：每级一张竖卡，右接箭头；成长与精通常驻显示，详情作为可选展开保留。
  function stepsHTML(c, steps, pd) {
    if (!steps || !steps.length) return "<div class=\"empty\">按上面选择职业后，这里会显示每一步。</div>";
    var done = pd.doneSteps || {};
    var parts = [];
    steps.forEach(function (s, i) {
      if (i) parts.push("<div class=\"path-arrow\">→</div>");
      parts.push(stepHTML(c, s, done));
    });
    return "<div class=\"path-flow\">" + parts.join("") + "</div>";
  }

  function stepHTML(c, s, done) {
    var cls = CLASS_BY_ID[s.classId];
    var key = s.tier;
    var isDone = !!done[key];
    var open = !!state.expandClass[c.id + "#" + s.classId];
    var mounted = cls && MOUNT_UNIT.test(cls.unitType || "");
    var hint = mounted ? "<button class=\"chip-btn btn-sm\" data-act=\"goto-mount\" data-char=\"" + c.id + "\">🐴 需要坐骑 · 去坐骑规划</button>" : "";
    var icon = cls && cls.icon ? "<img class=\"cicon\" src=\"" + cls.icon + "\" alt=\"\">" : "<div class=\"cicon\"></div>";
    var innate = cls && (cls.innate || []).length ? "<div class=\"node-stat\"><span class=\"node-label\">固有</span>" + esc(cls.innate.join("、")) + "</div>" : "";
    var detail = cls ? "<button class=\"chip-btn btn-sm\" data-act=\"class-info\" data-char=\"" + c.id + "\" data-class=\"" + s.classId + "\">" + (open ? "收起详情" : "职业详情") + "</button>" : "";
    return "<div class=\"step-node" + (isDone ? " done" : "") + "\">" +
      "<div class=\"node-top\">" + icon + "<div><div class=\"tier\">" + esc(s.tier || "") + "</div>" +
      "<div class=\"cname\">" + esc(s.name || (cls && cls.name) || "?") + "</div></div></div>" +
      "<div class=\"meta\">" + esc((cls && cls.requirement) || "") + (cls && cls.unlock ? " · " + esc(cls.unlock) : "") + "</div>" +
      "<div class=\"node-stat\"><span class=\"node-label\">成长</span>" + growthInline(cls && cls.growth) + "</div>" +
      "<div class=\"node-stat node-mastery\"><span class=\"node-label\">精通</span>" + esc((cls && cls.mastery) || "—") + "</div>" +
      innate + hint +
      "<div class=\"node-actions\"><button class=\"chk\" aria-pressed=\"" + isDone + "\" data-act=\"step-done\" data-char=\"" + c.id + "\" data-tier=\"" + esc(key) + "\"><span class=\"box\">" + (isDone ? "✓" : "") + "</span>已完成转职</button>" +
      detail + (open ? classDetailHTML(c, cls) : "") + "</div></div>";
  }

  function classDetailHTML(c, cls) {
    if (!cls) return "";
    var recs = REC_BY_CLASS[cls.id] || [];
    var recTxt = recs.map(function (r) { return "<li><b>" + esc(r.className) + "</b>：" + esc(r.desc || r.tag || "") + "</li>"; }).join("");
    return "<div class=\"detail\">" +
      "<div>成长加成：" + growthInline(cls.growth) + "</div>" +
      "<div>可习得技能：" + esc((cls.usableSkills || []).join("、") || "—") + "</div>" +
      "<div>固有：" + esc((cls.innate || []).join("、") || "—") + "</div>" +
      "<div>精通技能：" + esc(cls.mastery || "—") + "</div>" +
      "<div>解锁：" + esc(cls.unlock || "—") + "</div>" +
      (recTxt ? "<div>本站推荐：" + recTxt + "</div>" : "") + "</div>";
  }

  function growthInline(g) {
    if (!g) return "—";
    var parts = STATS.map(function (s) { var v = g[s[0]]; return v ? s[1] + (v > 0 ? "+" : "") + v : null; }).filter(Boolean);
    return esc(parts.join(" ") || "—");
  }

  function getStepsForChar(c) {
    var pd = data().paths[c.id] || {};
    var idx = pd.srcPathIndex == null ? 0 : pd.srcPathIndex;
    if (idx === -1) return customSteps(pd.custom);
    var p = (c.paths || [])[idx];
    return p ? (p.steps || []) : [];
  }

  // 角色信息矩阵：默认排除后期角色，搜索/筛选独立于挖角页，避免互相干扰。
  function charsHTML() {
    var cf = state.chars;
    return "<div class=\"toolbar\">" +
      "<input type=\"text\" class=\"grow\" id=\"chars-search\" placeholder=\"搜索角色名…\" value=\"" + esc(cf.q) + "\">" +
      "<span class=\"muted\">显示 " + charsList().length + " 人（已隐藏后期角色与其它三线主角）</span></div>" +
      "<div id=\"chars-matrix\">" + charsMatrixHTML() + "</div>";
  }

  function isCurrentRouteHero(c) {
    var route = data().route;
    var rec = (c.recruitByRoute || {})[route];
    return !!(c.isHero && rec && rec.hero && rec.kind === "story");
  }

  function charsList() {
    var cf = state.chars;
    return (FE.characters || []).filter(function (c) {
      // 从转职页跳转时会把目标角色放进 charsFocus：即使它被路线/搜索过滤掉，也必须保留该行，
      // 否则跳转后找不到目标行，定位与高亮会静默失效。
      if (state.charsFocus && state.charsFocus === c.id) return true;
      if (c.late) return false;
      if (c.isHero && !isCurrentRouteHero(c)) return false;
      if (cf.q && c.name.indexOf(cf.q) < 0 && c.id.indexOf(cf.q) < 0) return false;
      return true;
    });
  }

  function hasScout(c) {
    var by = c.recruitByRoute || {};
    return ROUTES.some(function (k) { return by[k] && by[k].kind === "scout"; });
  }

  function bestScoutScore(c) {
    var by = c.recruitByRoute || {}, best = null;
    ROUTES.forEach(function (k) {
      var r = by[k];
      if (r && r.kind === "scout") {
        var sc = cmpScore(r);
        if (best === null || sc < best) best = sc;
      }
    });
    return best;
  }

  function charsMatrixHTML() {
    var cur = data().route;
    var list = charsList();
    if (!list.length) return "<div class=\"empty\">没有符合筛选条件的角色。</div>";
    return "<div class=\"chars-wrap\"><table class=\"chars-matrix\"><thead><tr><th class=\"ch-col\">角色</th>" +
      ROUTES.map(function (k) {
        return "<th class=\"route-th" + (k === cur ? " cur" : "") + "\" data-act=\"route-from-chars\" data-route=\"" + esc(k) + "\">" + esc(k) + "</th>";
      }).join("") + "</tr></thead><tbody>" +
      list.map(function (c) { return charRowHTML(c); }).join("") + "</tbody></table></div>";
  }

  function charRowHTML(c) {
    var rec = data().recruits[c.id] || {};
    var dot = rec.done ? "<span class=\"pdot done\">已招募</span>" : (rec.planned ? "<span class=\"pdot plan\">已计划</span>" : "");
    var best = bestScoutScore(c);
    var open = !!state.expandChar[c.id];
    var row = "<tr id=\"char-" + esc(c.id) + "\" class=\"ch-row" + (rec.done ? " done" : "") + (open ? " open" : "") + "\">" +
      "<td class=\"ch-cell\"><div class=\"ch-face\">" +
      "<img class=\"portrait sm link\" data-act=\"toggle-char\" data-char=\"" + esc(c.id) + "\" src=\"" + c.portrait + "\" alt=\"" + esc(c.name) + "\" title=\"查看角色详情\">" +
      "<div><div class=\"ch-name\"><button class=\"ch-toggle\" data-act=\"toggle-char\" data-char=\"" + esc(c.id) + "\" aria-expanded=\"" + open + "\">" + (open ? "▾" : "▸") + "</button>" +
      esc(c.name) + (c.isHero ? " <span class=\"badge hero\">主角</span>" : "") + "</div>" +
      "<div class=\"ch-sub\">" + esc(c.startClass || "") + dot + "</div></div></div></td>" +
      ROUTES.map(function (k) { return charCellHTML(c, k, best); }).join("") + "</tr>";
    // 展开行单独占一行，colspan 覆盖全部路线列，避免撑破表格宽度。
    if (!open) return row;
    return row + "<tr class=\"ch-detail-row\"><td colspan=\"" + (ROUTES.length + 1) + "\">" + charDetailHTML(c) + "</td></tr>";
  }

  // 战技/skills/magic 均来自 bundle 的 learn 字段；空缺时明确显示「—」而不是留白。
  function charDetailHTML(c) {
    var learn = c.learn || {};
    return "<div class=\"ch-detail\">" +
      detailListHTML("战技", learn.arts) +
      detailListHTML("可习得技能", learn.skills) +
      detailListHTML("魔法", learn.magic) +
      detailListHTML("升级技能", c.lvSkills) +
      detailListHTML("擅长", c.skillsGood) +
      "</div>";
  }

  function detailListHTML(label, list) {
    var items = (list || []).filter(Boolean);
    if (!items.length) return "<div class=\"ch-detail-block\"><h6>" + esc(label) + "</h6><span class=\"muted\">—</span></div>";
    var chips = items.map(function (x) { return "<span class=\"tag\">" + esc(x) + "</span>"; }).join("");
    return "<div class=\"ch-detail-block\"><h6>" + esc(label) + " · " + items.length + "</h6><div class=\"tag-list\">" + chips + "</div></div>";
  }

  function charCellHTML(c, route, best) {
    var r = (c.recruitByRoute || {})[route];
    if (!r || r.kind === "no") return "<td class=\"c-no\">—</td>";
    if (r.kind === "story") return "<td class=\"c-story\"><span class=\"badge story\">剧情加入</span></td>";
    var isBest = best !== null && cmpScore(r) === best;
    return "<td class=\"c-scout" + (isBest ? " best-cell" : "") + "\">名声 <b>" + (r.rep == null ? "?" : r.rep) +
      "</b> · 支援 <b>" + (r.sup == null ? "?" : r.sup) + "</b> · " + esc(r.diff || "?") + (isBest ? " ★" : "") + "</td>";
  }

  function mountsHTML() {
    var groups = MOUNT_TYPES.concat((FE.mounts || []).map(function (m) { return m.type; }).filter(function (t) { return MOUNT_TYPES.indexOf(t) < 0; }));
    var uniq = groups.filter(function (t, i) { return groups.indexOf(t) === i; });
    var cards = uniq.map(function (type) {
      var list = (FE.mounts || []).filter(function (m) { return m.type === type; });
      if (!list.length) return "";
      return "<h3>" + esc(type) + " · " + list.length + " 种</h3><div class=\"mount-grid\">" + list.map(mountCardHTML).join("") + "</div>";
    }).join("");
    return "<div class=\"panel\"><p class=\"note\">🐴 <b>凯伊篇可捕获并饲养坐骑</b>：在地图上的坐骑图标处捕获，之后可喂养成长。部分坐骑仅特定主角篇或救世篇可获取，已在卡片上标注。</p></div>" +
      mountNeededHTML() + cards;
  }

  function mountNeededHTML() {
    var needed = mountNeeded();
    if (!needed.length) return "";
    var items = needed.map(function (n) {
      var hit = state.mountHighlight === n.char.id;
      return "<span class=\"hl-item" + (hit ? " hit" : "") + "\" id=\"need-" + n.char.id + "\"><img src=\"" + n.char.portrait + "\" alt=\"\">" + esc(n.char.name) + "<span class=\"muted\">" + esc(n.classes.join("、")) + "</span></span>";
    }).join("");
    return "<div class=\"panel\"><h2>需要坐骑的角色</h2><p class=\"muted\">这些角色的训练路线包含骑兵/飞行职业，凯伊线请提前规划捕获。</p><div class=\"hl-list\">" + items + "</div></div>";
  }

  function mountNeeded() {
    var d = data();
    return (FE.characters || []).filter(function (c) {
      if (!availableOnRoute(c, d.route)) return false;
      var rec = d.recruits[c.id] || {};
      return rec.planned || rec.done;
    }).map(function (c) {
      var names = getStepsForChar(c).map(function (s) { return CLASS_BY_ID[s.classId]; })
        .filter(function (cl) { return cl && MOUNT_UNIT.test(cl.unitType || ""); })
        .map(function (cl) { return cl.name; });
      return names.length ? { char: c, classes: names } : null;
    }).filter(Boolean);
  }

  function mountCardHTML(m) {
    var md = data().mounts[m.name] || {};
    var flagged = mountFlagged(m);
    var skills = (m.skills || []).map(function (s) { return "<li>" + esc(s) + "</li>"; }).join("");
    var levels = (m.levels || []).map(function (lv, i) { return "<div>Lv" + (i + 1) + "： " + esc((lv[0] || "")) + " ｜ " + esc((lv[1] || "")) + "</div>"; }).join("");
    return "<div class=\"mount-card" + (flagged ? " flagged" : "") + "\"><div class=\"mount-head\"><span class=\"mname\">" + esc(m.name) + "</span>" +
      (m.tentative ? "<span class=\"badge no\">推测</span>" : "") + (flagged ? "<span class=\"badge mount\">特定路线/救世篇</span>" : "") + "</div>" +
      "<div class=\"kv\"><b>加成：</b>" + esc(m.bonus || "—") + "</div>" +
      "<div class=\"kv\"><b>获取：</b>" + esc(m.where || "—") + "（" + esc(m.obtain || "") + "）</div>" +
      "<div class=\"kv\"><b>食物：</b>" + esc(m.food || "—") + "　<b>成长：</b>" + esc(m.growth || "—") + "</div>" +
      (skills ? "<ul class=\"detail\">" + skills + "</ul>" : "") +
      (levels ? "<div class=\"levels\">" + levels + "</div>" : "") +
      "<div class=\"path-picker\">" + mountChk("mount-plan", m.name, md.planned, "计划捕获") + mountChk("mount-cap", m.name, md.captured, "已捕获/已获得") + "</div>" +
      assignHTML(m.name, md.assigned || []) + "</div>";
  }

  function mountChk(act, name, on, label) {
    return "<button class=\"chk" + (act === "mount-plan" ? " plan" : "") + "\" aria-pressed=\"" + !!on + "\" data-act=\"" + act + "\" data-mount=\"" + esc(name) + "\"><span class=\"box\">" + (on ? "✓" : "") + "</span>" + label + "</button>";
  }

  function assignHTML(mountName, assigned) {
    var eligible = (FE.characters || []).filter(function (c) {
      if (!availableOnRoute(c, data().route)) return false;
      var rec = data().recruits[c.id] || {};
      return rec.planned || rec.done;
    });
    if (!eligible.length) return "<p class=\"muted\">先在「挖角规划」计划/招募角色，才能分配坐骑。</p>";
    var opts = eligible.map(function (c) { return "<option value=\"" + c.id + "\">" + esc(c.name) + "</option>"; }).join("");
    var tags = assigned.map(function (id) {
      var c = (FE.characters || []).find(function (x) { return x.id === id; });
      return "<span class=\"who\">" + esc(c ? c.name : id) + "<button data-act=\"mount-unassign\" data-mount=\"" + esc(mountName) + "\" data-char=\"" + id + "\">×</button></span>";
    }).join("");
    return "<div class=\"assign\"><select data-act=\"mount-assign\" data-mount=\"" + esc(mountName) + "\"><option value=\"\">分配给…</option>" + opts + "</select>" + tags + "</div>";
  }

  // 只有 where 明确提到某个主角篇名或「救世篇」时，才标注为特定路线限定。
  function mountFlagged(m) {
    var where = String(m.where || "");
    if (where.indexOf("救世篇") >= 0) return true;
    return ROUTES.some(function (k) { return where.indexOf(k) >= 0; });
  }

  function savesHTML() {
    var list = store.slots.map(slotItemHTML).join("");
    return "<h2 class=\"section-title\">存档槽</h2><div class=\"slot-list\">" + list + "</div>" +
      "<div class=\"toolbar\" style=\"margin-top:16px\">" +
      "<button class=\"btn\" data-act=\"slot-new\">＋ 新建槽</button>" +
      "<button class=\"btn-ghost\" data-act=\"export-cur\">导出当前槽</button>" +
      "<button class=\"btn-ghost\" data-act=\"export-all\">导出全部</button>" +
      "<button class=\"btn-ghost\" data-act=\"import\">导入文件</button>" +
      "<button class=\"btn-danger\" data-act=\"clear\">清空当前槽</button></div>";
  }

  function slotItemHTML(s) {
    var active = s.id === state.activeId;
    return "<div class=\"slot-item" + (active ? " active" : "") + "\">" +
      "<div class=\"meta\">" + esc(s.name) + (active ? " <span class=\"badge scout\">当前</span>" : "") +
      "<small>更新于 " + esc(fmtTime(s.updatedAt)) + "</small></div>" +
      "<div class=\"slot-ops\">" + (active ? "" : "<button class=\"btn-sm\" data-act=\"slot-switch\" data-id=\"" + s.id + "\">切换</button>") +
      "<button class=\"btn-sm\" data-act=\"slot-rename\" data-id=\"" + s.id + "\">重命名</button>" +
      "<button class=\"btn-sm\" data-act=\"slot-dup\" data-id=\"" + s.id + "\">复制</button>" +
      "<button class=\"btn-sm btn-danger\" data-act=\"slot-del\" data-id=\"" + s.id + "\">删除</button></div></div>";
  }

  function download(filename, text) {
    var blob = new Blob([text], { type: "application/json" });
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url; a.download = filename;
    document.body.appendChild(a); a.click();
    document.body.removeChild(a); URL.revokeObjectURL(url);
  }

  function exportCurrent() {
    var sl = activeSlot();
    download("fefw-plan.json", JSON.stringify({ app: "fefw-planner", version: 2, kind: "slot", name: sl.name, data: sl.data }, null, 2));
  }
  function exportAll() {
    download("fefw-plan-all.json", JSON.stringify({ app: "fefw-planner", version: 2, kind: "all", slots: store.slots }, null, 2));
  }

  function handleImport(file) {
    if (!file) return;
    var reader = new FileReader();
    reader.onload = function () {
      try { applyImport(JSON.parse(reader.result)); }
      catch (e) { reportError("导入失败：" + e.message); render(); }
    };
    reader.onerror = function () { reportError("读取文件失败"); render(); };
    reader.readAsText(file);
  }

  function applyImport(payload) {
    if (payload && payload.kind === "all" && Array.isArray(payload.slots)) {
      payload.slots.forEach(function (s) { s.id = uid(); s.data = withDefaults(s.data); store.slots.push(s); });
      restoreUi(); save(); render(); return;
    }
    var incoming = payload && payload.data ? payload.data : payload;
    if (!incoming || typeof incoming !== "object") { reportError("文件格式无法识别"); render(); return; }
    if (confirm("导入「" + (payload.name || "未命名") + "」。确定 = 替换当前槽；取消 = 新建槽。")) {
      activeSlot().data = withDefaults(incoming);
    } else {
      var s = newSlot(payload.name || "导入的规划");
      s.data = withDefaults(clone(incoming));
      store.slots.push(s);
    }
    restoreUi(); save(); render();
  }

  function onClick(e) {
    var t = e.target.closest("[data-act]");
    if (!t) return;
    var act = t.getAttribute("data-act"), id = t.getAttribute("data-char");
    if (act === "dismiss-error") { render(); return; }
    if (act === "tab") { state.tab = t.getAttribute("data-tab"); state.charsFocus = null; syncUi(); save(); render(); return; }
    if (act === "toggle-rules") { state.rulesOpen = !state.rulesOpen; return render(); }
    if (act === "route-from-chars") {
      data().route = t.getAttribute("data-route");
      state.tab = "recruit"; syncUi(); save(); render(); return;
    }
    if (act === "route") {
      data().route = t.getAttribute("data-route");
      data().onboarded = true;
      if (state.tab === "start") state.tab = "recruit";
      syncUi(); save(); render(); return;
    }
    if (act === "plan") { return toggleRecruit(id, "planned");
    }
    if (act === "done") { return toggleRecruit(id, "done"); }
    if (act === "cmp") { state.expandCmp[id] = !state.expandCmp[id]; return refreshRecruit(); }
    if (act === "item") { var it = t.getAttribute("data-item"); state.expandItem[it] = !state.expandItem[it]; return refreshDrawerOnly(); }
    if (act === "toggle-only-planned") { state.filters.onlyPlanned = !state.filters.onlyPlanned; syncUi(); save(); return render(); }
    if (act === "toggle-all-paths") { state.showAllPaths = !state.showAllPaths; return render(); }
    if (act === "path-src") { return selectPath(id, parseInt(t.getAttribute("data-idx"), 10)); }
    if (act === "step-done") { return toggleStepDone(id, t.getAttribute("data-tier")); }
    if (act === "class-info") { var ck = id + "#" + t.getAttribute("data-class"); state.expandClass[ck] = !state.expandClass[ck]; return render(); }
    if (act === "goto-chars") { state.tab = "chars"; state.charsFocus = id; state.charsScrollTo = id; state.expandChar[id] = true; syncUi(); save(); return render(); }
    if (act === "toggle-char") { state.expandChar[id] = !state.expandChar[id]; refreshChars(); return; }
    if (act === "goto-mount") { state.tab = "mounts"; state.mountHighlight = id; state.scrollTo = "need-" + id; syncUi(); save(); return render(); }
    if (act === "mount-plan") { return toggleMount(t.getAttribute("data-mount"), "planned"); }
    if (act === "mount-cap") { return toggleMount(t.getAttribute("data-mount"), "captured"); }
    if (act === "mount-unassign") { return unassignMount(t.getAttribute("data-mount"), id); }
    savesClick(act, t, id);
  }

  function savesClick(act, t, id) {
    if (act === "switch-slot") return;
    if (act === "slot-switch") { state.activeId = t.getAttribute("data-id"); restoreUi(); save(); render(); return; }
    if (act === "slot-new") { var s = newSlot(); store.slots.push(s); state.activeId = s.id; restoreUi(); save(); render(); return; }
    if (act === "slot-rename") { renameSlot(t.getAttribute("data-id")); return; }
    if (act === "slot-dup") { duplicateSlot(t.getAttribute("data-id")); return; }
    if (act === "slot-del") { deleteSlot(t.getAttribute("data-id")); return; }
    if (act === "export-cur") { exportCurrent(); return; }
    if (act === "export-all") { exportAll(); return; }
    if (act === "import") { var f = document.getElementById("import-file"); if (f) f.click(); return; }
    if (act === "clear") { clearCurrent(); return; }
  }

  function toggleRecruit(id, key) {
    var rec = data().recruits[id] || (data().recruits[id] = {});
    rec[key] = !rec[key];
    if (key === "done" && rec.done) rec.planned = true;
    save(); rerender();
  }
  function selectPath(id, idx) { data().paths[id] = Object.assign({}, data().paths[id], { srcPathIndex: idx }); save(); render(); }
  function toggleStepDone(id, tier) {
    var pd = data().paths[id] || (data().paths[id] = { doneSteps: {} });
    pd.doneSteps = pd.doneSteps || {};
    pd.doneSteps[tier] = !pd.doneSteps[tier];
    save(); render();
  }
  function toggleMount(name, key) {
    var m = data().mounts[name] || (data().mounts[name] = {});
    m[key] = !m[key];
    save(); render();
  }
  function setCustomStep(id, tierIdx, classId) {
    var pd = data().paths[id] || (data().paths[id] = {});
    pd.custom = pd.custom || [];
    pd.custom[tierIdx] = classId;
    pd.srcPathIndex = -1;
    save(); render();
  }
  function addMountAssign(name, charId) {
    if (!charId) return;
    var m = data().mounts[name] || (data().mounts[name] = {});
    m.assigned = m.assigned || [];
    if (m.assigned.indexOf(charId) < 0) m.assigned.push(charId);
    save(); render();
  }
  function unassignMount(name, charId) {
    var m = data().mounts[name]; if (!m || !m.assigned) return;
    m.assigned = m.assigned.filter(function (x) { return x !== charId; });
    save(); render();
  }

  function renameSlot(id) {
    var s = store.slots.find(function (x) { return x.id === id; }); if (!s) return;
    var n = prompt("重命名存档", s.name);
    if (n) { s.name = n.trim(); save(); render(); }
  }
  function duplicateSlot(id) {
    var s = store.slots.find(function (x) { return x.id === id; }); if (!s) return;
    var cp = { id: uid(), name: s.name + " 副本", createdAt: nowISO(), updatedAt: nowISO(), data: clone(s.data) };
    store.slots.push(cp); save(); render();
  }
  function deleteSlot(id) {
    if (store.slots.length <= 1) { reportError("至少保留一个存档槽"); render(); return; }
    var s = store.slots.find(function (x) { return x.id === id; });
    if (!confirm("确定删除存档「" + (s ? s.name : id) + "」？此操作不可恢复。")) return;
    store.slots = store.slots.filter(function (x) { return x.id !== id; });
    if (state.activeId === id) { state.activeId = store.slots[0].id; restoreUi(); }
    save(); render();
  }
  function clearCurrent() {
    if (!confirm("清空当前槽的全部规划？此操作不可恢复。")) return;
    activeSlot().data = defaultData();
    restoreUi(); save(); render();
  }

  function onChange(e) {
    var t = e.target.closest("[data-act]"); if (!t) return;
    var act = t.getAttribute("data-act");
    if (act === "switch-slot") { state.activeId = t.value; restoreUi(); save(); render(); }
    else if (act === "sort") { state.filters.sort = t.value; syncUi(); save(); refreshRecruit(); }
    else if (act === "rep-min") { state.filters.repMin = parseInt(t.value, 10) || 0; syncUi(); save(); refreshRecruit(); }
    else if (act === "custom-sel") { setCustomStep(t.getAttribute("data-char"), parseInt(t.getAttribute("data-tier"), 10), t.value); }
    else if (act === "mount-assign") { addMountAssign(t.getAttribute("data-mount"), t.value); }
    else if (act === "import-file") { handleImport(t.files && t.files[0]); }
  }

  function onInput(e) {
    var t = e.target;
    if (t.id === "recruit-search") { state.filters.q = t.value; syncUi(); save(); refreshRecruit(); return; }
    if (t.id === "chars-search") { state.chars.q = t.value; state.charsFocus = null; refreshChars(); return; }
    if (t.getAttribute("data-act") === "note") { data().notes[t.getAttribute("data-char")] = t.value; save(); }
  }

  function rerender() { if (state.tab === "recruit") refreshRecruit(); else render(); }
  function refreshRecruit() {
    if (state.tab !== "recruit") return render();
    var route = data().route;
    var list = document.getElementById("recruit-list"); if (list) list.innerHTML = recruitListHTML(route);
    var dr = document.getElementById("drawer-panel");
    if (dr) {
      var pool = recruitPool(route);
      var scoutAll = pool.filter(function (x) { return x.r && x.r.kind === "scout"; }).length;
    var planned = pool.filter(function (x) { return x.r && x.r.kind !== "no" && (data().recruits[x.char.id] || {}).planned; }).length;
      dr.innerHTML = drawerHTML(route, planned, scoutAll);
    }
  }
  function refreshDrawerOnly() {
    var dr = document.getElementById("drawer-panel");
    if (!dr) return render();
    var route = data().route, pool = recruitPool(route);
    var scoutAll = pool.filter(function (x) { return x.r && x.r.kind === "scout"; }).length;
    var planned = pool.filter(function (x) { return (data().recruits[x.char.id] || {}).planned; }).length;
    dr.innerHTML = drawerHTML(route, planned, scoutAll);
  }

  // 只重绘矩阵，保留搜索框焦点与输入光标位置。
  function refreshChars() {
    var box = document.getElementById("chars-matrix");
    if (box) box.innerHTML = charsMatrixHTML();
  }

  boot();
})();
