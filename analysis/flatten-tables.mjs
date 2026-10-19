// Flatten the autotile tables from {{x,y},{x,y},{x,y},{x,y}} to flat int rows.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const p = `${REPO}/app-java/app/src/main/java/com/rmmv/editor/core/Tileset.java`;
let s = fs.readFileSync(p, 'utf8');

s = s.replace(/static final boolean\[\]\[\] FLOOR = \{/, 'static final int[][] FLOOR = {');
s = s.replace(/static final boolean\[\]\[\] WALL = \{/, 'static final int[][] WALL = {');

const re = /\{\{(\d+),(\d+)\},\{(\d+),(\d+)\},\{(\d+),(\d+)\},\{(\d+),(\d+)\}\}/g;
let count = 0;
s = s.replace(re, (m, a, b, c, d, e, f, g, h) => {
  count++;
  return '{' + [a, b, c, d, e, f, g, h].join(',') + '}';
});

fs.writeFileSync(p, s, 'utf8');
const flat = (s.match(/\{\d,\d,\d,\d,\d,\d,\d,\d\}/g) || []).length;
console.log('rows flattened:', count, '| flat rows now:', flat);
console.log('FLOOR int[][]:', /static final int\[\]\[\] FLOOR/.test(s));
console.log('WALL  int[][]:', /static final int\[\]\[\] WALL/.test(s));
