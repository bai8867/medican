import { buildAiTherapyPlanData } from '../src/mocks/aiTherapyPlanResponse'

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = []
    req.on('data', (c) => chunks.push(c))
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8')
        resolve(raw ? JSON.parse(raw) : {})
      } catch (e) {
        reject(e)
      }
    })
    req.on('error', reject)
  })
}

function delay(ms) {
  return new Promise((r) => setTimeout(r, ms))
}

/**
 * 开发环境模拟 POST /api/ai/generate、/api/ai/feedback，返回 PRD 5.4.3 结构
 */
export function aiApiDevMockPlugin(options = {}) {
  const apiPrefix = (options.apiPrefix || '/api').replace(/\/$/, '') || '/api'
  const genPath = `${apiPrefix}/ai/generate`
  const fbPath = `${apiPrefix}/ai/feedback`

  return {
    name: 'ai-api-dev-mock',
    enforce: 'pre',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url?.split('?')[0] || ''
        if (req.method !== 'POST' || (url !== genPath && url !== fbPath)) {
          next()
          return
        }

        readJsonBody(req)
          .then(async (body) => {
            if (url === genPath) {
              // 验收：模拟接口在 5 秒内返回（约 3.2s，可感知加载态）
              await delay(3200)
              const symptom = typeof body.symptom === 'string' ? body.symptom : ''
              const vague = symptom.trim().length > 0 && symptom.trim().length < 4
              const data = buildAiTherapyPlanData({
                symptom,
                constitution:
                  typeof body.constitution === 'string' && body.constitution
                    ? body.constitution
                    : undefined,
              })
              const payload = vague ? { ...data, isGenericPlan: true } : data
              res.statusCode = 200
              res.setHeader('Content-Type', 'application/json; charset=utf-8')
              res.end(JSON.stringify({ code: 200, data: payload }))
              return
            }
            await delay(120)
            res.statusCode = 200
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ code: 200, data: { received: true } }))
          })
          .catch((err) => {
            res.statusCode = 500
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ code: 500, message: err?.message || 'mock error' }))
          })
      })
    },
  }
}
