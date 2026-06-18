import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const cwd = process.cwd();
const workbookPath = path.join(
  cwd,
  "outputs",
  "auth-matrix",
  "outputs",
  "auth-matrix",
  "项目鉴权权限矩阵_可编辑版.xlsx",
);

const input = await FileBlob.load(workbookPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const sheet = workbook.worksheets.getItem("权限矩阵主表");

const values = sheet.getRange("A1:T200").values;
const header = values[0];
const dataRows = values.slice(1).filter((row) => row[0]);

const cols = {
  id: header.indexOf("编号"),
  action: header.indexOf("操作项"),
  frontend: header.indexOf("前端页面/入口"),
  backend: header.indexOf("后端接口/资源"),
  sensitivity: header.indexOf("数据敏感度"),
  resourceLevel: header.indexOf("资源级别建议"),
  status: header.indexOf("当前代码状态"),
  actual: header.indexOf("当前实际权限"),
  final: header.indexOf("你的最终决策"),
  condition: header.indexOf("条件/判定规则"),
  remark: header.indexOf("备注"),
};

function normalize(text) {
  return String(text || "").trim();
}

function decidePriority(row) {
  const id = normalize(row[cols.id]);
  const action = normalize(row[cols.action]);
  const backend = normalize(row[cols.backend]);
  const sensitivity = normalize(row[cols.sensitivity]);
  const resourceLevel = normalize(row[cols.resourceLevel]);
  const status = normalize(row[cols.status]);
  const actual = normalize(row[cols.actual]);
  const finalDecision = normalize(row[cols.final]);
  const remark = normalize(row[cols.remark]);

  const text = [action, backend, sensitivity, resourceLevel, status, actual, finalDecision, remark].join(" | ");

  const mustNowPatterns = [
    "实现过宽",
    "比赛记录",
    "/api/v1/matches/{id}/record",
    "/api/v1/matches/{id}/lineup-config",
    "/api/v1/matches/{id}/theme-config",
    "/api/v1/tournaments/{id}/teams",
    "阵容配置",
    "主题配置",
    "事件流",
    "完赛",
    "重开比赛",
    "生成淘汰赛",
    "Dev Mock 登录",
    "profileCompleted",
  ];

  const laterPatterns = [
    "赛事协作者",
    "协作者",
    "裁判",
    "系统管理员",
    "页面访问",
    "本地态",
    "导出",
    "是否允许协作者",
    "中期扩展",
    "长期预留",
  ];

  const keepPatterns = [
    "当前公开",
    "公开",
    "登录可用",
  ];

  if (mustNowPatterns.some((item) => text.includes(item))) {
    return "必须马上实现";
  }

  if (
    sensitivity === "高" &&
    ["赛事成员", "赛事管理", "系统管理"].includes(resourceLevel) &&
    (status === "部分实现" || status === "未实现")
  ) {
    return "必须马上实现";
  }

  if (laterPatterns.some((item) => text.includes(item))) {
    return "可以后面实现";
  }

  if (
    keepPatterns.some((item) => text.includes(item)) &&
    (status === "已实现" || action.includes("查看比赛列表") || action.includes("查看比赛详情"))
  ) {
    return "暂时保持现状";
  }

  if (id.startsWith("A") || id.startsWith("B") || id.startsWith("C")) {
    return "暂时保持现状";
  }

  return "可以后面实现";
}

const extendedHeader = [[...header, "推进优先级", "为什么是这个优先级"]];
const extendedRows = dataRows.map((row) => {
  const priority = decidePriority(row);
  let reason = "";
  if (priority === "必须马上实现") {
    reason = "涉及高敏感数据、越权读取/写入风险，或上线前规则必须收口。";
  } else if (priority === "可以后面实现") {
    reason = "属于角色体系扩展、协作能力扩展或流程优化，不阻塞当前单创建者模型上线。";
  } else {
    reason = "当前规则基本合理，短期内可先保持现状。";
  }
  return [...row, priority, reason];
});

sheet.getRange("A1:V200").clear({ applyTo: "all" });
sheet.getRange(`A1:V${extendedRows.length + 1}`).values = extendedHeader.concat(extendedRows);

sheet.getRange("A1:V1").format = {
  fill: "#1F4E78",
  font: { bold: true, color: "#FFFFFF" },
};
sheet.getRange(`A2:V${extendedRows.length + 1}`).format.borders = {
  preset: "all",
  style: "thin",
  color: "#D9E2F3",
};

sheet.getRange("U:U").format.columnWidthPx = 130;
sheet.getRange("V:V").format.columnWidthPx = 300;
sheet.getRange(`U2:U${extendedRows.length + 1}`).dataValidation = {
  rule: {
    type: "list",
    values: ["必须马上实现", "可以后面实现", "暂时保持现状"],
  },
};

sheet.getRange(`U2:U${extendedRows.length + 1}`).conditionalFormats.add("cellIs", {
  operator: "equal",
  formula: "\"必须马上实现\"",
  format: {
    fill: "#F8CBAD",
    font: { bold: true, color: "#833C0C" },
  },
});
sheet.getRange(`U2:U${extendedRows.length + 1}`).conditionalFormats.add("cellIs", {
  operator: "equal",
  formula: "\"可以后面实现\"",
  format: {
    fill: "#FFF2CC",
    font: { bold: true, color: "#7F6000" },
  },
});
sheet.getRange(`U2:U${extendedRows.length + 1}`).conditionalFormats.add("cellIs", {
  operator: "equal",
  formula: "\"暂时保持现状\"",
  format: {
    fill: "#E2F0D9",
    font: { bold: true, color: "#2F5233" },
  },
});

sheet.freezePanes.freezeRows(1);
sheet.freezePanes.freezeColumns(6);

const preview = await workbook.render({
  sheetName: "权限矩阵主表",
  range: "A1:V24",
  scale: 1.25,
  format: "png",
});

await fs.writeFile(
  path.join(
    cwd,
    "outputs",
    "auth-matrix",
    "outputs",
    "auth-matrix",
    "权限矩阵主表预览_含优先级.png",
  ),
  new Uint8Array(await preview.arrayBuffer()),
);

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(workbookPath);

console.log(workbookPath);
