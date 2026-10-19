# 兼容层设计文档 / Compatibility-Layer-Plan.md

**项目**：RPG Maker MV 编辑器 → Android APK（MVP）
**工作目录**：`C:\Users\Yayoi\rmmv-editor-android`
**阶段**：Phase 2（兼容层设计）→ Phase 3（容器化）→ Phase 4（构建验证）
**对应证据**：[`android-incompatibility.json`](./android-incompatibility.json)（REA 边界图）

---

## 0. 设计前提：Phase 1 推翻的原假设

任务书要求"为编辑器程序本身（NW.js 桌面应用）编写 fs/path/nw Shim"。Phase 1 的 REA 静态分析用 **204 个 JS 文件的完整扫描**证明：

| 类别 | 命中数 | 涉及文件数 | **位于编辑器 UI 代码中** |
|---|---|---|---|
| 1. Node.js 核心模块 | 29 | 2 | **0** |
| 2. NW.js 桌面 API | 5 | 2 | **0** |
| 3. 原生路径假设 | 41 | 5 | 1（编译进 RPGMV.exe，无 JS 接口）|
| 4. IPC / 渲染进程边界 | **0** | **0** | **0** |

**关键事实**：`RPGMV.exe` 是 **Qt5/QML 原生桌面程序**（PE32/x86；`QtQuick` 字符串 ×451、`QQmlApplicationEngine` ×6、`NewData` ×898）。安装目录下的 `nwjs-win` / `nwjs-lnx` / `nwjs-win-test` 是**游戏播放器**（可执行文件名为 `Game.exe`/`game.exe`，`nwjs-win/www/` 里只有一个 `icon/` 目录）。

因此：

- **没有可供 shim 的编辑器 JavaScript**。`fs`/`path`/`nw` 不是"编辑器会用到的符号"，而是"游戏运行时才会用到、且本项目明确不移植的符号"。
- Phase 1 中唯一真正的 **阻断级（阻断）** 边界是类别 3：编辑器通过 Qt `QStandardPaths`/`QDir` 解析用户目录（Windows 上即 `AppData`）。这段逻辑编译在 `RPGMV.exe` 内部，**没有 JS 接口，无法从 WebView 打补丁**。

**本移植采取的路线**：把"移植编辑器 UI"换成"移植编辑器的**数据契约**"，即 **RPG Maker MV 工程文件格式**。Android 端重新实现该格式的读写，使工程能在桌面版 `RPGMV.exe` 与本 APK 之间**双向打开**。Shim 仍然照常提供（见 §2–§4），但它们承载的是 **MVP 应用自身**对 Node 风格的依赖，而不是桌面编辑器的残留依赖。

---

## 1. 兼容层总览

```
index.html
  │
  ├─ <head> 最先加载（同步执行，早于任何应用代码）   ← 注入点
  │    1. shim/bridge-loader.js    RMMVBridge + Capacitor 原生桥接轮询
  │    2. shim/path-shim.js         全局 path
  │    3. shim/android-fs-shim.js   全局 fs（同步 VFS + 异步落盘）
  │    4. shim/nw-shim.js           全局 nw（降级）
  │
  └─ <body> 末尾加载（此时 fs/path/nw 已就绪）
       js/mvformat.js   MV 工程格式层（tileId 代数、Map/Event/MapInfos schema）
       js/tileset.js    图块合成与渲染
       js/store.js      工程持久化（经 fs shim）
       js/editor.js     触摸 UI
```

**注入点验证**：`shim/*.js` 位于 `<head>` 且在所有 `<body>` 脚本之前；四个 shim 均为 **IIFE 同步执行**，不依赖任何异步回调。因此 DOMContentLoaded 时 `fs`、`path`、`nw` 一定已定义 —— 这正是 Phase 4 要用 logcat 消除的 `fs is not defined` / `nw is not defined` / `path is not defined` 三类崩溃的根治点。

---

## 2. 文件系统 Shim（`shim/android-fs-shim.js`）

### 对应 Phase-1 条目

| Phase-1 记录 | 风险 | 本 Shim 的处置 |
|---|---|---|
| `rpg_managers.js:608-715` `require('fs')`（9 处，`DataManager.makeBackupFile`，`isLocalMode()` 守卫） | 可裁剪 | **不移植**：游戏运行时存档备份，属任务明确排除范围 |
| `rpg_managers.js:756` `require('path')` | 可裁剪 | **不移植** |
| `RPGMV.exe` Qt `QStandardPaths`（AppData） | **阻断** | **本 Shim 即为替代方案**（见下） |

### 设计：同步 VFS 外壳 + 异步 Capacitor 后端

任务书要求把 `fs.readFileSync` / `writeFileSync` / `existsSync` 映射到 Capacitor Filesystem。**Capacitor Filesystem 只有 Promise API，Android WebView 不给 JavaScript 任何同步的原生桥接**，所以字面意义的 1:1 同步 shim 不可能实现。

本实现采用**内存镜像 + 异步灌水/落盘**：

```
openProject()    : 递归 readdir/readFile (async)  → 填充内存 VFS
readFileSync()   : 直接读 VFS            → 真正同步
writeFileSync()  : 写 VFS + 标记 dirty   → 真正同步
Store.save()     : 对每个 dirty 路径 writeFile (async) → 落盘
```

这样所有调用点保持同步（MV 格式层仍是 Node 形态），同时正确跨过异步原生边界。MVP 工程体量在数百 KB 量级，常驻内存可接受。

### 后端存储位置（解决类别 3 阻断项）

```
Directory.Documents  ==  /data/data/com.rmmv.editor/files/Documents
```

- **不需要运行时权限**：应用私有目录在所有 API 等级上都豁免 Scoped Storage。
- **不需要 `MANAGE_EXTERNAL_STORAGE`**：不需要全文件访问权，避免 Play 商店"所有文件访问"声明与用户摩擦。
- `MANAGE_EXTERNAL_STORAGE` 被**刻意排除**，理由记录在 `AndroidManifest.xml` 注释中。

### 二进制资源处理

PNG/OGG/TTF 不能经 utf8 往返（会损坏）。Shim 按扩展名自动切换：二进制以 **base64** 存取（`readBase64Sync` / `bytesToB64`），文本以 utf8。新建工程时从 APK 资源 `_template/` 复制脚手架即依赖此路径。

### 提供的 API

`readFileSync` `readBase64Sync` `readBinarySync` `existsSync` `statSync` `readdirSync` `writeFileSync` `appendFileSync` `mkdirSync` `mkdirpSync` `unlinkSync` `rmSync` `rmdirSync` `renameSync` `copyFileSync` + Promise 版本 `readFile` `writeFile` `mkdir` `readdir` `unlink` + 控制面 `_loadTree` `_flush` `_clear`

---

## 3. 路径 Shim（`shim/path-shim.js`）

纯 JavaScript 重实现，零原生依赖：`join` `resolve` `normalize` `dirname` `basename` `extname` `isAbsolute` `relative` `parse` `format`，附 `posix`/`win32` 自引用别名与 `sep`/`delimiter`。

语义要点：路径一律为 **POSIX 相对路径**（工程内统一使用 `Projects/<Name>/data/Map001.json` 形式），自动折叠 `.`/`..`，根目录处吞掉 `..`。仅当宿主未提供 `path` 时才注入全局，不覆盖真实实现。

---

## 4. NW.js API 降级（`shim/nw-shim.js`）

### 对应 Phase-1 条目

| Phase-1 记录 | 风险 | 处置 |
|---|---|---|
| `rpg_core.js:195` `nw.App.argv`（已被 `typeof nw !== "undefined"` 守卫） | 可裁剪 | 提供降级 `nw.App`，游戏运行时那段守卫表达式可安全求值 |
| `rpg_core.js:3211` `gui.*`（Pixi 插件桩） | 可裁剪 | 降级为 no-op |
| `rpg_managers.js:1875-1877` `gui.*`（移动 UA 字符串误报） | 可裁剪 | 无需处理 |
| **全树 0 处** `nw.Window` / `nw.Menu` / `nw.Shell` / `nw.Clipboard` | — | **结构性地不存在**（编辑器用 Qt，非 NW.js）。Shim 仅保证符号有定义 |

### 降级映射

| NW.js API | Android 替代 |
|---|---|
| `nw.Window.open/close` | 单 Activity 单 WebView，语义退化；`showDevTools` → 应用内诊断面板 |
| `nw.Window.minimize/maximize` | **移除**（移动端无意义） |
| `nw.Menu` / `nw.MenuItem` | 触摸友好的 HTML 底部操作栏 + 长按上下文菜单 |
| `nw.Clipboard.writeText` | 标准 `navigator.clipboard.writeText` |
| `nw.Shell.openExternal` | `<a target="_system">` 交由 Android Intent |
| `nw.Shell.browseOpen/browseSave` | **ACTION_OPEN_DOCUMENT**（见 §5） |
| `nw.Utils.guid` | `crypto.randomUUID()`，同形状回退 |
| `nw.screenSaver.disable` | no-op（Android 自行管理） |

---

## 5. 文件选择器桥接（`shim/bridge-loader.js`）

| 桌面端（QML/C++，无 JS 接口） | Android 端 |
|---|---|
| `QFileDialog.getOpenFileName` | `ACTION_OPEN_DOCUMENT`，通过 `<input type="file">` —— **无需插件、兼容性最好**，已实现为 `RMMVBridge.pickFile({accept})` |
| `QFileDialog.getSaveFileName` | `RMMVBridge.shareText(name, text)`（Blob 下载） |
| QStandardPaths 应用数据目录 | Capacitor `Filesystem` + `Directory.Documents` |

`bridge-loader.js` 另负责**桥接时序**：Capacitor 的 JS bundle 由原生侧异步注入，因此在 `<head>` 同步执行时 `window.CapacitorFilesystem` 可能尚不存在。处理方式是 shim 立即安装（后端延后绑定），并暴露 `RMMVBridge.awaitReady()` Promise 供所有异步调用方 await。

---

## 6. UI 触摸适配（Phase 3 实施记录）

| 桌面交互 | Android 替代 | 实现位置 |
|---|---|---|
| 鼠标拖拽平移 | 单指拖拽（"移动"模式）/ 双指平移 | `editor.js` `press` 手势 + 底部 `移动` 按钮 |
| 鼠标左键拖拽绘制 | 单指拖拽绘制（"图块"模式） | `editor.js` `paint` 手势 |
| 鼠标滚轮缩放 | 双指捏合缩放（0.2×–4×） | `editor.js` 双指分支 |
| 鼠标右键菜单 | **长按 480ms** 或 FAB 浮动按钮 | `editor.js` `openContextMenu` / `fabMenu` |
| 顶部菜单栏 | 底部操作栏（6 个大按钮：图块/移动/事件/网格/全图/日志） | `index.html` `.bottombar` |
| Ctrl+S | 顶栏 💾 按钮 / FAB 菜单 | `doSave()` |
| Ctrl+Z / Ctrl+Y | FAB 菜单"撤销/重做"（DeX 外接键盘时快捷键仍生效） | `doUndo()` / `doRedo()` |
| 悬停显示坐标 | 光标角标实时显示 x/y/z/tileId | `updateCursorInfo()` |
| 弹出式对话框 | 底部抽屉（Bottom Sheet） | `#eventpanel` |
| 一次拖拽 = 一次撤销 | 拖拽笔画合并为单个撤销快照 | `beginStroke()` / `strokeUndoTaken` |

### 6.1 拖拽手势分配

| 手势 | 绘制模式 | 移动模式 | 事件模式 |
|---|---|---|---|
| 单指点击 | 画一格 | 什么都不做 | 选中事件 |
| 单指拖拽 | **绘制** | **平移** | 平移 |
| 双指 | 捏合缩放 + 平移 | 同左 | 同左 |
| 长按 | 禁用（避免打断笔画） | 上下文菜单 | 上下文菜单 |
| 桌面中键拖拽 | 平移 | 平移 | 平移 |

"移动"按钮的存在是必需的：在绘制模式下只要选了笔刷，单指拖拽就一定被解释为绘制，
没有这个出口就**完全无法单指平移地图**。双指始终可用于平移缩放。

---

## 7. MVP 已知缺口（明确记录，不猜测）

### 7.1 关键区分：**预览** 与 **落盘数据**

本次移植中最重要的架构决策，是把"渲染预览"和"写入数据"彻底解耦：

| | 依据 | 状态 |
|---|---|---|
| **写入 `MapXXX.json` 的 tileId** | `rpg_core.js:5273-5302` 常量与代数，逐行验证 | ✅ 已验证 |
| **`data[]` 索引** | `rpg_objects.js:5929` `data[(z*height+y)*width+x]` | ✅ 已验证 |
| **在 App 内把 tileId 画成像素** | 图块组 → 合成画布 block 的绑定 | ⚠️ 见 7.2 |

因此即使预览不完全精确，**桌面 `RPGMV.exe` 打开本 App 产出的工程时，走的是它自己正确的渲染路径，显示是正确的**。这是"宁可少做，不可做错"原则的直接体现。

### 7.2 自动图块预览的 block 绑定（⚠️）

**已验证**：自动图块源区是合成画布左上 **768×768**（最大源坐标 `(14*2+3)*24+24 = 768`），A1/A2/A3/A4 四组通过各自 `bx/by` 落在同一 block 的不同纵向偏移上。

**未验证**：MV 的 `Tileset` 类（把 9 张 `tilesetNames` 组装成图块画布、并绑定 A1–A4 各组）**不在随包发行的 `NewData/js/` 里** —— 已 grep 全部 6 个 `rpg_*.js` 与 6 个 `js/libs/*.js`，无 `Tileset.prototype` 定义。它编译在 `RPGMV.exe` 的 Qt 资源内，本机未安装 Ghidra/Hopper 深度 provider，无法读取。

实测确认 shipped 素材的真实尺寸：

```
World_A1.png = 768x576    World_A2.png = 768x576
World_B.png  = 768x768    World_C.png = 768x768
```

⇒ 合成画布按 **3×3 的 768×768 block、原生尺寸不缩放**排布（2304×2304）。

> 踩坑记录：初版误按 512×512 block 且把 768×576 缩放到 512×512，剪切了图块网格，导致 128 个图块**全部**采样到透明像素。修正为原生尺寸后恢复。

处理方式：block 选择采用**探测法**（`pickBlock` 逐 block 检查该 origin 是否有像素），不猜测。实测 128 个自动图块中 **123 个渲染出真实美术**；其余 5 个落在未打包的图块区域内而为空 —— 属打包范围限制，非逻辑错误。

### 7.3 其他缺口

1. **非自动图块（B/C/D/E/A5，tileId 1–1535）未提供**
   与 7.2 同因：set 号 → 源图像绑定在 Qt 资源内无法验证。写入未经验证的 tileId 会产出"本机正常、桌面显示错误"的工程，故主动不提供。

2. **自动图块形状（shape）不自动推导**
   桌面编辑器会根据四邻/对角邻接自动选择 0–47 号形状。本 MVP 写入用户手选形状，默认 `shape 0`（合法值）。已提供"形状"标签页可手动浏览全部形状。

3. **事件命令列表只读 + 追加**
   可展示命令、可追加"显示信息气泡"（code 101）。完整命令编辑器（条件分支、脚本、变量、开关…）未实现。

4. **未打包完整素材**
   `NewData/img/` 218 MB、`audio/` 162 MB。APK 只内置默认图块的 4 张贴图 + 完整 `js/` + `data/` + `fonts/`（共 38 文件 / 7.13 MB）。`Tilesets.json` 保持原样不裁剪，图块 2+ 图像缺失但数据结构仍合法。

5. **仅支持新建工程**
   `pickFile` 的 ACTION_OPEN_DOCUMENT 桥接已就绪，UI 未接入（单文件导入无法还原整个目录树）。

---

## 8. 交付物索引

| 文件 | 说明 |
|---|---|
| `android-incompatibility.json` | Phase 1 REA 边界图（四类硬边界 + 真实可移植边界） |
| `analysis/scan-boundaries.mjs` | 边界扫描器（只读安装目录，可复现） |
| `analysis/boundary-scan.json` | 扫描器原始逐行命中输出 |
| `analysis/stage-template.ps1` | 脚手架复制脚本（只读复制 NewData） |
| `app/www/shim/*.js` | 四个 Shim 源文件 |
| `app/www/js/mvformat.js` | MV 格式层（含 tileId 代数） |
| `app/www/js/tileset.js` | 图块合成与渲染 |
| `app/www/js/store.js` | 工程持久化 |
| `app/www/js/editor.js` | 触摸 UI |
| `app/www/_template/` | 内置工程脚手架（38 文件 / 7.13 MB） |
