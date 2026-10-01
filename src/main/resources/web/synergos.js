// Synergos: a Java codebase as a society of object cubes.

import { createSociety, markers } from './society.js';
import { createFocus } from './focus.js';
import { renderCube } from './cube.js';
import { createDrawer } from './drawer.js';
import { escapeHtml as e, shortName, storage, STEREOTYPES, FLAG_NAMES, worstSeverity, redFlags, contradictions } from './util.js';

const FILTERS = [
  ['tests', 'Tests', false, 'Show test classes (the stories)'],
  ['nested', 'Nested', true, 'Show nested classes as cubes of their own'],
  ['kinds', 'Kind-of', true, 'Show is-a-kind-of lines'],
  ['creates', 'Creates', false, 'Show which objects create which'],
  ['foreign', 'Foreign', false, 'Show library and JDK objects that are sent messages'],
];

const app = {
  model: null,
  cubes: new Map(),
  filters: { ...Object.fromEntries(FILTERS.map(([key, , on]) => [key, on])), ...storage.get('syg:filters', {}) },
  focus: null,
  societyView: null,
  openCube: null,
  cubeView: null,
};

const svg = d3.select('#canvas');
markers(svg.append('defs'));
const viewport = svg.append('g');
const societyLayer = viewport.append('g').attr('class', 'society-layer');
const focusLayer = viewport.append('g').attr('class', 'focus-layer').style('display', 'none');
const zoom = d3.zoom().scaleExtent([0.08, 3]).on('zoom', (event) => viewport.attr('transform', event.transform));
svg.call(zoom).on('dblclick.zoom', null);

const $ = (id) => document.getElementById(id);
const tooltip = $('tooltip');
const layer = $('cube-layer');

const hooks = {
  onOpen: openCube,
  onFocus: focusOn,
  onNodeHover: nodeTip,
  onNodeLeave: hideTip,
  onEdgeHover: edgeTip,
  onEdgeLeave: hideTip,
  onSettled: () => { if (!app.focus) fit(society.bounds(), false); },
};
const society = createSociety(svg.node(), societyLayer, hooks);
const focus = createFocus(focusLayer, hooks);
const drawer = createDrawer($('drawer'), { onOpen: openCube, onSource: (file, line) => drawer.source(file, line) });

// ---------- Model ----------

async function load() {
  try {
    const response = await fetch('/api/model');
    if (!response.ok) throw new Error(await response.text());
    setModel(await response.json());
  } catch (error) {
    $('loading').textContent = `Couldn't load the model: ${error.message}`;
  }
}

function setModel(model, options = {}) {
  const first = !app.model;
  app.model = model;
  app.cubes = new Map(model.cubes.map(c => [c.id, c]));
  header();
  society.render(model, app.filters, options);
  if (first) fit(society.bounds(), false);
  if (app.focus && app.cubes.has(app.focus)) focusOn(app.focus, { keepView: true });
  else if (app.focus) leaveFocus();
  if (app.openCube && app.cubes.has(app.openCube)) openCube(app.openCube);
  else if (app.openCube) closeCube();
  $('loading').hidden = true;
  const emptyNote = $('empty');
  emptyNote.hidden = model.cubes.length > 0;
  emptyNote.innerHTML = `No Java types found under <code>${e(model.repo.path)}</code>.<br>
    The scanner looks for <code>src/main/java</code> and <code>src/test/java</code> folders.`;
}

async function rescan({ force = false } = {}) {
  const button = $('rescan');
  if (button.disabled) return;
  if (!force && app.cubeView?.editing()) {
    toast('Save or cancel the edit first.');
    return;
  }
  button.disabled = true;
  button.textContent = 'Scanning…';
  try {
    const response = await fetch('/api/rescan', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    });
    if (!response.ok) throw new Error(await response.text());
    setModel(await response.json(), { keepAlpha: true });
    toast(`Rescanned: ${app.model.stats.cubes} cubes`);
  } catch (error) {
    toast(`Rescan failed: ${error.message}`);
  } finally {
    button.disabled = false;
    button.textContent = 'Rescan';
  }
}

/** Writes a cube's text into its Javadoc, then shows the rescanned model. */
async function saveCubeText(cube, before, after) {
  try {
    const response = await fetch('/api/cube/text', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ file: cube.source.file, id: cube.id, before, after }),
    });
    if (!response.ok) return { ok: false, status: response.status, message: await response.text() };
    setModel(await response.json(), { keepAlpha: true });
    toast(`Saved to the Javadoc in ${cube.source.file.split('/').pop()}`);
    return { ok: true };
  } catch (error) {
    return { ok: false, status: 0, message: `Couldn't reach the viewer: ${error.message}` };
  }
}

// ---------- Header ----------

function header() {
  const { model } = app;
  $('repo').textContent = model.repo.name;
  $('repo').title = model.repo.path;
  document.title = `${model.repo.name} · Synergos`;
  const s = model.stats;
  const unresolved = model.unresolved.length;
  const pct = (s.resolved * 100).toFixed(s.resolved >= 0.995 && s.resolved < 1 ? 1 : 0);
  $('stats').innerHTML = `<span title="Cubes with at least one responsibility of their own">${s.written} of ${s.cubes} cubes written</span>`
    + ` · ${s.messages} messages · ${pct}% of calls resolved`
    + (unresolved ? ` <button type="button" id="unresolved">(${unresolved} unresolved)</button>` : '')
    + (s.unparsed.length ? ` · <span title="${e(s.unparsed.join('\n'))}">${s.unparsed.length} files didn't parse</span>` : '');
  const flagCount = model.cubes.reduce((sum, c) => sum + redFlags(c).length, 0);
  const disagreements = model.cubes.reduce((sum, c) => sum + contradictions(c).length, 0);
  $('flags-button').innerHTML = `Flags<span class="count${flagCount ? '' : ' zero'}" title="Red flags">${flagCount}</span>`
    + (disagreements ? `<span class="count amber" title="Contradictions between cube text and code">${disagreements}</span>` : '');
  $('unresolved')?.addEventListener('click', () => drawer.unresolved(app.model));
}

function filterChips() {
  const box = $('filters');
  box.innerHTML = FILTERS.map(([key, text, , title]) =>
    `<button type="button" class="chip" data-filter="${key}" aria-pressed="${app.filters[key]}" title="${e(title)}">${e(text)}</button>`).join('');
  box.onclick = (event) => {
    const chip = event.target.closest('[data-filter]');
    if (!chip) return;
    const key = chip.dataset.filter;
    app.filters[key] = !app.filters[key];
    chip.setAttribute('aria-pressed', app.filters[key]);
    storage.set('syg:filters', app.filters);
    society.render(app.model, app.filters, { keepAlpha: true });
    if (app.focus) focusOn(app.focus, { keepView: true });
    else highlight();
  };
}

function legend() {
  const line = (cls, marker, dash = '') =>
    `<svg class="sw" width="44" height="12"><line x1="2" y1="6" x2="38" y2="6" class="edge ${cls}" stroke-width="1.6" ${dash}
      marker-end="url(#arrow-${marker})"/></svg>`;
  const box = (extra, fill = 'hsl(220 var(--cube-s) var(--cube-l))') =>
    `<svg class="sw" width="44" height="22"><g class="node ${extra}" transform="translate(20,12)">
      <rect class="front" x="-16" y="-8" width="32" height="16" rx="2" style="fill:${fill}"/></g></svg>`;
  const cube = (solid) =>
    `<svg class="sw" width="44" height="24"><g class="node" transform="translate(19,14)">
      <polygon class="top" points="-15,-8 -10,-13 20,-13 15,-8" style="fill:${solid ? 'hsl(220 var(--cube-s) var(--cube-top-l))' : 'none'}"/>
      <polygon class="side" points="15,-8 20,-13 20,3 15,8" style="fill:${solid ? 'hsl(220 var(--cube-s) var(--cube-side-l))' : 'none'}"/>
      <rect class="front" x="-15" y="-8" width="30" height="16" rx="2"
        style="fill:${solid ? 'hsl(220 var(--cube-s) var(--cube-l))' : 'color-mix(in srgb, hsl(220 var(--cube-s) var(--cube-l)) 22%, var(--paper))'}"/></g></svg>`;
  $('legend-body').innerHTML = `
    <h4>Cubes</h4>
    <div class="legend-row">${cube(true)}<span>an object with responsibilities written</span></div>
    <div class="legend-row">${cube(false)}<span>an object not written yet</span></div>
    <div class="legend-row">${box('role', 'var(--paper)')}<span>a role (interface)</span></div>
    <div class="legend-row">${box('flag-red')}<span>red flag</span></div>
    <div class="legend-row">${box('flag-amber')}<span>amber flag</span></div>
    <div class="legend-row"><svg class="sw" width="44" height="20"><g class="node" transform="translate(20,11)">
      <path class="contradiction-mark" d="M0,-8 L8,6 L-8,6 Z"/><text class="contradiction-text" x="0" y="3.5" text-anchor="middle">!</text></g></svg>
      <span>its text and its code disagree</span></div>
    <h4>Lines</h4>
    <div class="legend-row">${line('message', 'message')}<span>sends messages to</span></div>
    <div class="legend-row">${line('procedure', 'procedure')}<span>calls static procedures of</span></div>
    <div class="legend-row">${line('kindOf', 'kindOf')}<span>is a kind of</span></div>
    <div class="legend-row">${line('creates', 'creates')}<span>creates</span></div>
    <div class="legend-row">${line('write', 'write')}<span>writes the fields of</span></div>
    <h4>Stereotypes (guessed)</h4>
    ${Object.entries(STEREOTYPES).filter(([k]) => k !== 'controller').map(([, s]) =>
      `<div class="legend-row"><code style="width:44px;text-align:center">${s.letter}</code><span>${e(s.text)}</span></div>`).join('')}
    <h4>Mouse</h4>
    <div class="legend-row muted">Click a cube to unfold it. Double-click to see through its eyes.
      Drag to pin a cube where you want it.</div>`;
  $('legend-toggle').onclick = () => {
    $('legend').classList.toggle('open');
    storage.set('syg:legend', $('legend').classList.contains('open'));
  };
  if (storage.get('syg:legend', false)) $('legend').classList.add('open');
}

// ---------- Views ----------

function fit(bounds, animate = true) {
  if (!bounds) return;
  const { width, height } = svg.node().getBoundingClientRect();
  const w = bounds.x1 - bounds.x0;
  const h = bounds.y1 - bounds.y0;
  const scale = Math.min(1.3, Math.max(0.08, Math.min(width / w, height / h)));
  const transform = d3.zoomIdentity
    .translate(width / 2, height / 2)
    .scale(scale)
    .translate(-(bounds.x0 + bounds.x1) / 2, -(bounds.y0 + bounds.y1) / 2);
  (animate ? svg.transition().duration(450) : svg).call(zoom.transform, transform);
}

function focusOn(id, { keepView = false } = {}) {
  if (!app.cubes.has(id)) return;
  closeCube();
  hideTip();
  if (!app.focus) app.societyView = d3.zoomTransform(svg.node());
  app.focus = id;
  societyLayer.style('display', 'none');
  focusLayer.style('display', null);
  const bounds = focus.render(app.model, id, app.filters);
  if (!keepView) fit(bounds);
  const cube = app.cubes.get(id);
  const banner = $('focus-banner');
  banner.hidden = false;
  banner.innerHTML = `<span>Through the eyes of <strong>${e(cube.name)}</strong></span>
    <span class="muted">click a neighbour to walk over · Esc to go back</span>
    <button type="button" id="leave-focus">Back to the society</button>`;
  $('leave-focus').onclick = leaveFocus;
}

function leaveFocus() {
  app.focus = null;
  focusLayer.style('display', 'none');
  societyLayer.style('display', null);
  $('focus-banner').hidden = true;
  if (app.societyView) svg.transition().duration(350).call(zoom.transform, app.societyView);
  highlight();
}

function openCube(id) {
  const cube = app.cubes.get(id);
  if (!cube) return;
  if (app.cubeView?.editing() && app.openCube !== id && !window.confirm('Leave this cube without saving the edit?')) return;
  hideTip();
  app.openCube = id;
  layer.hidden = false;
  app.cubeView = renderCube(layer, cube, app.model, {
    onOpen: openCube,
    onFocus: focusOn,
    onSource: (file, line) => drawer.source(file, line),
    onClose: closeCube,
    onSave: saveCubeText,
    onRescan: rescan,
  });
}

function closeCube() {
  app.openCube = null;
  app.cubeView = null;
  layer.hidden = true;
  layer.innerHTML = '';
}

// ---------- Tooltips ----------

function place(event) {
  const pad = 14;
  const rect = tooltip.getBoundingClientRect();
  let x = event.clientX + pad;
  let y = event.clientY + pad;
  if (x + rect.width > window.innerWidth - 8) x = event.clientX - rect.width - pad;
  if (y + rect.height > window.innerHeight - 8) y = event.clientY - rect.height - pad;
  tooltip.style.left = `${Math.max(8, x)}px`;
  tooltip.style.top = `${Math.max(8, y)}px`;
}

function nodeTip(node, event) {
  if (node.foreign) {
    tooltip.innerHTML = `<h5>${e(node.label)}</h5><div class="muted mono">${e(node.id)}</div><div class="muted">A library or JDK object</div>`;
  } else {
    const cube = node.cube;
    const stereotype = cube.faces.description.stereotype;
    const flags = redFlags(cube).map(f => `<li>${e(FLAG_NAMES[f.flag] ?? f.flag)}: <code>${e(f.where)}</code></li>`).join('');
    const disagreements = contradictions(cube).length;
    tooltip.innerHTML = `<h5>${e(cube.name)}</h5>
      <div class="muted">${e(cube.kind)}${stereotype ? ` · ${e(stereotype.value)}${stereotype.state === 'guessed' ? ' (guessed)' : ''}` : ''}${cube.test ? ' · test' : ''}</div>
      <div class="muted">${cube.written ? 'written' : 'not written yet'}${disagreements ? ` · <span style="color:var(--amber)">${disagreements} contradiction${disagreements === 1 ? '' : 's'}</span>` : ''}</div>
      <div class="muted mono">${e(cube.package)}</div>
      ${flags ? `<ul style="margin-top:6px;color:var(--${worstSeverity(redFlags(cube))})">${flags}</ul>` : ''}`;
  }
  tooltip.hidden = false;
  place(event);
}

function edgeTip(link, event) {
  const from = shortName(link.source.id, app.cubes);
  const to = shortName(link.target.id, app.cubes);
  const verbs = {
    message: 'sends messages to',
    procedure: 'calls static procedures of',
    creates: 'creates',
    kindOf: 'is a kind of',
    write: 'writes fields of',
    foreign: 'sends messages to',
  };
  const list = link.messages.length
    ? `<ul>${link.messages.map(m => `<li><code>${e(m.name)}</code>${m.sites.length > 1 ? ` <span class="muted">×${m.sites.length}</span>` : ''}
        <span class="muted">in ${e([...new Set(m.sites.map(s => s.member))].slice(0, 3).join(', '))}</span></li>`).join('')}</ul>`
    : '';
  tooltip.innerHTML = `<h5>${e(from)} <span class="muted">${verbs[link.kind] ?? link.kind}</span> ${e(to)}</h5>
    ${link.kind === 'creates' ? '' : list}`;
  tooltip.hidden = false;
  place(event);
}

function hideTip() {
  tooltip.hidden = true;
}

function toast(text) {
  const box = $('toast');
  box.textContent = text;
  box.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { box.hidden = true; }, 2600);
}

// ---------- Search and keys ----------

function highlight() {
  if (app.focus) return [];
  return society.highlight($('search').value);
}

$('search').addEventListener('input', highlight);
$('search').addEventListener('keydown', (event) => {
  if (event.key === 'Enter') {
    const hits = highlight();
    if (hits.length) openCube(hits[0].id);
  } else if (event.key === 'Escape') {
    $('search').value = '';
    highlight();
    $('search').blur();
  }
});

document.addEventListener('keydown', (event) => {
  const typing = ['INPUT', 'TEXTAREA'].includes(document.activeElement?.tagName);
  if (event.key === 'Escape' && !typing) {
    if (drawer.isOpen()) drawer.close();
    else if (app.openCube) closeCube();
    else if (app.focus) leaveFocus();
    return;
  }
  if (typing || event.metaKey || event.ctrlKey || event.altKey) return;
  if (event.key === '/') {
    event.preventDefault();
    $('search').focus();
  } else if (event.key === 'r' || event.key === 'R') {
    rescan();
  }
});

$('rescan').addEventListener('click', () => rescan());
$('flags-button').addEventListener('click', () => drawer.flags(app.model));
$('relayout').addEventListener('click', () => {
  if (app.focus) leaveFocus();
  society.forget();
  society.render(app.model, app.filters);
  highlight();
});

filterChips();
legend();
load();
