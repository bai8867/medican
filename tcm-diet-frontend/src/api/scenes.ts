import request from './request'

export function fetchScenes() {
  return request.get('/scenes') as Promise<{ list: unknown[] }>
}

export function fetchSceneSolution(id: number | string) {
  return request.get(`/scenes/${id}/recipes`)
}
