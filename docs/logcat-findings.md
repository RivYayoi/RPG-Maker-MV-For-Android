# adb logcat 关键片段与遗留问题 / logcat Findings & Open Issues

**被测产物**：`dist/rmmv-editor-mvp-debug.apk`（8.33 MB）
SHA-256：`103B27E4BDD894B966CC5063591BA2CD2FCE922F1C9A24F8C09CB8CA356442A0`
**测试环境**：Android 模拟器 `emulator-5554` — Android 14 (API 34)，x86_64，1080×2280
**测试方式**：`adb install -r -t` → `am start` → `adb logcat` + 通过 CDP (`webview_devtools_remote_<pid>`) 驱动真实 WebView 内的应用代码
**工具**：Capacitor 7.1.9 / Filesystem 7.1.9 / AGP 8.7.2 / Gradle 8.11.1 / JDK 21 / compileSdk 35 / targetSdk 35 / minSdk 23

---

## 1. Phase 4 首要目标：消除"未定义"类错误

任务书要求"首要目标是消除所有'未定义'类错误（`fs is not defined`、`nw is not defined`、`path is not defined`）"。

**最终结果：0 命中。**

```
$ adb logcat -d | Select-String -Pattern "is not defined|FATAL EXCEPTION|Uncaught |AndroidRuntime.*FATAL"
CLEAN: 0 matches

$ adb logcat -d -s "Capacitor/Console:E" "Capacitor/Console:W"
CLEAN: no E/W console output

$ adb shell pidof com.rmmv.editor
pid=3411        (进程存活)
```

### 1.1 成功启动的 boot 序列（真机日志）

```
I Capacitor: Registering plugin instance: CapacitorCookies
I Capacitor: Registering plugin instance: WebView
I Capacitor: Registering plugin instance: CapacitorHttp
I Capacitor: Registering plugin instance: Filesystem
I Capacitor: Loading app at https://localhost
D Capacitor: Handling local request: https://localhost/
D Capacitor: Handling local request: https://localhost/css/app.css
D Capacitor: Handling local request: https://localhost/shim/bridge-loader.js      <-- 注入点 1
D Capacitor: Handling local request: https://localhost/shim/path-shim.js          <-- 注入点 2
D Capacitor: Handling local request: https://localhost/shim/android-fs-shim.js    <-- 注入点 3
D Capacitor: Handling local request: https://localhost/shim/nw-shim.js            <-- 注入点 4
D Capacitor: Handling local request: https://localhost/js/mvformat.js
D Capacitor: Handling local request: https://localhost/js/tileset.js
D Capacitor: Handling local request: https://localhost/js/store.js
D Capacitor: Handling local request: https://localhost/js/editor.js
```

日志中 **shim 的加载顺序严格早于任何应用脚本**，这正是"消除未定义错误"的结构性保证（而非靠 try/catch 兜底）。

WebView 控制台：

```
[BOOT] 兼容层 Shim 已注入
[BOOT] 检查 Capacitor 原生桥接…
[RMMV-BRIDGE] Capacitor Filesystem bridge bound; storage = DATA (/data/data/<pkg>/files, app-private)
[BOOT] 存储: capacitor-filesystem (app-private)
[BOOT] 文件系统就绪
[BOOT] MV 格式层就绪 (tileId 编码已验证)
[BOOT] 完成
```

---

## 2. MVP 验收流程（真实设备存储，逐项实测）

| # | 验收项 | 结果 | 实测值 |
|---|---|---|---|
| 1 | 启动进入编辑器主界面 | ✅ | boot 6 阶段全通过 → 工程列表 |
| 2 | 创建项目 | ✅ | `DeviceAccept`，38 文件落到真实存储 |
| 3 | 打开项目 | ✅ | `fileCount: 38` |
| 4 | 图块合成 | ✅ | 4 张贴图 → 2304×2304 画布；kind16 渲染 2304 个不透明像素（真实美术） |
| 5 | 放置一个地图图块 | ✅ | 写入 `2816`（= `TILE_ID_A2`），读回 `2816`，原始索引 `data[(1*15+4)*20+3] === 2816` |
| 6 | 创建一个事件 | ✅ | `#1 DeviceEvent` |
| 7 | 保存 | ✅ | 37 ms，落盘 2 个文件 |
| 8 | **重启后打开** | ✅ | 页面完全 reload 后：38 文件、图块 `2816` 还原、事件 `DeviceEvent` 还原 |
| 9 | **Schema 校验** | ✅ | 产出的 `Map001.json` 与编辑器自带 `NewData/data/Map001.json` **键集合完全一致**（missing 0 / extra 0）；`data.length = 1200 = 20×15×4` |

---

## 3. Phase 4 迭代过程中实际修复的设备端 Bug

以下 5 个都是**只在真机/真 Capacitor 桥接下才暴露**的问题（浏览器降级模式无法覆盖）。全部依据实测日志定位，未做猜测式修改。

### Bug 1 — `Directory.DOCUMENTS` 在 targetSdk 35 下不可写

**症状**：`Could not read project "DeviceAccept" from device storage.`
**实测定位**：
```
$ adb shell ls -la /storage/emulated/0/Documents/
total 0                                  # 空
$ adb shell run-as com.rmmv.editor ls -la files/
-rw------- profileInstalled              # 只有 WebView 自己的文件
```
**原因**：Capacitor 的 `Directory.DOCUMENTS` 映射到 `Environment.DIRECTORY_DOCUMENTS`（**外部共享存储**），不是应用私有目录。Scoped Storage 下写入失败，而旧 `flush()` 用 `try/catch` 吞掉了错误，导致"写了但读不回来"。
**修复**：
1. 改用 `Directory.DATA`（= `Context.getFilesDir()` = `/data/data/<pkg>/files`，真正应用私有，无需任何运行时权限）。
2. **`flush()` 不再吞错误**，收集 `{path, message}` 并由 `assertFlushOk()` 抛出可读异常。

> 这一步本身就是 Phase 4 要求的"回到 Phase 1 证据链定位而非猜测"的体现：错误传播修好后，真正的根因立刻可见。

### Bug 2 — `Filesystem.Directory` 枚举在插件代理上不存在

**症状**：`新建工程 写入失败 38 个文件，例如 ... (Cannot read properties of undefined (reading 'DATA'))`
**原因**：`Capacitor.Plugins.Filesystem` 是**代理对象**，不携带插件的静态 TS 枚举，`Filesystem.Directory` 为 `undefined`。
**修复**：`capDir()` 优先取引举，缺失时回退到原生同样接受的字面量字符串（`'DATA'`）。

### Bug 3 — 二进制内容被重复 base64 编码

**症状**：`新建工程 写入失败 6 个文件，例如 .../mplus-1m-regular.ttf (bytes.subarray is not a function)`
**原因**：VFS 中二进制文件按不变量存为 base64 字符串，而 `writeOne()` 又对字符串调用 `bytesToB64()`。
**修复**：`writeOne()` 对二进制路径直接透传；`bytesToB64()` 增加类型守卫。

### Bug 4 — `readdir` 返回结构判断错误（工程只加载了顶层文件）

**症状**：工程看似"已创建"，但打开后 `data/MapInfos.json` 读不到。
**原因**：Capacitor 7 的 `readFileInfo` 为 `{name, type:'directory'|'file'|'special', size}`，而代码按旧版的 `{directory: boolean}` 判断，导致**目录被当成文件**，遍历器从不进入子目录。
**修复**：新增 `entryInfo()` 归一化三种字段形态（`directory` / `type` / `isDirectory`），并对"不可读且无扩展名"的条目按目录兜底。

### Bug 5 — 图块渲染目标画布用错（浏览器内发现，非设备专属）

**症状**：128 个自动图块**全部**渲染为全透明。
**原因**：`renderAutotile()` / `drawTile()` 把**源**图块画布的 context 当成了绘制目标（`src.drawImage(cv, ...)`），图块被画回了图块集自己身上。
**修复**：目标改为新建的图块画布（`octx`）与地图渲染上下文（`ctx`），对应 MV 中 `bitmap.bltImage(source, ...)` 的 source/destination 关系。
**附带修正**：初版按 512×512 合成且把 768×576 贴图缩放到 512×512，剪切了图块网格；改为**原生尺寸 + 768×768 block + 2304×2304 画布**后，123/128 个图块渲染出真实美术。

---

## 4. 良性日志（非错误，保留说明）

```
D Capacitor: Sending plugin error: {"methodName":"mkdir","success":false,
  "error":{"message":"Directory at '/data/user/0/com.rmmv.editor/files/Projects/' already exists, cannot be overwritten.",
           "code":"OS-PLUG-FILE-0010"}}
```
Capacitor 对 `mkdir` 已存在目录的报错，级别为 **Debug**，且被 `mkdirp()` 有意吞掉（幂等建目录的正常语义）。不影响功能。

```
W Capacitor/Console: Canvas2D: Multiple readback operations using getImageData
  are faster with the willReadFrequently attribute set to true.
```
性能提示，源自 `pickBlock()` 用 `getImageData` 探测 block。

> 第一轮修复只改了探测侧（`getContext('2d', {willReadFrequently:true})`），**无效** —— `compose()` 早已先用无参 `getContext('2d')` 建好上下文，而 `getContext()` 对已存在的上下文**忽略新的属性参数**。最终改为在 `compose()` 创建合成画布上下文时就带上该标志。修复后 `Capacitor/Console:W` 为空。

---

### Bug 6 — 拖拽手势三个缺陷（浏览器 + 设备双端确认）

**症状**：选中图块后**单指拖拽无法平移地图**；双指缩放后抬起一根手指，剩下那根手指**完全无响应**；拖拽绘制**明显卡顿**且一次笔画就吃光撤销历史。

**根因（均在 `editor.js` 手势层）**：

| # | 位置 | 问题 |
|---|---|---|
| a | 手势分类 `kind: (E.mode==='paint' && E.brush!==0) ? 'paint' : 'press'` | 选中笔刷后单指恒为绘制态，没有平移出口 |
| b | `touchend` 仅处理 `pointers.size===0` | 双指抬起一根后 gesture 仍是 `'pinch'`，剩余手指命中不了任何分支 |
| c | `paintAt()` 每格调用 `pushUndo()` | 每次 `touchmove` 都 `JSON.stringify` 整张地图 |

**修复**：
- 新增 `E.panMode` 与底部"✋ 移动"按钮；绘制态判定加 `!E.panMode`，点击/鼠标左键路径同步受约束
- `touchend` 在 `pointers.size===1 && kind==='pinch'` 时重建手势为 `press`（`moved:true` 防误触发点击，`undoTaken` 防缩放收尾误绘制）
- 撤销按笔画合并：`beginStroke()` 在笔画开始时复位 `strokeUndoTaken`，`paintAt()` 每个笔画只压一次快照
- 绘制态下禁用长按菜单，避免手指停顿打断笔画时弹出菜单

**验证**：`analysis/device-drag-regression.mjs` 经 CDP 在真机 WebView 内合成 `TouchEvent` 驱动真实监听器，**7/7 通过**：

```
viewport: 393x455
  PASS  pan-mode-drag-scrolls          {"delta":[112,80]}
  PASS  pan-mode-paints-nothing        {"painted":0}
  PASS  paint-mode-drag-draws          {"painted":4}
  PASS  paint-mode-does-not-scroll
  PASS  one-stroke-one-undo            {"entries":1}
  PASS  pinch-tail-finger-still-pans   {"delta":-182}
  PASS  pinch-tail-paints-nothing      {"painted":0}
```

---

## 5. 遗留问题清单（兼容层待修复项）

按优先级：

| # | 问题 | 影响 | 状态 |
|---|---|---|---|
| 1 | **普通图块（B/C/D/E/A5）未提供** | 只能用自动图块作画；无法使用非自动图块 | 需读取 `RPGMV.exe` Qt 资源中的 set→图像绑定（本机无 Ghidra/Hopper）。**落盘数据不受影响**，桌面端渲染正确 |
| 2 | **自动图块形状不自动推导** | 地图边缘不会出现自动收边效果（默认 `shape 0`） | 需实现四邻/对角邻接 → shape 映射表 |
| 3 | **事件命令编辑器缺失** | 只能查看命令 + 追加"显示信息气泡" | 需实现 code/parameters 编辑 UI |
| 4 | **导入/导出工程未接入 UI** | `pickFile` 桥接已就绪但无入口 | 需实现目录树导入（单文件导入不足） |
| 5 | **素材库仅 4 张图块贴图** | 图块集 2+ 图像缺失 | 打包体积权衡（完整 `img/` 218 MB + `audio/` 162 MB） |
| 6 | **地图层级树 / 多页事件 / 事件列表侧栏** | 字段保留但无 UI | 增强项 |
| 7 | **REA 深度 provider 不可用** | 无法读取 `RPGMV.exe` 内部资源（导致 #1） | 需安装 Ghidra 或 Hopper 后重跑 `rea doctor` |

### 未受影响项（明确声明）

- **落盘数据格式正确性**：不受 #1 #2 影响。写入 `Map001.json` 的 tileId 来自已验证的 `rpg_core.js` 代数，`data[]` 索引来自已验证的 `rpg_objects.js:5929`，Schema 键集合与编辑器自带文件逐项一致。桌面 `RPGMV.exe` 打开本 App 产出的工程时使用自己的正确渲染路径。
- **存储与权限**：`Directory.DATA` 完全应用私有，无需任何运行时权限，未申请 `MANAGE_EXTERNAL_STORAGE`。

---

## 6. 最终产物校验

```
$ aapt2 dump badging app-debug.apk
package: name='com.rmmv.editor' versionCode='1' versionName='1.0'
compileSdkVersion='35' / targetSdkVersion='35' / minSdkVersion='23'
launchable-activity: name='com.rmmv.editor.MainActivity'
uses-permission: INTERNET
uses-permission: READ_EXTERNAL_STORAGE  maxSdkVersion='32'
uses-permission: READ_MEDIA_IMAGES / READ_MEDIA_AUDIO / READ_MEDIA_VIDEO
  （无 MANAGE_EXTERNAL_STORAGE —— 有意排除）

APK 内 web 资源：50 条 assets/public/*，含 4 个 shim
  assets/public/shim/bridge-loader.js     4.8 KB
  assets/public/shim/path-shim.js         5.1 KB
  assets/public/shim/android-fs-shim.js  13.7 KB
  assets/public/shim/nw-shim.js           6.0 KB
assets 总计 7.24 MB（= _template 脚手架 38 文件）
```
