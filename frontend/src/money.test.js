import { describe, expect, it } from 'vitest';
import { formatMoney, totalsByCurrency } from './money.js';
describe('currency handling', () => {
  it('keeps USD and KHR aggregates in separate buckets', () => {
    expect(totalsByCurrency([{currency:'USD',amount:'2.25'},{currency:'KHR',amount:'3000'},{currency:'USD',amount:'1.75'}])).toEqual({USD:4,KHR:3000});
  });
  it('rejects currencies outside the V1 set', () => { expect(() => totalsByCurrency([{currency:'EUR',amount:5}])).toThrow('Unsupported currency'); });
  it('formats display values with currency labels', () => { expect(formatMoney(1200,'KHR')).toContain('KHR'); });
});
