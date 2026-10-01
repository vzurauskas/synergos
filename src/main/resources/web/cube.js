// The unfolded object cube: six faces around the CRC card. The description,
// the stereotype and the responsibilities can be edited here; saving writes
// them into the class's Javadoc.

import {
  escapeHtml as e, shortName, STEREOTYPES, STEREOTYPE_VALUES, FLAG_NAMES,
  renderLinks, linkSignature, redFlags, contradictions,
} from './util.js';

function typeLink(id, cubes) {
  const name = shortName(id, cubes);
  if (!cubes.has(id)) return `<span class="muted" title="${e(id)}">${e(name)}</span>`;
  return `<button type="button" class="link" data-open="${e(id)}" title="${e(id)}">${e(name)}</button>`;
}

function site(file, line, text) {
  return `<button type="button" class="link site" data-file="${e(file)}" data-line="${line}" title="${e(file)}:${line}">${e(text ?? `:${line}`)}</button>`;
}

function empty(text) {
  return `<p class="empty-note">${e(text)}</p>`;
}

// A draft carried over a rescan after a conflict, so what you typed survives.
let carried = null;

/** What the file says now: the "before" of any edit. */
function authoredText(cube) {
  const description = cube.faces.description;
  const stereotype = description.stereotype;
  return {
    description: description.state === 'authored' ? description.text : null,
    stereotype: stereotype?.state === 'authored' ? stereotype.text : null,
    responsibilities: cube.faces.crc.responsibilities.filter(r => r.state === 'authored').map(r => r.text),
  };
}

function problems(cube, where) {
  const found = contradictions(cube).filter(c => c.where === where);
  if (!found.length) return '';
  return `<ul class="contradictions">${found.map(c => `<li>${e(c.why)}</li>`).join('')}</ul>`;
}

function editActions(view) {
  return `${view.notice ? `<div class="edit-notice">${view.notice}</div>` : ''}<div class="edit-actions">
    <button type="button" class="button primary" data-save ${view.busy ? 'disabled' : ''}>${view.busy ? 'Saving…' : 'Save to Javadoc'}</button>
    <button type="button" class="button quiet" data-cancel>Cancel</button>
    <span class="edit-hint">⌘/Ctrl+Enter saves · Esc cancels</span>
    ${view.error ? `<div class="edit-error">${e(view.error)}${view.conflict ? ' <button type="button" class="link" data-rescan>Rescan now (your draft is kept)</button>' : ''}</div>` : ''}
  </div>`;
}

// ---------- Faces ----------

function crcFace(cube, cubes, view) {
  const crc = cube.faces.crc;
  const parts = [];
  parts.push('<h4>Is a kind of</h4>');
  parts.push(crc.kindOf.length
    ? `<div class="msgs">${crc.kindOf.map(k => typeLink(k.id, cubes)).join(' · ')}</div>`
    : empty('Nothing in the project.'));
  if (cube.kind === 'interface' || crc.playedBy.length) {
    parts.push('<h4>Played by</h4>');
    parts.push(crc.playedBy.length ? `<div>${crc.playedBy.map(id => typeLink(id, cubes)).join(' · ')}</div>` : empty('Nobody in the project.'));
  }
  parts.push(`<h4 class="with-action">Responsibilities
    ${view.editing ? '' : `<button type="button" class="button small" data-edit="responsibilities">${crc.responsibilities.some(r => r.state === 'authored') ? 'Edit' : 'Write'}</button>`}</h4>`);
  parts.push(view.editing === 'responsibilities' ? responsibilitiesEditor(cube, cubes, view) : responsibilitiesView(cube, cubes));
  parts.push('<h4>Collaborators</h4>');
  parts.push(crc.collaborators.length
    ? `<ul>${crc.collaborators.map(c => `
        <li class="collab">${typeLink(c.id, cubes)}
          <div class="msgs">${c.messages.map(m => `<span class="msg">${e(m)}</span>`).join('')}</div>
        </li>`).join('')}</ul>`
    : empty(cube.kind === 'interface' ? 'A role has no collaborators of its own.' : 'Sends no messages to other project objects.'));
  if (crc.procedures.length) {
    parts.push('<h4>Procedure calls</h4>');
    parts.push(`<ul>${crc.procedures.map(c => `
      <li class="collab">${typeLink(c.id, cubes)}
        <div class="msgs">${c.messages.map(m => `<span class="msg">${e(m)}</span>`).join('')}</div>
      </li>`).join('')}</ul>`);
  }
  if (crc.creates.length) {
    parts.push('<h4>Creates</h4>');
    parts.push(`<div>${crc.creates.map(c => typeLink(c.id, cubes)).join(' · ')}</div>`);
  }
  if (crc.foreign.length) {
    parts.push(`<details><summary>Library and JDK objects it talks to: ${crc.foreign.length}</summary>
      <div class="muted mono">${crc.foreign.map(id => e(shortName(id))).join(', ')}</div></details>`);
  }
  return parts.join('');
}

function responsibilitiesView(cube, cubes) {
  const own = cube.faces.crc.responsibilities.filter(r => r.state === 'authored');
  const inherited = cube.faces.crc.responsibilities.filter(r => r.state === 'inherited');
  const parts = [];
  parts.push(own.length
    ? `<ol class="responsibilities">${own.map(r => `
        <li>${renderLinks(r.text, r.links, cubes)}${problems(cube, r.text)}</li>`).join('')}</ol>`
    : empty('Not written yet.'));
  if (inherited.length) {
    parts.push(`<ul class="responsibilities inherited">${inherited.map(r => `
      <li>${renderLinks(r.text, r.links, cubes)} <span class="tag" title="Written on ${e(r.from)}">from ${typeLink(r.from, cubes)}</span></li>`).join('')}</ul>`);
  }
  return parts.join('');
}

function responsibilitiesEditor(cube, cubes, view) {
  const rows = view.draft.responsibilities;
  const crc = cube.faces.crc;
  const types = [...new Set([...crc.collaborators, ...crc.procedures, ...crc.creates].map(c => c.id))];
  const messages = cube.faces.afferent.filter(m => !m.identity && !m.static);
  const chip = (text, insert, title, cls = '') =>
    `<button type="button" class="chip insert ${cls}" data-insert="${e(insert)}" title="${e(title)}">${e(text)}</button>`;
  return `<div class="editor">
    ${rows.map((text, i) => `
      <div class="resp-row">
        <span class="resp-no">${i + 1}.</span>
        <textarea data-row="${i}" rows="${Math.max(2, Math.ceil(text.length / 70) + 1)}" placeholder="A service ${e(cube.simpleName)} offers others…">${e(text)}</textarea>
        <button type="button" class="close" data-remove="${i}" title="Remove this responsibility" aria-label="Remove">×</button>
      </div>`).join('')}
    <button type="button" class="link" data-add>+ Add a responsibility</button>
    <div class="inserts">
      ${types.length ? `<div><span class="muted">Collaborator:</span> ${types.map(id => chip(shortName(id, cubes), `{@link ${shortName(id, cubes)}}`, `Insert {@link ${shortName(id, cubes)}}`)).join('')}</div>` : ''}
      ${crc.foreign.length ? `<div><span class="muted">Library:</span> ${crc.foreign.map(id => chip(shortName(id), `{@link ${shortName(id)}}`, id, 'foreign')).join('')}</div>` : ''}
      ${messages.length ? `<div><span class="muted">Message:</span> ${messages.map(m => chip(m.signature, `{@link #${linkSignature(m.signature)}}`, 'The message that carries it out', 'message')).join('')}</div>` : ''}
    </div>
    ${editActions(view)}
  </div>`;
}

function descriptionFace(cube, view) {
  const d = cube.faces.description;
  const parts = [];
  if (view.editing === 'description') {
    const current = view.draft.stereotype ?? '';
    const known = STEREOTYPE_VALUES.includes(current.toLowerCase().replace(/[-_\s]+/g, ' ').trim());
    const guess = d.stereotype?.state === 'guessed' ? d.stereotype.value : null;
    parts.push(`<div class="editor">
      <label class="field">Description
        <textarea data-field="description" rows="5" placeholder="Who is ${e(cube.simpleName)}? E.g. “I carry transactions past a stamp…”">${e(view.draft.description ?? '')}</textarea>
      </label>
      <label class="field">Stereotype
        <select data-field="stereotype">
          <option value="">${guess ? `Not set (the guess is “${e(guess)}”)` : 'Not set'}</option>
          ${STEREOTYPE_VALUES.map(v => `<option value="${e(v)}" ${known && current.toLowerCase().replace(/[-_\s]+/g, ' ').trim() === v ? 'selected' : ''}>${e(v)}</option>`).join('')}
          ${current && !known ? `<option value="${e(current)}" selected>${e(current)} (not one of the six)</option>` : ''}
        </select>
      </label>
      ${editActions(view)}
    </div>`);
    return parts.join('');
  }
  if (d.stereotype) {
    const s = d.stereotype;
    const info = STEREOTYPES[s.value];
    const label = s.value[0].toUpperCase() + s.value.slice(1);
    if (s.state === 'authored') {
      parts.push(`<div class="stereotype-line">${e(label)}</div>`);
      if (info) parts.push(`<div class="why">${e(info.text)}</div>`);
      parts.push(problems(cube, '@stereotype'));
    } else {
      parts.push(`<div class="stereotype-line">${e(label)}
        <span class="tag guess" title="Guessed from the code. Pick one to write it down.">guessed</span></div>`);
      parts.push(`<div class="why">Because it ${e(s.why)}.</div>`);
      if (info) parts.push(`<div class="why">${e(info.text)}</div>`);
    }
  } else {
    parts.push('<div class="stereotype-line">Role</div><div class="why">An interface: a role that other objects play.</div>');
  }
  parts.push(d.text
    ? `<div class="javadoc">${e(d.text)}</div>`
    : empty('No description yet.'));
  parts.push(`<div class="face-actions"><button type="button" class="button small" data-edit="description">${d.text || d.stereotype?.state === 'authored' ? 'Edit' : 'Write'}</button></div>`);
  return parts.join('');
}

function knowledgeFace(cube, cubes) {
  const k = cube.faces.knowledge;
  const parts = [];
  parts.push('<h4>Fields</h4>');
  parts.push(k.fields.length
    ? `<ul class="items">${k.fields.map(f => `
        <li><code>${e(f.name)}</code>
          <span class="muted">${f.typeId ? typeLink(f.typeId, cubes) : e(f.type)}</span>
          <span class="src ${e(f.origin)}" title="${originText(f.origin)}">${e(f.origin)}</span></li>`).join('')}</ul>`
    : empty('Knows nothing of its own.'));
  if (k.methods.length) parts.push('<h4>Per message</h4>');
  for (const method of k.methods) {
    const own = method.items.filter(i => !(i.kind === 'asks' && i.foreign));
    const foreign = method.items.filter(i => i.kind === 'asks' && i.foreign);
    parts.push(`<div class="entry"><div class="signature">${e(method.signature)}</div>`);
    parts.push(own.length ? `<ul class="items">${own.map(i => knowledgeItem(i, cubes)).join('')}</ul>` : empty('Needs nothing.'));
    if (foreign.length) {
      parts.push(`<details><summary>Asks library objects: ${foreign.length}</summary>
        <ul class="items">${foreign.map(i => knowledgeItem(i, cubes)).join('')}</ul></details>`);
    }
    parts.push('</div>');
  }
  return parts.join('');
}

function originText(origin) {
  return {
    given: 'Given to the object when it was constructed',
    made: 'Made by the object itself',
    constant: 'A static constant',
    unset: 'Never set in a constructor',
  }[origin] ?? origin;
}

function knowledgeItem(item, cubes) {
  if (item.kind === 'param') {
    return `<li><span class="src param">parameter</span><code>${e(item.name)}</code>
      <span class="muted">${item.typeId ? typeLink(item.typeId, cubes) : e(item.type)}</span></li>`;
  }
  if (item.kind === 'field') {
    return `<li><span class="src field">field</span><code>${e(item.name)}</code></li>`;
  }
  const how = {
    field: `through field <code>${e(item.receiver)}</code>`,
    param: `through parameter <code>${e(item.receiver)}</code>`,
    local: `through local <code>${e(item.receiver)}</code>`,
    call: 'on an answer it got',
    new: 'on an object it just made',
  }[item.receiverSource] ?? '';
  const mix = item.usesParameters && item.receiverSource !== 'param'
    ? '<span class="src mix" title="Combines what it asks with a parameter it was given">mix</span>' : '';
  return `<li class="${item.foreign ? 'foreign-item' : ''}"><span class="src asks">asks</span>
    ${typeLink(item.target, cubes)}<span class="muted">.</span><code>${e(item.name)}()</code>
    ${mix}<span class="why">${how}</span> ${site(item.file, item.line)}</li>`;
}

function afferentFace(cube, cubes) {
  const messages = cube.faces.afferent;
  if (!messages.length) return empty('Responds to no messages.');
  const regular = messages.filter(m => !m.identity);
  const identity = messages.filter(m => m.identity);
  const parts = [];
  for (const message of regular) {
    const production = message.senders.filter(s => !cubes.get(s.from)?.test);
    const tests = message.senders.filter(s => cubes.get(s.from)?.test);
    const orphan = contradictions(cube).some(c => c.where === message.signature);
    parts.push(`<div class="entry"><div class="signature">${e(message.signature)}
      ${message.static ? '<span class="tag amber" title="A static procedure, not a message to an object">static</span>' : ''}
      ${orphan ? '<span class="tag amber" title="None of the responsibilities links this message">serves no responsibility</span>' : ''}
      ${site(cube.source.file, message.line)}</div>`);
    parts.push(production.length ? senderList(production, cube, cubes) : empty(tests.length ? 'Only tests send it.' : 'Nobody in the scanned code sends it.'));
    if (tests.length) {
      parts.push(`<details><summary>From tests: ${tests.length}</summary>${senderList(tests, cube, cubes)}</details>`);
    }
    parts.push('</div>');
  }
  if (identity.length) {
    parts.push(`<p class="muted">Identity: ${identity.map(m => `<code>${e(m.signature)}</code>`).join(', ')}</p>`);
  }
  return parts.join('');
}

function senderList(senders, cube, cubes) {
  const grouped = new Map();
  for (const s of senders) {
    const key = `${s.from}#${s.member}#${s.target}`;
    if (!grouped.has(key)) grouped.set(key, { ...s, count: 0 });
    grouped.get(key).count++;
  }
  return `<ul>${[...grouped.values()].map(s => `
    <li>${typeLink(s.from, cubes)}<span class="muted">.${e(s.member)}</span>
      ${s.target !== cube.id ? `<span class="tag via" title="Sent to ${e(s.target)}">via ${e(shortName(s.target, cubes))}</span>` : ''}
      ${site(s.file, s.line)}${s.count > 1 ? ` <span class="muted">×${s.count}</span>` : ''}</li>`).join('')}</ul>`;
}

function eventsFace(cube, cubes) {
  const ev = cube.faces.events;
  const parts = [];
  if (ev.accepts.length) {
    parts.push('<h4>Accepts registrations</h4>');
    for (const accept of ev.accepts) {
      parts.push(`<div class="entry"><div class="signature">${e(accept.signature)}</div>
        ${accept.senders.length ? senderList(accept.senders, cube, cubes) : empty('Nobody registers.')}</div>`);
    }
  }
  if (ev.registersWith.length) {
    parts.push('<h4>Registers with</h4><ul>');
    for (const s of ev.registersWith) {
      parts.push(`<li>${typeLink(s.target, cubes)}<span class="muted">.</span><code>${e(s.name)}</code> ${site(s.file, s.line)}</li>`);
    }
    parts.push('</ul>');
  }
  if (!parts.length) {
    parts.push(empty('None detected.'));
    parts.push('<p class="why">Guessed from message names like register…, subscribe…, add…Listener and on….</p>');
  }
  return parts.join('');
}

function flagStrip(cube) {
  const flags = redFlags(cube);
  const found = contradictions(cube);
  const parts = [];
  if (flags.length) {
    parts.push(flags.map(f => `
      <div class="flag-item ${e(f.severity)}">
        <div class="what">${e(FLAG_NAMES[f.flag] ?? f.flag)} · <code>${e(f.where)}</code> ${site(cube.source.file, f.line)}</div>
        <div>${e(f.why)}</div>
        <div class="principle">${e(f.principle)}</div>
      </div>`).join(''));
  }
  if (found.length) {
    parts.push(`<details class="flag-item amber contradiction-list">
      <summary><span class="what">The text and the code disagree in ${found.length} place${found.length === 1 ? '' : 's'}</span></summary>
      <ul>${found.map(c => `<li><code>${e(c.where)}</code>: ${e(c.why)}</li>`).join('')}</ul>
    </details>`);
  }
  return parts.length ? `<div class="flag-strip">${parts.join('')}</div>` : '';
}

// ---------- The cube ----------

export function renderCube(container, cube, model, hooks) {
  const cubes = new Map(model.cubes.map(c => [c.id, c]));
  const before = authoredText(cube);
  const view = { editing: null, draft: null, error: '', conflict: false, busy: false, notice: '' };
  let lastFocused = null;
  if (carried && carried.id === cube.id) {
    view.editing = carried.editing;
    view.draft = carried.draft;
    const now = view.editing === 'description'
      ? `It now says: <q>${e(before.description ?? '(no description)')}</q>${before.stereotype ? `, stereotype <q>${e(before.stereotype)}</q>` : ''}.`
      : `It now has ${before.responsibilities.length} responsibilit${before.responsibilities.length === 1 ? 'y' : 'ies'}${before.responsibilities.length ? `: ${before.responsibilities.map(r => `<q>${e(r)}</q>`).join(', ')}` : ''}.`;
    view.notice = `The file changed while you were editing. Your draft is kept, and saving replaces what the file says now. ${now}`;
  }
  carried = null;

  const face = (key, num, title, body, extra = '') =>
    `<section class="face ${key}${view.editing && ((view.editing === 'description' && key === 'description') || (view.editing === 'responsibilities' && key === 'crc')) ? ' editing' : ''}">
      <h3><span class="num">${num}</span>${e(title)}${extra}</h3>${body}</section>`;

  function draw() {
    const scroll = container.querySelector('.cube-sheet')?.scrollTop ?? 0;
    container.innerHTML = `
      <div class="cube-sheet" role="dialog" aria-modal="true" aria-label="Cube ${e(cube.name)}">
        <div class="cube-head">
          <div class="cube-title">
            <h2>${e(cube.name)}</h2>
            <div class="sub">
              <span class="tag kind">${e(cube.kind)}</span>
              <span class="tag ${cube.written ? 'written' : 'unwritten'}" title="${cube.written ? 'Has responsibilities of its own' : 'No responsibilities written yet'}">${cube.written ? 'written' : 'not written yet'}</span>
              ${cube.test ? '<span class="tag">test</span>' : ''}
              <span>${e(cube.package)}</span>
              ${site(cube.source.file, cube.source.line, `${cube.source.file}:${cube.source.line}`)}
            </div>
          </div>
          <div class="cube-actions">
            <button class="button" type="button" data-focus="${e(cube.id)}" title="See the world as this object sees it">Through its eyes</button>
            <button class="close" type="button" aria-label="Close" data-close>×</button>
          </div>
        </div>
        ${flagStrip(cube)}
        <div class="net">
          ${face('description', 2, 'Description', descriptionFace(cube, view))}
          ${face('knowledge', 4, 'Knowledge', knowledgeFace(cube, cubes))}
          ${face('crc', 1, 'CRC', crcFace(cube, cubes, view))}
          ${face('afferent', 5, 'Afferent messages', afferentFace(cube, cubes))}
          ${face('events', 6, 'Events', eventsFace(cube, cubes), ' <span class="tag guess">guessed</span>')}
          ${face('contracts', 3, 'Contracts', empty('Left empty for now.'))}
        </div>
      </div>`;
    const sheet = container.querySelector('.cube-sheet');
    sheet.scrollTop = scroll;
    const first = container.querySelector('.editing textarea');
    if (first && !lastFocused) first.focus();
  }

  /** Keep what's typed in the editor in the draft, so redraws don't lose it. */
  function keepDraft() {
    if (view.editing === 'description') {
      view.draft.description = container.querySelector('[data-field="description"]')?.value ?? view.draft.description;
      view.draft.stereotype = container.querySelector('[data-field="stereotype"]')?.value ?? view.draft.stereotype;
    } else if (view.editing === 'responsibilities') {
      view.draft.responsibilities = [...container.querySelectorAll('textarea[data-row]')].map(t => t.value);
    }
  }

  function edit(part) {
    view.editing = part;
    view.draft = structuredClone(before);
    if (part === 'responsibilities' && !view.draft.responsibilities.length) view.draft.responsibilities = [''];
    view.error = '';
    view.conflict = false;
    lastFocused = null;
    draw();
  }

  function cancel() {
    view.editing = null;
    view.draft = null;
    view.error = '';
    view.notice = '';
    draw();
  }

  async function save() {
    keepDraft();
    const after = {
      description: (view.draft.description ?? '').trim() || null,
      stereotype: (view.draft.stereotype ?? '').trim() || null,
      responsibilities: view.draft.responsibilities.map(r => r.trim()).filter(Boolean),
    };
    view.busy = true;
    view.error = '';
    draw();
    const result = await hooks.onSave(cube, before, after);
    if (!result.ok) {
      view.busy = false;
      view.error = result.message;
      view.conflict = result.status === 409;
      draw();
    }
    // On success the app redraws the cube from the rescanned model.
  }

  function insert(text) {
    const target = lastFocused && container.contains(lastFocused)
      ? lastFocused
      : [...container.querySelectorAll('textarea[data-row]')].pop();
    if (!target) return;
    const start = target.selectionStart ?? target.value.length;
    const end = target.selectionEnd ?? start;
    const spaced = (start > 0 && !/\s$/.test(target.value.slice(0, start)) ? ' ' : '') + text;
    target.setRangeText(spaced, start, end, 'end');
    target.focus();
  }

  container.onclick = (event) => {
    if (event.target === container) {
      if (!view.editing) hooks.onClose();
      return;
    }
    const target = event.target.closest('[data-open], [data-file], [data-focus], [data-close], [data-edit], [data-save], [data-cancel], [data-add], [data-remove], [data-insert], [data-rescan]');
    if (!target) return;
    if (target.dataset.open) hooks.onOpen(target.dataset.open);
    else if (target.dataset.file) hooks.onSource(target.dataset.file, Number(target.dataset.line));
    else if (target.dataset.focus) hooks.onFocus(target.dataset.focus);
    else if (target.hasAttribute('data-close')) hooks.onClose();
    else if (target.dataset.edit) edit(target.dataset.edit);
    else if (target.hasAttribute('data-save')) save();
    else if (target.hasAttribute('data-cancel')) cancel();
    else if (target.hasAttribute('data-rescan')) {
      keepDraft();
      carried = { id: cube.id, editing: view.editing, draft: structuredClone(view.draft) };
      view.editing = null;
      hooks.onRescan({ force: true });
    }
    else if (target.hasAttribute('data-add')) {
      keepDraft();
      view.draft.responsibilities.push('');
      draw();
      [...container.querySelectorAll('textarea[data-row]')].pop()?.focus();
    } else if (target.dataset.remove !== undefined) {
      keepDraft();
      view.draft.responsibilities.splice(Number(target.dataset.remove), 1);
      draw();
    } else if (target.dataset.insert) insert(target.dataset.insert);
  };
  container.onfocusin = (event) => {
    if (event.target.matches('textarea[data-row]')) lastFocused = event.target;
  };
  container.onkeydown = (event) => {
    if (!view.editing) return;
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
      event.preventDefault();
      save();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      cancel();
    }
  };
  draw();
  return { editing: () => Boolean(view.editing) };
}
