// The side drawer: source files, the list of red flags, unresolved calls.

import { escapeHtml as e, FLAG_NAMES } from './util.js';

const TOKEN = new RegExp([
  '(\\/\\*[\\s\\S]*?\\*\\/|\\/\\/[^\\n]*)',
  '("(?:\\\\.|[^"\\\\\\n])*"|\'(?:\\\\.|[^\'\\\\\\n])*\')',
  '(@\\w+)',
  '\\b(abstract|assert|boolean|break|byte|case|catch|char|class|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|new|package|private|protected|public|record|return|short|static|super|switch|synchronized|this|throw|throws|try|var|void|volatile|while|yield|true|false|null)\\b',
  '\\b([A-Z][A-Za-z0-9_]*)\\b',
].join('|'), 'g');

/** Java source as HTML lines with light syntax colouring. */
export function highlight(text) {
  const lines = [[]];
  const push = (chunk, cls) => {
    chunk.split('\n').forEach((part, i) => {
      if (i > 0) lines.push([]);
      if (part) lines[lines.length - 1].push(cls ? `<span class="tok-${cls}">${e(part)}</span>` : e(part));
    });
  };
  let last = 0;
  for (const m of text.matchAll(TOKEN)) {
    push(text.slice(last, m.index), null);
    push(m[0], m[1] ? 'c' : m[2] ? 's' : m[3] ? 'a' : m[4] ? 'k' : 't');
    last = m.index + m[0].length;
  }
  push(text.slice(last), null);
  return lines.map(parts => parts.join(''));
}

export function createDrawer(element, hooks) {
  function open(title, subtitle, body, codeBody = false) {
    element.innerHTML = `
      <div class="drawer-head">
        <h2 title="${e(subtitle)}">${title}</h2>
        <button class="close" type="button" aria-label="Close" data-close>×</button>
      </div>
      <div class="drawer-body ${codeBody ? 'code' : ''}">${body}</div>`;
    element.hidden = false;
    element.onclick = (event) => {
      const target = event.target.closest('[data-open], [data-file], [data-close]');
      if (!target) return;
      if (target.dataset.open) hooks.onOpen(target.dataset.open);
      else if (target.dataset.file) hooks.onSource(target.dataset.file, Number(target.dataset.line));
      else close();
    };
  }

  function close() {
    element.hidden = true;
    element.innerHTML = '';
  }

  async function source(file, line) {
    const name = file.split('/').pop();
    open(`${e(name)}<span class="muted">:${line}</span>`, file, '<p class="muted" style="padding:12px 14px">Loading…</p>', true);
    let text;
    try {
      const response = await fetch(`/api/source?file=${encodeURIComponent(file)}`);
      if (!response.ok) throw new Error(await response.text());
      text = await response.text();
    } catch (error) {
      element.querySelector('.drawer-body').innerHTML = `<p class="muted" style="padding:12px 14px">${e(error.message)}</p>`;
      return;
    }
    const rows = highlight(text).map((html, i) => `
      <div class="ln${i + 1 === line ? ' hl' : ''}" id="L${i + 1}"><span class="no">${i + 1}</span><span class="tx">${html || ' '}</span></div>`);
    const body = element.querySelector('.drawer-body');
    body.innerHTML = `<div class="source">${rows.join('')}</div>`;
    const target = body.querySelector(`#L${line}`);
    if (target) target.scrollIntoView({ block: 'center' });
  }

  function flags(model) {
    const group = (predicate) => {
      const groups = new Map();
      for (const cube of model.cubes) {
        for (const flag of cube.flags.filter(predicate)) {
          if (!groups.has(flag.flag)) groups.set(flag.flag, { principle: flag.principle, severity: flag.severity, rows: [] });
          groups.get(flag.flag).rows.push({ cube, flag });
        }
      }
      return groups;
    };
    const red = group(f => f.flag !== 'contradiction');
    const text = group(f => f.flag === 'contradiction').get('contradiction');
    const total = [...red.values()].reduce((sum, g) => sum + g.rows.length, 0);
    const row = ({ cube, flag }) => `
      <button class="flag-row" type="button" data-open="${e(cube.id)}">
        <strong>${e(cube.name)}</strong> · <code>${e(flag.where)}</code>
        <div class="muted">${e(flag.why)}</div>
      </button>`;
    const redBody = total === 0
      ? '<p class="empty-note">No red flags. Either the code is in good shape, or the flags don\'t look for what\'s wrong with it yet.</p>'
      : [...red.entries()].map(([id, g]) => `
        <div class="flag-group">
          <h3><span class="dot ${e(g.severity)}"></span>${e(FLAG_NAMES[id] ?? id)} <span class="muted">${g.rows.length}</span></h3>
          <div class="principle">${e(g.principle)}</div>
          ${g.rows.map(row).join('')}
        </div>`).join('');
    const textBody = text
      ? `<div class="flag-group">
          <h3 class="section"><span class="dot amber"></span>Contradictions <span class="muted">${text.rows.length}</span></h3>
          <div class="principle">${e(text.principle)}</div>
          ${text.rows.map(row).join('')}
        </div>`
      : '';
    open(`Red flags <span class="muted">${total}</span>`, '', redBody + textBody);
  }

  function unresolved(model) {
    const rows = model.unresolved.map(u => `
      <li style="margin:4px 0"><button class="link site" data-file="${e(u.file)}" data-line="${u.line}">${e(u.file.split('/').pop())}:${u.line}</button>
        <code>${e(u.expression)}</code></li>`).join('');
    open(`Unresolved calls <span class="muted">${model.unresolved.length}</span>`, '', `
      <p class="muted">The scanner couldn't tell which type receives these calls, so they aren't drawn anywhere.</p>
      <ul style="list-style:none;padding:0">${rows}</ul>`);
  }

  return { source, flags, unresolved, close, isOpen: () => !element.hidden };
}
