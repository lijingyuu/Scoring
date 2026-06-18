import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const cwd = process.cwd();
const outputDir = path.join(cwd, "outputs", "auth-matrix", "outputs", "auth-matrix");
const outputFile = path.join(outputDir, "项目鉴权权限矩阵_可编辑版.xlsx");

await fs.mkdir(outputDir, { recursive: true });

const workbook = Workbook.create();
const roleSheet = workbook.worksheets.add("角色等级定义");
const matrixSheet = workbook.worksheets.add("权限矩阵主表");
const enumSheet = workbook.worksheets.add("可选值");
const mappingSheet = workbook.worksheets.add("当前代码映射");
const instructionsSheet = workbook.worksheets.add("填写说明");

const roleRows = [
  ["角色代码", "角色名称", "定义", "当前项目状态", "建议用途", "备注"],
  ["L0", "匿名访客", "未登录用户，只能访问公开资源", "已存在", "公开浏览", "默认最低权限"],
  ["L1", "已登录未补资料", "已完成微信登录，但未补全资料", "已存在", "受限登录态", "建议限制写操作"],
  ["L2", "已登录完整资料", "已登录且资料完整的普通用户", "已存在", "基础用户操作", "可收藏、可创建比赛"],
  ["L3", "比赛创建者", "创建该赛事/比赛的人", "已存在", "赛事所有权", "当前核心高权限"],
  ["L4", "赛事协作者/裁判", "被赛事拥有者授权的协作人员", "未实现", "记分/执裁/配置", "建议中期扩展"],
  ["L5", "系统管理员", "平台级管理者", "未实现", "治理/纠错/审计", "建议长期预留"],
];

roleSheet.getRange(`A1:F${roleRows.length}`).values = roleRows;
roleSheet.getRange("A1:F1").format = {
  fill: "#1F4E78",
  font: { bold: true, color: "#FFFFFF" },
};
roleSheet.getRange(`A2:F${roleRows.length}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
roleSheet.getRange("A:A").format.columnWidthPx = 90;
roleSheet.getRange("B:B").format.columnWidthPx = 170;
roleSheet.getRange("C:C").format.columnWidthPx = 280;
roleSheet.getRange("D:D").format.columnWidthPx = 120;
roleSheet.getRange("E:E").format.columnWidthPx = 180;
roleSheet.getRange("F:F").format.columnWidthPx = 180;
roleSheet.freezePanes.freezeRows(1);

const enumRows = [
  ["字段", "可选值", "说明"],
  ["权限值", "允许", "该角色允许执行该操作"],
  ["权限值", "禁止", "该角色禁止执行该操作"],
  ["权限值", "待定", "你还没定规则"],
  ["权限值", "条件允许", "需要额外条件，例如仅本人、仅创建者、仅当前阶段"],
  ["资源级别", "公开", "任何人可访问"],
  ["资源级别", "登录后", "登录用户可访问"],
  ["资源级别", "完整资料后", "资料完整用户可访问"],
  ["资源级别", "赛事成员", "赛事内成员可访问"],
  ["资源级别", "赛事管理", "创建者/协作者可访问"],
  ["资源级别", "系统管理", "系统管理员可访问"],
  ["实现状态", "已实现", "代码里已经做了"],
  ["实现状态", "部分实现", "前后端只做了一部分"],
  ["实现状态", "未实现", "当前代码里没有做"],
  ["实现状态", "实现过宽", "当前权限比建议更宽松"],
  ["实现状态", "实现过严", "当前权限比建议更严格"],
];

enumSheet.getRange(`A1:C${enumRows.length}`).values = enumRows;
enumSheet.getRange("A1:C1").format = {
  fill: "#44546A",
  font: { bold: true, color: "#FFFFFF" },
};
enumSheet.getRange(`A2:C${enumRows.length}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
enumSheet.getRange("A:A").format.columnWidthPx = 120;
enumSheet.getRange("B:B").format.columnWidthPx = 130;
enumSheet.getRange("C:C").format.columnWidthPx = 260;
enumSheet.freezePanes.freezeRows(1);

const matrixHeader = [[
  "编号",
  "一级模块",
  "二级模块",
  "操作项",
  "前端页面/入口",
  "后端接口/资源",
  "操作类型",
  "数据敏感度",
  "资源级别建议",
  "L0 匿名访客",
  "L1 已登录未补资料",
  "L2 已登录完整资料",
  "L3 比赛创建者",
  "L4 赛事协作者/裁判",
  "L5 系统管理员",
  "当前代码状态",
  "当前实际权限",
  "你的最终决策",
  "条件/判定规则",
  "备注"
]];

const rows = [
  ["A001", "认证", "登录", "微信登录", "启动流程 / requireAuth", "POST /api/v1/auth/wechat-login", "写", "低", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "登录入口本身必须公开", "小程序 code 换 token"],
  ["A002", "认证", "个人资料", "查看我的资料", "我的页初始化", "GET /api/v1/users/me", "读", "中", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "登录可用", "", "", "当前走 AuthGuard"],
  ["A003", "认证", "个人资料", "补全个人资料", "资料补全弹窗", "POST /api/v1/auth/profile", "写", "中", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "登录可用", "", "", "头像 + 昵称"],
  ["A004", "认证", "登录态", "恢复本地 token", "App 启动 bootstrapAuth", "本地存储", "本地态", "低", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "前端本地恢复", "", "", "不属于服务端权限，但影响交互"],

  ["B001", "比赛大厅", "浏览", "查看比赛列表", "pages/index/index", "GET /api/v1/tournaments", "读", "低", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", "当前公开列表"],
  ["B002", "比赛大厅", "浏览", "搜索比赛", "pages/index/index", "GET /api/v1/tournaments?keyword=...", "读", "低", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", "是列表的同一接口变体"],
  ["B003", "比赛大厅", "交互", "点击进入比赛详情", "pages/index/index -> detail", "GET /api/v1/tournaments/{id}", "读", "低", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", "详情页当前公开"],
  ["B004", "比赛大厅", "交互", "创建比赛入口跳转", "pages/index/index -> create/sport", "页面导航", "页面访问", "低", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "部分实现", "前端通过 requireProfile 控制", "", "如果只是跳页面也可更宽，但真正提交建议受限", "建议把真正权限放在提交动作"],
  ["B005", "比赛大厅", "用户操作", "收藏比赛", "首页卡片按钮", "POST /api/v1/tournaments/{id}/favorite", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "允许", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "建议后端也校验 profileCompleted", "当前规则未完全后端化"],
  ["B006", "比赛大厅", "用户操作", "取消收藏比赛", "首页卡片按钮", "DELETE /api/v1/tournaments/{id}/favorite", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "允许", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "建议后端也校验 profileCompleted", ""],

  ["C001", "我的", "资料", "进入我的页面", "pages/mine/index", "GET /api/v1/users/me", "读", "低", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "登录后会拉取", "", "", "页面本身也可以让匿名进入，但会空态展示"],
  ["C002", "我的", "收藏", "查看我的收藏比赛", "pages/mine/index", "GET /api/v1/tournaments/mine/favorites", "读", "中", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "登录可用", "", "", ""],
  ["C003", "我的", "比赛", "查看我创建的比赛", "pages/mine/index", "GET /api/v1/tournaments/mine/created", "读", "中", "登录后", "禁止", "允许", "允许", "允许", "允许", "允许", "已实现", "登录可用", "", "", ""],
  ["C004", "我的", "交互", "在我的页面收藏/取消收藏比赛", "pages/mine/index", "POST/DELETE /favorite", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "允许", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "", ""],

  ["D001", "比赛详情", "浏览", "查看比赛详情", "pages/tournament/detail", "GET /api/v1/tournaments/{id}", "读", "低", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", ""],
  ["D002", "比赛详情", "交互", "详情页收藏比赛", "pages/tournament/detail", "POST /api/v1/tournaments/{id}/favorite", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "允许", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "", ""],
  ["D003", "比赛详情", "交互", "详情页取消收藏比赛", "pages/tournament/detail", "DELETE /api/v1/tournaments/{id}/favorite", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "允许", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "", ""],
  ["D004", "比赛详情", "交互", "查看队伍入口", "pages/tournament/detail -> teams", "GET /api/v1/tournaments/{id}/teams", "读", "中", "登录后", "禁止", "禁止", "允许", "允许", "允许", "允许", "实现过宽", "当前公开", "", "如果你不想公开队员名单，应该收紧", "排球队伍名单包含成员信息"],
  ["D005", "比赛详情", "交互", "进入赛程/裁判入口", "pages/tournament/detail -> bracket/groups", "页面导航 + 后续 GET", "页面访问", "中", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "前端按钮仅对 creator 展示，后端读接口未收紧", "", "不要只靠按钮隐藏", "这是典型 UI 权限与后端权限不一致"],

  ["E001", "赛事创建", "羽毛球", "打开羽毛球创建页", "pages/create/index", "页面访问", "页面访问", "低", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "部分实现", "前端提交前受限", "", "页面是否开放可单独定；真正权限在提交", ""],
  ["E002", "赛事创建", "羽毛球", "提交创建羽毛球比赛", "pages/create/index", "POST /api/v1/tournaments", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "建议后端也校验 profileCompleted", "提交成功后成为创建者"],
  ["E003", "赛事创建", "排球", "打开排球创建页", "pages/create/volleyball", "页面访问", "页面访问", "低", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "部分实现", "前端提交前受限", "", "", ""],
  ["E004", "赛事创建", "排球", "编辑队伍草稿", "pages/create/volleyball", "前端本地状态", "本地态", "低", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "已实现", "纯前端", "", "", "不涉及后端，但属于业务操作"],
  ["E005", "赛事创建", "排球", "提交创建排球比赛", "pages/create/volleyball", "POST /api/v1/tournaments", "写", "中", "完整资料后", "禁止", "禁止", "允许", "允许", "禁止", "允许", "部分实现", "前端要求补资料；后端只要求登录", "", "建议后端也校验 profileCompleted", ""],

  ["F001", "赛程浏览", "淘汰赛", "查看淘汰赛对阵图", "pages/tournament/bracket", "GET /api/v1/tournaments/{id}/bracket", "读", "中", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "如果你的赛事是公开可看，建议保留公开", ""],
  ["F002", "赛程浏览", "淘汰赛", "点击淘汰赛未完成比赛进入记分", "pages/tournament/bracket", "后续进入记分页面/写接口", "写入口", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "前端页面可点；真正写接口由后端限制创建者", "", "如果将来开放协作者，这里就是协作者入口", ""],
  ["F003", "赛程浏览", "淘汰赛", "点击已完成排球比赛查看记录", "pages/tournament/bracket", "GET /api/v1/matches/{id}/record", "读", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "实现过宽", "当前公开", "", "我建议至少赛事成员可读", ""],
  ["F004", "赛程浏览", "小组赛", "查看小组页", "pages/tournament/groups", "GET /api/v1/tournaments/{id}/groups", "读", "中", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", ""],
  ["F005", "赛程浏览", "小组赛", "查看小组积分榜", "pages/tournament/groups", "GET /api/v1/tournaments/{id}/group-standings", "读", "中", "公开", "允许", "允许", "允许", "允许", "允许", "允许", "已实现", "公开", "", "", ""],
  ["F006", "赛程浏览", "小组赛", "点击小组赛比赛进入记分", "pages/tournament/groups", "后续进入记分页面/写接口", "写入口", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "真正写权限在后端 requireCreatorTournament", "", "", ""],
  ["F007", "赛程管理", "小组转淘汰", "生成淘汰赛", "pages/tournament/groups", "POST /api/v1/tournaments/{id}/generate-knockout", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "条件允许", "允许", "已实现", "当前仅创建者", "", "如果未来有裁判长，可设为条件允许", ""],

  ["G001", "队伍与成员", "队伍列表", "查看队伍列表", "pages/tournament/teams", "GET /api/v1/tournaments/{id}/teams", "读", "中", "登录后", "禁止", "禁止", "允许", "允许", "允许", "允许", "实现过宽", "当前公开", "", "", ""],
  ["G002", "队伍与成员", "成员详情", "查看某队成员详情", "pages/tournament/team-members", "基于 teams 数据继续展示", "读", "中", "登录后", "禁止", "禁止", "允许", "允许", "允许", "允许", "实现过宽", "受上游公开接口影响", "", "", "如果 teams 收紧，这里自然收紧"],

  ["H001", "比赛后台", "阵容准备", "打开排球轮次填写页", "pages/volleyball/lineup", "GET /api/v1/matches/{id}/lineup-config", "读", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "实现过宽", "当前公开读取", "", "阵容数据不建议公开", ""],
  ["H002", "比赛后台", "阵容准备", "保存报表元数据", "pages/volleyball/lineup", "PUT /api/v1/matches/{id}/report-meta", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "已实现", "当前仅创建者", "", "", ""],
  ["H003", "比赛后台", "阵容准备", "保存轮次配置", "pages/volleyball/lineup", "PUT /api/v1/matches/{id}/lineup-config", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "已实现", "当前仅创建者", "", "", ""],
  ["H004", "比赛后台", "阵容准备", "进入排球记分页", "pages/volleyball/lineup -> scoreboard", "后续比赛写接口", "写入口", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "入口页本身不是真正权限边界", "", "", ""],

  ["I001", "比赛后台", "羽毛球记分", "进入羽毛球记分页", "pages/scoreboard/index", "后续 /matches/{id}/finish or /score", "写入口", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "真正写权限在后端", "", "", ""],
  ["I002", "比赛后台", "排球记分", "打开排球记分页", "pages/volleyball/scoreboard", "GET/PUT 多个 matches 接口", "读写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "真正写权限在后端 requireCreatorTournament", "", "", ""],
  ["I003", "比赛后台", "排球记分", "读取主题配置", "pages/volleyball/scoreboard", "GET /api/v1/matches/{id}/theme-config", "读", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "实现过宽", "当前公开", "", "主题配置虽不如阵容敏感，但仍是后台数据", ""],
  ["I004", "比赛后台", "排球记分", "保存主题配置", "pages/volleyball/scoreboard", "PUT /api/v1/matches/{id}/theme-config", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "条件允许", "允许", "已实现", "当前仅创建者", "", "是否允许协作者改主题可单独决定", ""],
  ["I005", "比赛后台", "排球记分", "保存事件流", "pages/volleyball/scoreboard", "PUT /api/v1/matches/{id}/events", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "已实现", "当前仅创建者", "", "", ""],
  ["I006", "比赛后台", "排球记分", "完赛推进晋级", "pages/volleyball/scoreboard", "PUT /api/v1/matches/{id}/finish", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "已实现", "当前仅创建者", "", "", ""],
  ["I007", "比赛后台", "排球记分", "重开比赛", "pages/volleyball/scoreboard / record", "PUT /api/v1/matches/{id}/restart", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "条件允许", "允许", "已实现", "当前仅创建者", "", "建议比 finish 更高一点", ""],
  ["I008", "比赛后台", "旧记分接口", "更新比分（旧接口）", "历史/兼容", "PUT /api/v1/matches/{id}/score", "写", "高", "赛事管理", "禁止", "禁止", "禁止", "允许", "允许", "允许", "已实现", "当前仅创建者", "", "如果已不再使用，可标记废弃", ""],

  ["J001", "比赛记录", "记录查看", "打开比赛记录页", "pages/volleyball/record", "GET /api/v1/matches/{id}/record", "读", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "实现过宽", "当前公开", "", "我强烈建议收紧", "记录页包含事件流、轮次、签字等细节"],
  ["J002", "比赛记录", "记录查看", "查看签字与报表信息", "pages/volleyball/record", "GET /api/v1/matches/{id}/record", "读", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "实现过宽", "当前公开", "", "", ""],
  ["J003", "比赛记录", "记录导出", "H5 打印/PDF 导出", "pages/volleyball/record", "基于记录页数据", "导出", "高", "赛事成员", "禁止", "禁止", "禁止", "允许", "允许", "允许", "部分实现", "导出能力受记录页读取权限影响", "", "", ""],

  ["K001", "系统治理", "开发调试", "Dev Mock 登录", "H5 调试", "DevMockAuthFilter", "开发能力", "高", "系统管理", "禁止", "禁止", "禁止", "禁止", "禁止", "允许", "部分实现", "当前有越界风险", "", "只能在 dev profile 生效", "这是之前提到的 P0"],
];

matrixSheet.getRange(`A1:T${rows.length + 1}`).values = matrixHeader.concat(rows);
matrixSheet.getRange("A1:T1").format = {
  fill: "#1F4E78",
  font: { bold: true, color: "#FFFFFF" },
};
matrixSheet.getRange(`A2:T${rows.length + 1}`).format.borders = {
  preset: "all",
  style: "thin",
  color: "#D9E2F3",
};

matrixSheet.getRange("A:A").format.columnWidthPx = 78;
matrixSheet.getRange("B:B").format.columnWidthPx = 110;
matrixSheet.getRange("C:C").format.columnWidthPx = 110;
matrixSheet.getRange("D:D").format.columnWidthPx = 180;
matrixSheet.getRange("E:E").format.columnWidthPx = 210;
matrixSheet.getRange("F:F").format.columnWidthPx = 240;
matrixSheet.getRange("G:G").format.columnWidthPx = 90;
matrixSheet.getRange("H:H").format.columnWidthPx = 90;
matrixSheet.getRange("I:I").format.columnWidthPx = 120;
matrixSheet.getRange("J:O").format.columnWidthPx = 100;
matrixSheet.getRange("P:P").format.columnWidthPx = 110;
matrixSheet.getRange("Q:Q").format.columnWidthPx = 160;
matrixSheet.getRange("R:R").format.columnWidthPx = 130;
matrixSheet.getRange("S:S").format.columnWidthPx = 220;
matrixSheet.getRange("T:T").format.columnWidthPx = 220;
matrixSheet.freezePanes.freezeRows(1);
matrixSheet.freezePanes.freezeColumns(6);

for (const col of ["J", "K", "L", "M", "N", "O", "R"]) {
  const range = matrixSheet.getRange(`${col}2:${col}${rows.length + 1}`);
  range.dataValidation = {
    rule: {
      type: "list",
      values: ["允许", "禁止", "待定", "条件允许"],
    },
  };
}

matrixSheet.getRange(`I2:I${rows.length + 1}`).dataValidation = {
  rule: {
    type: "list",
    values: ["公开", "登录后", "完整资料后", "赛事成员", "赛事管理", "系统管理"],
  },
};

matrixSheet.getRange(`P2:P${rows.length + 1}`).dataValidation = {
  rule: {
    type: "list",
    values: ["已实现", "部分实现", "未实现", "实现过宽", "实现过严"],
  },
};

const decisionCols = ["J", "K", "L", "M", "N", "O", "R"];
for (const col of decisionCols) {
  const range = matrixSheet.getRange(`${col}2:${col}${rows.length + 1}`);
  range.conditionalFormats.add("cellIs", {
    operator: "equal",
    formula: "\"允许\"",
    format: {
      fill: "#E2F0D9",
      font: { bold: true, color: "#2F5233" },
    },
  });
  range.conditionalFormats.add("cellIs", {
    operator: "equal",
    formula: "\"禁止\"",
    format: {
      fill: "#FCE4D6",
      font: { bold: true, color: "#843C0C" },
    },
  });
  range.conditionalFormats.add("cellIs", {
    operator: "equal",
    formula: "\"待定\"",
    format: {
      fill: "#EDEDED",
      font: { bold: true, color: "#404040" },
    },
  });
  range.conditionalFormats.add("cellIs", {
    operator: "equal",
    formula: "\"条件允许\"",
    format: {
      fill: "#FFF2CC",
      font: { bold: true, color: "#7F6000" },
    },
  });
}

matrixSheet.getRange(`P2:P${rows.length + 1}`).conditionalFormats.add("containsText", {
  text: "实现过宽",
  format: {
    fill: "#F8CBAD",
    font: { bold: true, color: "#833C0C" },
  },
});
matrixSheet.getRange(`P2:P${rows.length + 1}`).conditionalFormats.add("containsText", {
  text: "部分实现",
  format: {
    fill: "#FFF2CC",
    font: { bold: true, color: "#7F6000" },
  },
});

const mappingRows = [
  ["前端页/组件", "主要操作", "关联接口", "当前前端门禁", "备注"],
  ["pages/index/index", "大厅浏览 / 收藏 / 去创建", "GET /tournaments, POST/DELETE /favorite", "收藏与创建前要求 requireProfile", "浏览本身公开"],
  ["pages/mine/index", "看我的收藏/我创建的比赛", "GET /mine/favorites, GET /mine/created", "ensureAuth + fetchProfile", "我的页偏登录后"],
  ["pages/create/index", "创建羽毛球比赛", "POST /tournaments", "requireProfile", "后端仍建议加 profile 校验"],
  ["pages/create/volleyball", "创建排球比赛", "POST /tournaments", "requireProfile", "后端仍建议加 profile 校验"],
  ["pages/tournament/detail", "看详情 / 收藏 / 去赛程 / 看队伍", "GET /tournaments/{id}, favorite, /teams", "收藏和 goJudge 前 requireProfile", "赛程读接口当前未收紧"],
  ["pages/tournament/bracket", "看淘汰赛 / 去记分 / 看记录", "GET /bracket, GET /matches/{id}/record", "无真正后端读限制", "记录接口当前公开"],
  ["pages/tournament/groups", "看小组赛 / 生成淘汰赛 / 去记分", "GET /groups, GET /group-standings, POST /generate-knockout", "后端生成淘汰赛仅创建者", "读接口当前公开"],
  ["pages/tournament/teams", "看队伍列表", "GET /teams", "无前端门禁", "受后端公开策略影响"],
  ["pages/volleyball/lineup", "填阵容 / 填报表信息", "GET/PUT lineup-config, PUT report-meta", "无前端角色体系", "真正写权限靠后端"],
  ["pages/volleyball/scoreboard", "排球记分 / 事件 / 完赛 / 主题", "GET/PUT theme-config, PUT events, PUT finish, PUT restart", "无前端角色体系", "真正写权限靠后端"],
  ["pages/volleyball/record", "查看比赛记录 / 导出", "GET /matches/{id}/record", "无前端门禁", "建议后端收紧"],
];

mappingSheet.getRange(`A1:E${mappingRows.length}`).values = mappingRows;
mappingSheet.getRange("A1:E1").format = {
  fill: "#44546A",
  font: { bold: true, color: "#FFFFFF" },
};
mappingSheet.getRange(`A2:E${mappingRows.length}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
mappingSheet.getRange("A:A").format.columnWidthPx = 170;
mappingSheet.getRange("B:B").format.columnWidthPx = 170;
mappingSheet.getRange("C:C").format.columnWidthPx = 250;
mappingSheet.getRange("D:D").format.columnWidthPx = 180;
mappingSheet.getRange("E:E").format.columnWidthPx = 220;
mappingSheet.freezePanes.freezeRows(1);

const instructionRows = [
  ["说明项", "内容"],
  ["这份表怎么用", "先改“资源级别建议”和 L0-L5 各列权限，再填写“你的最终决策”列。"],
  ["权限值推荐", "允许 / 禁止 / 待定 / 条件允许。条件允许时，把条件写进“条件/判定规则”列。"],
  ["推荐讨论顺序", "先定公开资源边界，再定登录用户边界，再定创建者/协作者边界，最后看管理员。"],
  ["重点高风险项", "比赛记录、阵容配置、主题配置、队伍成员名单、生成淘汰赛、完赛、重开比赛。"],
  ["当前代码已知偏宽", "GET /matches/{id}/record, GET /matches/{id}/lineup-config, GET /matches/{id}/theme-config, GET /tournaments/{id}/teams。"],
  ["当前代码已知未完全后端化", "创建比赛、收藏比赛目前主要靠前端 requireProfile，后端还没有强制资料完整。"],
  ["建议最终落地方式", "用这张表先拍板规则，之后再做接口权限矩阵实现。"],
];

instructionsSheet.getRange(`A1:B${instructionRows.length}`).values = instructionRows;
instructionsSheet.getRange("A1:B1").format = {
  fill: "#44546A",
  font: { bold: true, color: "#FFFFFF" },
};
instructionsSheet.getRange(`A2:B${instructionRows.length}`).format.borders = { preset: "all", style: "thin", color: "#D9E2F3" };
instructionsSheet.getRange("A:A").format.columnWidthPx = 180;
instructionsSheet.getRange("B:B").format.columnWidthPx = 520;
instructionsSheet.freezePanes.freezeRows(1);

for (const sheet of [roleSheet, enumSheet, matrixSheet, mappingSheet, instructionsSheet]) {
  sheet.showGridLines = true;
}

const preview = await workbook.render({
  sheetName: "权限矩阵主表",
  range: "A1:T22",
  scale: 1.4,
  format: "png",
});

await fs.writeFile(
  path.join(outputDir, "权限矩阵主表预览.png"),
  new Uint8Array(await preview.arrayBuffer()),
);

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(outputFile);

console.log(outputFile);
