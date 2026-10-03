import { describe, expect, it } from 'vitest'
import { parseComputerDocView } from './computerView'

describe('parseComputerDocView', () => {
  it('parses html v2', () => {
    expect(
      parseComputerDocView({
        version: 2,
        title: '选题',
        format: 'html',
        content: '<p>a</p>',
      }),
    ).toEqual({
      version: 2,
      title: '选题',
      format: 'html',
      content: '<p>a</p>',
    })
  })

  it('rejects v1 blocks', () => {
    expect(
      parseComputerDocView({ version: 1, title: 'x', blocks: [] }),
    ).toBeNull()
  })

  it('rejects bad format', () => {
    expect(
      parseComputerDocView({ version: 2, title: 'x', format: 'pdf', content: 'a' }),
    ).toBeNull()
  })
})
