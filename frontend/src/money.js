export function formatMoney(amount, currency) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: currency === 'KHR' ? 0 : 2 }).format(Number(amount || 0));
}
export function totalsByCurrency(rows) {
  return rows.reduce((totals, row) => {
    if (!['USD', 'KHR'].includes(row.currency)) throw new Error('Unsupported currency');
    totals[row.currency] = (totals[row.currency] || 0) + Number(row.amount);
    return totals;
  }, { USD: 0, KHR: 0 });
}
