#!/usr/bin/env node
/**
 * KnowBrain RAG 评测体系 — 一键初始化脚本
 *
 * 功能：登录 → 建空间 → 上传文档 → 建评测数据集 → 导入问题 → (可选)基线评测
 *
 * 用法：
 *   # 默认连接 localhost:8080
 *   node provision.mjs
 *
 *   # 连接远程服务
 *   KB_BASE_URL=http://192.168.1.100:8080 node provision.mjs
 *
 *   # 自定义管理员账号
 *   KB_ADMIN_USER=admin KB_ADMIN_PASS=Admin@123 node provision.mjs
 *
 *   # 初始化后自动运行基线评测
 *   RUN_EVAL=true node provision.mjs
 *
 * 前置条件：
 *   1. KnowBrain 服务已启动（Docker Compose 或本地）
 *   2. 数据库中已有管理员账号（默认 admin/Admin@123）
 *   3. Node.js 18+ 可用
 *
 * 可重复执行 — 每次执行会创建新的空间、数据集和运行记录。
 */

import { readdir, readFile, stat } from 'node:fs/promises';
import { join, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

// ============================================================
// 配置区（可通过环境变量覆盖）
// ============================================================
const BASE_URL  = process.env.KB_BASE_URL  || 'http://localhost:8080';
const ADMIN     = process.env.KB_ADMIN_USER || 'admin';
const PASSWORD  = process.env.KB_ADMIN_PASS || 'KnowBrain@2026';
const RUN_EVAL  = process.env.RUN_EVAL === 'true';  // 是否自动触发基线评测
const BATCH     = parseInt(process.env.KB_BATCH || '3', 10);  // 并发上传数

const SCRIPT_DIR = fileURLToPath(new URL('.', import.meta.url));
const FAQ_DIR    = join(SCRIPT_DIR, '..', 'knowbrain-server', 'src', 'main', 'resources', 'scenarios');

// 场景定义
const SCENARIOS = [
  {
    key: 'it-helpdesk',
    label: 'IT 运维',
    spaceName: 'IT 运维测试库',
    spaceDesc: 'RAG 评测用 — IT 运维场景测试文档（VPN/网络/安全/设备等）',
    datasetName: 'IT 运维回归测试',
    datasetDesc: '覆盖 VPN/WiFi/邮箱/打印机/安全/账号等 15 类常见 IT 问题',
  },
  {
    key: 'hr-policy',
    label: 'HR 制度',
    spaceName: 'HR 制度测试库',
    spaceDesc: 'RAG 评测用 — HR 制度场景测试文档（假期/薪酬/入离职/晋升等）',
    datasetName: 'HR 制度回归测试',
    datasetDesc: '覆盖年假/婚假/产假/薪酬/入离职/晋升等 19 类常见 HR 问题',
  },
];

// ============================================================
// 工具函数
// ============================================================

function bold(text) { return `\x1b[1m${text}\x1b[0m`; }
function green(text) { return `\x1b[32m${text}\x1b[0m`; }
function yellow(text) { return `\x1b[33m${text}\x1b[0m`; }
function red(text) { return `\x1b[31m${text}\x1b[0m`; }
function dim(text) { return `\x1b[2m${text}\x1b[0m`; }

const log = {
  step: (n, msg) => console.log(`\n${bold(`[${n}]`)} ${bold(msg)}`),
  ok: (msg) => console.log(`  ${green('✔')} ${msg}`),
  warn: (msg) => console.log(`  ${yellow('⚠')} ${msg}`),
  err: (msg) => console.log(`  ${red('✘')} ${msg}`),
  info: (msg) => console.log(`  ${dim(msg)}`),
};

/** 统一 API 调用 */
async function api(method, path, body, token, isMultipart = false) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (isMultipart) delete headers['Content-Type']; // 让 fetch 自动设 boundary

  const opts = { method, headers };
  if (body) {
    opts.body = isMultipart ? body : JSON.stringify(body);
  }

  const url = `${BASE_URL}/api/v1${path}`;
  const res = await fetch(url, opts);
  const data = await res.json();

  if (data.code !== 200) {
    throw new Error(`${method} ${path} → ${res.status} ${data.message || JSON.stringify(data)}`);
  }
  return data;
}

/** 构造 multipart 上传 body（纯手工，零依赖） */
function createUploadBody(fileName, fileContent, spaceId, category) {
  const boundary = `----KnowBrainProvision${Date.now()}`;
  const CRLF = '\r\n';
  const parts = [];

  // 文件字段
  parts.push(`--${boundary}`);
  parts.push(`Content-Disposition: form-data; name="file"; filename="${fileName}"`);
  parts.push('Content-Type: text/markdown');
  parts.push('');
  parts.push(fileContent);

  // spaceId 字段
  parts.push(`--${boundary}`);
  parts.push('Content-Disposition: form-data; name="spaceId"');
  parts.push('');
  parts.push(String(spaceId));

  // category 字段
  parts.push(`--${boundary}`);
  parts.push('Content-Disposition: form-data; name="category"');
  parts.push('');
  parts.push(category || '');

  // 结束
  parts.push(`--${boundary}--`);
  parts.push('');

  const body = parts.join(CRLF);
  const contentType = `multipart/form-data; boundary=${boundary}`;
  return { body, contentType };
}

/** 上传单个文档 */
async function uploadFile(filePath, spaceId, category, token) {
  const content = await readFile(filePath, 'utf-8');
  const fileName = basename(filePath);
  const { body, contentType } = createUploadBody(fileName, content, spaceId, category);

  const res = await fetch(`${BASE_URL}/api/v1/documents/upload`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': contentType,
    },
    body,
  });
  const data = await res.json();
  if (data.code !== 200) {
    throw new Error(`上传 ${fileName} 失败: ${data.message || JSON.stringify(data)}`);
  }
  return data.data;  // EkDocument
}

/** 分批并发执行 */
async function batchRun(items, fn, concurrency) {
  const results = [];
  for (let i = 0; i < items.length; i += concurrency) {
    const batch = items.slice(i, i + concurrency);
    const batchResults = await Promise.allSettled(batch.map(fn));
    results.push(...batchResults);
  }
  return results;
}

/** 读取 FAQ JSON，返回评测问题数组 */
async function loadFaqQuestions(scenarioKey) {
  const faqPath = join(FAQ_DIR, scenarioKey, 'faq.json');
  const raw = await readFile(faqPath, 'utf-8');
  const faqs = JSON.parse(raw);
  return faqs.map(faq => ({
    question: faq.question,
    expectedAnswer: faq.answer,
    expectedDocIds: null,  // 暂不标注预期文档（MVP 不校验 Context Precision）
  }));
}

/** 根据文件名推断 category（文件名格式: NN-category.md） */
function inferCategory(fileName) {
  const base = basename(fileName, extname(fileName));  // 01-vpn
  return base.replace(/^\d{2}-/, '');  // vpn
}

// ============================================================
// 主流程
// ============================================================

async function main() {
  const startTime = Date.now();
  console.log(bold('\n╔══════════════════════════════════════╗'));
  console.log(bold('║  KnowBrain RAG 评测体系 · 初始化脚本  ║'));
  console.log(bold('╚══════════════════════════════════════╝'));
  console.log(dim(`  服务地址: ${BASE_URL}`));
  console.log(dim(`  管理员:   ${ADMIN}`));
  console.log(dim(`  自动评测: ${RUN_EVAL ? '是' : '否'}`));
  console.log(dim(`  并发数:   ${BATCH}`));

  // ---- 第 1 步：登录 ----
  log.step(1, '管理员登录...');
  let token;
  try {
    const authRes = await api('POST', '/auth/login', { username: ADMIN, password: PASSWORD });
    token = authRes.data.token;
    log.ok(`登录成功 (token: ${token.substring(0, 20)}...)`);
  } catch (e) {
    log.err(`登录失败: ${e.message}`);
    log.warn('请确认服务已启动，且管理员账号存在。若首次部署，请先注册管理员账号。');
    process.exit(1);
  }

  // ---- 对每个场景执行 ----
  const spaceMap = {};   // scenarioKey → spaceId
  const datasetMap = {}; // scenarioKey → datasetId

  for (const scenario of SCENARIOS) {
    console.log(`\n${bold('─'.repeat(50))}`);
    console.log(bold(`  场景: ${scenario.label} (${scenario.key})`));
    console.log(bold('─'.repeat(50)));

    // ---- 第 2 步：创建空间 ----
    log.step(2, `创建知识空间「${scenario.spaceName}」...`);
    try {
      const spaceRes = await api('POST', '/spaces', {
        name: scenario.spaceName,
        description: scenario.spaceDesc,
        visibility: 'SPACE',
      }, token);
      const spaceId = spaceRes.data.id;
      spaceMap[scenario.key] = spaceId;
      log.ok(`空间已创建 (id=${spaceId})`);
    } catch (e) {
      log.err(`创建空间失败: ${e.message}`);
      continue;
    }

    // ---- 第 3 步：上传文档 ----
    const docsDir = join(SCRIPT_DIR, scenario.key);
    let files;
    try {
      const entries = await readdir(docsDir);
      files = entries
        .filter(f => f.endsWith('.md') && f !== 'README.md')
        .sort()
        .map(f => join(docsDir, f));
    } catch (e) {
      log.err(`读取文档目录失败: ${docsDir}`);
      continue;
    }

    log.step(3, `上传 ${files.length} 篇测试文档 (并发=${BATCH})...`);
    let uploaded = 0, failed = 0;
    const uploadResults = await batchRun(files, async (filePath) => {
      const fileName = basename(filePath);
      const category = inferCategory(fileName);
      try {
        const doc = await uploadFile(filePath, spaceMap[scenario.key], category, token);
        log.ok(`${fileName} → id=${doc.id} 分类=${category}`);
        uploaded++;
        return doc;
      } catch (e) {
        log.err(`${fileName} 上传失败: ${e.message}`);
        failed++;
        return null;
      }
    }, BATCH);

    log.info(`上传完成: 成功 ${uploaded}/${files.length} 篇` + (failed ? `, 失败 ${failed} 篇` : ''));

    // ---- 第 4 步：创建评测数据集 ----
    log.step(4, `创建评测数据集「${scenario.datasetName}」...`);
    let datasetId;
    try {
      const dsRes = await api('POST', '/admin/evaluation/datasets', {
        name: scenario.datasetName,
        description: scenario.datasetDesc,
        scenario: scenario.key,
      }, token);
      datasetId = dsRes.data.id;
      datasetMap[scenario.key] = datasetId;
      log.ok(`数据集已创建 (id=${datasetId})`);
    } catch (e) {
      log.err(`创建数据集失败: ${e.message}`);
      continue;
    }

    // ---- 第 5 步：导入评测问题 ----
    log.step(5, '从 FAQ 导入评测问题...');
    try {
      const questions = await loadFaqQuestions(scenario.key);
      const batchRes = await api('POST',
        `/admin/evaluation/datasets/${datasetId}/questions/batch`,
        questions,
        token
      );
      log.ok(`已导入 ${questions.length} 个问题`);
    } catch (e) {
      log.err(`导入问题失败: ${e.message}`);
    }
  }

  // ---- 第 6 步（可选）：运行基线评测 ----
  if (RUN_EVAL) {
    console.log(`\n${bold('─'.repeat(50))}`);
    log.step(6, '自动触发基线评测...');
    for (const scenario of SCENARIOS) {
      const dsId = datasetMap[scenario.key];
      if (!dsId) continue;
      try {
        const runRes = await api('POST', '/admin/evaluation/runs', { datasetId: dsId }, token);
        log.ok(`${scenario.label}: 评测已启动 (runId=${runRes.data.id})`);
      } catch (e) {
        log.err(`${scenario.label}: 启动评测失败: ${e.message}`);
      }
    }
  }

  // ---- 汇总 ----
  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`\n${bold('═'.repeat(50))}`);
  console.log(green(bold('  初始化完成！')));
  console.log(dim(`  耗时: ${elapsed}s`));
  console.log('');
  console.log(bold('  空间:'));
  for (const [key, id] of Object.entries(spaceMap)) {
    console.log(`    ${SCENARIOS.find(s => s.key === key).label}: spaceId=${id}`);
  }
  console.log(bold('  数据集:'));
  for (const [key, id] of Object.entries(datasetMap)) {
    console.log(`    ${SCENARIOS.find(s => s.key === key).label}: datasetId=${id}`);
  }
  console.log('');
  if (RUN_EVAL) {
    console.log(dim('  基线评测已在后台执行，请前往管理后台「评测管理」查看结果。'));
  } else {
    console.log(dim('  提示: 设置 RUN_EVAL=true 可在初始化后自动触发基线评测。'));
  }
  console.log(dim(`  管理后台: ${BASE_URL}/admin/evaluation`));
  console.log('');
}

main().catch(e => {
  console.error(red(`\n脚本异常退出: ${e.message}`));
  console.error(e.stack);
  process.exit(1);
});
