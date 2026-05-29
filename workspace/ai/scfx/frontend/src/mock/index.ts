import { generateMockScripts, generateMockExecutions, generateMockVersions } from './generators'

const scripts = generateMockScripts(10)
const executions = generateMockExecutions(1, 20)
const versions = generateMockVersions(1, 5)

export const mockApi = {
  // Script APIs
  getScripts: () => Promise.resolve({ data: scripts }),
  getScriptById: (id: number) => Promise.resolve({ data: scripts.find(s => s.id === id) }),

  // Execution APIs
  getExecutions: (scriptId: number) => Promise.resolve({ data: executions }),

  // Version APIs
  getVersions: (scriptId: number) => Promise.resolve({ data: versions }),
  getVersionById: (scriptId: number, versionId: number) => Promise.resolve({ data: versions.find(v => v.id === versionId) }),

  // Simulate network delay
  delay: (ms: number = 300) => new Promise(resolve => setTimeout(resolve, ms)),
}

export { scripts, executions, versions }