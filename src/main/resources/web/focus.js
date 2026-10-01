// "Through its eyes": the world as one object sees it.

import { cubeNode, foreignNode, nodeShape, edgePath, border } from './society.js';
import { worstSeverity } from './util.js';

const COLUMN = 430;
const ROW = 52;
const TIER = 190;

function column(entries, x) {
  const top = -((entries.length - 1) * ROW) / 2;
  entries.forEach((entry, i) => {
    entry.node.x = x;
    entry.node.y = top + i * ROW;
  });
}

function row(entries, y) {
  let total = entries.reduce((sum, e) => sum + e.node.w + DEPTH_GAP, -DEPTH_GAP);
  let x = -total / 2;
  for (const entry of entries) {
    entry.node.x = x + entry.node.w / 2;
    entry.node.y = y;
    x += entry.node.w + DEPTH_GAP;
  }
}
const DEPTH_GAP = 36;

function label(names, max = 3) {
  const list = [...names];
  return list.length <= max ? list.join(', ') : `${list.slice(0, max).join(', ')} +${list.length - max}`;
}

export function createFocus(layer, hooks) {
  function neighbour(cubes, id) {
    const cube = cubes.get(id);
    return cube ? cubeNode(cube) : foreignNode(id);
  }

  function render(model, id, filters) {
    layer.selectAll('*').remove();
    const cubes = new Map(model.cubes.map(c => [c.id, c]));
    const cube = cubes.get(id);
    if (!cube) return null;
    const visible = (other) => filters.tests || !(cubes.get(other)?.test);

    const center = cubeNode(cube);
    center.w += 30;
    center.h = 44;
    center.x = 0;
    center.y = 0;

    const senders = new Map();
    for (const message of cube.faces.afferent) {
      for (const sender of message.senders) {
        if (sender.from === id || !visible(sender.from)) continue;
        if (!senders.has(sender.from)) senders.set(sender.from, { names: new Set(), via: new Set() });
        senders.get(sender.from).names.add(message.name);
        if (sender.target !== id) senders.get(sender.from).via.add(sender.target);
      }
    }
    const left = [...senders.entries()].map(([other, info]) => ({
      node: neighbour(cubes, other), names: info.names, via: info.via, direction: 'in', kind: 'message',
    }));

    const crc = cube.faces.crc;
    const right = [...crc.collaborators.map(c => ({ ...c, kind: 'message' })), ...crc.procedures.map(c => ({ ...c, kind: 'procedure' }))]
      .filter(c => visible(c.id))
      .map(c => ({ node: neighbour(cubes, c.id), names: c.messages, direction: 'out', kind: c.kind }));

    const up = crc.kindOf.map(k => ({ node: neighbour(cubes, k.id), names: [], direction: 'out', kind: 'kindOf' }));
    const downIds = cube.kind === 'interface' ? crc.playedBy : crc.creates.map(c => c.id);
    const down = downIds.filter(visible).map(other => ({
      node: neighbour(cubes, other), names: [], direction: cube.kind === 'interface' ? 'in' : 'out',
      kind: cube.kind === 'interface' ? 'kindOf' : 'creates',
    }));

    column(left, -COLUMN);
    column(right, COLUMN);
    row(up, -Math.max(TIER, (Math.max(left.length, right.length) * ROW) / 2 - 40));
    row(down, Math.max(TIER, (Math.max(left.length, right.length) * ROW) / 2 - 40));

    const all = [...left, ...right, ...up, ...down];
    const links = all.map(entry => ({
      source: entry.direction === 'in' ? entry.node : center,
      target: entry.direction === 'in' ? center : entry.node,
      kind: entry.kind,
      bend: 0,
      text: label(entry.names) + (entry.via && entry.via.size ? `  (via ${[...entry.via].map(v => cubes.get(v)?.simpleName ?? v).join(', ')})` : ''),
    }));

    const titles = [[-COLUMN, left, 'Sends it messages']];
    if (cube.kind !== 'interface' || right.length) titles.push([COLUMN, right, 'It sends messages to']);
    for (const [x, entries, text] of titles) {
      const y = entries.length ? Math.min(...entries.map(e => e.node.y)) - 38 : -30;
      layer.append('text').attr('class', 'focus-title').attr('x', x).attr('y', y).attr('text-anchor', 'middle')
        .text(entries.length ? text : `${text}: nobody`);
    }
    if (up.length) {
      layer.append('text').attr('class', 'focus-title').attr('x', 0).attr('y', up[0].node.y - 34)
        .attr('text-anchor', 'middle').text('Is a kind of');
    }
    if (down.length) {
      layer.append('text').attr('class', 'focus-title').attr('x', 0).attr('y', down[0].node.y + 40)
        .attr('text-anchor', 'middle').text(cube.kind === 'interface' ? 'Played by' : 'Creates');
    }

    layer.append('g').selectAll('path')
      .data(links)
      .join('path')
      .attr('class', l => `edge ${l.kind}`)
      .attr('stroke-width', l => l.kind === 'message' ? 1.6 : 1.2)
      .attr('marker-end', l => `url(#arrow-${l.kind})`)
      .attr('d', edgePath);

    layer.append('g').selectAll('text')
      .data(links.filter(l => l.text))
      .join('text')
      .attr('class', 'edge-label')
      .attr('text-anchor', 'middle')
      .attr('x', l => {
        const [x1] = border(l.source, l.target.x, l.target.y);
        const [x2] = border(l.target, l.source.x, l.source.y);
        return (x1 + x2) / 2;
      })
      .attr('y', l => {
        const [, y1] = border(l.source, l.target.x, l.target.y);
        const [, y2] = border(l.target, l.source.x, l.source.y);
        return (y1 + y2) / 2 - 6;
      })
      .text(l => l.text);

    const nodes = [center, ...all.map(e => e.node)];
    layer.append('g').selectAll('g.node')
      .data(nodes)
      .join('g')
      .attr('class', n => [
        'node',
        n.foreign ? 'foreign' : '',
        n.role ? 'role' : '',
        n.test ? 'test' : '',
        n.nested ? 'nested' : '',
        n.flags.length ? `flag-${worstSeverity(n.flags)}` : '',
        !n.foreign && !n.role ? (n.written ? 'written' : 'unwritten') : '',
      ].join(' '))
      .attr('transform', n => `translate(${n.x},${n.y})`)
      .call(nodeShape)
      .on('mouseenter', (event, n) => hooks.onNodeHover(n, event))
      .on('mousemove', (event, n) => hooks.onNodeHover(n, event))
      .on('mouseleave', () => hooks.onNodeLeave())
      .on('click', (event, n) => {
        if (n.foreign) return;
        if (n === center) hooks.onOpen(n.id);
        else hooks.onFocus(n.id);
      });

    const xs = nodes.map(n => n.x);
    const ys = nodes.map(n => n.y);
    return {
      x0: Math.min(-COLUMN - 110, ...nodes.map(n => n.x - n.w / 2)) - 60,
      x1: Math.max(COLUMN + 110, ...nodes.map(n => n.x + n.w / 2)) + 60,
      y0: Math.min(...ys) - 90,
      y1: Math.max(...ys) + 90,
      cx: (Math.min(...xs) + Math.max(...xs)) / 2,
    };
  }

  return { render };
}
