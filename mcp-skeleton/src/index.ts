// 문항 은행 MCP 서버 골격 (TypeScript SDK v2 · stdio)
//
// stdio 서버에서는 stdout 이 곧 프로토콜 채널이다.
// 로그는 console.error 만 쓴다(stdout 으로 찍는 로그 함수는 절대 쓰지 않는다).
import { McpServer } from '@modelcontextprotocol/server';
import { StdioServerTransport } from '@modelcontextprotocol/server/stdio';
import * as z from 'zod/v4';
import { searchItems } from './itemApi.js';

const server = new McpServer({ name: 'item-bank', version: '1.0.0' });

// 예시 도구: 서버가 살아 있는지 확인한다. 입력 없음.
server.registerTool(
  'ping',
  {
    description: '서버 연결 확인용 예시 도구. "pong" 을 돌려준다.'
  },
  async () => ({
    content: [{ type: 'text', text: 'pong' }]
  })
);

// ================================================================
// 여기에 도구를 등록합니다
//
//   server.registerTool('도구_이름', { description, inputSchema }, handler)
//
// - inputSchema 는 z.object({...}) 전체 스키마로 넘긴다 (import * as z from 'zod/v4')
// - 감쌀 API 는 ./itemApi.js 에 있다 (ESM 이라 확장자를 .js 로 적는다)
// - import 문도 이 자리에 함께 붙여 넣어도 된다
// ================================================================

// 문항 검색 (읽기 전용). itemApi.searchItems 를 그대로 감싼다.
server.registerTool(
  'search_items',
  {
    description:
      '초등 수학 문항 은행에서 문항을 찾을 때 쓴다. 사용자가 특정 개념 · 계산 유형 · 태그(예: 계산, 문장제, 오답률높음)에 ' +
      '맞는 문항을 찾거나, 단원(코드 M5-1 … M6-2 또는 단원 이름)이나 난이도(하 · 중 · 상)로 문항을 추려 달라고 할 때 호출한다. ' +
      '문항 id · 단원 코드 · 난이도 · 태그 · 본문을 돌려준다. 문항을 만들거나 고치는 용도로는 쓰지 않는다.',
    inputSchema: z.object({
      keyword: z.string().describe('찾을 단어. 문항 본문이나 태그에 이 문자열이 들어 있으면 일치한다(예: "분수", "문장제")'),
      unit: z
        .string()
        .optional()
        .describe('단원 코드(예: "M5-1") 또는 단원 이름 일부(예: "분수의 곱셈"). 생략하면 모든 단원'),
      difficulty: z.enum(['하', '중', '상']).optional().describe('난이도. "하" · "중" · "상" 중 하나. 생략하면 모든 난이도'),
      limit: z.number().int().min(1).max(20).default(5).describe('돌려줄 최대 문항 수(1~20, 기본 5)')
    })
  },
  async ({ keyword, unit, difficulty, limit }) => {
    const results = await searchItems({ keyword, unit, difficulty, limit });
    console.error(`search_items: ${results.length}건`);

    if (results.length === 0) {
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({ message: '검색 결과 없음', conditions: { keyword, unit, difficulty, limit } })
          }
        ]
      };
    }

    return {
      content: [{ type: 'text', text: JSON.stringify(results) }]
    };
  }
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error('item-bank MCP server running on stdio');
}

main().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
