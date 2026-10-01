// Small helpers shared by the viewer's modules.

export function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

/** "com.acme.Outer.Inner" -> "Outer.Inner" when the package is known, else the last segment. */
export function shortName(id, cubes) {
  const cube = cubes && cubes.get(id);
  if (cube) return cube.name;
  const parts = String(id).split('.');
  const firstUpper = parts.findIndex(p => /^[A-Z]/.test(p));
  return firstUpper >= 0 ? parts.slice(firstUpper).join('.') : parts[parts.length - 1];
}

export const storage = {
  get(key, fallback) {
    try {
      let raw = window.localStorage.getItem(key);
      if (raw === null && key.startsWith('syg:')) {
        // Layouts saved before the tool was renamed from "otv" to "syg".
        raw = window.localStorage.getItem(`otv:${key.slice(4)}`);
      }
      return raw === null ? fallback : JSON.parse(raw);
    } catch {
      return fallback;
    }
  },
  set(key, value) {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // Storage may be unavailable; the viewer works without it.
    }
  },
  remove(key) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // Ignore.
    }
  },
};

export function hashHue(text) {
  let hash = 0;
  for (const ch of String(text)) hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
  return hash % 360;
}

const canvas = document.createElement('canvas');
export function textWidth(text, font) {
  const ctx = canvas.getContext('2d');
  ctx.font = font;
  return ctx.measureText(text).width;
}

export const STEREOTYPES = {
  'structurer': { letter: 'S', text: 'Structurer: maintains relationships between objects' },
  'coordinator': { letter: 'C', text: 'Coordinator: reacts to events by delegating to others' },
  'interfacer': { letter: 'I', text: 'Interfacer: talks to the world outside the program' },
  'information holder': { letter: 'H', text: 'Information holder: knows and provides information' },
  'service provider': { letter: 'P', text: 'Service provider: performs work and offers services' },
  'controller': { letter: 'K', text: 'Controller: makes decisions and directs others' },
};

export const FLAG_NAMES = {
  'refused-message': 'Refused message',
  'puppet-master': 'Puppet master',
  'procedure-bag': 'Procedure bag',
  'manipulates-others': 'Manipulates others',
  'controller-name': 'Controller name',
  'contradiction': 'Contradiction',
};

export const STEREOTYPE_VALUES = [
  'information holder', 'structurer', 'service provider', 'coordinator', 'controller', 'interfacer',
];

export function worstSeverity(flags) {
  if (!flags || flags.length === 0) return null;
  return flags.some(f => f.severity === 'red') ? 'red' : 'amber';
}

/** Red flags are about the design; contradictions are about the text written on a cube. */
export function redFlags(cube) {
  return (cube.flags ?? []).filter(f => f.flag !== 'contradiction');
}

export function contradictions(cube) {
  return (cube.flags ?? []).filter(f => f.flag === 'contradiction');
}

/** The reference of a {@link ...}: up to the closing paren of a signature, or the first space. */
function linkParts(content) {
  const text = content.trim();
  const paren = text.indexOf('(');
  const space = text.search(/\s/);
  let end;
  if (paren >= 0 && (space < 0 || paren < space)) {
    const close = text.indexOf(')', paren);
    end = close < 0 ? text.length : close + 1;
  } else {
    end = space < 0 ? text.length : space;
  }
  return { reference: text.slice(0, end).replace(/\s+/g, ' '), label: text.slice(end).trim() };
}

/**
 * A responsibility's text as HTML, with each {@link} shown as a chip: a
 * collaborator you can open, a message, or a link nobody could resolve.
 */
export function renderLinks(text, links, cubes) {
  const byReference = new Map((links ?? []).map(link => [link.reference, link]));
  let html = '';
  let last = 0;
  for (const match of text.matchAll(/\{@link(?:plain)?\s+([^}]*)\}/g)) {
    html += escapeHtml(text.slice(last, match.index));
    const { reference, label } = linkParts(match[1]);
    const link = byReference.get(reference);
    if (reference.startsWith('#')) {
      html += `<code class="msg" title="The message that carries this out">${escapeHtml(label || reference.slice(1))}</code>`;
    } else if (link?.target && cubes.has(link.target)) {
      html += `<button type="button" class="link chip" data-open="${escapeHtml(link.target)}" title="${escapeHtml(link.target)}">${escapeHtml(label || shortName(link.target, cubes))}</button>`;
    } else if (link?.target) {
      html += `<span class="chip foreign" title="${escapeHtml(link.target)}">${escapeHtml(label || shortName(link.target))}</span>`;
    } else {
      html += `<span class="chip unresolved" title="The scanner can't find this">${escapeHtml(label || reference)}</span>`;
    }
    last = match.index + match[0].length;
  }
  return html + escapeHtml(text.slice(last));
}

/** A method signature as a Javadoc link wants it: no generics. */
export function linkSignature(signature) {
  let erased = signature;
  while (/<[^<>]*>/.test(erased)) erased = erased.replace(/<[^<>]*>/g, '');
  return erased;
}

/** Build an element from an HTML string. */
export function html(markup) {
  const template = document.createElement('template');
  template.innerHTML = markup.trim();
  return template.content.firstElementChild;
}
