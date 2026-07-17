export const ADMIN = {
  username: 'admin',
  password: 'KnowBrain@2026',
  name: '管理员',
  role: 'ADMIN',
};

export const TEST_USER = {
  username: 'e2e_test_user',
  password: 'Test@2026',
  name: 'E2E测试用户',
};

export const TEST_SPACE = {
  name: 'e2e-test-space',
  description: 'E2E automated test space',
  visibility: 'PRIVATE' as const,
};

export const FAQ_QUESTIONS = [
  { question: '年假有几天', expectHit: true },
  { question: '打卡失败怎么办', expectHit: true },
];

export const RAG_QUESTIONS = {
  simple: 'VPN怎么配置',
  comparison: '全职员工和外包员工远程办公有什么区别',
  noResult: '火星移民政策2026',
};

export const SSE_TIMEOUT_MS = 30000;

/** Regression 测试专用数据 */
export const REGRESSION = {
  /** 场景配置 */
  scenario: {
    category: { name: 'E2E回归测试分类', key: 'reg-test-cat' },
    glossary: { term: 'reg测试口语', formal: 'E2E回归测试正式术语', synonyms: '同义A,同义B' },
    faq: { question: '回归测试FAQ问题', answer: '预设答案内容', keywords: '回归测试,regtest', category: 'it-helpdesk' },
  },
  /** 评测 */
  evaluation: {
    datasetName: 'E2E评测回归测试',
    questions: [
      { question: 'VPN怎么配置？', expectedAnswer: '先打开VPN客户端...' },
      { question: '年假有几天？', expectedAnswer: '根据公司制度规定...' },
    ],
  },
  /** 空间 */
  space: {
    name: 'reg-test-space',
    description: 'E2E regression test space',
  },
  /** 用户 */
  user: {
    username: 'reg-test-user',
    password: 'Reg@2026',
    name: '回归测试用户',
  },
};
