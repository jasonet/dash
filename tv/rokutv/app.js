const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const shell = $('.tv-shell');
const sheet = $('.sheet');
const instruments = [
  ['SPX', '美国 · 标普500指数', 5648.40, 30.26, '1.82B'],
  ['IXIC', '美国 · 纳斯达克指数', 17713.62, 146.03, '3.24B'],
  ['HSI', '香港 · 恒生指数', 17444.30, -67.86, '926亿'],
  ['SSE', '中国 · 上证指数', 2765.81, -4.67, '2381亿'],
  ['BTC', '数字资产 · Bitcoin / USD', 58214, 713, '186亿'],
  ['NVDA', '美国 · 英伟达 / USD', 119.10, 2.24, '218M'],
  ['AAPL', '美国 · 苹果 / USD', 222.50, 0.40, '42M'],
  ['TSLA', '美国 · 特斯拉 / USD', 226.17, -3.49, '76M'],
  ['BABA', '美国 · 阿里巴巴 / USD', 84.69, -0.63, '19M'],
].map(([symbol, name, price, delta, volume], index) => ({ symbol, name, price, delta, volume, index }));
let selected = instruments[0];
let range = 0;
let returnFocus = null;
let toastTimer;
let favorites = ['NVDA', 'AAPL', 'TSLA', 'BABA'];
try {
  const saved = JSON.parse(localStorage.getItem('market-wall-favorites'));
  if (Array.isArray(saved)) favorites = [...new Set(saved.filter((s) => instruments.some((i) => i.symbol === s)))];
} catch { /* Restricted browsers can still use session-only favorites. */ }
const format = (number) => number.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function fitScreen() {
  const scale = Math.min(innerWidth / 1920, innerHeight / 1080);
  shell.style.transform = `translate(-50%, -50%) scale(${scale})`;
}
function focus(element) {
  if (!element) return;
  element.focus({ preventScroll: true });
  if (element.closest('.sheet-content')) element.scrollIntoView({ block: 'nearest' });
}
function available() {
  return [...(sheet.hidden ? shell : sheet).querySelectorAll('.focusable')].filter((el) => el.getClientRects().length && !el.closest('[hidden], [inert]'));
}
function move(key) {
  const elements = available();
  const active = document.activeElement;
  if (!elements.includes(active)) return focus(elements[0]);
  const a = active.getBoundingClientRect();
  const horizontal = key === 'ArrowLeft' || key === 'ArrowRight';
  const sign = key === 'ArrowLeft' || key === 'ArrowUp' ? -1 : 1;
  const options = elements.filter((el) => el !== active).map((el) => {
    const b = el.getBoundingClientRect();
    const dx = (b.left + b.right - a.left - a.right) / 2;
    const dy = (b.top + b.bottom - a.top - a.bottom) / 2;
    const forward = (horizontal ? dx : dy) * sign;
    const cross = Math.abs(horizontal ? dy : dx);
    const aligned = horizontal ? b.top < a.bottom && b.bottom > a.top : b.left < a.right && b.right > a.left;
    return { el, forward, score: forward + cross * 3 + (aligned ? 0 : 10000) };
  }).filter((item) => item.forward > 1).sort((a, b) => a.score - b.score);
  focus(options[0]?.el);
}

function series(item, period) {
  const change = period === 0 ? item.delta : item.price * [0, 0.022, -0.037, 0.19][period] * (item.index % 2 ? -1 : 1);
  const start = item.price - change;
  return Array.from({ length: 61 }, (_, i) => {
    const t = i / 60;
    const ripple = (Math.sin(i * 0.72 + item.index) + 0.7 * Math.sin(i * 0.27 + period)) * item.price * 0.0008 * Math.sin(Math.PI * t);
    return start + change * t + ripple;
  });
}
function renderQuote() {
  const values = series(selected, range);
  const change = values.at(-1) - values[0];
  const positive = change >= 0;
  const color = positive ? '#54e7ab' : '#ff7369';
  const min = Math.min(...values);
  const max = Math.max(...values);
  const points = values.map((v, i) => [i * 16, 258 - (v - min) / (max - min || 1) * 204]);
  const path = points.map(([x, y], i) => `${i ? 'L' : 'M'}${x},${y.toFixed(2)}`).join(' ');
  $('.section-label').textContent = selected.name;
  $('#heroValue').textContent = `${selected.symbol === 'BTC' ? '$' : ''}${format(selected.price)}`;
  $('#heroChange').className = `quote-change ${positive ? 'up' : 'down'}`;
  $('#heroChange').textContent = `${positive ? '▲ +' : '▼ '}${format(change)}  /  ${positive ? '+' : ''}${(change / values[0] * 100).toFixed(2)}%`;
  $('.chart-line').setAttribute('d', path);
  $('.chart-line').style.stroke = color;
  $('.area').setAttribute('d', `${path} L960,286 L0,286 Z`);
  $$('#chartFill stop').forEach((stop) => stop.setAttribute('stop-color', color));
  $('.cursor-line').setAttribute('x1', 960);
  $('.cursor-line').setAttribute('x2', 960);
  $('.cursor-dot').setAttribute('cx', 960);
  $('.cursor-dot').setAttribute('cy', points.at(-1)[1]);
  $('.cursor-dot').style.stroke = color;
  const period = ['1日', '1周', '1月', '1年'][range];
  $('.chart-wrap').setAttribute('aria-label', `${selected.symbol} ${period} 演示走势`);
  $('.chart-wrap svg').setAttribute('aria-label', `${selected.symbol} ${period} ${positive ? '上涨' : '下跌'}演示走势图`);
  const labels = [
    ['HSI', 'SSE'].includes(selected.symbol) ? ['09:30', '11:00', '13:30', '15:00'] : selected.symbol === 'BTC' ? ['00:00', '08:00', '16:00', '24:00'] : ['09:30', '11:00', '13:00', '16:00'],
    ['周一', '周二', '周四', '周五'], ['月初', '第2周', '第3周', '月末'], ['1月', '4月', '8月', '12月'],
  ][range];
  $$('.axis-labels text').forEach((el, i) => { el.textContent = labels[i]; });
  const stats = [format(values[0]), format(max), format(min), range === 0 ? selected.volume : '—'];
  $$('.chart-stats strong').forEach((el, i) => { el.textContent = stats[i]; });
  $$('.chart-stats span').forEach((el, i) => { el.textContent = ['区间起点', '区间最高', '区间最低', '日成交量'][i]; });
  $$('.range').forEach((el, i) => {
    el.classList.toggle('is-selected', i === range);
    el.setAttribute('aria-pressed', i === range);
  });
  $$('.ticker-card, .watch-row').forEach((el) => {
    const active = el.dataset.symbol === selected.symbol;
    el.classList.toggle('is-active', active);
    el.setAttribute('aria-pressed', active);
  });
}

function announce(text) {
  clearTimeout(toastTimer);
  $('.toast').hidden = false;
  $('.toast').textContent = text;
  toastTimer = setTimeout(() => { $('.toast').hidden = true; }, 2600);
}
function toggleFavorite(symbol) {
  const exists = favorites.includes(symbol);
  favorites = exists ? favorites.filter((s) => s !== symbol) : [...favorites, symbol];
  let persisted = true;
  try { localStorage.setItem('market-wall-favorites', JSON.stringify(favorites)); } catch { persisted = false; }
  announce(`${symbol} ${exists ? '已移出自选' : '已加入自选'}${persisted ? '' : '（仅本次会话）'}`);
}
function closeSheet() {
  sheet.hidden = true;
  [...shell.children].forEach((el) => { el.inert = false; });
  $$('.nav-item').forEach((el) => {
    el.classList.toggle('is-selected', el.dataset.section === 'global');
    el.setAttribute('aria-pressed', el.dataset.section === 'global');
  });
  focus(returnFocus?.isConnected ? returnFocus : $('.nav-item'));
}
function openSheet(section, source) {
  returnFocus = source;
  const content = $('.sheet-content');
  content.replaceChildren();
  $('#sheetTitle').textContent = { watchlist: '我的自选', news: '市场要闻', calendar: '经济日历' }[section];
  if (section === 'watchlist') {
    const hint = document.createElement('p');
    hint.textContent = favorites.length ? '选择查看走势；焦点停在行情上按 * 添加或移除。' : '自选为空。返回主屏，选中行情后按 * 添加。';
    content.append(hint);
    favorites.forEach((symbol) => {
      const item = instruments.find((i) => i.symbol === symbol);
      const button = document.createElement('button');
      button.className = 'sheet-row focusable';
      button.dataset.symbol = symbol;
      button.textContent = `${symbol}  ·  ${item.name}     ${format(item.price)}`;
      content.append(button);
    });
  } else {
    const rows = section === 'news' ? [
      ['01 / 宏观观察', '利率、通胀与市场预期', '演示摘要：展示宏观事件对不同资产的影响。正式版将在此显示来源及发布时间。'],
      ['02 / 科技板块', '聚焦半导体与大型科技公司', '演示摘要：跟踪自选公司消息、财报与行业动态。'],
      ['03 / 全球市场', '亚洲市场与跨市场表现', '演示摘要：汇总各市场交易时段与指数表现。'],
    ] : [
      ['周二 · 20:30', '美国 CPI', '示例日程 · 重要性：高 · 前值和预期尚未接入'],
      ['周四 · 02:00', '美联储利率决议', '示例日程 · 重要性：高 · 时间采用北京时间'],
      ['周五 · 20:30', '美国就业报告', '示例日程 · 重要性：高 · 非实际发布日程'],
    ];
    rows.forEach(([meta, title, body]) => {
      const article = document.createElement('article');
      article.className = 'editorial';
      [meta, title, body].forEach((text, i) => {
        const el = document.createElement(['small', 'h3', 'p'][i]);
        el.textContent = text;
        article.append(el);
      });
      content.append(article);
    });
  }
  $$('.nav-item').forEach((el) => {
    el.classList.toggle('is-selected', el.dataset.section === section);
    el.setAttribute('aria-pressed', el.dataset.section === section);
  });
  [...shell.children].forEach((el) => { if (el !== sheet && !el.matches('.toast')) el.inert = true; });
  sheet.hidden = false;
  focus($('.close-sheet'));
}

shell.addEventListener('click', (event) => {
  const el = event.target.closest('.focusable');
  if (!el) return;
  if (el.matches('.close-sheet')) return closeSheet();
  focus(el);
  if (el.dataset.section) {
    if (el.dataset.section === 'global') return;
    return openSheet(el.dataset.section, el);
  }
  if (el.matches('.range')) range = $$('.range').indexOf(el);
  if (el.dataset.symbol) {
    selected = instruments.find((i) => i.symbol === el.dataset.symbol);
    if (!sheet.hidden) closeSheet();
  }
  renderQuote();
});
document.addEventListener('keydown', (event) => {
  if (event.altKey || event.ctrlKey || event.metaKey) return;
  if (event.key.startsWith('Arrow')) {
    event.preventDefault();
    move(event.key);
  } else if (event.key === 'Escape' || event.key === 'Backspace') {
    event.preventDefault();
    if (!sheet.hidden) closeSheet();
    else focus($('.nav-item'));
  } else if (event.key === '*') {
    event.preventDefault();
    const symbol = document.activeElement?.dataset.symbol || selected.symbol;
    toggleFavorite(symbol);
    if (!sheet.hidden && $('#sheetTitle').textContent === '我的自选') openSheet('watchlist', returnFocus);
  } else if (event.key === 'Tab' && !sheet.hidden) {
    event.preventDefault();
    const items = available();
    const index = items.indexOf(document.activeElement);
    focus(items[(index + (event.shiftKey ? -1 : 1) + items.length) % items.length]);
  } else if ((event.key === 'Enter' || event.key === ' ') && document.activeElement.matches('[role="button"]')) {
    event.preventDefault();
    document.activeElement.click();
  }
});
$$('.ticker-card').forEach((el) => { el.setAttribute('role', 'button'); el.tabIndex = 0; });
$$('.watch-row').forEach((el, i) => { el.dataset.symbol = instruments[i + 5].symbol; });
$('.watch-panel .side-title > span').textContent = '热门关注';
function updateClock() {
  $('#marketClock').textContent = `纽约 ${new Intl.DateTimeFormat('zh-CN', { timeZone: 'America/New_York', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date())}`;
}
addEventListener('resize', fitScreen);
fitScreen();
renderQuote();
updateClock();
setInterval(updateClock, 10000);
focus($('.ticker-card'));
