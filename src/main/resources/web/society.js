// The society view: every cube on a table, messages as arrows between them.

import { shortName, hashHue, textWidth, STEREOTYPES, worstSeverity, storage, redFlags, contradictions } from './util.js';

const FONT = '12px -apple-system, BlinkMacSystemFont, "Segoe UI", Inter, Roboto, sans-serif';
const SMALL_FONT = '11px -apple-system, BlinkMacSystemFont, "Segoe UI", Inter, Roboto, sans-serif';
const DEPTH = 7;
const HEIGHT = 32;
const KINDS = ['message', 'procedure', 'write', 'creates', 'kindOf', 'foreign'];

export function markers(defs) {
  const specs = [
    ['message', 'M0,-4L9,0L0,4Z', true],
    ['procedure', 'M0,-4L9,0L0,4Z', true],
    ['write', 'M0,-4L9,0L0,4Z', true],
    ['foreign', 'M0,-3.5L8,0L0,3.5Z', true],
    ['creates', 'M0,-4L8,0L0,4', false],
    ['kindOf', 'M0,-6L11,0L0,6Z', false],
    ['hot', 'M0,-4L9,0L0,4Z', true],
  ];
  for (const [kind, path, filled] of specs) {
    defs.append('marker')
      .attr('id', `arrow-${kind}`)
      .attr('viewBox', '-1 -7 13 14')
      .attr('refX', kind === 'kindOf' ? 11 : 9)
      .attr('refY', 0)
      .attr('markerWidth', kind === 'kindOf' ? 13 : 10)
      .attr('markerHeight', 14)
      .attr('markerUnits', 'userSpaceOnUse')
      .attr('orient', 'auto')
      .append('path')
      .attr('d', path)
      .attr('class', `marker-${kind}`)
      .style('fill', filled ? `var(--${kind === 'hot' ? 'accent' : kind === 'write' ? 'red' : kind})` : 'var(--paper)')
      .style('stroke', filled ? 'none' : `var(--${kind === 'kindOf' ? 'kind' : kind})`)
      .style('stroke-width', 1.3);
  }
}

/** The point where the line from a node's centre towards (tx, ty) leaves its box. */
export function border(node, tx, ty, pad = 3) {
  const dx = tx - node.x;
  const dy = ty - node.y;
  if (dx === 0 && dy === 0) return [node.x, node.y];
  const hw = node.w / 2 + pad;
  const hh = node.h / 2 + pad;
  const scale = Math.min(Math.abs(dx) > 1e-6 ? hw / Math.abs(dx) : Infinity, Math.abs(dy) > 1e-6 ? hh / Math.abs(dy) : Infinity);
  return [node.x + dx * Math.min(scale, 1), node.y + dy * Math.min(scale, 1)];
}

export function edgePath(link) {
  const s = link.source;
  const t = link.target;
  const mx = (s.x + t.x) / 2;
  const my = (s.y + t.y) / 2;
  const dx = t.x - s.x;
  const dy = t.y - s.y;
  const len = Math.hypot(dx, dy) || 1;
  const cx = mx - (dy / len) * link.bend;
  const cy = my + (dx / len) * link.bend;
  const [x1, y1] = border(s, cx, cy, 2);
  const [x2, y2] = border(t, cx, cy, link.kind === 'kindOf' ? 3 : 4);
  return `M${x1},${y1} Q${cx},${cy} ${x2},${y2}`;
}

export function nodeShape(selection) {
  selection.each(function (d) {
    const g = d3.select(this);
    g.selectAll('*').remove();
    if (d.foreign) {
      g.append('ellipse').attr('rx', d.w / 2).attr('ry', d.h / 2);
      g.append('text').attr('text-anchor', 'middle').attr('dy', '0.35em').text(d.label);
      return;
    }
    const x = -d.w / 2;
    const y = -d.h / 2;
    const solid = d.written || d.role;
    const tint = (l) => solid
      ? `hsl(${d.hue} var(--cube-s) var(${l}))`
      : `color-mix(in srgb, hsl(${d.hue} var(--cube-s) var(${l})) 22%, var(--paper))`;
    if (!d.role) {
      g.append('polygon').attr('class', 'top')
        .attr('points', `${x},${y} ${x + DEPTH},${y - DEPTH} ${x + d.w + DEPTH},${y - DEPTH} ${x + d.w},${y}`)
        .style('fill', solid ? tint('--cube-top-l') : 'none');
      g.append('polygon').attr('class', 'side')
        .attr('points', `${x + d.w},${y} ${x + d.w + DEPTH},${y - DEPTH} ${x + d.w + DEPTH},${y + d.h - DEPTH} ${x + d.w},${y + d.h}`)
        .style('fill', solid ? tint('--cube-side-l') : 'none');
    }
    g.append('rect').attr('class', 'front')
      .attr('x', x).attr('y', y).attr('width', d.w).attr('height', d.h).attr('rx', 2)
      .style('fill', tint('--cube-l'));
    const stereo = STEREOTYPES[d.stereotype];
    g.append('text').attr('class', 'stereo').attr('x', x + 8).attr('dy', '0.35em')
      .text(d.role ? '◇' : stereo ? stereo.letter : '·');
    g.append('text').attr('class', 'label').attr('x', x + 22).attr('dy', '0.35em').text(d.label);
    g.append('path').attr('class', 'pin').attr('d', `M${x + d.w - 9},${y + 4} l3,3 l-3,3 l-3,-3 Z`);
    if (d.flags.length) {
      const severity = worstSeverity(d.flags);
      g.append('circle').attr('class', `badge ${severity}`).attr('cx', x).attr('cy', y).attr('r', 8);
      g.append('text').attr('class', 'badge-text').attr('x', x).attr('y', y).attr('dy', '0.35em')
        .attr('text-anchor', 'middle').text(d.flags.length);
    }
    if (d.contradictions > 0) {
      const cx = x + d.w + (d.role ? 0 : DEPTH);
      const cy = y - (d.role ? 0 : DEPTH);
      g.append('path').attr('class', 'contradiction-mark')
        .attr('d', `M${cx},${cy - 8} L${cx + 8},${cy + 6} L${cx - 8},${cy + 6} Z`);
      g.append('text').attr('class', 'contradiction-text').attr('x', cx).attr('y', cy + 3.5)
        .attr('text-anchor', 'middle').text('!');
    }
  });
}

export function cubeNode(cube, position) {
  const nested = Boolean(cube.outer);
  const role = cube.kind === 'interface';
  const label = cube.name;
  const w = Math.max(76, Math.ceil(textWidth(label, nested ? SMALL_FONT : FONT)) + 34);
  const stereotype = cube.faces.description.stereotype?.value;
  return {
    id: cube.id, cube, label, w, h: HEIGHT, role, nested, test: cube.test, foreign: false,
    hue: hashHue(cube.package), stereotype, flags: redFlags(cube), contradictions: contradictions(cube).length,
    written: cube.written,
    x: position?.[0], y: position?.[1],
    fx: position?.[2] ? position[0] : undefined, fy: position?.[2] ? position[1] : undefined,
  };
}

export function foreignNode(id, position) {
  const label = shortName(id);
  return {
    id, label, w: Math.ceil(textWidth(label, SMALL_FONT)) + 24, h: 22, foreign: true, flags: [], contradictions: 0,
    x: position?.[0], y: position?.[1],
  };
}

/** Give links between the same pair of nodes different bends so they don't overlap. */
export function bend(links) {
  const groups = new Map();
  for (const link of links) {
    const a = typeof link.source === 'object' ? link.source.id : link.source;
    const b = typeof link.target === 'object' ? link.target.id : link.target;
    const key = a < b ? `${a}|${b}` : `${b}|${a}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(link);
  }
  for (const group of groups.values()) {
    group.forEach((link, index) => {
      const a = typeof link.source === 'object' ? link.source.id : link.source;
      const b = typeof link.target === 'object' ? link.target.id : link.target;
      const sign = a < b ? 1 : -1;
      link.bend = group.length === 1 ? 0 : (index - (group.length - 1) / 2) * 22 * sign;
    });
  }
}

export function createSociety(svg, layer, hooks) {
  const state = { nodes: [], links: [], key: null, simulation: null, byId: new Map(), neighbours: new Map() };

  const edgeLayer = layer.append('g').attr('class', 'edges');
  const hitLayer = layer.append('g').attr('class', 'edge-hits');
  const nodeLayer = layer.append('g').attr('class', 'nodes');

  function positions() {
    return storage.get(state.key, {});
  }

  function save() {
    const saved = {};
    for (const node of state.nodes) {
      if (Number.isFinite(node.x)) saved[node.id] = [Math.round(node.x), Math.round(node.y), node.fx != null];
    }
    storage.set(state.key, saved);
  }

  function render(model, filters, { keepAlpha = false } = {}) {
    state.key = `syg:positions:${model.repo.path}`;
    const saved = positions();
    const previous = new Map(state.nodes.map(n => [n.id, n]));
    const place = (id) => {
      const old = previous.get(id);
      if (old && Number.isFinite(old.x)) return [old.x, old.y, old.fx != null];
      return saved[id];
    };
    const visible = model.cubes.filter(c => (filters.tests || !c.test) && (filters.nested || !c.outer));
    const nodes = visible.map(c => cubeNode(c, place(c.id)));
    const ids = new Set(nodes.map(n => n.id));
    if (filters.foreign) {
      const used = new Set(model.edges.filter(e => e.kind === 'foreign' && ids.has(e.from)).map(e => e.to));
      for (const id of used) nodes.push(foreignNode(id, place(id)));
      used.forEach(id => ids.add(id));
    }
    const shown = new Set(['message', 'procedure', 'write']);
    if (filters.kinds) shown.add('kindOf');
    if (filters.creates) shown.add('creates');
    if (filters.foreign) shown.add('foreign');
    const links = model.edges
      .filter(e => shown.has(e.kind) && ids.has(e.from) && ids.has(e.to) && e.from !== e.to)
      .map(e => ({ source: e.from, target: e.to, kind: e.kind, messages: e.messages, edge: e }));
    bend(links);
    const fresh = nodes.filter(n => !Number.isFinite(n.x)).length;

    state.nodes = nodes;
    state.links = links;
    state.byId = new Map(nodes.map(n => [n.id, n]));
    state.neighbours = new Map(nodes.map(n => [n.id, new Set([n.id])]));
    for (const link of links) {
      state.neighbours.get(link.source)?.add(link.target);
      state.neighbours.get(link.target)?.add(link.source);
    }
    draw();
    simulate(fresh > nodes.length * 0.5 ? 1 : fresh > 0 ? 0.35 : keepAlpha ? 0.05 : 0.02, fresh > nodes.length * 0.5);
  }

  function draw() {
    const width = (l) => l.kind === 'kindOf' ? 1 : Math.min(4, 1.2 + Math.log2(Math.max(1, l.messages.length)) * 0.9);
    edgeLayer.selectAll('path.edge')
      .data(state.links, l => `${l.source}|${l.target}|${l.kind}`)
      .join('path')
      .attr('class', l => `edge ${l.kind}`)
      .attr('stroke-width', width)
      .attr('marker-end', l => `url(#arrow-${l.kind})`);
    hitLayer.selectAll('path.edge-hit')
      .data(state.links, l => `${l.source}|${l.target}|${l.kind}`)
      .join('path')
      .attr('class', 'edge-hit')
      .on('mouseenter', (event, l) => { hot(l, true); hooks.onEdgeHover(l, event); })
      .on('mousemove', (event, l) => hooks.onEdgeHover(l, event))
      .on('mouseleave', (event, l) => { hot(l, false); hooks.onEdgeLeave(); });

    let clickTimer = null;
    const nodeSel = nodeLayer.selectAll('g.node')
      .data(state.nodes, n => n.id)
      .join('g')
      .attr('class', n => [
        'node',
        n.foreign ? 'foreign' : '',
        n.role ? 'role' : '',
        n.test ? 'test' : '',
        n.nested ? 'nested' : '',
        n.flags.length ? `flag-${worstSeverity(n.flags)}` : '',
        !n.foreign && !n.role ? (n.written ? 'written' : 'unwritten') : '',
        n.fx != null ? 'pinned' : '',
      ].join(' '))
      .call(nodeShape)
      .on('mouseenter', (event, n) => { neighbourhood(n.id); hooks.onNodeHover(n, event); })
      .on('mousemove', (event, n) => hooks.onNodeHover(n, event))
      .on('mouseleave', () => { neighbourhood(null); hooks.onNodeLeave(); })
      .on('click', (event, n) => {
        if (n.foreign) return;
        clearTimeout(clickTimer);
        clickTimer = setTimeout(() => hooks.onOpen(n.id), 230);
      })
      .on('dblclick', (event, n) => {
        event.stopPropagation();
        clearTimeout(clickTimer);
        if (!n.foreign) hooks.onFocus(n.id);
      });
    nodeSel.call(
      d3.drag()
        .clickDistance(4)
        .on('start', (event, n) => {
          if (!event.active) state.simulation.alphaTarget(0.15).restart();
          n.fx = n.x;
          n.fy = n.y;
        })
        .on('drag', (event, n) => {
          n.fx = event.x;
          n.fy = event.y;
        })
        .on('end', (event, n) => {
          if (!event.active) state.simulation.alphaTarget(0);
          d3.select(event.sourceEvent?.target?.closest?.('g.node')).classed('pinned', true);
          save();
        })
    );
  }

  function tick() {
    edgeLayer.selectAll('path.edge').attr('d', edgePath);
    hitLayer.selectAll('path.edge-hit').attr('d', edgePath);
    nodeLayer.selectAll('g.node').attr('transform', n => `translate(${n.x},${n.y})`);
  }

  function simulate(alpha, fit) {
    state.simulation?.stop();
    const n = state.nodes.length;
    for (const node of state.nodes) {
      if (!Number.isFinite(node.x)) {
        const angle = Math.random() * Math.PI * 2;
        const radius = 60 + Math.random() * Math.sqrt(n) * 60;
        node.x = Math.cos(angle) * radius;
        node.y = Math.sin(angle) * radius;
      }
    }
    state.simulation = d3.forceSimulation(state.nodes)
      .force('link', d3.forceLink(state.links).id(d => d.id)
        .distance(l => l.kind === 'kindOf' ? 80 : l.kind === 'foreign' ? 90 : 150)
        .strength(l => l.kind === 'kindOf' ? 0.5 : l.kind === 'creates' ? 0.05 : 0.25))
      .force('charge', d3.forceManyBody().strength(-520).distanceMax(900))
      .force('collide', d3.forceCollide().radius(d => d.w / 2 + 14).iterations(2))
      .force('x', d3.forceX(0).strength(0.045))
      .force('y', d3.forceY(0).strength(0.07))
      .alpha(alpha)
      .alphaDecay(alpha >= 1 ? 0.03 : 0.05)
      .on('tick', tick)
      .on('end', () => { save(); if (fit) hooks.onSettled(); });
    if (alpha >= 1) {
      // Settle most of the way before the first paint, so the table doesn't explode on screen.
      state.simulation.stop();
      for (let i = 0; i < 220; i++) state.simulation.tick();
      tick();
      state.simulation.alpha(0.08).restart();
      hooks.onSettled();
    } else {
      tick();
    }
  }

  function neighbourhood(id) {
    const near = id ? state.neighbours.get(id) : null;
    nodeLayer.selectAll('g.node').classed('dim', n => near ? !near.has(n.id) : false);
    edgeLayer.selectAll('path.edge').classed('dim', l => id ? l.source.id !== id && l.target.id !== id : false);
  }

  function hot(link, on) {
    edgeLayer.selectAll('path.edge')
      .filter(l => l === link)
      .classed('hot', on)
      .attr('marker-end', l => `url(#arrow-${on && l.kind !== 'kindOf' ? 'hot' : l.kind})`);
  }

  function highlight(query) {
    const q = query.trim().toLowerCase();
    nodeLayer.selectAll('g.node')
      .classed('hit', n => q !== '' && n.label.toLowerCase().includes(q))
      .classed('dim', n => q !== '' && !n.label.toLowerCase().includes(q));
    edgeLayer.selectAll('path.edge').classed('dim', q !== '');
    return state.nodes.filter(n => q !== '' && n.label.toLowerCase().includes(q));
  }

  function bounds() {
    if (!state.nodes.length) return null;
    const xs = state.nodes.map(n => n.x);
    const ys = state.nodes.map(n => n.y);
    const pad = 60;
    return {
      x0: Math.min(...state.nodes.map(n => n.x - n.w / 2)) - pad,
      x1: Math.max(...state.nodes.map(n => n.x + n.w / 2 + DEPTH)) + pad,
      y0: Math.min(...ys) - pad - DEPTH,
      y1: Math.max(...ys) + pad,
      cx: (Math.min(...xs) + Math.max(...xs)) / 2,
    };
  }

  function forget() {
    storage.remove(state.key);
    for (const node of state.nodes) {
      node.x = undefined;
      node.y = undefined;
      node.fx = undefined;
      node.fy = undefined;
    }
    nodeLayer.selectAll('g.node').classed('pinned', false);
  }

  return {
    render,
    highlight,
    bounds,
    forget,
    node: (id) => state.byId.get(id),
    neighbours: (id) => state.neighbours.get(id),
  };
}

export { KINDS };
