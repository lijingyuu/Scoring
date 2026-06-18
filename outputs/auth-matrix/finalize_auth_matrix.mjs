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

const dataRange = sheet.getRange("A1:T200");
const values = dataRange.values;

const header = values[0];
const rows = values.slice(1).filter((row) => row[0]);

const indexes = {
  id: header.indexOf("编号"),
  resourceLevel: header.indexOf("资源级别建议"),
  l0: header.indexOf("L0 匿名访客"),
  l1: header.indexOf("L1 已登录未补资料"),
  l2: header.indexOf("L2 已登录完整资料"),
  l3: header.indexOf("L3 比赛创建者"),
  l4: header.indexOf("L4 赛事协作者/裁判"),
  l5: header.indexOf("L5 系统管理员"),
  final: header.indexOf("你的最终决策"),
  conditions: header.indexOf("条件/判定规则"),
};

function readRoleSet(row) {
  return {
    l0: row[indexes.l0],
    l1: row[indexes.l1],
    l2: row[indexes.l2],
    l3: row[indexes.l3],
    l4: row[indexes.l4],
    l5: row[indexes.l5],
  };
}

function same(value, target) {
  return String(value || "").trim() === target;
}

function buildDecision(row) {
  const roleSet = readRoleSet(row);
  const allowed = Object.entries(roleSet)
    .filter(([, value]) => same(value, "允许"))
    .map(([role]) => role.toUpperCase());
  const conditional = Object.entries(roleSet)
    .filter(([, value]) => same(value, "条件允许"))
    .map(([role]) => role.toUpperCase());

  const resourceLevel = String(row[indexes.resourceLevel] || "").trim();
  const conditionText = String(row[indexes.conditions] || "").trim();

  if (allowed.length === 6) {
    return "公开允许";
  }

  if (resourceLevel === "公开") {
    return "公开可用";
  }

  if (resourceLevel === "登录后") {
    return "登录用户可用";
  }

  if (resourceLevel === "完整资料后") {
    return "仅完整资料用户及以上可用";
  }

  if (resourceLevel === "赛事成员") {
    if (conditional.length) {
      return `赛事成员可用，${conditional.join(" / ")}需附加条件`;
    }
    return "仅赛事成员及以上可用";
  }

  if (resourceLevel === "赛事管理") {
    if (conditional.length) {
      return `仅赛事管理层可用，${conditional.join(" / ")}按条件放开`;
    }
    return "仅赛事管理层可用";
  }

  if (resourceLevel === "系统管理") {
    return "仅系统管理员可用";
  }

  if (conditionText) {
    return `按条件控制：${conditionText}`;
  }

  if (allowed.length) {
    return `允许角色：${allowed.join(" / ")}`;
  }

  return "待定";
}

const finalColumnValues = [["你的最终决策"]];

for (const row of rows) {
  const existing = String(row[indexes.final] || "").trim();
  if (existing) {
    finalColumnValues.push([existing]);
    continue;
  }
  finalColumnValues.push([buildDecision(row)]);
}

sheet.getRange(`R1:R${finalColumnValues.length}`).values = finalColumnValues;
sheet.getRange(`R2:R${finalColumnValues.length}`).format.wrapText = true;

const preview = await workbook.render({
  sheetName: "权限矩阵主表",
  range: "A1:T22",
  scale: 1.4,
  format: "png",
});

await fs.writeFile(
  path.join(
    cwd,
    "outputs",
    "auth-matrix",
    "outputs",
    "auth-matrix",
    "权限矩阵主表预览_最终版.png",
  ),
  new Uint8Array(await preview.arrayBuffer()),
);

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(workbookPath);

console.log(workbookPath);
