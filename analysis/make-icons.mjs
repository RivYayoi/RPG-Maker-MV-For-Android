// Generate the PC-style toolbar vector icons (the framework's ic_menu_* set is
// incomplete and several icons we need do not exist).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const DIR = `${REPO}/app-java/app/src/main/res/drawable`;

const ICONS = {
  // (1) new project: sheet with a folded corner
  ic_new: `<path android:fillColor="#E8ECF5" android:pathData="M6,3 L14,3 L18,7 L18,21 L6,21 Z M14,3 L14,7 L18,7 Z M9,12 L15,12 L15,13.4 L9,13.4 Z M9,15.5 L15,15.5 L15,16.9 L9,16.9 Z"/>`,
  // open project: folder
  ic_open: `<path android:fillColor="#E8ECF5" android:pathData="M3,6 L10,6 L12,8.5 L21,8.5 L21,19 L3,19 Z"/>`,
  // save: floppy
  ic_save: `<path android:fillColor="#E8ECF5" android:pathData="M4,4 L17,4 L20,7 L20,20 L4,20 Z M8,4 L8,10 L16,10 L16,4 Z M8,13 L16,13 L16,20 L8,20 Z"/>`,
  // undo: arrow curving left
  ic_undo: `<path android:fillColor="#E8ECF5" android:pathData="M8,7 L3,12 L8,17 L8,13.5 L14,13.5 C16.5,13.5 18,15 18,17.5 L20,17.5 C20,13.6 17.3,11 14,11 L8,11 Z"/>`,
  // redo: mirror of undo
  ic_redo: `<path android:fillColor="#E8ECF5" android:pathData="M16,7 L21,12 L16,17 L16,13.5 L10,13.5 C7.5,13.5 6,15 6,17.5 L4,17.5 C4,13.6 6.7,11 10,11 L16,11 Z"/>`,
  // zoom in
  ic_zoom_in: `<path android:fillColor="#E8ECF5" android:pathData="M10,3 A7,7 0 1,0 10,17 A7,7 0 1,0 10,3 Z M10,5.5 A4.5,4.5 0 1,1 10,14.5 A4.5,4.5 0 1,1 10,5.5 Z M9,7 L11,7 L11,9 L13,9 L13,11 L11,11 L11,13 L9,13 L9,11 L7,11 L7,9 L9,9 Z M15.2,15.2 L20,20 L18.6,21.4 L13.8,16.6 Z"/>`,
  // zoom out
  ic_zoom_out: `<path android:fillColor="#E8ECF5" android:pathData="M10,3 A7,7 0 1,0 10,17 A7,7 0 1,0 10,3 Z M10,5.5 A4.5,4.5 0 1,1 10,14.5 A4.5,4.5 0 1,1 10,5.5 Z M7,9 L13,9 L13,11 L7,11 Z M15.2,15.2 L20,20 L18.6,21.4 L13.8,16.6 Z"/>`,
  // actual size: 1:1 box
  ic_actual: `<path android:fillColor="#E8ECF5" android:pathData="M4,4 L20,4 L20,20 L4,20 Z M6,6 L6,18 L18,18 L18,6 Z M10,8 L10,16 L14,16 L14,8 Z"/>`,
  // map mode: folded map
  ic_mapmode: `<path android:fillColor="#E8ECF5" android:pathData="M3,5 L9,3 L15,6 L21,4 L21,19 L15,21 L9,18 L3,20 Z M9,5.5 L9,16.4 L15,19.2 L15,8.3 L9,5.5 Z"/>`,
  // event mode: map pin
  ic_eventmode: `<path android:fillColor="#E8ECF5" android:pathData="M12,2 A7,7 0 0,0 12,16 C12,16 18,9.5 18,8 A6,6 0 0,0 6,8 C6,9.5 12,16 12,16 Z M12,5.5 A2.8,2.8 0 1,1 12,11.1 A2.8,2.8 0 1,1 12,5.5 Z"/>`,
  // grid toggle
  ic_grid: `<path android:fillColor="#E8ECF5" android:pathData="M3,3 L21,3 L21,21 L3,21 Z M4.5,4.5 L10,4.5 L10,10 L4.5,10 Z M14,4.5 L19.5,4.5 L19.5,10 L14,10 Z M4.5,14 L10,14 L10,19.5 L4.5,19.5 Z M14,14 L19.5,14 L19.5,19.5 L14,19.5 Z"/>`,
  // fit to map
  ic_fit: `<path android:fillColor="#E8ECF5" android:pathData="M3,3 L9,3 L9,5.5 L5.5,5.5 L5.5,9 L3,9 Z M15,3 L21,3 L21,9 L18.5,9 L18.5,5.5 L15,5.5 Z M3,15 L5.5,15 L5.5,18.5 L9,18.5 L9,21 L3,21 Z M18.5,15 L21,15 L21,21 L15,21 L15,18.5 L18.5,18.5 Z"/>`,
  // new map
  ic_newmap: `<path android:fillColor="#E8ECF5" android:pathData="M4,4 L20,4 L20,20 L4,20 Z M11,7 L13,7 L13,10.5 L16.5,10.5 L16.5,12.5 L13,12.5 L13,16 L11,16 L11,12.5 L7.5,12.5 L7.5,10.5 L11,10.5 Z"/>`,
  // map properties
  ic_props: `<path android:fillColor="#E8ECF5" android:pathData="M4,4 L20,4 L20,20 L4,20 Z M6.5,7.5 L17.5,7.5 L17.5,9 L6.5,9 Z M6.5,11.5 L17.5,11.5 L17.5,13 L6.5,13 Z M6.5,15.5 L13,15.5 L13,17 L6.5,17 Z"/>`,
  // database
  ic_database: `<path android:fillColor="#E8ECF5" android:pathData="M12,3 C17,3 20,4.5 20,6.5 L20,17.5 C20,19.5 17,21 12,21 C7,21 4,19.5 4,17.5 L4,6.5 C4,4.5 7,3 12,3 Z M12,5 C8.1,5 6,5.9 6,6.5 C6,7.1 8.1,8 12,8 C15.9,8 18,7.1 18,6.5 C18,5.9 15.9,5 12,5 Z"/>`,
  // plugin manager: puzzle piece
  ic_plugin: `<path android:fillColor="#E8ECF5" android:pathData="M10,3 L14,3 L14,6 A2.2,2.2 0 1,0 14,10.4 L18,10.4 L18,14.4 L15,14.4 A2.2,2.2 0 1,0 10.6,14.4 L10.6,18 L6.6,18 L6.6,14.4 A2.2,2.2 0 1,0 6.6,10.4 L10,10.4 Z"/>`,
  // resource manager: folder with image
  ic_resource: `<path android:fillColor="#E8ECF5" android:pathData="M3,5 L10,5 L12,7.5 L21,7.5 L21,19 L3,19 Z M6,11 L18,11 L18,17 L6,17 Z M8,15.5 L11,12.5 L13.5,15 L15,13.5 L16.5,15.5 Z"/>`,
};

for (const [name, path] of Object.entries(ICONS)) {
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#E8ECF5">
    ${path}
</vector>
`;
  fs.writeFileSync(`${DIR}/${name}.xml`, xml, 'utf8');
}
console.log('wrote', Object.keys(ICONS).length, 'vector icons');
