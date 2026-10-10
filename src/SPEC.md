# 火焰纹章·万缕千丝 游玩规划器 — 构建规格 (SPEC)

你要构建一个 **单个离线 HTML** 的游玩规划工具，用于在游玩前定义规划、游玩中打勾。纯前端、**零外部依赖**（不许用 CDN / 外链字体 / 框架），Vanilla JS 即可。

## 交付文件（放在 `/Users/sunjian/Downloads/fe-fw-planner/src/`）

1. `index.html` — 页面骨架，含 `<title>`、`<link rel="stylesheet" href="style.css">`、`<script src="bundle.js"></script>`、`<script src="app.js"></script>`（构建脚本会自动把它们内联成单文件，所以引用方式必须**正好**是这三个标签）。
2. `style.css` — 全部样式。
3. `app.js` — 全部逻辑，读取全局 `window.FE_DATA`。

数据已经生成在 `src/bundle.js`（`window.FE_DATA = {...}`）。**先读它了解真实字段**（不要臆测）。构建：`python3 tools/build.py` → `planner.html`。

## 数据模型 `window.FE_DATA`

```
meta{site,game,generated,note}
stats: [[key,label]*9]   // hp,str,mag,spd,dex,def,res,lck,cha
routes: [{key,color,feature,unlocks[],fameSource,note}]   // key ∈ 凯伊/迪托利希/赛奥朵拉/蕾达
recommendedOrder: [4 个 key]   // 赛奥朵拉→迪托利希→凯伊→蕾达
routeKeys: [4 个 key]
characters: [{
  id,name,sex,faction,startClass,tentative,
  portrait: dataURI,
  growth{9项}, growthTotal, baseLevel, baseStats{9项},
  skillsGood[], personalSkill{name,text}, bloodMarks[{name,text}],
  recruit, joinChapter[], joinHow[], storyJoin{},
  late: bool,          // true = 序幕/第2/3部加入，不参与第1部挖角
  isHero: bool,        // 4 位主角之一
  paths: [{src, steps:[{tier,name,classId}]}],   // 站点推荐转职路线
  learn:{skills[],arts[],magic[],ranks?}, lvSkills[],
  recruitByRoute: { <routeKey>: {
      route,
      kind: "scout"|"story"|"no"|"hero",
      rep, sup, diff,            // kind=scout 时：名声LV / 支援LV / 交涉难度(低|中|高|超高)
      cond: [字符串条件...],       // 附加条件（付钱/交材料/前置外传/前置支线/对话选项）
      ch, how, gift, minCh, minAt, hard   // 最早可挖时机 / 赠礼 / 人工难点标注
  }}
}]
classes: [{id,name,tier,tierKey,family,unitType,requirement,unlock,move,
  growth{9项},growthTotal,innate[],mastery,usableSkills[],baseStats,icon:dataURI}]
recommend: [{group,classId,className,tag,desc,color,picks:[{charId,name,trait,note}],footer}]
mounts: [{name,tentative,type,bonus,food,growth,where,obtain,skills[],levels[][]}]
materials: { name: {id,name,kind:"item"|"weapon",cat,price,text,obtain[],obtainGW[],note} }
guides: {g-hero,g-fame,g-scout}   // 纯文本说明
```

## 功能规格

### 全局
- 顶部：标题、**存档槽选择器**、导出/导入按钮、主题（深色为主，主色取游戏青蓝 `#1c3a4a`，可用 CSS 变量）。
- Tab 导航：`设定` / `挖角规划` / `转职规划` / `坐骑规划` / `存档`。
- 响应式：桌面双栏，窄屏单栏。
- 任何勾选/改动**立即自动保存**当前存档槽。

### 存档模型
- `localStorage` key：`fefw.planner.v2`，值 `{activeId, slots:[{id,name,createdAt,updatedAt,data}]}`。
- `data` 结构（自行设计但要覆盖全部状态）：
  - `route`：当前主角 key。
  - `recruits`：`{charId:{planned:bool, done:bool}}`。
  - `paths`：`{charId:{srcPathIndex:int|-1, custom:[classId...], doneSteps:{tier:bool}}}`（-1 表示自定义）。
  - `mounts`：`{mountName:{planned:bool,captured:bool,assigned:[charId]}}`。
  - `notes`：`{charId:text}`（可选）。
- 多周目：新建/重命名/复制/删除槽；切换槽立即重渲染。
- 导出：下载 `fefw-plan.json`；导入：读取文件、可选「替换当前槽」或「新建槽」。

### Tab 设定
- 4 张主角卡片可选（高亮当前）；每张显示 `feature / unlocks / fameSource / note`。
- 显示推荐通关顺序 `recommendedOrder`，当前选择不在首位时给出提示。
- 显示挖角总规则（来自 `guides.g-scout` / `g-fame`，摘要即可）：名声LV2送礼免费、战争篇后不能挖、第1部12章结束前挖完。
- 显示「本篇解锁职业」= `classes` 中 `unlock` 含当前主角名的职业 + `route.unlocks`。

### Tab 挖角规划（核心）
- 角色池 = `!late` 的角色。按当前 `route` 展示 `recruitByRoute[route]`：
  - `kind=hero`（当前主角）→ 标记「本篇主角·剧情加入」。
  - `kind=story` → 绿标「剧情加入」+ 章节 `ch`。
  - `kind=no` 且 `isHero` → 灰字「其它主角本篇不可招募」。
  - `kind=no` 其它 → 「不可挖角」。
  - `kind=scout` → 主体：名声LV、支援LV、难度、`ch`（最早时机）、`cond[]` 附加条件、`hard` 难点警告（⚠）。
- 每个 scout 角色行提供两个按钮：`☆ 计划招募` 与 `✓ 已招募`（独立于计划）；`done` 时整行打勾变暗。
- **四线对比**：每行可展开一个小表，列出该角色 4 条线的 `rep/sup/diff`，并标注最低的那条（帮助判断哪条线最好挖）。
- 过滤/排序：按名声LV区间、只看已计划、名字搜索；排序按名声LV / 支援LV / 名字。
- **材料与条件清单（右侧固定面板，重点需求）**：只统计「已计划招募」的角色，聚合：
  1. **资金**：把所有 `cond` 里 `交涉时付 (\d+)G` 求和 → 总金额 + 逐条明细（角色→金额）。
  2. **道具**：`交涉时交出 (名)×(数量)` → 按物品名合并数量；每条可展开显示 `materials[名].obtain`（在哪获取）。找不到的标灰。
  3. **前置外传**：`先通关(名)外传` → 去重列表。
  4. **前置支线**：`先做完/做完.*支线「(名)」` → 去重列表。
  5. **对话/其它**：其余 `cond` 原样列出。
  6. **名声门槛汇总**：显示「名声 LV{n}: m 人」的分组，提醒优先冲名声。
- 清单上方显示「已计划 N 人 / 可挖 M 人」。空状态时提示如何操作。
- 条件解析写成独立函数，容错（正则见上）。找不到匹配的保留为「其它条件」。

### Tab 转职规划
- 列表：默认展示「已计划或已招募」的角色，顶部可切换「显示全部」。
- 每个角色卡：头像、名字、初始职业 `startClass`、成长率小表（`stats`）。
- 路线选择：渲染 `paths[]`（`src` 作为方案名，如「外网 A」「本站兵种推荐」），单选；另提供「自定义」选项，允许逐档从 `classes` 里选职业拼一条路线。
- 选定路线后，逐步显示每一步：职业图标、`tier`、职业名、`requirement`、`unlock`、成长加成（`growth`）、精通技能 `mastery`；每步可勾选「已完成转职」。
- 点击职业弹出/展开详情：`usableSkills / innate / growth / requirement / unlock`，以及 `recommend` 里同 `classId` 的推荐说明。
- 若某步职业 `unitType` 含 `骑兵` 或 `飞行` → 显示 🐴「需要坐骑，去坐骑规划」提示，并在坐骑 Tab 高亮该角色。

### Tab 坐骑规划
- 顶部说明：**凯伊篇可捕获并饲养坐骑**；部分坐骑仅特定路线/救世篇可获取（`where` 含「救世篇」或路线名时标注）。
- 按 `type`（马/飞鸵/天马/飞龙/战象）分组卡片：`bonus / where / obtain / food / growth / skills / levels`。
- 每个坐骑：`□ 计划捕获` + `✓ 已捕获`（或 `✓ 已获得`）。
- **分配给角色**：下拉选择当前存档里「已计划/已招募」的角色（可多选标签显示），特别是骑兵/飞行路线的角色。
- 高亮提醒：列出「当前训练路线需要坐骑的角色」，方便凯伊线提前规划。

### Tab 存档
- 槽列表：名称、更新时间、当前槽标记；操作：新建、重命名、复制、删除、切换。
- 导出当前槽 / 导出全部；导入文件。
- 「清空当前槽」按钮（二次确认）。

## 质量要求
- 中文 UI。命名清晰的函数，单函数 < 50 行。
- 关键逻辑（条件解析、材料聚合、路线对比最优）必须有解释「为什么」的注释；不要给显而易见的代码加注释。
- 不吞错误：解析失败保留原文并可见。
- 无外部请求；`portrait`/`icon` 已是 dataURI 直接 `<img src>`。
- 构建后 `planner.html` 双击可离线运行。

## 验证
1. `python3 tools/build.py` 成功产出 `planner.html`。
2. 用 node 跑一段 smoke：加载 bundle.js 后确认 `FE_DATA.characters.length===63`。
3. 手动核对：选「凯伊」线 → 媞雅拉应显示「剧情加入」（1部1章）；选「赛奥朵拉」线 → 媞雅拉显示名声10/支援3/中。
