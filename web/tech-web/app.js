/* ============================================================
   Eunomia 技术导览 · 交互与内容数据（零依赖）
   ============================================================ */
"use strict";
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

/* ============================================================
   一、全景架构节点数据
   ============================================================ */
const NODES = {
  users: {
    badge: "用户层", title: "裁判 · 观众 · 组织者",
    what: "三种角色共用一个小程序：裁判横屏记分（大按钮防误触），观众看对阵图和战报，组织者建赛事、排签表（也可在 PC 管理端批量操作）。",
    why: "场景决定形态：裁判需要「大按钮 + 横屏 + 震动反馈」，观众需要「点开即看」，组织者偶尔要大屏精细操作——所以小程序为主、PC 为辅，而不是做一个响应式网站通吃所有场景。",
    files: ["frontend/src/pages.json", "docs/用例.md"],
  },
  miniapp: {
    badge: "客户端层", title: "小程序 · uni-app（Vue 3）",
    what: "用户的唯一入口，26 个页面：赛事大厅、创建赛事、横屏记分板、对阵图、小组积分、战报、签名确认。用 uni-app 框架写，一套 Vue 3 代码编译成微信小程序。⚠️ 排球的全部规则逻辑（轮转/自由人/换边/撤销）就在这一端的手机上本地运行。",
    why: "选 uni-app 而非原生小程序：保留微信入口和横屏体验的同时，用的是标准 Vue 3 语法 + Vite 工程，还能写 Vitest 单测（后面讲的千场混沌测试正是得益于此）。选 Vue 而非 Taro 的 React 语法：团队更熟 Vue。不做成 H5 网页：球场场景下小程序「即开即用」体验最好。",
    files: ["frontend/package.json", "frontend/src/utils/request.js", "frontend/src/pages/volleyball/composables/useScoreboard.js"],
  },
  adminweb: {
    badge: "客户端层", title: "PC 管理端 · Vue 3（无 UI 库）",
    what: "给组织者的大屏工具，只有 5 个页面：登录、赛事大厅、创建赛事、手写签表、分组管理。没引任何 UI 组件库，样式纯手写（含 7 套主题）。登录靠微信扫码（手机上确认），或账号密码兜底。",
    why: "5 个页面的小体量，引 Element Plus 这类组件库反而增加包体和学习成本；vue-router + 原生 fetch 已绰绰有余。它与后端复用同一套 API 和 JWT，不需要单独的账号体系。",
    files: ["web/admin-web/package.json", "web/admin-web/src/views/LoginView.vue", "docs/manuals/后台管理.md"],
  },
  productweb: {
    badge: "客户端层", title: "产品官网 · 纯静态 HTML",
    what: "介绍产品的静态网页，不调用任何 API，部署在 product 域名下。（你现在看的这个「技术导览」页，就是它的同门师妹——同样的纯静态套路。）",
    why: "官网只需要「被看到」，不需要后端逻辑——纯 HTML/CSS 意味着零成本、零崩溃风险、加载飞快。内容型页面的最优解往往就是「不用框架」。",
    files: ["web/product-web/index.html"],
  },
  techweb: {
    badge: "客户端层", title: "技术导览 · 你正在看的这一页",
    what: "本页自己：web/tech-web/ 下的纯静态站，零依赖零构建，双击即开。它已经和两个哥哥（管理端、产品官网）一起，被收进架构文档的 web/ 三前端名单——讲解系统的方式，本身就是系统的一部分。",
    why: "内容型页面用纯 HTML 就够：加载快、永不崩溃、部署只是拷文件。这套「能不加框架就不加」的审美贯穿了全部三个 web 站。",
    files: ["web/tech-web/index.html", "docs/架构.md"],
  },
  nginx: {
    badge: "接入层", title: "Nginx · 唯一的入口",
    what: "服务器的门卫：80 端口全部跳转 443（强制 HTTPS）；管理 Let's Encrypt 证书；静态站点直接吐文件；/api/* 反向代理到本机 8080 端口的 Spring Boot；把访客真实 IP 传给后端做限流依据。",
    why: "一台机器上跑着三个域名（api. / www. / product.），必须有人做流量分发。Nginx 同源反代还顺手消灭了跨域（CORS）问题——前后端看起来就是「同一家」。",
    files: ["scripts/deploy/nginx/scoring-api.conf.example"],
  },
  controller: {
    badge: "应用层", title: "Controller · 全项目只有 3 个",
    what: "AuthController（微信登录/密码登录/PC 扫码）、TournamentController（赛事全生命周期，40+ 端点）、MatchController（执裁锁/事件上报/完赛）。它们只做四件事：接请求、验参数、调 Service、按统一格式 {code, message, data} 返回。",
    why: "Controller 少 = 路由按「资源」收敛而不是按「页面」堆砌；统一响应结构让前端只需要写一种解析逻辑。业务逻辑一概不写在这里——Controller 瘦，是分层纪律的第一条。",
    files: ["backend/src/main/java/com/scoring/backend/controller/"],
  },
  service: {
    badge: "应用层", title: "Service · 业务心脏",
    what: "门面服务（TournamentServiceImpl / MatchServiceImpl 等）接住 Controller，再指挥一队协作者：MatchLockService（执裁锁）、MatchSettlementService（完赛结算，877 行）、TournamentDrawService（抽签）、PcLoginService（扫码票据状态机）……每个协作者只管一件事。",
    why: "「门面 + 协作者」模式让 40+ 端点不会挤成几个几千行的上帝类。协作者拆得足够小，单元测试才写得动——这是整个后端 300+ 测试能存在的前提。",
    files: ["backend/src/main/java/com/scoring/backend/service/"],
  },
  engine: {
    badge: "应用层", title: "Engine · 纯算法层",
    what: "三个不含一滴业务杂质的算法模块：BracketEngine（淘汰赛签表：种子蛇形排位、轮空坍缩，407 行）、RoundRobinEngine（循环赛程生成）、GroupStandingEngine（小组积分榜）。它们不依赖数据库、不依赖 Spring 框架——输入数组，输出数组。",
    why: "赛程编排本质是纯数学题。抽成纯函数后可以脱离数据库独立单测、可以暴力混沌测试，将来如果拆微服务，这里的边界是现成的。「把容易的先做纯」，架构就留了退路。",
    files: ["backend/src/main/java/com/scoring/backend/engine/BracketEngine.java"],
  },
  mapper: {
    badge: "应用层", title: "Mapper · MyBatis-Plus",
    what: "数据库的翻译官。MyBatis-Plus 自带单表 CRUD（零 SQL），复杂查询仍手写 SQL 保证可控；主键用雪花 ID 自动生成，创建/更新时间由 MetaObjectHandler 自动填充。",
    why: "对比 JPA：SQL 完全在自己手里，不会出现「框架替我生成的意外查询」；对比裸 MyBatis：省掉 80% 的模板 XML。对这种「表结构常演进、查询不复杂」的中小项目，是甜点区选择。",
    files: ["backend/pom.xml", "backend/src/main/resources/application.yml"],
  },
  mysql: {
    badge: "数据层", title: "MySQL 8 · 21 张表的真相之地",
    what: "一切业务数据的家：赛事、队伍、比赛、事件流水（match_event）、执裁锁、扫码票据……21 张表。表结构由 Flyway 管理了 29 个版本（V1 初始结构 → V26 统一时间与排序规则 → V29 数据清洗）。开发/测试环境用 H2 内存库模拟 MySQL 模式，秒级启动。",
    why: "赛事-队伍-比赛-事件是典型的强关系数据，完赛结算还要靠行锁保证一致性——关系型数据库 + InnoDB 事务是最稳的默认解。Flyway 让「代码和库结构永远配对」：新人克隆项目，启动即得正确结构。",
    files: ["backend/src/main/resources/db/migration/", "docs/数据库.md"],
  },
  infra: {
    badge: "地基", title: "一台 Ubuntu 云服务器 · systemd",
    what: "所有东西跑在一台云服务器上：后端 jar 由 systemd 托管（进程崩溃自动拉起，内存上限 1.5GB 防失控）；发布 = 上传到时间戳目录 + 软链一条命令切换，出问题秒级回滚；数据库备份有脚本兜底。",
    why: "校园赛规模下，单机的可靠性上限远高于实际需求；Docker/K8s 解决的是「很多机器、很多服务」的问题，这里一台机器一个进程，基础设施越少，出问题的面越小、凌晨被叫醒的概率越低。",
    files: ["scripts/deploy/systemd/scoring-backend.service", "scripts/deploy/deploy-backend.ps1"],
  },
};

/* ============================================================
   二、技术栈对比卡数据
   ============================================================ */
const TECH = [
  {
    icon: "📱", name: "uni-app + Vue 3", ver: "Vue 3.4 / Vite 5",
    who: "一套 Vue 3 代码，经 Vite 编译成微信小程序。页面、组件、组合式 API 全是标准 Vue 写法，还能继续用 Vitest 跑单元测试。",
    why: "既要微信小程序的入口和横屏体验，又要标准前端的工程能力（单测、npm 生态、热更新）。团队熟 Vue，所以没选 React 语系的 Taro。",
    alt: "<b>原生小程序：</b>性能上限更高，但工程化/自动化测试弱一截。<b>Taro：</b>能力相当，React 语法不合口味。<b>纯 H5：</b>省掉审核，但球场里「扫一下即用」的入口体验差。",
  },
  {
    icon: "☕", name: "Spring Boot 单体", ver: "3.3.5 / Java 17",
    who: "整个后端是一个 jar、一个进程：Controller(3) → Service(门面+协作者) → Engine(纯算法) → Mapper 四层。内嵌 Tomcat，启动即服务。",
    why: "生态最全：事务、行锁、连接池、定时任务开箱即用。强类型 + 单体架构，在「业务规则复杂、频繁改动」的场景下最不容易失控——一个 IDE 就能追完整条调用链。",
    alt: "<b>Node/Express：</b>写起来快，但复杂事务和类型约束偏弱。<b>Go：</b>并发强，可这个项目瓶颈不在并发而在业务正确性。<b>微服务：</b>解决几十个团队协作的问题——这里只有一个开发者。",
  },
  {
    icon: "🗃️", name: "MyBatis-Plus", ver: "3.5.7",
    who: "MyBatis 的增强版：单表 CRUD 一行代码不写，复杂查询仍然手写 SQL；雪花 ID、字段自动填充内置。",
    why: "在「SQL 可控」和「省样板代码」之间取了平衡。数据库操作大部分是简单读写，没必要为 20% 的复杂查询付出整个 JPA 的学习成本。",
    alt: "<b>JPA/Hibernate：</b>黑盒生成 SQL，复杂查询难以掌控。<b>裸 MyBatis：</b>全手写 XML，啰嗦。<b>手上写 JDBC：</b>只有折磨。",
  },
  {
    icon: "🗄️", name: "MySQL 8 + Flyway", ver: "Flyway V1→V29",
    who: "21 张关系表承载全部业务。Flyway 把每次表结构变更写成编号 SQL 脚本（V15__赛段规则、V20__会话锁……），后端启动时自动补跑未执行的版本。",
    why: "赛事-队伍-比赛-事件是强关系数据，完赛结算依赖事务和行锁——关系型是唯一正解。Flyway 则解决「改了 29 版结构，凭什么每台环境的库都一致」这个经典事故。",
    alt: "<b>MongoDB：</b>文档灵活，但这里关系太密、事务要求高，绕不开。<b>不用迁移工具：</b>环境漂移灾难，「我这好好的」式 bug 的头号来源。",
  },
  {
    icon: "🔑", name: "自研 JWT 鉴权", ver: "auth0 java-jwt",
    who: "登录签发 JWT → 前端每个请求带 Bearer 头 → 拦截器验签后把用户放进 ThreadLocal → 401 时前端静默重登再重发。另有限流：生产配置登录 20 次/分、写接口 60 次/分（代码默认 30/120，可按环境调整），超限一律返回 429。",
    why: "需求只有三件事：微信登录、验签、限流。几百行完全可控的代码，好过引入 Spring Security 后再花一周理解它的过滤器链。密码学本体交给成熟库，自己只做「组装」。",
    alt: "<b>Spring Security：</b>功能全（OAuth2/RBAC/SSO），配置重——等真需要那些功能时再上。<b>Session+Cookie：</b>小程序端管理 Cookie 别扭，JWT 无状态更适合多端。",
  },
  {
    icon: "🖥️", name: "Vue 3 管理端 · 零 UI 库", ver: "vue-router 4 / 原生 fetch",
    who: "5 个页面的后台，样式纯手写（含 7 套可切换主题），路由守卫控制登录态，签名逻辑与小程序端同构。",
    why: "页面这么少，引组件库是负资产：包体变大、升级要跟、样式还得覆盖。手写 CSS 反而换来 100% 的视觉控制权和极小的构建产物。",
    alt: "<b>Element Plus / Ant Design：</b>50 页以上的大后台才划算。<b>React：</b>与小程序端（Vue）技术栈统一更重要。",
  },
  {
    icon: "🐧", name: "Nginx + systemd 单机", ver: "Ubuntu 22.04 / 无 Docker",
    who: "一台云服务器承载全部：Nginx 分流三个域名并管 HTTPS；systemd 守护后端进程（崩溃自拉、内存上限）；发布用「时间戳目录 + 软链切换」，回滚就是改一条软链。",
    why: "单机可靠性上限远高于校园赛的实际负载。运维面最小 = 出问题时深夜排查的步骤最少。安全上用「一次性 SSH 密钥」，发完即毁。",
    alt: "<b>Docker：</b>环境一致性更好，但单机单项目收益有限。<b>K8s：</b>为几十个服务而生，这里是杀鸡用牛刀。<b>Serverless：</b>长连接/事务/状态都不友好。",
  },
];

/* ============================================================
   三、链路数据
   ============================================================ */
const PIPE_SCORE = [
  {
    chip: "抢执裁锁", badge: "第 1 步 · 入场",
    story: "裁判打开记分板，前端先向后端申请这一场的「执裁锁」：一个 UUID 令牌写进数据库——锁不是独立表，就是 match_record 上的三列。前端每 15 秒心跳一次，租约顺延 75 秒。其他人打开同一场比赛只能围观；同一个裁判换设备重进会顶掉旧锁，并收到 kicked=true 提示（创建者「无条件接管」的能力已被有意移除——锁面前人人平等）。",
    data: '{\n  "matchId": 123,\n  "lockToken": "9f3c41a8-…(uuid)",\n  "heartbeatSec": 15,\n  "leaseSec": 75,\n  "kicked": false\n}',
    design: "一场比赛只能有一个记分源，否则比分立刻「精神分裂」。用「数据库租约 + 心跳」而不是分布式锁——单机 MySQL 的条件更新本身就是最可靠的锁。",
    files: ["frontend/src/utils/match-lock.js", "backend/…/service/match/MatchLockService.java"],
  },
  {
    chip: "本地状态机计分", badge: "第 2 步 · 本地推演",
    story: "得分、换人、撤销这些动作不直接发请求——手机本地用纯函数状态机即时推演：轮转位次、自由人规则、换边、最多 40 步的撤销栈，全程零延迟。状态快照存进本地 Storage，断网、杀进程都能恢复现场。",
    data: '{\n  "sets": [{ "a": 21, "b": 19 }],\n  "serving": "A",\n  "rotation": 3,\n  "undoStack": 40,\n  "syncStatus": "pending"\n}',
    design: "规则计算放在离用户最近的地方：响应快、能离线、服务器最省。代价是后端信任了前端的事件流——这笔账在「取舍与演进」章细算。",
    files: ["frontend/src/pages/volleyball/composables/useScoreboard.js", "frontend/src/pages/volleyball/match-state.js"],
  },
  {
    chip: "事件流水上报", badge: "第 3 步 · 记账",
    story: "状态机把每个动作记成「事件流水」，批量上报 PUT /matches/{id}/events，后端原样存进 match_event 表——它不判断这球该不该得分、轮转对不对，只负责忠实记账。",
    data: '[\n  { "seq": 1, "type": "SCORE", "side": "A" },\n  { "seq": 2, "type": "SUBSTITUTE",\n    "team": "B", "out": 7, "in": 12 }\n]',
    design: "「前端算规则，后端存流水」让两端职责都极简；流水永远在，将来任何时候都能重放、审计、追责。",
    files: ["backend/…/controller/MatchController.java", "db/migration/V4__比赛事件"],
  },
  {
    chip: "完赛", badge: "第 4 步 · 终场哨",
    story: "比赛结束，前端发 PUT /matches/{id}/finish。这一发，就触发了全项目最重要的一段事务代码——完赛结算（往下翻，真实代码时刻②就是它）。",
    data: '{\n  "matchId": 123,\n  "winner": "A",\n  "score": "2:1",\n  "sets": [ … ]\n}',
    design: "「完赛」是状态跃迁的原子起点：在它之前比分随便改，在它之后晋级链开始滚动——必须一刀切干净，不允许半完成状态存在。",
    files: ["backend/…/controller/MatchController.java"],
  },
  {
    chip: "结算与晋级传播", badge: "第 5 步 · 多米诺",
    story: "行锁保护下，一个数据库事务里连做四件事：写比分 → 把赢家写进下一轮对阵的空位 → 团体赛父场试结算（子场全踢完才算父场）→ 若全部组别完赛则赛事完赛。战报同时装配成 JSON。",
    data: '{\n  "nextMatchId": 456,\n  "slot": "B",\n  "fromMatch": 123,\n  "parentMatch": { "winsA": 2, "winsB": 1 }\n}',
    design: "晋级是连锁反应，任何一步只落库一半，就会出现「赢了却没进下一轮」的事故——所以必须单事务 + 行锁：要么全发生，要么全没发生。",
    files: ["backend/…/service/match/MatchSettlementService.java"],
  },
  {
    chip: "观众拉取", badge: "第 6 步 · 被看见",
    story: "观众打开对阵图或战报页，页面每次回到前台（onShow）就 GET 一次最新数据。没有推送、没有长连接——所谓「实时」，就是「你每次看，都给你最新的」。",
    data: 'GET /tournaments/88/bracket\n→ {\n  "rounds": [\n    { "name": "半决赛", "matches": [ … ] }\n  ]\n}',
    design: "校园赛观众是「看一眼就走」的节奏，拉模式实现最简单、服务器零长连接负担。等要做现场大屏秒级刷新时，再升级 WebSocket——路径在取舍章。",
    files: ["frontend/src/pages/tournament/bracket.vue", "frontend/src/pages/tournament/detail.vue"],
  },
];

const PIPE_TOURNEY = [
  {
    chip: "创建赛事", badge: "第 1 步",
    story: "组织者选运动（羽毛球/排球）和赛制形态，后端 TournamentCreationFactory 按模板生成赛事骨架：组别、比赛形态、初始状态。",
    data: 'POST /tournaments\n{\n  "sport": "badminton",\n  "format": "team_knockout",\n  "divisions": [ … ]\n}',
    design: "用「工厂」而不是 if-else：三种运动的差异（人数、赛制、计分单位）全部收进模板——将来新增运动 = 新增一个工厂，别处不动。",
    files: ["backend/…/service/tournament/TournamentCreationFactory.java"],
  },
  {
    chip: "录入队伍", badge: "第 2 步",
    story: "创建者手动录入队伍与队员（注意：没有自助报名系统——为什么？见下方洞察框）。队员可以后补、可以随时编辑。",
    data: 'PUT /tournaments/88/teams/12\n{\n  "name": "计算机学院一队",\n  "members": ["张三", "李四"]\n}',
    design: "校园赛名单本来就躺在微信群里，人工整理一步到位；省掉报名审核、退出、资格校验的整套复杂度。",
    files: ["frontend/src/pkg-manage/teams.vue", "backend/…/service/tournament/TournamentTeamEditService.java"],
  },
  {
    chip: "抽签编排", badge: "第 3 步",
    story: "一键抽签决定谁打谁；算法排得不合心意？PC 管理端还有「手写签表」模式，逐格拖拽调整对阵位置。",
    data: 'PUT …/divisions/3/draw-slots\n{\n  "slots": [\n    { "position": 1, "teamId": 12 }, …\n  ]\n}',
    design: "自动 + 手动双通道：算法给起点，人保留最终裁量权——工具不该比裁判更懂「这两个队不能首轮相遇」。手动精修有专门的守卫纪律，往下看「手写签表」专题链路。",
    files: ["backend/…/service/tournament/TournamentDrawService.java", "web/admin-web/src/views/DrawSlotsView.vue"],
  },
  {
    chip: "循环赛程生成", badge: "第 4 步",
    story: "RoundRobinEngine 生成组内循环赛程——每两队都恰好碰面一次，自动切分轮次，生成一场场待打的比赛记录。",
    data: '{\n  "rounds": 5,\n  "matches": 20,\n  "algorithm": "round-robin"\n}',
    design: "纯算法不碰数据库：输入队伍列表，输出赛程数组。好处是可以脱离环境独立单测，算法错了不会污染数据。",
    files: ["backend/…/engine/RoundRobinEngine.java"],
  },
  {
    chip: "小组积分榜", badge: "第 5 步",
    story: "每场小分进账后，GroupStandingEngine 实时计算积分榜：胜场、局分、净胜分，并按出线规则排出晋级名单。",
    data: '{\n  "standings": [\n    { "team": "计算机一队", "played": 4,\n      "won": 4, "setsDiff": +7 }\n  ]\n}',
    design: "积分规则做成可配置的排名模板——不同赛事「几出线、怎么排名次」各不相同，但都不用改代码。",
    files: ["backend/…/engine/ranking/GroupStandingEngine.java", "backend/…/engine/RankingConfig.java"],
  },
  {
    chip: "淘汰赛排位", badge: "第 6 步",
    story: "出线队伍交给 BracketEngine：蛇形排位让同组两队尽量分守上下半区（避免提前内战），轮空坍缩自动处理人数不足 2 的幂的尴尬。",
    data: '{\n  "seeding": "snake",\n  "byes": 3,\n  "round1": "A1 vs B2 / B1 vs A2 …"\n}',
    design: "「蛇形排位 + 轮空坍缩」是体育赛事的百年惯例。惯例沉淀成 407 行纯算法，界面只负责画——领域知识放进代码里，而不是某个运营的脑子里。",
    files: ["backend/…/engine/BracketEngine.java"],
  },
  {
    chip: "开赛 → 交给记分链路", badge: "第 7 步",
    story: "淘汰赛对阵全部生成，比赛记录就绪。此后每一场比赛都进入第三章的记分链路：抢锁 → 记分 → 完赛 → 晋级传播，层层滚动，直到决赛见分晓。",
    data: '{\n  "tournamentStatus": "ongoing",\n  "nextStage": "score"\n}',
    design: "编排链路的终点就是记分链路的起点——两条链路首尾相接，构成「建赛 → 打赛 → 出成绩」的完整闭环。",
    files: ["backend/…/service/match/MatchSettlementService.java"],
  },
];

const PIPE_LOGIN = [
  {
    chip: "① 点「微信一键登录」",
    story: "小程序调用 uni.login()，微信 SDK 返回一个一次性 code。此刻微信已经在它那边确认过「你是谁」。",
    data: '{ "code": "0a3Xx9Fa…（5 分钟有效，用一次就废）" }',
    design: "code 是微信给的一次性凭证，真正的身份（openid）必须由后端去换——密钥永远不出服务器。",
  },
  {
    chip: "② 后端拿 code 换 openid",
    story: "后端带着 code + AppSecret 调微信官方接口 jscode2session，换回 openid——这个用户在本小程序里的唯一身份证号。",
    data: 'POST https://api.weixin.qq.com/sns/jscode2session\n→ { "openid": "oXyz789…" }',
    design: "AppSecret 只存在服务器环境变量里，手机端永远拿不到。这是微信登录安全的基石：手机端只有 code，换身份必须过服务器。",
  },
  {
    chip: "③ 签发 JWT",
    story: "后端查库（老用户）或建库（新用户），然后签发一枚 JWT——一串自带签名的身份令牌——返回给小程序，小程序存进本地 Storage。",
    data: '{ "token": "eyJhbGciOiJIUzI1NiJ9…",\n  "expiresIn": 7200 }',
    design: "JWT 自带签名，后端不需要存 session 表就能验明正身，天然适合「小程序 + PC」多端共用一套鉴权。",
  },
  {
    chip: "④ 之后每个请求自动带令牌",
    story: "网络层统一封装在 request.js：每次请求自动加 Authorization: Bearer 头；一旦收到 401，静默重新登录一次再重发原请求——用户全程无感。",
    data: 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9…',
    design: "把带令牌、处理过期这类「新手最容易忘的事」收进统一入口，用结构性设计消灭遗漏，而不是靠每个人自觉。",
    files: ["frontend/src/utils/request.js", "frontend/src/store/auth.js"],
  },
  {
    chip: "⑤ 后端拦截器验明正身",
    story: "拦截器在业务代码运行前验证签名和有效期，把用户 ID 放进 ThreadLocal——之后任何业务代码随取随用。旁边还有限流：登录 20 次/分、写接口 60 次/分。",
    data: 'AuthContext.getUserId()  →  10086',
    design: "鉴权在「门口」统一做完，业务代码零侵入；限流则挡住脚本恶意刷接口——超限返回 429（代码默认登录 30 次/分、写 120 次/分，生产收紧为 20/60）。三行拦截器代码见本章「真实代码时刻③」。",
    files: ["backend/…/security/AuthInterceptor.java", "backend/…/common/RequestRateLimiter.java"],
  },
];

const PIPE_PC = [
  {
    chip: "① PC 点「扫码登录」",
    story: "PC 管理端请求后端生成一个登录票据 ticket，再拿 ticket 换一张真正的「小程序码」显示在屏幕上。票据入库，状态 CREATED，出码起 3 分钟内有效。",
    data: '{ "ticket": "t-9f3c41a8…",\n  "status": "CREATED",\n  "expire": "+3min" }',
    design: "屏幕上二维码的背后是一次性票据，不是链接本身——只有「票据」才有状态，才能被手机「确认」。",
  },
  {
    chip: "② PC 开始轮询",
    story: "PC 端每 1.5 秒问一次后端：「这张票到什么状态了？」——普通 HTTP 轮询，没有长连接。",
    data: 'GET /auth/pc/status?ticket=t-9f3c41a8…\n→ { "status": "CREATED" }',
    design: "1.5 秒一次的普通轮询对登录场景绰绰有余——不值得为「扫码后少等一秒」引入长连接。",
    files: ["web/admin-web/src/views/LoginView.vue"],
  },
  {
    chip: "③ 微信扫码 → 上报「已扫」",
    story: "用微信扫码，直接落进小程序的确认页（ticket 已带在参数里）。小程序随即回手调 POST /auth/pc/scan，向服务器上报「有人扫了这张票」。",
    data: 'POST /auth/pc/scan\n{ "ticket": "t-9f3c41a8…" }',
    design: "「扫」和「认」是两个独立动作、两个独立端点——微信的交互习惯里，扫了不代表认了。",
  },
  {
    chip: "④ 票据状态：CREATED → SCANNED",
    story: "后端执行条件更新：UPDATE … SET status='SCANNED' WHERE ticket=? AND status='CREATED'。如果同时有两个人扫码，数据库保证只有一个 UPDATE 生效。",
    data: '{ "rowsAffected": 1,\n  "status": "CREATED → SCANNED" }',
    design: "状态机的每一次转移都是「条件更新」：数据库层面的原子操作。并发、重复、过期，全部被一条 SQL 的 WHERE 挡住。",
  },
  {
    chip: "⑤ 手机点「确认登录」→ CONFIRMED",
    story: "用户在手机上看到「PC 登录请求」，点确认。后端把票据置为 CONFIRMED，并绑定该用户 ID。",
    data: '{ "status": "SCANNED → CONFIRMED",\n  "userId": 10086 }',
    design: "确认动作发生在已登录的手机上——「拿着手机的人」向服务器证明「坐在 PC 前的人」是他本人。这就是扫码登录的全部信任逻辑。",
  },
  {
    chip: "⑥ PC 取走令牌 → CONSUMED",
    story: "PC 的轮询终于等到 CONFIRMED，取走一枚 JWT——票据随之终结于 CONSUMED。令牌一次性下发，取走即作废，防重放。",
    data: '{ "status": "CONFIRMED → CONSUMED",\n  "token": "eyJhbGciOiJIUzI1NiJ9…" }',
    design: "CREATED → SCANNED → CONFIRMED → CONSUMED 四态、单向、不可跳步；EXPIRED 是虚拟态（比对过期时间即可，不落库）。每一步都有服务器记账，客户端说的不算。",
    files: ["backend/…/service/auth/PcLoginService.java"],
  },
  {
    chip: "⑦ 登录成功",
    story: "PC 进入管理端。这枚 JWT 和手机端的完全同构——后端一套拦截器管两端，没有为 PC 另起炉灶。",
    data: '{ "user": { "name": "李金" },\n  "role": "creator" }',
    design: "复用，而不是重写。整套扫码登录没有引入任何新框架：一张表（web_login_session）、一个服务类（205 行）、四个端点。",
    files: ["backend/…/service/auth/PcLoginService.java", "db/migration/V22__add_web_login_session.sql"],
  },
];

/* 专题链路 ①：手写签表 / 手写分组 */
const PIPE_DRAW = [
  {
    chip: "进入手写模式", badge: "手写签表 · 第 1 步",
    story: "组别的抽签模式 draw_mode 决定走哪条路：0 自动抽签、1 手写签表（V27，线下抽完录入）、2 手写分组（V28，先手动分队再自动排组内赛程）。组织者在 PC 端从大厅进入对应页面。",
    data: '{\n  "divisionId": "88D01",\n  "drawMode": 1   // 1 = 手写签表\n}',
    design: "抽签模式是组别级配置，不是赛事级——同一赛事的甲组可以自动抽、乙组可以手写。",
    files: ["web/admin-web/src/views/DrawSlotsView.vue", "web/admin-web/src/views/GroupAssignmentsView.vue"],
  },
  {
    chip: "双闸：前端判定 + 后端痕迹守卫", badge: "手写签表 · 第 2 步",
    story: "保存前要过两道闸：前端先做零开赛判定（体验优化），后端再做「痕迹法守卫」（唯一权威）——只认比分、局分、退赛标记、执裁锁、事件流水这些真实开赛痕迹；winner_id 和 status 不算数，因为轮空坍缩在创建签表时就会写入这两个字段。",
    data: '痕迹清单：\n  game_scores / score_display /\n  game_wins(非NULL) / retired_side /\n  lock_* / match_event / lineup_config …\n（winner_id、status 不在内）',
    design: "「能不能改」必须只有一个权威判定者。前端判定只管少弹一次报错，最终裁决永远是后端。",
    files: ["backend/…/service/tournament/TournamentDrawService.java"],
  },
  {
    chip: "保存签位：全量替换、原地更新", badge: "手写签表 · 第 3 步",
    story: "手写签表的保存是全量替换首轮签位、原地 UPDATE：比赛的 id 不变、晋级链不变，然后重放一次轮空坍缩处理人数缺口。",
    data: 'PUT …/draw-slots\n{\n  "slots": [\n    { "position": 1, "teamId": 12 },\n    { "position": 2, "teamId": 7 } …\n  ]\n}',
    design: "保 match id 稳定 = 已生成的对阵引用、外部分享链接统统不失效。「微调」就要有微调的样子。",
  },
  {
    chip: "保存分组：全删全建", badge: "手写分组 · 第 4 步",
    story: "手写分组正好相反：先重写队伍的组号/组内序，再把小组赛赛程全删全建——比赛的 id 会变。这是刻意为之的两种策略。",
    data: 'PUT …/group-assignments\n{\n  "groups": [\n    { "groupNo": 1, "teamIds": [12, 7, 3] }\n  ]\n}',
    design: "分组改动是「重排」而非「微调」——赛程整个重建反而最干净、最不容易残留半新半旧的数据。两种语义，边界清楚。",
  },
  {
    chip: "幂等与事务纪律", badge: "手写签表 · 第 5 步",
    story: "提交内容没变就直接返回（幂等）；真正的保存跑在显式事务里，锁序固定为先 match_record 后 division，避免并发死锁。重开比赛后，痕迹被清掉，编辑窗口重新打开。",
    data: 'if (内容未变) return 现状;   // 幂等\nBEGIN;                        // 显式事务\n  锁 match_record → 锁 division\n  …重排…\nCOMMIT;',
    design: "幂等保护的是「手抖多点两次」，事务保护的是「改一半崩了」——两种故障，两种防护。",
  },
  {
    chip: "同组回避：自动硬约束，手动有意放开", badge: "手写签表 · 第 6 步",
    story: "自动抽签时「同组两队不首轮相遇」是硬约束——实在排不开直接报错，唯一例外是单组出线者。手动模式下这条约束有意放开：人工排表时，人自己权衡。",
    data: '自动：Constraint failed\n  → IllegalArgumentException（创建被拒）\n手动：允许同组首轮相遇\n  → 责任转移给人',
    design: "工具不该比裁判更懂球——但工具要守住自动路径的公平底线。约束的严格程度跟着「谁在做决定」变。",
    files: ["backend/…/engine/BracketEngine.java"],
  },
];

/* 专题链路 ②：分段规则解析链 */
const PIPE_RULE = [
  {
    chip: "创建时的覆盖性校验", badge: "分段规则 · 第 1 步",
    story: "创建赛事时允许按「组别 + 阶段 + 轮次」携带分段规则（比如「半决赛决胜局打 15 分」）。校验铁律叫覆盖性：roundRules 必须与声明的作用域一一对应——缺失、多出、重复，整单拒绝。",
    data: 'POST /tournaments\n{\n  "roundRuleEnabled": true,\n  "roundRules": [\n    { "stage": 1, "round": 2,\n      "decidingPointsToWin": 15 }\n  ]\n}',
    design: "规则写一次不可修改——所以配置错误必须在创建那一刻拦下，而不是比赛打到半决赛才发现。",
    files: ["backend/…/service/tournament/TournamentCreationFactory.java"],
  },
  {
    chip: "落库：一行一条作用域", badge: "分段规则 · 第 2 步",
    story: "规则落进 tournament_round_rule 表，唯一键 (division_id, stage_type, round_num)。V15 建表时挂在赛事级，V23 组别化改造后彻底归组别管。",
    data: '{\n  "division_id": "88D01",\n  "stage_type": 1,     // 1 = 淘汰赛\n  "round_num": 2,      // 第 2 轮（半决赛）\n  "rules_json": "…"\n}',
    design: "规则不在代码里，在配置表里——换规则不改代码、不发版。",
    files: ["db/migration/V15", "db/migration/V23"],
  },
  {
    chip: "开赛前解析：Resolver", badge: "分段规则 · 第 3 步",
    story: "每场比赛开赛前，TournamentRuleResolver（110 行小类）按比赛所属组别解析出这套比赛最终生效的规则对象 MatchRuleConfig。",
    data: 'TournamentRuleResolver.resolve(match)\n→ MatchRuleConfig {\n  bestOf, gamesToWin,\n  pointsToWin, decidingPointsToWin …\n}',
    design: "解析逻辑集中在一个 110 行的纯读小类里——「这套比赛到底按什么规则打」永远只有一个出处。",
    files: ["backend/…/service/impl/TournamentRuleResolver.java"],
  },
  {
    chip: "特例优先：季军赛", badge: "分段规则 · 第 4 步",
    story: "解析顺序里有个特例：季军赛（match_role=1）不走轮次匹配，直接用组别上预留的季军赛规则快照（third_place_*）；团体赛的季军子场会先定位到父场再取。",
    data: 'resolve 顺序：\n  ① 数据兜底 → ② 季军赛快照\n  ③ 分轮覆盖 → ④ 组别默认',
    design: "季军赛经常想跟决赛打不同的局制——单独快照，不靠轮号兜；特例显式建模，而不是藏在魔法数字里。",
  },
  {
    chip: "分轮命中 → 覆盖；未命中 → 默认", badge: "分段规则 · 第 5 步",
    story: "普通比赛的查法：小组赛查 (0, 0)，淘汰赛查 (1, roundNum)。查到就用覆盖规则，查不到就回落到组别默认规则——这就是「半决赛 15 分、决赛 21 分」能共存的机制。",
    data: 'uk_round_rule_division:\n  (88D01, 1, 2) → 决胜局 15 分\n  (其余轮次)    → 组别默认 21 分',
    design: "覆盖 + 默认的双层结构，让「例外」只声明例外本身，不用复制整套规则。",
    files: ["db/migration/V15", "docs/业务规则.md §15"],
  },
  {
    chip: "关闭形态：总开关一关全忽略", badge: "分段规则 · 第 6 步",
    story: "组别的 roundRuleEnabled=false 时进入关闭形态：roundRules 即使存在也被整体忽略，一律用组别默认规则。",
    data: '{\n  "roundRuleEnabled": false,\n  "roundRules": [ … ]  // 被忽略\n}',
    design: "给配置一个明确的「停用」开关，比让人把规则一条条删掉友好得多——数据留着，随时能再开。",
  },
];

/* ============================================================
   四、自测题数据
   ============================================================ */
const QUIZ = [
  {
    q: "观众在手机上看到的「最新比分」，是服务器主动推给他的吗？",
    opts: ["是，用了 WebSocket 长连接", "不是——页面回到前台时重新拉取一次", "是，靠微信服务通知", "不是，要等管理员手动发布战报"],
    a: 1,
    explain: "全项目没有 WebSocket。「实时」= onShow 时拉一次最新数据（拉模式）。对校园赛场景完全够用，什么时候必须升级见第七章。",
    back: ["#tradeoffs", "回到「取舍与演进」"],
  },
  {
    q: "排球的轮转、自由人、换边这些复杂规则，是在哪台机器上计算的？",
    opts: ["后端服务器", "微信服务器", "裁判的手机（小程序端本地状态机）", "MySQL 存储过程"],
    a: 2,
    explain: "规则状态机整体放在前端本地推演：零延迟、可离线、服务器最省；后端只存事件流水不做校验。这是全项目最大胆的架构决策。",
    back: ["#pipe-score", "回到「记分核心链路」"],
  },
  {
    q: "两台手机同时想给同一场比赛记分，系统靠什么防止比分错乱？",
    opts: ["后到者直接报错闪退", "执裁锁：lockToken 租约 + 心跳续约", "弹出验证码比拼手速", "分布式事务协调器"],
    a: 1,
    explain: "每场比赛一把「执裁锁」：数据库租约 + 定时心跳，第二个人只能围观。用的是单机数据库条件更新，不需要花哨的分布式组件。",
    back: ["#pipe-score", "回到「记分核心链路」第 1 步"],
  },
  {
    q: "淘汰赛某场打完，下一轮的对阵是什么时候出现的？",
    opts: ["裁判手动把赢家填进下一轮", "每天凌晨定时任务统一计算", "完赛事务里自动「晋级传播」", "观众刷新时随机生成"],
    a: 2,
    explain: "完赛结算在同一个数据库事务里完成：写比分 → 赢家写入下一轮空位 → 父场试结算。行锁保证并发安全，事务保证要么全发生、要么全没发生。",
    back: ["#pipe-score", "回到「记分核心链路」第 5 步"],
  },
  {
    q: "PC 扫码登录时，二维码显示在屏幕上但手机还没扫，此时票据处于什么状态？",
    opts: ["CONFIRMED", "SCANNED", "EXPIRED", "CREATED"],
    a: 3,
    explain: "票据状态机：CREATED（已出码）→ SCANNED（手机已扫）→ CONFIRMED（手机已确认）。每次转移都是一条条件 UPDATE，原子且不可跳步。",
    back: ["#auth", "回到「登录鉴权」"],
  },
  {
    q: "数据库表结构改了 29 版，靠什么保证每台环境的结构都一致？",
    opts: ["每次手动改，然后口口相传", "Flyway：编号迁移脚本启动时自动执行", "每月全量导出再导入", "在微信群里发 SQL 文件"],
    a: 1,
    explain: "Flyway 相当于「数据库结构的 Git」：V1 到 V29 每版一个编号 SQL，后端启动时自动补跑——代码和库结构永远配对。",
    back: ["#engineering", "回到「工程化三件套」"],
  },
  {
    q: "小程序请求后端时收到 401（令牌过期），用户会经历什么？",
    opts: ["页面闪退，需重新打开", "被踢出到微信首页", "前端静默重新登录一次，然后自动重发刚才的请求，用户无感", "要求手动输入账号密码"],
    a: 2,
    explain: "request.js 统一封装了 401 处理：静默重登（带防循环保护）→ 重发原请求。用户根本不知道发生过什么。",
    back: ["#auth", "回到「登录鉴权」"],
  },
  {
    q: "这个项目的部署方式是？",
    opts: ["Docker Compose 编排", "Kubernetes 集群", "jar + systemd + 软链发布，单机 Ubuntu", "各大云厂商 Serverless 函数"],
    a: 2,
    explain: "一台 Ubuntu：jar 由 systemd 托管（崩溃自拉、内存上限），发布 = 新目录 + 软链切换 + 秒回滚。规模决定架构，单机足够就不上容器。",
    back: ["#engineering", "回到「工程化三件套」"],
  },
  {
    q: "保存手写签表时，后端凭什么判断「这份签表还能不能重排」？",
    opts: ["看比赛 status 是否为「未开始」", "看有没有 winner_id", "痕迹法守卫：只认比分、局分、退赛标记、执裁锁、事件流水等真实开赛痕迹", "看组别是否处于小组赛阶段"],
    a: 2,
    explain: "winner_id 和 status 不算开赛信号——轮空坍缩在创建签表时就会写入这两个字段，当初按它们判定导致「带轮空的签表永远没法重排」。现在「开赛与否」只由后端痕迹守卫说了算。",
    back: ["#pipe-tournament", "回到「手写签表」专题链路"],
  },
  {
    q: "裁判在排球记分板点了「撤销」，之前已上报的换人/暂停事件怎么处理？",
    opts: ["后端物理删除那条事件", "前端把整条流水重传一遍", "不删除——追加一条撤销水位快照（reason=undo），把高水位的相关事件标记为已撤销", "不处理，战报自动忽略所有换人记录"],
    a: 2,
    explain: "流水账不撕页：撤销以 revertToSeq 水位追加 score_snapshot(reason=undo)，记录页与战报不再显示「幽灵事件」，但审计时仍能完整还原「发生了什么、后来撤了什么」。",
    back: ["#pipe-score", "回到「记分核心链路」"],
  },
];

/* ============================================================
   七、类与数据地图数据
   ============================================================ */
const CLASSES = {
  "controller-match": {
    name: "MatchController", layer: "Controller", layerClass: "chip-controller", lines: "164 行",
    desc: "比赛全生命周期端点的接待员：抢锁、心跳、事件、完赛、重开、战报封签。",
    badge: "Controller 层", story: "接待 /api/v1/matches 下比赛的所有端点：抢锁/心跳/释放、事件上报、完赛、重开、战报封签、团体赛子场。只做四件事：接请求、验参数、调门面、按 {code, message, data} 回包——不写一行业务逻辑。",
    why: "Controller 瘦身是分层纪律第一条。全后端只有 3 个 Controller，路由按「资源」收敛而不是按「页面」堆砌。",
    design: "上游：前端 HTTP 请求<br>下游：MatchServiceImpl（门面）、AuthGuard（鉴权）",
    files: ["backend/…/controller/MatchController.java"],
  },
  "facade": {
    name: "MatchServiceImpl", layer: "Service 门面", layerClass: "chip-service", lines: "913 行",
    desc: "比赛用例的编排入口，也是「谁该干这件事」的分诊台。",
    badge: "Service 门面", story: "锁、详情、事件、阵容、战报的用例编排入口。finish / restart / settle 全部直通转调 MatchSettlementService——门面自己不结算，只负责找到对的协作者。",
    why: "门面 + 协作者模式让 40+ 端点不会挤成上帝类；每个协作者足够小，300+ 单元测试才写得动。",
    design: "上游：MatchController<br>下游：MatchSettlementService、MatchLockService、MatchAccessGuard、MatchReportAssembler、MatchDetailAssembler、TournamentRuleResolver",
    files: ["backend/…/service/impl/MatchServiceImpl.java"],
  },
  "settlement": {
    name: "MatchSettlementService", layer: "Service · 心脏", layerClass: "chip-service", lines: "877 行",
    desc: "完赛结算核心：比分、晋级传播、组别聚合，全在行锁事务里。",
    badge: "Service · 状态机红线", story: "完赛结算核心：比分落定 → 胜者晋级槽位传播（propagateFinishedMatch）→ 三四名败者传播（propagateLoserIfNeeded）→ 组别完赛聚合（finishDivisionIfReady）→ 重开时清下游。全部在 @Transactional + FOR UPDATE 行锁内。",
    why: "类注释里的「状态机红线」就是它：任何一次晋级/聚合的并发错判，都是「赢了却没进下一轮」级别的事故。行锁 + 单事务保证要么全发生、要么全没发生。",
    design: "上游：MatchServiceImpl<br>下游：TournamentRuleResolver、MatchReportAssembler、MatchLockService、各 Mapper",
    files: ["backend/…/service/match/MatchSettlementService.java"],
  },
  "lock": {
    name: "MatchLockService", layer: "Service", layerClass: "chip-service", lines: "171 行",
    desc: "75 秒执裁锁的获取/续期/释放，条件 UPDATE 原子转移。",
    badge: "Service · 并发守门", story: "执裁锁全生命周期：获取、心跳续期（MATCH_LOCK_SECONDS = 75 秒）、释放、校验。所有状态转移都是条件 UPDATE——数据库层面保证同一时刻只有一个人持锁。",
    why: "锁不是独立表，就是 match_record 上的三列（见本章数据库地图）。为一把锁建独立表？单机场景不需要。",
    design: "上游：MatchServiceImpl、MatchSettlementService<br>下游：MatchRecordMapper（条件 UPDATE）、MatchAccessGuard",
    files: ["backend/…/service/match/MatchLockService.java", "db/migration/V20__add_match_session_lock.sql"],
  },
  "report": {
    name: "MatchReportAssembler", layer: "Service", layerClass: "chip-service", lines: "258 行",
    desc: "战报元数据：草稿保证、完整性校验、封签。",
    badge: "Service · 成绩单", story: "战报元数据的草稿保证、完整性校验、封签校验与 metaJson 构建——1 场比赛对应 1 份战报，封签后不可再改。",
    why: "战报是赛事的官方成绩单，签名页就盖在它上面。可信赖的出处，来自「封签后不可变」的纪律。",
    design: "上游：MatchServiceImpl、MatchSettlementService<br>下游：MatchReportMetaMapper",
    files: ["backend/…/service/match/MatchReportAssembler.java"],
  },
  "guard": {
    name: "MatchAccessGuard", layer: "Guard", layerClass: "chip-security", lines: "82 行",
    desc: "比赛操作权限判定：创建者或授权裁判才放行。",
    badge: "权限守卫", story: "比赛操作/可读/战报的权限判定：是赛事创建者，还是被授权的裁判（tournament_referee_grant），否则一律拒绝。",
    why: "权限判定集中一处，而不是散落在每个方法里的 if——改规则只改一个地方。",
    design: "上游：MatchServiceImpl、MatchSettlementService、MatchLockService<br>下游：TournamentMapper、TournamentRefereeGrantMapper",
    files: ["backend/…/service/match/MatchAccessGuard.java"],
  },
  "mapper": {
    name: "Mapper 层", layer: "数据访问", layerClass: "chip-service", lines: "19 个",
    desc: "MyBatis-Plus 翻译官：单表 CRUD 零 SQL，复杂查询手写。",
    badge: "数据访问层", story: "每个表对应一个 Mapper 接口：MatchRecordMapper、MatchEventMapper……单表 CRUD 由 MyBatis-Plus 自动生成，复杂查询手写 SQL 保证可控。雪花 ID 与时间自动填充在框架回调层全局生效。",
    why: "SQL 完全在自己手里 + 省掉 80% 模板代码——对「表结构常演进、查询不复杂」的项目是甜点区。",
    design: "上游：各 Service / Engine<br>下游：MySQL 8",
    files: ["backend/…/mapper/", "backend/pom.xml"],
  },
  "mysql-node": {
    name: "MySQL 8", layer: "数据终点", layerClass: "chip-engine", lines: "21 张表",
    desc: "一切业务数据的终点与真相之地。",
    badge: "数据层", story: "所有链路的终点：比赛、事件、锁、票据、配置……21 张表由 Flyway 管着 29 版结构变更。完赛结算的行锁、执裁锁的条件更新、扫码票据的状态机，全部依赖它的事务能力。",
    why: "强关系 + 强事务的数据，关系型数据库是最稳的默认解。它长什么样，直接看本章下半场的「数据库地图」。",
    design: "上游：Mapper 层<br>结构变更：Flyway V1 → V29",
    files: ["docs/数据库.md", "backend/src/main/resources/db/migration/"],
  },
  "auth3": {
    name: "鉴权三小件", layer: "Security", layerClass: "chip-security", lines: "49+21+16 行",
    desc: "拦截器 → ThreadLocal → 守卫，三件合计不到 90 行。",
    badge: "安全链 · 三件套", story: "AuthInterceptor（preHandle 验 JWT，把用户写进 ThreadLocal，afterCompletion 清理）→ AuthContext（ThreadLocal 存取，21 行）→ AuthGuard（requireUserId()：取不到就抛 401，16 行）。业务代码随取随用，零侵入。",
    why: "三个类加起来不到 90 行，撑起全后端鉴权。「自己组装成熟轮子」和「自己发明密码学」是两回事——这里显然是前者。",
    design: "写入：AuthInterceptor（每个请求）<br>读取：三个 Controller 经 AuthGuard<br>限流拍档：RequestRateLimiter（52 行）",
    files: ["backend/…/security/AuthInterceptor.java", "backend/…/security/AuthContext.java", "backend/…/security/AuthGuard.java"],
  },
  "factory": {
    name: "TournamentCreationFactory", layer: "Service", layerClass: "chip-service", lines: "1479 行",
    desc: "建赛一条龙工厂，全项目最大的 Java 文件。",
    badge: "Service · 建赛工厂", story: "建赛一条龙：落库赛事 + 组别 + 选手 + 队员 + 子项配置，再按赛制调 BracketEngine 或 RoundRobinEngine 生成首轮对阵，并把规则列镜像写回赛事表作回滚保险。1479 行，全项目最大的 Java 文件。",
    why: "三种运动形态的差异（人数/赛制/计分单位）全部收进工厂模板——新增一种运动 = 新增一个工厂，其余代码不动。",
    design: "上游：TournamentServiceImpl<br>下游：BracketEngine、RoundRobinEngine、TournamentRankingService + 9 个 Mapper",
    files: ["backend/…/service/tournament/TournamentCreationFactory.java"],
  },
  "bracket": {
    name: "BracketEngine", layer: "Engine", layerClass: "chip-engine", lines: "407 行",
    desc: "淘汰赛排位引擎：蛇形种子、轮空坍缩、晋级关系。",
    badge: "Engine · 纯算法", story: "淘汰赛编排除法引擎：种子蛇形排位（同组两队分守上下半区）、轮空坍缩（人数不足 2 的幂时自动 bye）、晋级传播关系计算。输入数组、输出数组，不碰数据库、不依赖 Spring。",
    why: "「蛇形排位 + 轮空坍缩」是体育赛事的百年惯例，沉淀成纯函数后可以脱库单测、可以混沌测试——领域知识进了代码，而不是某个运营的脑子里。",
    design: "上游：TournamentCreationFactory、TournamentDrawService<br>下游：无（产出 MatchRecord 数据交给调用方落库）",
    files: ["backend/…/engine/BracketEngine.java"],
  },
  "roundrobin": {
    name: "RoundRobinEngine", layer: "Engine", layerClass: "chip-engine", lines: "177 行",
    desc: "循环赛圆周旋转法：每两队恰好碰面一次。",
    badge: "Engine · 纯算法", story: "循环赛程生成：圆周旋转法（circle method），支持分组/联赛、单双循环，保证每两队恰好碰面一次，自动切分轮次。",
    why: "177 行解决一个纯数学问题——小而美的典范。和 BracketEngine 一样不碰库，错了也不会污染数据。",
    design: "上游：TournamentCreationFactory、TournamentDrawService<br>下游：无（产出赛程数据）",
    files: ["backend/…/engine/RoundRobinEngine.java"],
  },
  "standing": {
    name: "GroupStandingEngine", layer: "Engine", layerClass: "chip-engine", lines: "963 行",
    desc: "小组积分排名与多队破平的最终裁决者。",
    badge: "Engine · 排名裁决", story: "小组积分排名：胜场、局分、净胜分……按 RankingConfig 模板的多指标优先级排序，处理多队破平（相互胜负小联赛）、退赛队伍清洗。",
    why: "「积分相同谁出线」是赛事运营最大的争吵源——把它变成可配置模板 + 确定性算法，规则写在 tournament_ranking_config 表里，不同赛事不用改代码。",
    design: "上游：TournamentRankingService<br>下游：RankingConfig（读模板）",
    files: ["backend/…/engine/ranking/GroupStandingEngine.java", "backend/…/engine/ranking/RankingConfig.java"],
  },
  "pclogin": {
    name: "PcLoginService", layer: "Service", layerClass: "chip-service", lines: "205 行",
    desc: "PC 扫码票据状态机：出码/扫码/确认/轮询。",
    badge: "Service · 票据状态机", story: "PC 扫码登录状态机：createQrCode（出码）/ reportScan（上报已扫）/ confirm（确认授权）/ pollStatus（PC 轮询）。票据 CREATED → SCANNED → CONFIRMED → CONSUMED，每步条件 UPDATE 原子转移。",
    why: "它管的 web_login_session 是全库唯一用自增主键的表（其余全是雪花 ID）——扫码轮询高频轻量，自增最省。EXPIRED 是虚拟态，比对过期时间即可，不落库。",
    design: "上游：AuthController<br>下游：WebLoginSessionMapper、WechatQrCodeClient、AuthService",
    files: ["backend/…/service/auth/PcLoginService.java", "db/migration/V22__add_web_login_session.sql"],
  },
  "use-scoreboard": {
    name: "useScoreboard.js", layer: "前端 · composable", layerClass: "chip-frontend", lines: "2990 行",
    desc: "排球记分板响应层，全项目最大文件。",
    badge: "前端 · 记分响应层", story: "排球记分板的 UI 响应层：界面状态 + 执裁锁心跳 + 事件队列 flush + 本地恢复 + 主题。2990 行，全项目最大文件——复杂度贵在排球规则本身，而不是框架。",
    why: "它是「规则状态机放前端」这个架构决策的落地处：响应零延迟、断网可记分、服务器最省。",
    design: "上游：记分板页面<br>下游：match-state.js（纯函数）、match-lock.js（锁心跳）、request.js（上报）、store/auth.js",
    files: ["frontend/src/pages/volleyball/composables/useScoreboard.js"],
  },
  "match-state": {
    name: "match-state.js", layer: "前端 · 纯函数", layerClass: "chip-frontend", lines: "396 行",
    desc: "排球状态机：同输入同输出，撤销栈 40 层。",
    badge: "前端 · 状态机纯函数", story: "排球状态机纯函数模块：空状态/克隆/快照/换边/撤销栈（40 层）；本地存储 key 为 volleyball_scoreboard_state；事件队列项含 seq/type/gameNo/score/serveSide/payload/syncStatus。",
    why: "纯函数 = 同输入同输出 = 可批量仿真。千场混沌测试能存在，根基就是它。",
    design: "上游：useScoreboard.js<br>下游：本地 Storage（恢复）、事件队列（待上报）",
    files: ["frontend/src/pages/volleyball/match-state.js"],
  },
  "request-fe": {
    name: "request.js", layer: "前端 · 网络层", layerClass: "chip-frontend", lines: "204 行",
    desc: "统一请求封装：注入令牌、401 静默重登。",
    badge: "前端 · 统一网络层", story: "统一请求封装：base URL 规则（H5 同源/环境变量）、10 秒超时、Bearer 令牌注入、{code,message,data} 解析。401 自动重登走「注册式回调」——由 store/auth.js 注册处理器，避免两个文件互相 import 形成循环依赖。",
    why: "把「每个请求都该做的事」收进一处，结构性消灭「某个页面忘了带 token」这类遗漏。",
    design: "上游：所有业务页面<br>下游：后端 API、store/auth.js（经回调）",
    files: ["frontend/src/utils/request.js"],
  },
  "auth-fe": {
    name: "store/auth.js", layer: "前端 · 状态", layerClass: "chip-frontend", lines: "295 行",
    desc: "全局认证状态：reactive 手写，不用 Pinia。",
    badge: "前端 · 认证状态", story: "全局认证状态：用 Vue 的 reactive() 手写（没有 Pinia/Vuex）——token 存 Storage（key: scoring_token）、用户资料、401 处理器注册、资料守卫。295 行。",
    why: "认证状态就一个对象，引状态管理库是杀鸡用牛刀。「状态简单到不需要状态库」本身也是一种选型判断。",
    design: "上游：uni.login（微信登录）、request.js（401 回调）<br>下游：所有需要用户身份的页面",
    files: ["frontend/src/store/auth.js"],
  },
  "match-detail": {
    name: "MatchDetailAssembler", layer: "Service · 装配", layerClass: "chip-service", lines: "1184 行",
    desc: "比赛详情聚合器，全项目第二大 Java 文件。",
    badge: "Service · 详情装配", story: "把比赛、对阵双方、事件流水、阵容、战报聚合成前端一次能拿全的详情视图——1184 行，全项目第二大 Java 文件（仅次于建赛工厂），配有专属单元测试。",
    why: "详情是典型的「读多写少 + 多表拼装」场景：聚在一个装配器里，Controller 和门面都保持瘦身。拆开看，它也是撤销幽灵事件的最终裁决处（哪些事件该显示、哪些被标记撤销）。",
    design: "上游：MatchServiceImpl<br>下游：各 Mapper + match_event / match_lineup_config / match_report_meta",
    files: ["backend/…/service/match/MatchDetailAssembler.java"],
  },
  "draw-service": {
    name: "TournamentDrawService", layer: "Service", layerClass: "chip-service", lines: "577 行",
    desc: "手写签表/分组重排服务，痕迹守卫的执行者。",
    badge: "Service · 手写编排", story: "手写签表与手写分组的重提交服务：draw-slots 全量替换首轮签位（原地 UPDATE、晋级链不动）、group-assignments 全删全建小组赛程、痕迹法守卫判定可否重排。577 行，类注释就是一部设计文档。",
    why: "「算法给起点、人做精修」能安全存在，全靠它把守卫、幂等、锁序、事务纪律一条条钉死。两条现成的 bug 故事（轮空坍缩误判、V29 重开卡死）都发生在它守的这道门上。",
    design: "上游：TournamentController<br>下游：RoundRobinEngine、TournamentAccessGuard + 7 个 Mapper",
    files: ["backend/…/service/tournament/TournamentDrawService.java", "docs/plans/手写签表改造方案.md"],
  },
  "rule-resolver": {
    name: "TournamentRuleResolver", layer: "Service", layerClass: "chip-service", lines: "110 行",
    desc: "规则解析链：季军快照 → 分轮覆盖 → 组别默认。",
    badge: "Service · 规则解析", story: "每场比赛开赛前解析出最终生效规则（MatchRuleConfig）：按 match.division_id 加载组别 → 季军赛走组别 third_place_* 快照 → 小组赛查 (0,0)、淘汰赛查 (1, roundNum) → 未命中回落组别默认。纯读、无副作用。",
    why: "「这套比赛按什么规则打」只有一个出处。「半决赛决胜局 15 分」这类需求零代码落地——规则在配置表里，解析链说了算。",
    design: "上游：MatchServiceImpl / 结算流程<br>下游：tournament_round_rule、tournament_division（读）",
    files: ["backend/…/service/impl/TournamentRuleResolver.java", "docs/业务规则.md §15"],
  },
  "wechat-qr": {
    name: "小程序码出码双件", layer: "Service · 微信", layerClass: "chip-service", lines: "Client + Token",
    desc: "WechatQrCodeClient + WechatAccessTokenManager：签码与凭证缓存。",
    badge: "Service · 微信对接", story: "PC 扫码登录的出码链路：WechatQrCodeClient 向微信申请小程序码（scene 带票据）；WechatAccessTokenManager 负责 stable_token 的获取与缓存——所有要调微信的场合都从它这里拿凭证。",
    why: "微信 access token 有调用配额，缓存管理（而不是每次现取）是对接微信的标准姿势。出码凭证与业务代码隔离，换票据逻辑不动它。",
    design: "上游：PcLoginService<br>下游：微信开放平台接口（getStableToken / getWxaCodeUnlimit）",
    files: ["backend/…/service/wechat/"],
  },
  "use-scoreboard-bd": {
    name: "use-scoreboard-state.js", layer: "前端 · 纯函数", layerClass: "chip-frontend", lines: "595 行",
    desc: "羽毛球记分状态机——排球状态机的羽毛球兄弟。",
    badge: "前端 · 羽毛球状态机", story: "羽毛球记分板的状态机 composable：比分/局数/发球权/历史栈/胜利判定。2026-09 从 index.vue「行为保持」重构提取，index.vue 变成薄封装；存储 key 为 badminton_scoreboard_state；通过回调注入 onMatchEnded / announceScore。",
    why: "与排球侧（2990 行）对照着记很妙：羽毛球规则简单，状态机只要 595 行；历史栈不设上限（40 层上限是排球专属）——同一套思路，按运动复杂度伸缩。",
    design: "上游：pages/scoreboard/index.vue<br>下游：match-lock.js（执裁锁）、本地 Storage（恢复）",
    files: ["frontend/src/pages/scoreboard/use-scoreboard-state.js"],
  },
};

const TABLES = {
  "tournament": {
    name: "tournament", tag: "赛事主表", badge: "赛事域 · 核心表",
    story: "赛事主表：名称、聚合状态（0 未开始 / 1 进行中 / 2 已结束）、运动类型（羽毛球/排球）、赛事形态（纯淘汰/小组+淘汰/纯循环）、创建者。",
    why: "V23 之后有个重要变化：赛制与规则列「下沉」到了组别表，本表的这些列只是占位镜像（回滚保险）。但 status 仍是本表权威——它是聚合状态，全部组别完赛才置 2。「谁是规则的主人、谁是聚合的主人」在这里分得很清。",
    design: "1:N → tournament_division（组别）；creator_user_id → app_user；1:N → 收藏、裁判授权等",
    data: '{\n  "id": "88",\n  "name": "2026 春季排球联赛",\n  "status": 1,        // 1 = 进行中\n  "sport_type": 1,     // 1 = 排球\n  "tournament_type": 1, // 1 = 小组+淘汰\n  "creator_user_id": "u_10086"\n}',
    files: ["db/migration/V1__init_schema.sql", "docs/数据库.md §2.2"],
  },
  "division": {
    name: "tournament_division", tag: "组别 · V23", badge: "赛事域 · 核心表",
    story: "组别表（V23 新增）：一个组别 = 一个完整子赛事。赛制类型、几局几胜、小组几个出线、抽签模式（自动/手写签表/手写分组）全部下沉在这里。",
    why: "V23 的关键决策：原来赛事表身上的十几条规则列整体搬家到组别，且「只加不改不删」——旧赛事无感（回填默认组别），新多组别赛事从此可能。组别有自己的 status 和 current_stage，赛事级 status 只做聚合。",
    design: "N:1 → tournament；1:N → player、match_record、tournament_round_rule；1:1 → tournament_ranking_config",
    data: '{\n  "id": "88D01",        // 存量回填 = 赛事id + "D01"\n  "tournament_id": "88",\n  "name": "甲组",\n  "status": 1,\n  "current_stage": 0,    // 0 = 小组赛阶段\n  "tournament_type": 1,\n  "draw_mode": 0,        // 0 自动抽签\n  "best_of": 3           // 三局两胜\n}',
    files: ["db/migration/V23__add_tournament_division.sql", "db/migration/V27/V28（抽签模式）"],
  },
  "match-record": {
    name: "match_record", tag: "比赛 + 晋级链 + 锁", badge: "比赛域 · 核心表",
    story: "比赛记录：对阵双方、比分、状态（0 待赛 / 1 进行中 / 2 已完赛 / 3 退赛）、轮次轮号、局分 JSON、以及最重要的——晋级链和执裁锁三列。",
    why: "全系统的枢纽表。「赢了自动进下一轮」的实现就是三个字段：next_match_id（下一场是谁）+ next_match_slot（站在 left 还是 right）+ winner_id；三四名决赛的败者走 loser_next_match_id（V16）。V20 又把执裁锁三列（locked_by_user_id / lock_token / lock_expire_time）加在它身上——一把锁三列，不建独立表。",
    design: "N:1 → tournament、tournament_division；left/right/winner_id → player；1:N → match_event、match_lineup_config、team_match_item；1:1 → match_report_meta；自关联 next_match_id（晋级链）",
    data: '{\n  "id": "m_201",\n  "division_id": "88D01",\n  "round_num": 2,\n  "stage_type": 1,       // 1 = 淘汰赛\n  "left_player_id": "p_12",\n  "right_player_id": "p_7",\n  "status": 2,           // 2 = 已完赛\n  "winner_id": "p_12",\n  "score_display": "2:1",\n  "next_match_id": "m_205",   // 晋级链\n  "next_match_slot": "left",\n  "lock_token": "9f3c…"       // 执裁锁\n}',
    files: ["db/migration/V1、V16、V20", "docs/数据库.md §2.5"],
  },
  "match-event": {
    name: "match_event", tag: "事件流水 · V4", badge: "比赛域 · 核心表",
    story: "排球比赛事件流水：换人、暂停、换边、队长更换、阵容快照、比分快照……每条带全局序号 event_seq、事件时刻比分、发球方、payload 详情。",
    why: "唯一键 (match_id, event_seq) 是点睛之笔：同序号同内容 → 跳过（幂等）；同序号不同内容 → 409 整批拒绝，前端重排序号后重试。断线重传因此永远不会重复入账。撤销也不删记录——新增一条 reason:\"undo\" 的水位快照，流水永远完整可审计。「前端算规则、后端存流水」的架构就落在这张表上。",
    design: "N:1 → match_record；event_type 实际取值：substitution / timeout / captain_change / side_switch / roster_snapshot / lineup_snapshot / score_snapshot",
    data: '{\n  "id": "e_1032",\n  "match_id": "m_201",\n  "event_seq": 3,        // 幂等键的一半\n  "event_type": "substitution",\n  "game_no": 2,\n  "left_score": 14,\n  "right_score": 11,\n  "serve_side": "A",\n  "payload_json": "{ \\"side\\":\\"B\\", \\"out\\":7, \\"in\\":12 }"\n}',
    files: ["db/migration/V4__add_match_event.sql", "docs/数据库.md §2.7"],
  },
  "player": {
    name: "player", tag: "选手 / 队伍两种语义", badge: "人员域 · 核心表",
    story: "参赛主体表：羽毛球模式下一条记录 = 一个选手；排球模式下一条记录 = 一支队伍（队员明细在 tournament_team_member）。带种子排名、组号、组内排名。",
    why: "没有独立的 team 表——用「一张表、两种语义」省掉一整套队伍关联。种子排名 seed_rank 喂给 BracketEngine 排签表，group_no/group_position 喂给积分榜，编排和排名的数据原料都在这。",
    design: "N:1 → tournament、tournament_division；1:N → tournament_team_member（排球队员）；被 match_record 的 left/right/winner_id 引用",
    data: '{\n  "id": "p_12",\n  "tournament_id": "88",\n  "division_id": "88D01",\n  "name": "计算机学院一队",  // 排球模式下是队名\n  "seed_rank": 1,        // 种子排名 → 排签表\n  "group_no": 2          // 小组编号\n}',
    files: ["db/migration/V1、V2", "docs/数据库.md §2.3"],
  },
  "web-session": {
    name: "web_login_session", tag: "扫码票据 · V22", badge: "支撑域 · 核心表",
    story: "PC 扫码登录票据：ticket（32 位 hex，就是小程序码里的 scene）、状态、用户 ID、过期时间（出码 +3 分钟）。状态机 CREATED → SCANNED → CONFIRMED → CONSUMED。",
    why: "两个细节见功力：① 全库唯一用自增主键的表（其余全是雪花 ID）——扫码轮询高频轻量，自增最省；② EXPIRED 是虚拟态（NOW() > expire_time 即判定，不落库），token 只在 CONSUMED 时一次性下发，防重放。",
    design: "user_id → app_user；ticket 唯一键；懒清理：出码时顺手删掉过期超 24h 的旧行",
    data: '{\n  "id": 42,            // 全库唯一自增主键\n  "ticket": "a1b2c3…(32位hex)",\n  "status": "SCANNED",   // 已扫码待确认\n  "user_id": "u_10086",\n  "expire_time": "+3min"\n}',
    files: ["db/migration/V22__add_web_login_session.sql", "docs/数据库.md §2.18"],
  },
  "team-member": {
    name: "tournament_team_member", tag: "队员明细", domain: "人员域",
    story: "排球/团体赛的队员明细：姓名、球衣号（队内唯一）、是否自由人、是否队长、展示顺序。挂在 player（队伍）之下。",
    files: ["db/migration/V2__add_volleyball_support.sql"],
  },
  "app-user": {
    name: "app_user", tag: "用户", domain: "人员域",
    story: "用户表：微信 openid 或账号密码（V14）双通道登录身份。赛事创建者、执裁者、扫码确认者都指向它。",
    files: ["db/migration/V1、V14"],
  },
  "lineup": {
    name: "match_lineup_config", tag: "阵容配置", domain: "比赛域",
    story: "排球每局的场上阵容与自由人配置——开局站位从这里来。",
    files: ["db/migration/V3__add_match_lineup_config.sql"],
  },
  "team-item": {
    name: "team_match_item", tag: "团体子项", domain: "比赛域",
    story: "团体赛父场下的子项（男单/女单/……/接力 R1~RN）及子场指针——苏杯模板的载体。",
    files: ["db/migration/V12、V13"],
  },
  "report-meta": {
    name: "match_report_meta", tag: "战报元数据", domain: "比赛域",
    story: "战报元数据 JSON（裁判名/签名等），1 场 1 份，封签后不可改；V19 把字段扩到 MEDIUMTEXT。",
    files: ["db/migration/V7、V19"],
  },
  "round-rule": {
    name: "tournament_round_rule", tag: "赛段规则", domain: "赛制配置域",
    story: "赛段规则：按 (组别, stage, round) 粒度覆盖计分规则——半决赛决胜局到 15 分这种需求靠它实现（V15）。",
    files: ["db/migration/V15"],
  },
  "ranking-config": {
    name: "tournament_ranking_config", tag: "排名模板", domain: "赛制配置域",
    story: "每组别一份排名模板 JSON（指标优先级/数学口径/退赛策略/积分制），GroupStandingEngine 照它排序（V17）。",
    files: ["db/migration/V17"],
  },
  "qual-override": {
    name: "tournament_qualification_override", tag: "晋级覆盖", domain: "赛制配置域",
    story: "按组别+槽位手动指定晋级者——算法之外保留人工裁量权（V18）。",
    files: ["db/migration/V18"],
  },
  "custom-item": {
    name: "tournament_custom_item", tag: "自定义多项", domain: "赛制配置域",
    story: "自定义多项团体赛（template=3）的子项固化配置（V24）。",
    files: ["db/migration/V24"],
  },
  "favorite": {
    name: "tournament_favorite", tag: "收藏", domain: "支撑域",
    story: "用户-赛事收藏关联，唯一键 (user, tournament) 防重复收藏。",
    files: ["db/migration/V1"],
  },
  "referee-config": {
    name: "tournament_referee_config", tag: "裁判密码", domain: "支撑域",
    story: "赛事裁判密码哈希（与赛事 1:1）——裁判验证的前置关卡。",
    files: ["db/migration/V8"],
  },
  "referee-grant": {
    name: "tournament_referee_grant", tag: "裁判授权", domain: "支撑域",
    story: "已授权裁判用户列表——MatchAccessGuard 放行的依据之一。",
    files: ["db/migration/V8__add_tournament_referee_access.sql"],
  },
  "runtime-state": {
    name: "match_runtime_state", tag: "预留死表", domain: "预留与废弃", retired: true,
    story: "预留未接线的死表（V21）：设计意图是事件水位检查点/断线恢复，但目前零代码引用。官方文档原话提醒：勿假定存在服务端重放能力。保留待将来实现，不删表。",
    files: ["db/migration/V21__add_match_runtime_state.sql", "docs/数据库.md（死表说明）"],
  },
  "theme-match": {
    name: "match_theme_config", tag: "已废弃", domain: "预留与废弃", retired: true,
    story: "比赛配色主题——已废弃：配色改为前端硬编码直选，后端接口已注释。实体和 Mapper 暂留，不能据此推断接口可用。",
    files: ["db/migration/V5__add_match_theme_config.sql"],
  },
  "theme-global": {
    name: "global_theme_config", tag: "仅建表", domain: "预留与废弃", retired: true,
    story: "全局配色主题——仅存在建表脚本，没有实体、Mapper 和有效 API。",
    files: ["db/migration/V6__add_global_theme_config.sql"],
  },
};

const TABLE_DOMAINS = [
  { name: "人员域的另外两张", items: ["team-member", "app-user"] },
  { name: "比赛域的另外三张", items: ["lineup", "team-item", "report-meta"] },
  { name: "赛制配置域（规则不在代码里，在配置表里）", items: ["round-rule", "ranking-config", "qual-override", "custom-item"] },
  { name: "支撑域的另外三张", items: ["favorite", "referee-config", "referee-grant"] },
  { name: "预留与废弃（讲给懂行的人听，最显坦诚）", items: ["runtime-state", "theme-match", "theme-global"] },
];

/* ============================================================
   通用：详情面板填充
   ============================================================ */
function fillDetail(prefix, panelId, d) {
  const set = (suffix, html) => { const el = $("#" + prefix + "-" + suffix); if (el) el.innerHTML = html; };
  const toggleBlock = (suffix, content) => {
    const wrap = $("#" + prefix + "-" + suffix + "-wrap");
    if (!wrap) { if (content != null) set(suffix, content); return; }
    if (content) { wrap.style.display = ""; set(suffix, content); }
    else wrap.style.display = "none";
  };
  set("badge", d.badge || "");
  set("title", d.title || "");
  set("story", d.story || "");
  toggleBlock("why", d.why);
  toggleBlock("design", d.design);
  const dataEl = $("#" + prefix + "-data");
  const dataWrap = $("#" + prefix + "-data-wrap");
  if (dataWrap) {
    if (d.data) { dataWrap.style.display = ""; if (dataEl) dataEl.textContent = d.data; }
    else dataWrap.style.display = "none";
  }
  const filesWrap = $("#" + prefix + "-files-wrap");
  if (filesWrap) {
    if (d.files && d.files.length) {
      filesWrap.style.display = "";
      set("files", d.files.map((f) => `<span class="file-chip">${f}</span>`).join(""));
    } else filesWrap.style.display = "none";
  }
  const panel = $("#" + panelId);
  panel.hidden = true; // 重触发入场动画
  void panel.offsetWidth;
  panel.hidden = false;
  return panel;
}

/* 架构图节点面板字段名不同（what/why），单独填充 */
function fillNodeDetail(d) {
  $("#node-detail-badge").textContent = d.badge || "";
  $("#node-detail-title").textContent = d.title;
  $("#node-detail-what").innerHTML = d.what;
  $("#node-detail-why").innerHTML = d.why;
  $("#node-detail-files").innerHTML = d.files.map((f) => `<span class="file-chip">${f}</span>`).join("");
  const panel = $("#node-detail");
  panel.hidden = true;
  void panel.offsetWidth;
  panel.hidden = false;
}
function bindDetailClose(btnId, panelId) {
  $("#" + btnId).addEventListener("click", () => { $("#" + panelId).hidden = true; });
}

/* 把面板搬到锚点元素的正下方，保证「点哪儿、哪儿下面展开」 */
function placePanelAfter(panelId, anchor) {
  const panel = $("#" + panelId);
  if (panel && anchor && panel.previousElementSibling !== anchor) {
    anchor.insertAdjacentElement("afterend", panel);
  }
}
/* 把点选的锚点滚到视口顶部（CSS scroll-margin-top 预留了吸顶导航高度），面板随即同屏可见 */
function scrollAnchorToTop(anchor) {
  if (anchor && anchor.scrollIntoView) anchor.scrollIntoView({ behavior: "smooth", block: "start" });
}

/* ============================================================
   1. 全景架构图交互
   ============================================================ */
function initArch() {
  $$("#arch-map .node").forEach((node) => {
    node.addEventListener("click", () => {
      $$("#arch-map .node").forEach((n) => n.classList.remove("active"));
      node.classList.add("active");
      const key = node.dataset.node;
      const d = NODES[key];
      const layer = node.closest(".arch-layer");
      placePanelAfter("node-detail", layer);
      fillNodeDetail(d);
      scrollAnchorToTop(layer);
    });
  });
  bindDetailClose("node-detail-close", "node-detail");
}

/* ============================================================
   2. 技术栈卡片渲染
   ============================================================ */
function initTech() {
  const grid = $("#tech-grid");
  grid.innerHTML = TECH.map((t, i) => `
    <div class="tech-card">
      <div class="tech-card-head"><h3>${t.icon} ${t.name}</h3><span class="tech-card-ver">${t.ver}</span></div>
      <div class="tech-tabs">
        <button class="tech-tab active" data-tab="who">它是谁</button>
        <button class="tech-tab" data-tab="why">为什么是它</button>
        <button class="tech-tab" data-tab="alt">换别人会怎样</button>
      </div>
      <div class="tech-body" data-body>${t.who}</div>
    </div>`).join("");
  grid.querySelectorAll(".tech-card").forEach((card, i) => {
    const tabs = card.querySelectorAll(".tech-tab");
    const body = card.querySelector("[data-body]");
    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        tabs.forEach((x) => x.classList.remove("active"));
        tab.classList.add("active");
        body.innerHTML = TECH[i][tab.dataset.tab];
        body.style.animation = "none"; void body.offsetWidth; body.style.animation = "";
      });
    });
  });
}

/* ============================================================
   3. 链路步骤条（通用）
   ============================================================ */
function makePipe(steps, stepsElId, detailPrefix, opts = {}) {
  const stepsEl = $("#" + stepsElId);
  stepsEl.innerHTML = steps
    .map((s, i) => `<button class="pipe-step" data-i="${i}"><span class="step-num">${i + 1}</span>${s.chip}</button>`)
    .join("");
  let current = -1;
  let timer = null;

  function select(i, user) {
    current = i;
    stepsEl.querySelectorAll(".pipe-step").forEach((el, j) => {
      el.classList.toggle("active", j === i);
      if (user) el.classList.toggle("done", j < i);
    });
    const anchor = stepsEl.closest(".auth-col") || stepsEl;
    placePanelAfter(opts.panelId, anchor);
    fillDetail(detailPrefix, opts.panelId, {
      badge: steps[i].badge || (opts.label ? `${opts.label} · 第 ${i + 1} 步` : steps[i].chip),
      title: steps[i].chip, story: steps[i].story,
      data: steps[i].data, design: steps[i].design, files: steps[i].files,
    });
    if (user) scrollAnchorToTop(anchor);
  }

  stepsEl.querySelectorAll(".pipe-step").forEach((el) => {
    el.addEventListener("click", () => {
      stopPlay();
      select(+el.dataset.i, true);
      if (opts.onUserSelect) opts.onUserSelect();
    });
  });

  function stopPlay() {
    if (timer) { clearInterval(timer); timer = null; }
    stepsEl.querySelectorAll(".pipe-step").forEach((el) => el.classList.remove("playing"));
  }
  function play() {
    stopPlay();
    let i = 0;
    select(i, false);
    stepsEl.querySelector(`[data-i="${i}"]`).classList.add("playing");
    timer = setInterval(() => {
      stepsEl.querySelector(`[data-i="${i}"]`)?.classList.remove("playing");
      stepsEl.querySelector(`[data-i="${i}"]`)?.classList.add("done");
      i += 1;
      if (i >= steps.length) { stopPlay(); return; }
      select(i, false);
      stepsEl.querySelector(`[data-i="${i}"]`).classList.add("playing");
    }, 2400);
  }
  return { play, stopPlay, select };
}

let scorePipe = null;
function initPipes() {
  scorePipe = makePipe(PIPE_SCORE, "pipe-score-steps", "pipe-score", { panelId: "pipe-score-detail" });
  bindDetailClose("pipe-score-close", "pipe-score-detail");
  const playBtn = $("#pipe-score-play");
  playBtn.addEventListener("click", () => {
    if (playBtn.dataset.playing === "1") {
      scorePipe.stopPlay();
      playBtn.dataset.playing = "0";
      playBtn.textContent = "▶ 自动演示一遍";
      return;
    }
    playBtn.dataset.playing = "1";
    playBtn.textContent = "⏸ 停止演示";
    scorePipe.play();
  });
  $("#pipe-score-steps").addEventListener("click", () => {
    playBtn.dataset.playing = "0";
    playBtn.textContent = "▶ 自动演示一遍";
  });

  makePipe(PIPE_TOURNEY, "pipe-tournament-steps", "pipe-tournament", { panelId: "pipe-tournament-detail" });
  bindDetailClose("pipe-tournament-close", "pipe-tournament-detail");

  makePipe(PIPE_DRAW, "pipe-draw-steps", "pipe-draw", { panelId: "pipe-draw-detail", label: "手写签表" });
  bindDetailClose("pipe-draw-close", "pipe-draw-detail");

  makePipe(PIPE_RULE, "pipe-rule-steps", "pipe-rule", { panelId: "pipe-rule-detail", label: "分段规则" });
  bindDetailClose("pipe-rule-close", "pipe-rule-detail");

  makePipe(PIPE_LOGIN, "pipe-login-steps", "pipe-auth", { panelId: "pipe-auth-detail", label: "微信登录" });
  makePipe(PIPE_PC, "pipe-pc-steps", "pipe-auth", { panelId: "pipe-auth-detail", label: "扫码登录" });
  bindDetailClose("pipe-auth-close", "pipe-auth-detail");
}

/* ============================================================
   4. 手风琴
   ============================================================ */
function initAcc() {
  $$("#acc-tradeoffs .acc-item").forEach((item) => {
    const head = item.querySelector(".acc-head");
    head.addEventListener("click", () => {
      const open = item.classList.contains("open");
      $$("#acc-tradeoffs .acc-item").forEach((x) => { x.classList.remove("open"); x.querySelector(".acc-head").setAttribute("aria-expanded", "false"); });
      if (!open) { item.classList.add("open"); head.setAttribute("aria-expanded", "true"); }
    });
  });
}

/* ============================================================
   5. 自测
   ============================================================ */
function initQuiz() {
  const list = $("#quiz-list");
  list.innerHTML = QUIZ.map((q, i) => `
    <div class="quiz-item" data-i="${i}">
      <p class="quiz-q"><span class="q-num">Q${i + 1}</span>${q.q}</p>
      <div class="quiz-opts">
        ${q.opts.map((o, j) => `<button class="quiz-opt" data-j="${j}">${String.fromCharCode(65 + j)}. ${o}</button>`).join("")}
      </div>
      <div class="quiz-explain"></div>
    </div>`).join("");

  let answered = 0, correct = 0;
  const scoreEl = $("#quiz-score");
  const resultEl = $("#quiz-result");

  list.querySelectorAll(".quiz-item").forEach((item) => {
    const i = +item.dataset.i;
    const q = QUIZ[i];
    item.querySelectorAll(".quiz-opt").forEach((opt) => {
      opt.addEventListener("click", () => {
        if (item.classList.contains("answered")) return;
        item.classList.add("answered");
        const j = +opt.dataset.j;
        const isRight = j === q.a;
        item.classList.add(isRight ? "ok" : "bad");
        item.querySelectorAll(".quiz-opt").forEach((o) => {
          o.disabled = true;
          if (+o.dataset.j === q.a) o.classList.add("correct");
        });
        if (!isRight) opt.classList.add("wrong");
        else opt.classList.add("correct");
        answered += 1; if (isRight) correct += 1;
        item.querySelector(".quiz-explain").innerHTML =
          `${isRight ? "✅ 答对了！" : "❌ 答错了。"} ${q.explain} <a class="quiz-back" href="${q.back[0]}">${q.back[1]} →</a>`;
        scoreEl.hidden = false;
        scoreEl.innerHTML = `已答 <b>${answered}</b>/${QUIZ.length} · 答对 <b>${correct}</b> 题`;
        if (answered === QUIZ.length) {
          resultEl.hidden = false;
          const verdict = correct === QUIZ.length
            ? "🏆 满分！你现在可以对着任何人侃侃而谈了。"
            : correct >= 8
              ? "👍 相当扎实。错的那几题，点链接回去再看一眼，你就是全懂的那个人。"
              : "📖 还有几条链路没吃透——按上面每题的链接回炉一遍，再来挑战满分。";
          resultEl.innerHTML = `<b>${correct} / ${QUIZ.length}</b><span class="verdict">${verdict}</span>`;
          resultEl.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      });
    });
  });
}

/* ============================================================
   5.5 类与数据地图
   ============================================================ */
function initClasses() {
  // 类卡册渲染
  const grid = $("#class-grid");
  if (grid) {
    grid.innerHTML = Object.entries(CLASSES)
      .map(([id, c]) => `
        <button class="class-card" data-class="${id}">
          <span class="class-card-head">
            <span class="class-card-name">${c.name}</span>
            <span class="layer-chip ${c.layerClass}">${c.layer}</span>
          </span>
          <span class="class-card-lines">${c.lines}</span>
          <p class="class-card-desc">${c.desc}</p>
        </button>`).join("");
  }

  // 其余表的域分组渲染
  const domainsEl = $("#table-domains");
  if (domainsEl) {
    domainsEl.innerHTML = TABLE_DOMAINS.map((g) => `
      <div class="domain-group">
        <p class="domain-name">${g.name}</p>
        <div class="domain-chips">
          ${g.items.map((id) => {
            const t = TABLES[id];
            if (!t) return "";
            return `<button class="table-chip${t.retired ? " retired" : ""}" data-table="${id}">${t.name}</button>`;
          }).join("")}
        </div>
      </div>`).join("");
  }

  // 统一点击：类（链上节点 + 卡片）→ class-detail；表（ER 节点 + 芯片）→ table-detail
  function fillClass(id) {
    const c = CLASSES[id];
    if (!c) return;
    fillDetail("class-detail", "class-detail", {
      badge: c.badge, title: c.name, story: c.story, why: c.why, design: c.design, files: c.files,
    });
  }
  function fillTable(id) {
    const t = TABLES[id];
    if (!t) return;
    fillDetail("table-detail", "table-detail", {
      badge: t.badge || t.domain, title: t.name, story: t.story, why: t.why, design: t.design,
      data: t.data, files: t.files,
    });
  }
  document.addEventListener("click", (e) => {
    const classHit = e.target.closest("[data-class]");
    const tableHit = e.target.closest("[data-table]");
    if (classHit) {
      // 类卡在网格内：面板作为全宽网格行插到卡片正下方；链上节点：面板插到所属泳道正下方
      const anchor = classHit.closest("#class-grid .class-card") || classHit.closest(".lane");
      placePanelAfter("class-detail", anchor);
      fillClass(classHit.dataset.class);
      markActive(classHit, "#classes .class-card, #classes .node-class");
      scrollAnchorToTop(anchor);
    }
    if (tableHit) {
      // ER 主链节点：面板插到所在行正下方；域分组芯片：插到该分组正下方
      const anchor = tableHit.closest(".er-row") || tableHit.closest(".domain-group");
      placePanelAfter("table-detail", anchor);
      fillTable(tableHit.dataset.table);
      markActive(tableHit, "#classes .node-table, #classes .table-chip");
      scrollAnchorToTop(anchor);
    }
  });
  function markActive(hit, scope) {
    document.querySelectorAll(scope).forEach((el) => el.classList.remove("active"));
    hit.classList.add("active");
  }
  bindDetailClose("class-detail-close", "class-detail");
  bindDetailClose("table-detail-close", "table-detail");
}

/* ============================================================
   6. 滚动显现 + 导航高亮
   ============================================================ */
function initRevealAndSpy() {
  const revealTargets = $$(".arch-layer, .tech-card, .code-moment, .eng-card, .auth-col, .acc-item, .insight-box, .tldr, .hero-stats, .lane, .class-card, .domain-group");
  revealTargets.forEach((el) => el.classList.add("reveal"));
  const io = new IntersectionObserver(
    (entries) => entries.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }),
    { threshold: 0.08 }
  );
  revealTargets.forEach((el) => io.observe(el));

  const navLinks = $$("#topnav a");
  const sections = navLinks
    .map((a) => ({ a, sec: document.querySelector(a.getAttribute("href")) }))
    .filter((x) => x.sec);
  const spy = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          navLinks.forEach((a) => a.classList.remove("active"));
          const hit = sections.find((x) => x.sec === e.target);
          if (hit) hit.a.classList.add("active");
        }
      });
    },
    { rootMargin: "-30% 0px -60% 0px" }
  );
  sections.forEach((x) => spy.observe(x.sec));
}

/* ============================================================
   启动
   ============================================================ */
document.addEventListener("DOMContentLoaded", () => {
  initArch();
  initTech();
  initPipes();
  initAcc();
  initQuiz();
  initClasses();
  initRevealAndSpy();
});
