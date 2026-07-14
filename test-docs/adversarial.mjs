#!/usr/bin/env node
/**
 * KnowBrain RAG 评测体系 — 对抗测试问题导入脚本
 *
 * 功能：创建对抗测试数据集 → 导入边界场景问题 → (可选)运行评测
 *
 * 对抗问题覆盖 5 类边界场景：
 *   1. 跨文档综合 — 需要多篇文档信息整合
 *   2. 无答案问题 — 文档中不存在答案，测诚实拒绝
 *   3. 口语化/术语混用 — 口语表达 + 专业术语混合
 *   4. 模糊简短 — 单关键词或极短查询
 *   5. 多义词 — 同一表述在不同上下文有不同含义
 *
 * 用法：
 *   node adversarial.mjs                    # 仅导入问题
 *   RUN_EVAL=true node adversarial.mjs      # 导入后自动运行评测
 *
 * 前置条件：provision.mjs 已执行（基线数据集已创建）
 */

import { fileURLToPath } from 'node:url';

// ============================================================
// 配置区
// ============================================================
const BASE_URL  = process.env.KB_BASE_URL  || 'http://localhost:8080';
const ADMIN     = process.env.KB_ADMIN_USER || 'admin';
const PASSWORD  = process.env.KB_ADMIN_PASS || 'KnowBrain@2026';
const RUN_EVAL  = process.env.RUN_EVAL === 'true';

// ============================================================
// 对抗测试问题定义
// ============================================================

/**
 * 问题分类说明：
 *   cross-doc    = 跨文档综合 — 需多篇文档信息整合才能完整回答
 *   unanswerable = 无答案问题 — 文档中不存在相关信息，应诚实拒绝
 *   colloquial   = 口语化/术语混用 — 口语表达 + 专业术语混合
 *   vague        = 模糊简短 — 单个关键词或极短查询，歧义大
 *   polysemy     = 多义词 — 同一表述在不同上下文有不同含义
 */

const IT_ADVERSARIAL = [
  // ===== 跨文档综合 (3) =====
  {
    question: "新员工入职第一天，需要设置和配置哪些 IT 系统和账号？请给出完整的配置清单和操作步骤。",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 account-register + vpn + email + wifi + printer + office 共 6 篇文档"
  },
  {
    question: "我想在家远程办公，从零开始需要配置哪些东西？请按顺序说明每一步。",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 vpn + rdp + wifi + mobile 共 4 篇文档"
  },
  {
    question: "电脑中了勒索病毒，账号密码可能也泄露了，我应该按什么紧急顺序处理？先做什么后做什么？",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 anti-phishing + password-reset + data-protection 共 3 篇文档"
  },

  // ===== 无答案问题 (3) =====
  {
    question: "公司食堂在哪栋楼？提供免费午餐和晚餐吗？有什么菜系？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "文档库中没有食堂/餐饮相关信息"
  },
  {
    question: "Linux Ubuntu 系统怎么安装和配置公司 VPN 客户端？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "VPN 文档只覆盖 Windows/macOS/手机，未提 Linux"
  },
  {
    question: "怎么申请公司在阿里云上的开发服务器资源？需要什么审批？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "文档库中没有云服务器申请相关内容"
  },

  // ===== 口语化/术语混用 (3) =====
  {
    question: "那个公司的 wifi 咋连啊？我手机老显示连不上，密码也不知道是啥。",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化 WiFi 问题 — 测试口语理解 + 企业 WiFi 术语（SSID/802.1X）映射"
  },
  {
    question: "我电脑好卡好卡，开机要五分钟，打开个 word 文档都转半天圈圈，这破电脑咋整啊？",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化 PC 性能问题 — 测试「卡」「慢」「转圈」→ 系统优化术语映射"
  },
  {
    question: "手机上那个公司邮件的 app 叫啥来着？收不到新邮件推送，咋设置？",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化移动邮箱问题 — 测试「推送」「app」→ Exchange/IMAP 术语映射"
  },

  // ===== 模糊简短 (3) =====
  {
    question: "密码",
    expectedAnswer: null,
    category: "vague",
    note: "单关键词 — 可能是忘记密码/修改密码/密码要求/密码过期"
  },
  {
    question: "连不上",
    expectedAnswer: null,
    category: "vague",
    note: "极简短 — 可能是 VPN/WiFi/邮箱/打印机/远程桌面"
  },
  {
    question: "远程",
    expectedAnswer: null,
    category: "vague",
    note: "单关键词多义 — VPN 远程接入 / RDP 远程桌面 / 远程办公"
  },

  // ===== 多义词 (3) =====
  {
    question: "打卡失败怎么办？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「打卡」多义 — 考勤打卡失败 vs VPN 连接失败（口语也说打卡）"
  },
  {
    question: "账号被锁了，怎么解锁？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「账号」多义 — 可能是 VPN 账号/邮箱账号/域账号/WiFi 认证"
  },
  {
    question: "软件怎么下载和安装？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「下载安装」多义 — 可能是公司软件白名单自助安装 vs 新软件申请采购"
  }
];

const HR_ADVERSARIAL = [
  // ===== 跨文档综合 (3) =====
  {
    question: "新员工从入职到转正这三个月里，能享受哪些假期？工资是什么时候开始发的？社保什么时候开始交？",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 onboarding + probation + annual-leave + salary + social-insurance 共 5 篇文档"
  },
  {
    question: "如果在试用期内怀孕了，产假还能正常休吗？转正会受影响吗？生育津贴怎么算？",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 probation + maternity-leave + social-insurance 共 3 篇文档"
  },
  {
    question: "我准备离职了，未休完的年假和加班调休怎么算？年终奖还能拿到吗？最后一个月社保交到什么时候？",
    expectedAnswer: null,
    category: "cross-doc",
    note: "涉及 offboarding + annual-leave + overtime + bonus + social-insurance 共 5 篇文档"
  },

  // ===== 无答案问题 (3) =====
  {
    question: "公司有员工宿舍吗？提供租房补贴吗？补贴标准是多少？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "文档库中没有住宿/房补相关信息"
  },
  {
    question: "公司给员工发股票期权吗？期权怎么行权？行权价是多少？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "文档库中没有股票期权相关内容"
  },
  {
    question: "公司有托儿所或者合作幼儿园吗？员工子女入学有优惠吗？",
    expectedAnswer: null,
    category: "unanswerable",
    note: "文档库中没有子女教育/托育相关信息"
  },

  // ===== 口语化/术语混用 (3) =====
  {
    question: "生孩子的假到底能休几天啊？那个钱是公司发的还是社保发的？到手能有多少？",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化产假问题 — 「生孩子的假」→「产假」，「社保发的钱」→「生育津贴」"
  },
  {
    question: "每个月工资条上扣的那些钱都扣的啥呀？养老医保公积金扣完到手少了好多。",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化薪酬问题 — 「扣的钱」→「五险一金个人缴纳」，「到手」→「税后工资」"
  },
  {
    question: "不想干了，辞职要怎么弄？多久能走？最后一个月工资什么时候结？",
    expectedAnswer: null,
    category: "colloquial",
    note: "口语化离职问题 — 「不想干了」→「离职申请」，「怎么弄」→「离职流程」"
  },

  // ===== 模糊简短 (3) =====
  {
    question: "请假",
    expectedAnswer: null,
    category: "vague",
    note: "单关键词 — 可能是年假/病假/事假/婚假/产假/陪产假"
  },
  {
    question: "钱",
    expectedAnswer: null,
    category: "vague",
    note: "极简短 — 工资/奖金/报销/加班费/公积金提取/生育津贴/赔偿金"
  },
  {
    question: "合同",
    expectedAnswer: null,
    category: "vague",
    note: "单关键词多义 — 劳动合同/续签/试用期合同/竞业限制协议"
  },

  // ===== 多义词 (3) =====
  {
    question: "合同到期了怎么办？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「到期」多义 — 劳动合同到期续签 vs 年假到期清零 vs 试用期到期转正"
  },
  {
    question: "奖金什么时候发？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「奖金」多义 — 年终奖（春节前） vs 绩效奖金（月度/季度） vs 内推奖金（转正后）"
  },
  {
    question: "我要报销，流程怎么走？",
    expectedAnswer: null,
    category: "polysemy",
    note: "「报销」多义 — 差旅报销 vs 医疗报销 vs 办公用品报销 vs 加班餐补"
  }
];

// ============================================================
// 工具函数
// ============================================================

function bold(text) { return `\x1b[1m${text}\x1b[0m`; }
function green(text) { return `\x1b[32m${text}\x1b[0m`; }
function yellow(text) { return `\x1b[33m${text}\x1b[0m`; }
function red(text) { return `\x1b[31m${text}\x1b[0m`; }
function dim(text) { return `\x1b[2m${text}\x1b[0m`; }
function cyan(text) { return `\x1b[36m${text}\x1b[0m`; }

const log = {
  step: (n, msg) => console.log(`\n${bold(`[${n}]`)} ${bold(msg)}`),
  ok: (msg) => console.log(`  ${green('✔')} ${msg}`),
  warn: (msg) => console.log(`  ${yellow('⚠')} ${msg}`),
  err: (msg) => console.log(`  ${red('✘')} ${msg}`),
  info: (msg) => console.log(`  ${dim(msg)}`),
  cat: (cat, count) => console.log(`    ${cyan(cat)}: ${count} 题`),
};

async function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const opts = { method, headers };
  if (body) opts.body = JSON.stringify(body);

  const url = `${BASE_URL}/api/v1${path}`;
  const res = await fetch(url, opts);
  const data = await res.json();

  if (data.code !== 200) {
    throw new Error(`${method} ${path} → ${res.status} ${data.message || JSON.stringify(data)}`);
  }
  return data;
}

// ============================================================
// 主流程
// ============================================================

const SCENARIOS = [
  {
    key: 'it-helpdesk',
    label: 'IT 运维',
    datasetName: 'IT 运维对抗测试',
    datasetDesc: '边界场景评测 — 跨文档综合/无答案/口语化/模糊简短/多义词，验证 RAG 鲁棒性',
    questions: IT_ADVERSARIAL,
  },
  {
    key: 'hr-policy',
    label: 'HR 制度',
    datasetName: 'HR 制度对抗测试',
    datasetDesc: '边界场景评测 — 跨文档综合/无答案/口语化/模糊简短/多义词，验证 RAG 鲁棒性',
    questions: HR_ADVERSARIAL,
  },
];

async function main() {
  const startTime = Date.now();
  console.log(bold('\n╔══════════════════════════════════════╗'));
  console.log(bold('║  KnowBrain RAG 评测 · 对抗测试导入  ║'));
  console.log(bold('╚══════════════════════════════════════╝'));
  console.log(dim(`  服务地址: ${BASE_URL}`));
  console.log(dim(`  自动评测: ${RUN_EVAL ? '是' : '否'}`));
  console.log(dim(`  问题总数: ${IT_ADVERSARIAL.length + HR_ADVERSARIAL.length} 题 (IT ${IT_ADVERSARIAL.length} + HR ${HR_ADVERSARIAL.length})`));

  // ---- 第 1 步：登录 ----
  log.step(1, '管理员登录...');
  let token;
  try {
    const authRes = await api('POST', '/auth/login', { username: ADMIN, password: PASSWORD });
    token = authRes.data.token;
    log.ok(`登录成功`);
  } catch (e) {
    log.err(`登录失败: ${e.message}`);
    process.exit(1);
  }

  const datasetMap = {};

  for (const scenario of SCENARIOS) {
    console.log(`\n${bold('─'.repeat(50))}`);
    console.log(bold(`  场景: ${scenario.label} (${scenario.key})`));
    console.log(bold('─'.repeat(50)));

    // ---- 第 2 步：创建对抗测试数据集 ----
    log.step(2, `创建对抗测试数据集「${scenario.datasetName}」...`);
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

    // ---- 第 3 步：按类别统计并导入 ----
    const questions = scenario.questions;
    const catCount = {};
    for (const q of questions) {
      catCount[q.category] = (catCount[q.category] || 0) + 1;
    }

    log.step(3, `导入 ${questions.length} 个对抗测试问题...`);
    log.info('  分类分布:');
    const catLabels = {
      'cross-doc': '跨文档综合',
      'unanswerable': '无答案问题',
      'colloquial': '口语化/术语混用',
      'vague': '模糊简短',
      'polysemy': '多义词',
    };
    for (const [cat, count] of Object.entries(catCount)) {
      log.cat(`${catLabels[cat] || cat} (${cat})`, count);
    }

    // 将问题转为 API 格式（只保留 question, expectedAnswer, expectedDocIds）
    const apiQuestions = questions.map(q => ({
      question: q.question,
      expectedAnswer: q.expectedAnswer,
      expectedDocIds: null,
    }));

    try {
      const batchRes = await api('POST',
        `/admin/evaluation/datasets/${datasetId}/questions/batch`,
        apiQuestions,
        token
      );
      log.ok(`已导入 ${questions.length} 个问题`);

      // 打印每个问题的分类标注
      log.info('  问题明细:');
      for (let i = 0; i < questions.length; i++) {
        const q = questions[i];
        const catLabel = catLabels[q.category] || q.category;
        log.info(`    ${String(i + 1).padStart(2)}. [${catLabel}] ${q.question.substring(0, 60)}${q.question.length > 60 ? '...' : ''}`);
      }
    } catch (e) {
      log.err(`导入问题失败: ${e.message}`);
    }
  }

  // ---- 第 4 步（可选）：运行对抗评测 ----
  if (RUN_EVAL) {
    console.log(`\n${bold('─'.repeat(50))}`);
    log.step(4, '自动触发对抗评测...');
    for (const scenario of SCENARIOS) {
      const dsId = datasetMap[scenario.key];
      if (!dsId) continue;
      try {
        const runRes = await api('POST', '/admin/evaluation/runs', { datasetId: dsId }, token);
        log.ok(`${scenario.label}: 对抗评测已启动 (runId=${runRes.data.id})`);
      } catch (e) {
        log.err(`${scenario.label}: 启动评测失败: ${e.message}`);
      }
    }
  }

  // ---- 汇总 ----
  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
  console.log(`\n${bold('═'.repeat(50))}`);
  console.log(green(bold('  对抗测试问题导入完成！')));
  console.log(dim(`  耗时: ${elapsed}s`));
  console.log('');
  console.log(bold('  对抗测试数据集:'));
  for (const [key, id] of Object.entries(datasetMap)) {
    const s = SCENARIOS.find(s => s.key === key);
    console.log(`    ${s.label}: datasetId=${id} (${s.questions.length} 题)`);
  }
  console.log('');
  console.log(bold('  预期评测差异:'));
  console.log(dim('  ┌─────────────────┬──────────────────────┐'));
  console.log(dim('  │ 问题类别         │ 预期得分特征          │'));
  console.log(dim('  ├─────────────────┼──────────────────────┤'));
  console.log(dim('  │ 跨文档综合       │ Faithfulness↓ Recall↓ │'));
  console.log(dim('  │ 无答案问题       │ Faithfulness↓ Relev↓  │'));
  console.log(dim('  │ 口语化/术语混用  │ Recall↓ (术语不匹配)  │'));
  console.log(dim('  │ 模糊简短         │ Relevance↓ Recall↓    │'));
  console.log(dim('  │ 多义词           │ Relevance↓ Recall↓    │'));
  console.log(dim('  └─────────────────┴──────────────────────┘'));
  console.log('');
  if (RUN_EVAL) {
    console.log(dim('  对抗评测已在后台执行，请前往管理后台「评测管理」查看结果。'));
  } else {
    console.log(dim('  提示: 设置 RUN_EVAL=true 可在导入后自动触发评测。'));
  }
  console.log(dim(`  管理后台: ${BASE_URL}/admin/evaluation`));
  console.log('');
}

main().catch(e => {
  console.error(red(`\n脚本异常退出: ${e.message}`));
  console.error(e.stack);
  process.exit(1);
});
