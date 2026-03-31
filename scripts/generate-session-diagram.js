#!/usr/bin/env node
// Generates an Excalidraw diagram of the CoCro session flow
// Usage: node scripts/generate-session-diagram.js
// Output: docs/session-flow.excalidraw  (import at https://excalidraw.com)

const fs = require('fs');
const path = require('path');

const elements = [];
let _id = 0;

function uid() { return `e${String(++_id).padStart(4, '0')}`; }
function sd() { return (_id * 7919 + 12345) % 1000000; }

const BASE = {
  angle: 0, fillStyle: 'solid', strokeWidth: 2, strokeStyle: 'solid',
  roughness: 0, opacity: 100, groupIds: [], frameId: null, roundness: null,
  version: 1, isDeleted: false, boundElements: [], updated: 1, link: null, locked: false,
};

function el(type, props) {
  const e = { ...BASE, id: uid(), type, seed: sd(), versionNonce: sd(), ...props };
  elements.push(e);
  return e.id;
}

function rect(x, y, w, h, opts = {}) {
  return el('rectangle', {
    x, y, width: w, height: h,
    strokeColor: opts.stroke || '#495057',
    backgroundColor: opts.bg || 'transparent',
    roundness: opts.rounded ? { type: 3 } : null,
    strokeStyle: opts.dashed ? 'dashed' : 'solid',
    strokeWidth: opts.sw || 2,
  });
}

function diamond(x, y, w, h, opts = {}) {
  return el('diamond', {
    x, y, width: w, height: h,
    strokeColor: opts.stroke || '#495057',
    backgroundColor: opts.bg || 'transparent',
    roundness: { type: 2 },
  });
}

function txt(x, y, w, h, str, opts = {}) {
  const fontSize = opts.size || 14;
  return el('text', {
    x, y, width: w, height: h,
    strokeColor: opts.color || '#1e1e1e',
    backgroundColor: 'transparent',
    roundness: null, strokeWidth: 1,
    text: str,
    fontSize,
    fontFamily: opts.mono ? 3 : 1,
    textAlign: opts.align || 'left',
    verticalAlign: opts.valign || 'top',
    baseline: Math.round(fontSize * 0.85),
    containerId: opts.container || null,
  });
}

function arrow(x1, y1, x2, y2, opts = {}) {
  return el('arrow', {
    x: x1, y: y1,
    width: Math.abs(x2 - x1), height: Math.abs(y2 - y1),
    strokeColor: opts.color || '#868e96',
    backgroundColor: 'transparent',
    roundness: { type: 2 },
    strokeStyle: opts.dashed ? 'dashed' : 'solid',
    strokeWidth: opts.sw || 2,
    startArrowhead: null, endArrowhead: 'arrow',
    points: [[0, 0], [x2 - x1, y2 - y1]],
    lastCommittedPoint: null,
    startBinding: null, endBinding: null,
  });
}

function curvedArrow(x1, y1, mx, my, x2, y2, opts = {}) {
  return el('arrow', {
    x: x1, y: y1,
    width: Math.abs(x2 - x1), height: Math.abs(Math.min(y1, y2, my) - Math.max(y1, y2, my)),
    strokeColor: opts.color || '#868e96',
    backgroundColor: 'transparent',
    roundness: { type: 2 },
    strokeStyle: opts.dashed ? 'dashed' : 'solid',
    strokeWidth: opts.sw || 2,
    startArrowhead: null, endArrowhead: 'arrow',
    points: [[0, 0], [mx - x1, my - y1], [x2 - x1, y2 - y1]],
    lastCommittedPoint: null,
    startBinding: null, endBinding: null,
  });
}

function hline(x1, y, x2, opts = {}) {
  return el('line', {
    x: x1, y, width: x2 - x1, height: 0,
    strokeColor: opts.color || '#dee2e6',
    backgroundColor: 'transparent', roundness: null,
    strokeWidth: opts.sw || 1, strokeStyle: 'solid',
    points: [[0, 0], [x2 - x1, 0]],
    lastCommittedPoint: null, startBinding: null, endBinding: null,
    startArrowhead: null, endArrowhead: null,
  });
}

// Helpers
function sectionBg(x, y, w, h, bg, stroke) {
  rect(x, y, w, h, { bg, stroke: stroke || '#ced4da', sw: 1 });
}

function sectionTitle(x, y, str, color) {
  txt(x, y, 900, 30, str, { color: color || '#1864ab', size: 22, align: 'left' });
}

function badge(x, y, label, bg, textColor) {
  rect(x, y, 56, 24, { bg, stroke: bg, rounded: true });
  txt(x, y + 5, 56, 14, label, { color: textColor || '#fff', size: 11, align: 'center' });
}

// ============================================================
// LAYOUT
// ============================================================
const W = 1540;

// ============================================================
// §1  STATE MACHINE  (y: 0–310)
// ============================================================
sectionBg(0, 0, W, 310, '#f8f9fa');
sectionTitle(20, 12, '🔄   SESSION STATE MACHINE', '#1864ab');

// States
diamond(30,  110, 130, 80,  { bg: '#e9ecef', stroke: '#868e96' });
txt(95,  143, 0, 0, '(new)',       { color: '#495057', size: 13, align: 'center' });

rect(230, 110, 170, 70,  { bg: '#d3f9d8', stroke: '#2f9e44', rounded: true });
txt(315,  138, 0, 0, 'PLAYING',     { color: '#2f9e44', size: 18, align: 'center' });

rect(510, 110, 210, 70,  { bg: '#ffe8cc', stroke: '#e8590c', rounded: true });
txt(615,  138, 0, 0, 'INTERRUPTED', { color: '#e8590c', size: 16, align: 'center' });

rect(840, 110, 150, 70,  { bg: '#dbe4ff', stroke: '#4263eb', rounded: true });
txt(915,  138, 0, 0, 'ENDED',       { color: '#4263eb', size: 18, align: 'center' });

// Arrows
arrow(161, 145, 229, 145, { color: '#2f9e44', sw: 2 });
txt(168, 124, 60, 0, 'join', { color: '#2f9e44', size: 11, align: 'center' });

arrow(401, 130, 509, 130, { color: '#e8590c', sw: 2 });
txt(415, 106, 90, 0, 'last leave', { color: '#e8590c', size: 11, align: 'center' });
txt(415, 119, 90, 0, '/ timeout',  { color: '#e8590c', size: 11, align: 'center' });

// INTERRUPTED → PLAYING (curved below)
curvedArrow(510, 178, 455, 240, 400, 178, { color: '#2f9e44', dashed: true, sw: 2 });
txt(380, 228, 140, 0, 'rejoin (prev. LEFT)', { color: '#2f9e44', size: 11, align: 'center' });

// PLAYING → ENDED (arc above)
curvedArrow(401, 112, 615, 40, 839, 112, { color: '#4263eb', sw: 2 });
txt(555, 24, 180, 0, 'check ✓ complete+correct', { color: '#4263eb', size: 11, align: 'center' });

// Warning note
rect(1060, 110, 400, 90, { bg: '#fff9db', stroke: '#fcc419', sw: 1, dashed: true, rounded: true });
txt(1072, 118, 376, 74, '⚠️  Sessions never expire\nINTERRUPTED persists until someone rejoins\n\n⚠️  Only previously-LEFT participants can rejoin\nINTERRUPTED — new users are locked out', { color: '#856404', size: 12 });

// ============================================================
// §2  REST ENDPOINTS  (y: 325–660)
// ============================================================
const R_Y = 325;
sectionBg(0, R_Y, W, 335, '#e7f5ff');
sectionTitle(20, R_Y + 12, '📡   REST ENDPOINTS', '#1864ab');

// Headers
txt(20,  R_Y + 50, 80,  18, 'METHOD',   { color: '#6c757d', size: 11 });
txt(96,  R_Y + 50, 380, 18, 'ENDPOINT', { color: '#6c757d', size: 11 });
txt(490, R_Y + 50, 440, 18, 'RETURNS',  { color: '#6c757d', size: 11 });
txt(950, R_Y + 50, 560, 18, 'NOTES',    { color: '#6c757d', size: 11 });
hline(10, R_Y + 71, W - 10, { color: '#adb5bd', sw: 1 });

const rows = [
  { m: 'POST', path: '/api/sessions',
    body: '{ gridId }',
    ret: '201  { sessionId, shareCode }',
    note: 'Creates session (status=PLAYING, no participants yet)' },
  { m: 'POST', path: '/api/sessions/join',
    body: '{ shareCode }',
    ret: '200  SessionFullDto\n{ sessionId, shareCode, status,\n  participantCount, topicToSubscribe,\n  gridTemplate, gridRevision, cells[] }',
    note: '⚠️  Creator must call this too!\n409 if SessionFull / ENDED\n400 if invalid status' },
  { m: 'POST', path: '/api/sessions/leave',
    body: '{ shareCode }',
    ret: '200',
    note: 'Last participant → INTERRUPTED\n+ broadcasts SessionInterrupted' },
  { m: 'GET',  path: '/api/sessions/{code}/state',
    body: '—',
    ret: 'SessionGridStateDto\n{ revision, cells[] }',
    note: 'Redis cache → MongoDB fallback\nAny authenticated user' },
  { m: 'POST', path: '/api/sessions/{code}/sync',
    body: '—',
    ret: 'SessionFullDto',
    note: 'JOINED participant only\nFlushes Redis → MongoDB' },
  { m: 'POST', path: '/api/sessions/{code}/check',
    body: '—',
    ret: 'GridCheckSuccess\n{ isComplete, isCorrect,\n  filledCount, totalCount }',
    note: 'JOINED participant only\nFlushes + validates grid\nMay trigger SessionEnded' },
];

const MC = { POST: '#2f9e44', GET: '#1971c2' };
let ry = R_Y + 77;
for (const [i, row] of rows.entries()) {
  const lines = row.ret.split('\n').length;
  const nlines = row.note.split('\n').length;
  const rowH = Math.max(lines, nlines) * 16 + 16;

  badge(20, ry + (rowH - 24) / 2, row.m, MC[row.m] || '#868e96', '#fff');
  txt(96,  ry + 6, 380, rowH - 12, row.path + '\n' + row.body, { color: '#343a40', size: 12, mono: true });
  txt(490, ry + 6, 440, rowH - 12, row.ret,  { color: '#343a40', size: 11 });
  txt(950, ry + 6, 560, rowH - 12, row.note, { color: i === 1 || i === 2 ? '#c92a2a' : '#868e96', size: 11 });

  ry += rowH;
  if (i < rows.length - 1) hline(10, ry, W - 10);
}

// ============================================================
// §3  WEBSOCKET / STOMP  (y: 675–970)
// ============================================================
const WS_Y = 675;
sectionBg(0, WS_Y, W, 295, '#f3f0ff');
sectionTitle(20, WS_Y + 12, '🔌   WEBSOCKET / STOMP', '#5f3dc4');

const panels = [
  { x: 20,   label: '1. Connection',
    body: 'WS handshake  →  /ws\n\nSTOMP CONNECT\n{ Authorization: Bearer <token>,\n  shareCode: <code> }\n\n← STOMP CONNECTED' },
  { x: 390,  label: '2. Subscribe (in afterConnected)',
    body: 'Private welcome:\nSUBSCRIBE /app/session/{code}/welcome\n← SessionWelcome  (synchronous, private)\n\nBroadcast channel:\nSUBSCRIBE /topic/session/{code}\n← all broadcast events' },
  { x: 780,  label: '3. Send Commands',
    body: 'SEND /app/session/{code}/grid\n{ posX, posY, commandType, letter? }\n→ broadcasts GridUpdated\n   or → SyncRequired on CAS conflict\n\nSEND /app/session/{code}/heartbeat\n(every ≤30s to stay ACTIVE)' },
  { x: 1150, label: '4. Private Events',
    body: '/user/queue/session\n\nSessionWelcome\n{ shareCode, topicToSubscribe,\n  participantCount, status,\n  gridRevision }\n\nSyncRequired  { currentRevision }' },
];

for (const p of panels) {
  rect(p.x, WS_Y + 52, 360, 210, { bg: '#ede9fe', stroke: '#7950f2', rounded: true, sw: 1 });
  txt(p.x + 12, WS_Y + 60, 336, 20, p.label, { color: '#5f3dc4', size: 13 });
  hline(p.x + 8, WS_Y + 82, p.x + 352, { color: '#9775fa' });
  txt(p.x + 12, WS_Y + 90, 336, 160, p.body, { color: '#343a40', size: 12, mono: true });
}

// ============================================================
// §4  BROADCAST EVENTS  (y: 985–1100)
// ============================================================
const EV_Y = 985;
sectionBg(0, EV_Y, W, 115, '#f8f9fa');
txt(20, EV_Y + 10, 900, 24, '📨   BROADCAST EVENTS  →  /topic/session/{shareCode}', { color: '#343a40', size: 18 });

const evs = [
  ['ParticipantJoined',   '{ userId, participantCount }'],
  ['ParticipantLeft',     '{ userId, participantCount, reason }'],
  ['GridUpdated',         '{ actorId, posX, posY, commandType, letter }'],
  ['GridChecked',         '{ isComplete, isCorrect, correctCount, totalCount }'],
  ['SessionEnded',        '{ shareCode, correctCount, totalCount }'],
  ['SessionInterrupted',  '{ shareCode }'],
];
let ex = 20;
for (const [name, payload] of evs) {
  const w = Math.max(name.length * 8.5 + 24, 200);
  rect(ex, EV_Y + 44, w, 58, { bg: '#e9ecef', stroke: '#868e96', rounded: true, sw: 1 });
  txt(ex + 10, EV_Y + 50, w - 20, 20, name,    { color: '#343a40', size: 13 });
  txt(ex + 10, EV_Y + 70, w - 20, 24, payload, { color: '#868e96', size: 10 });
  ex += w + 10;
}

// ============================================================
// §5  HEARTBEAT  (y: 1115–1255)
// ============================================================
const HB_Y = 1115;
sectionBg(0, HB_Y, 750, 140, '#fff9db');
txt(20, HB_Y + 10, 500, 22, '💓   HEARTBEAT & RECONNECT', { color: '#e67700', size: 18 });

const hbStates = [
  { x: 20,  label: 'ACTIVE',  bg: '#d3f9d8', stroke: '#2f9e44', tc: '#2f9e44' },
  { x: 195, label: 'AWAY',    bg: '#fff3bf', stroke: '#fcc419', tc: '#e67700' },
  { x: 370, label: 'EVICTED', bg: '#ffe8cc', stroke: '#e8590c', tc: '#e8590c' },
  { x: 545, label: 'LEFT',    bg: '#ffd8d8', stroke: '#c92a2a', tc: '#c92a2a' },
];
for (const s of hbStates) {
  rect(s.x, HB_Y + 52, 130, 50, { bg: s.bg, stroke: s.stroke, rounded: true });
  txt(s.x + 65, HB_Y + 70, 0, 0, s.label, { color: s.tc, size: 14, align: 'center' });
}
arrow(151, HB_Y + 77, 194, HB_Y + 77, { color: '#868e96' });
txt(152, HB_Y + 57, 42, 0, 'discon.', { color: '#868e96', size: 10, align: 'center' });

arrow(326, HB_Y + 77, 369, HB_Y + 77, { color: '#e8590c' });
txt(316, HB_Y + 57, 56, 0, '>30s tmout', { color: '#e8590c', size: 10, align: 'center' });

arrow(501, HB_Y + 77, 544, HB_Y + 77, { color: '#c92a2a' });
txt(504, HB_Y + 57, 40, 0, 'leave()', { color: '#c92a2a', size: 10, align: 'center' });

// Reconnect arrow above
curvedArrow(194, HB_Y + 58, 172, HB_Y + 24, 151, HB_Y + 58, { color: '#2f9e44', dashed: true });
txt(108, HB_Y + 20, 80, 0, 'reconnect\n(<30s, no\nbroadcast)', { color: '#2f9e44', size: 9, align: 'center' });

// ============================================================
// §6  CAS CONFLICT  (y: 1115–1255, right)
// ============================================================
sectionBg(760, HB_Y, 780, 140, '#fff9db');
txt(780, HB_Y + 10, 600, 22, '⚡   CAS CONFLICT FLOW', { color: '#e67700', size: 18 });
txt(780, HB_Y + 42, 740, 110,
  '① Client sends grid update  →  compareAndSet() detects revision mismatch\n' +
  '② BFF fetches authoritative revision  (Redis → MongoDB fallback)\n' +
  '③ Sends private SyncRequired { currentRevision } → /user/queue/session\n' +
  '④ Returns 409 ConcurrentModification\n' +
  '⑤ Client receives SyncRequired → calls POST /sync\n' +
  '⑥ POST /sync flushes Redis → MongoDB, returns SessionFullDto\n' +
  '⑦ Client rehydrates grid, resumes editing',
  { color: '#343a40', size: 12 });

// ============================================================
// §7  BLINDSPOTS  (y: 1270–1660)
// ============================================================
const BS_Y = 1270;
sectionBg(0, BS_Y, W, 390, '#fff3cd');
txt(20, BS_Y + 12, 1100, 30, '⚠️   BLINDSPOTS & POTENTIAL GAPS', { color: '#856404', size: 22 });

const bs = [
  ['1. INTERRUPTED — new participants locked out',
   'Only previously-LEFT participants can rejoin INTERRUPTED sessions.\n' +
   'A brand-new user who never joined at all CANNOT enter.\n' +
   'Undocumented behavior — may surprise client developers.'],
  ['2. Sessions never expire',
   'INTERRUPTED sessions persist indefinitely in MongoDB + Redis.\n' +
   'No TTL, no cleanup scheduler. A month-old abandoned session\n' +
   'stays in the DB. Consider an expiry/archive mechanism.'],
  ['3. POST /sync requires JOINED participant',
   'If a user reconnects (STOMP) but hasn\'t called POST /join yet,\n' +
   'they cannot call POST /sync. The reconnect sequence\n' +
   'CONNECT → /join → sync must be followed strictly.'],
  ['4. Creator must call POST /join separately',
   'POST /api/sessions creates the session but does NOT add\n' +
   'the creator as participant. They must immediately call\n' +
   'POST /join — an unusual two-step UX pattern.'],
  ['5. SyncRequired — no retry/catch-up',
   'SyncRequired is sent once as a private STOMP message.\n' +
   'If the client misses it (hidden tab, micro-disconnect)\n' +
   'there\'s no retry — client silently falls behind.'],
  ['6. Angular GridEditor (Phase 4) pending',
   'Grid creation UI is not yet implemented in cocro-web.\n' +
   'Users cannot create new grids through the frontend.\n' +
   'Only grids inserted via API/DB work end-to-end today.'],
];

let bsx = 20, bsy = BS_Y + 55;
const BW = 475, BH = 125;
for (let i = 0; i < bs.length; i++) {
  const [title, desc] = bs[i];
  rect(bsx, bsy, BW, BH, { bg: '#fffbf0', stroke: '#ffc107', rounded: true, sw: 1 });
  txt(bsx + 12, bsy + 10, BW - 24, 22, title, { color: '#856404', size: 13 });
  hline(bsx + 8, bsy + 34, bsx + BW - 8, { color: '#ffd43b' });
  txt(bsx + 12, bsy + 40, BW - 24, BH - 48, desc, { color: '#495057', size: 11 });
  bsx += BW + 20;
  if ((i + 1) % 3 === 0) { bsx = 20; bsy += BH + 16; }
}

// ============================================================
// OUTPUT
// ============================================================
const diagram = {
  type: 'excalidraw',
  version: 2,
  source: 'https://excalidraw.com',
  elements,
  appState: { gridSize: null, viewBackgroundColor: '#ffffff' },
  files: {},
};

const outPath = path.join(__dirname, '..', 'docs', 'session-flow.excalidraw');
fs.writeFileSync(outPath, JSON.stringify(diagram, null, 2));
console.log(`✅  Generated ${elements.length} elements → ${outPath}`);
