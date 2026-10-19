# RPG Maker MV 编辑器 → Android（**原生 Java 版**）

> A native-Java Android port of the **RPG Maker MV editor** (the authoring tool),
> reproducing the PC editor's Main Window and its RPG Maker MV project format.

**产物**：`dist/rmmv-editor-native-debug.apk`（11.66 MB）
SHA-256 `F4BF03B812FE6D3ADD8E43AAF22D2914538A2596F844CB046C46F2522FEC5493`

```bash
adb install -r -t dist\rmmv-editor-native-debug.apk
```

| | |
|---|---|
| 语言 | **纯 Java**（无 Kotlin、无 JavaScript） |
| UI | **原生 Android View**（`View` / `Canvas` / `RecyclerView` / `PopupMenu` / `AlertDialog`） |
| 容器 | **无** —— 不使用 WebView、不使用 Capacitor、不加载任何 HTML |
| 存储 | `java.io.File` + `org.json`，应用私有目录 |
| 依赖 | 仅 appcompat / recyclerview / constraintlayout / material（AndroidX） |
| minSdk / targetSdk | 23 / 35 |
| 方向 | 竖屏 + 横屏，布局随方向切换（见下文） |

**验证**：APK 内 `assets/` 只有 `template/`，**没有 `assets/public`**，本应用代码对 `android.webkit` 的引用为 **0**。

---

## 快速开始（从源码构建）

RPG Maker MV 的引擎与美术资源属于 Kadokawa，**本仓库不分发它们**。请先从你本地的
RPG Maker MV 安装目录复制出工程脚手架，再构建：

```bash
# 1) 取回 MV 脚手架（默认读 "C:\Program Files (x86)\KADOKAWA\RPGMV"）
node scripts/fetch-template.mjs
# 或指定安装目录：
MV_INSTALL_DIR="D:\Games\RPGMV" node scripts/fetch-template.mjs

# 2) 构建 debug APK
cd app-java
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

需要 Android SDK（`platforms;android-35`、`build-tools;35.0.0`、`platform-tools`）与 JDK 17+。
`app-java/local.properties` 需指向你的 SDK：

```properties
sdk.dir=C\:\\path\\to\\android-sdk
```

---

## 为什么不套壳：Phase 1 的结论

RPG Maker MV 的**编辑器不是 NW.js 应用，而是 Qt5/QML 原生桌面程序**：

| 全树扫描（204 个 JS 文件） | 命中 | 文件 | **在编辑器 UI 代码中** |
|---|---|---|---|
| Node.js 核心模块 | 29 | 2 | **0** |
| NW.js 桌面 API | 5 | 2 | **0** |
| 原生路径假设 | 41 | 5 | 1（编译进 exe，无 JS 接口） |
| IPC / 渲染进程 | **0** | **0** | **0** |

`RPGMV.exe` 含 `QtQuick` ×451、`QQmlApplicationEngine` ×6；`nwjs-win/` 里的可执行文件叫 `Game.exe`，`www/` 里只有一个 `icon/` 目录 —— 那是**游戏播放器**。

所以"套壳 + fs/nw shim"这条路没有可移植的对象。可移植的边界是 **RPG Maker MV 工程文件格式**，本项目用 Java 重新实现了它。详见 [`android-incompatibility.json`](android-incompatibility.json)。

---

## PC 页面移植对照

页面清单来自编辑器**自带手册** `Help/page/01_03.html`（"Basic Editor Controls" 的六个主窗口组件）与 `01_04.html`（菜单栏）。

### 主窗口（Main Window）—— 六个组件全部移植

布局**逐像素对齐编辑器自带手册的截图** `Help/inc/img/01_03_img01.png`：

| PC 组件 | 原生实现 |
|---|---|
| (1) Menu Bar | **横向菜单行**（File Edit Mode Draw Scale Tools Game Help），每项点开下拉 `PopupMenu`，与桌面端交互一致 |
| (2) Toolbar | **横向图标行**，17 个自绘矢量图标（新建/打开/保存/撤销/重做/缩放/模式/网格/新建地图/属性/数据库/插件/资源…） |
| (3) Tile Palette | `TilePaletteView`：**竖向网格**贴在左栏，下方 A / B / R 三个标签（同 PC） |
| (4) Map List | `MapListPanel`：**树形**（工程 → MAP001），可折叠、选中高亮、长按重命名/删除 |
| (5) Map View | `MapCanvasView`（`Canvas` 硬件加速绘制），占满右侧 |
| (6) Status Bar | 底部**分栏**读数：`001:MAP001 (20x15) │ Map │ 100% │ Tileset 1 · Overworld` |

### 横屏 / 竖屏适配

单套布局 + 资源限定符驱动，行为与 PC 桌面端一致：

| 资源限定符 | 左栏宽度 | 图块格 | 菜单字号 | 工具图标 |
|---|---|---|---|---|
| `values/`（竖屏） | 150dp | 32dp | 13sp | 30dp |
| `values-land/`（横屏） | **230dp** | 40dp | 13sp | 32dp |
| `values-sw600dp/`（平板） | **270dp** | 44dp | 14sp | 36dp |

横屏下左栏 633px（5 列图块），竖屏 413px（4 列），均为桌面端比例。

**关键点**：`configChanges` 故意**不**包含 `orientation|screenSize`，让 Activity 在旋转时重建，
否则 `values-land` 不会重新解析（这是实测发现：旋转后左栏仍是 413px）。旋转时用
`onSaveInstanceState` 保留当前地图 id。

### 状态栏适配（系统栏）

`WindowCompat.setDecorFitsSystemWindows(window, false)` + `WindowInsetsCompat`：

- 系统状态栏 / 导航栏 / 刘海 / IME 四类 inset 全部取 `systemBars() | displayCutout()`
- 根布局按 inset 加 padding ⇒ **PC 菜单栏永远在系统状态栏下方**，PC 状态栏永远在导航栏上方
- 窗口背景设为 `@color/bg`，横屏时右侧导航栏那条空隙显示应用底色而不是黑边
- 键盘弹出时用 IME inset 兜底（`adjustResize` + ime bottom 取最大值）

### 菜单栏（01_04.html 逐条对照）

```
[File]  Save Project / Close Project / Map Properties / Deployment / Exit
[Edit]  Undo / Redo / Copy / Paste / Delete
[Mode]  Map / Event / Region          ← Region 即手册里的 R 标签
[Scale] Zoom In / Zoom Out / Actual Size / Fit to Map
[Tools] Database / Plugin Manager / Resource Manager
        Sound Test / Event Searcher / Character Generator
[Game]  Playtest / Open Folder
[Help]  About / Contents
```

### 独立页面

| 页面 | 状态 |
|---|---|
| **Database**（01_08，16 个实体页） | ✅ 主从式：实体选择器 + 记录列表 + **按 JSON 键动态生成表单**。Actors/Classes/Skills/Items/Weapons/Armors/Enemies/Troops/States/Animations/Tilesets/CommonEvents/System/Types/Terms |
| **Plugin Manager**（01_05） | ✅ 解析真实 `js/plugins.js`，启用开关、名称/作者/版本/描述编辑，回写时保留原包装文本 |
| **Resource Manager**（01_05） | ✅ 21 个分类根目录的树形浏览、图片预览（只读） |
| **Map Properties**（01_07_03） | ✅ 视差、遭遇步数、BGM、滚动方式、图块集、地图列表名 |
| Map Design / Map Data Controls（01_07_01/02） | ⚠️ 绘制 ✅；矩形选区框架已就绪，UI 未接 |
| Event Commands（01_10，16 类） | ⚠️ 事件属性可编辑；完整命令编辑器未实现 |
| Sound Test / Event Searcher / Character Generator | ❌ 未实现（菜单项在，点击提示不支持） |
| Deployment / Playtest | ❌ 未实现（桌面依赖 NW.js 运行时） |

---

## 格式保真（跨语言验证）

Java 实现与已验证的 MV 契约逐项对应：

```
NewData/js/rpg_core.js:5273-5302    tileId 常量与 kind/shape 代数
NewData/js/rpg_core.js:5040-5126    自动图块 bx/by 与合成
NewData/js/rpg_core.js:5388-5424    FLOOR / WALL_AUTOTILE_TABLE
NewData/js/rpg_objects.js:5929      data[(z*height + y)*width + x]
```

真机端到端（`analysis/native-e2e.mjs`，**19/19 通过**）：

| 断言 | 结果 |
|---|---|
| 绘制写入 tileId | `2816`（= `TILE_ID_A2`，默认地面） |
| `data[]` 长度 | `1200` = 20 × 15 × 4 |
| **Schema 与编辑器自带 `Map001.json` 对比** | **missing=[] extra=[]** |
| 保存后重启再打开 | 通过 |
| PC 菜单栏 8 项全部可达（含横竖屏滚动） | 通过 |
| Tools → Database 下拉 → Database 页 | 通过（Actors 4 条真实数据） |
| 崩溃扫描 | CLEAN |

横竖屏适配（`analysis/native-orientation.mjs`，**20/20 通过**）：

| 断言 | 竖屏 | 横屏 |
|---|---|---|
| (1)(2)(3)(4)(5)(6) 六个 PC 组件 | ✅ | ✅ |
| 菜单栏 8 项齐全 | ✅ | ✅ |
| 菜单栏在系统状态栏之下 | ✅ `menu.y1=66` | ✅ |
| 图块面板/地图树在地图视图左侧 | ✅ | ✅ |
| 左栏宽度随方向变化 | 413px | **633px** |
| PC 状态栏贴底 | ✅ | ✅ |
| 崩溃扫描 | CLEAN | CLEAN |

---

## 已知边界（不猜测）

1. **普通图块 B/C/D/E/A5 未提供**。其 set 号 → 源图像绑定编译在 `RPGMV.exe` 的 Qt 资源里，本机未装 Ghidra/Hopper，无法读取。自动图块 A1–A4 的来源已逐行验证并用探测法确定 block，写入磁盘的 tileId 不受影响（桌面端用自己的正确映射渲染）。
2. **自动图块形状不自动推导**，写入用户手选形状（默认 0）。
3. **事件命令编辑器**未实现。
4. **素材仅内置默认图块的 4 张贴图**（完整 `img/` 218 MB + `audio/` 162 MB 未打包）。

---

## 目录

```
RPG-Maker-MV-For-Android/
├── README.md
├── LICENSE
├── android-incompatibility.json     REA 边界图（四类硬边界 + 真实可移植边界）
├── app-java/                        纯 Java Gradle 工程（产品本体）
│   ├── app/src/main/java/com/rmmv/editor/
│   │   ├── MainActivity.java        工程列表（[File] 入口）
│   │   ├── core/                    TileId / MvMap / MapInfos / Tileset /
│   │   │                            Project / ProjectStore / Session
│   │   └── ui/                      MapEditorActivity / MapCanvasView /
│   │                                TilePaletteView / MapListPanel /
│   │                                MapPropertiesActivity / DatabaseActivity /
│   │                                PluginManagerActivity / ResourceManagerActivity
│   │                                + db/ plugin/ resource/ 辅助类
│   ├── app/src/main/res/
│   │   ├── layout/                  activity_map_editor.xml（PC 主窗口）
│   │   ├── values/      dimens.xml  竖屏尺寸
│   │   ├── values-land/ dimens.xml  横屏尺寸（PC 比例）
│   │   ├── values-sw600dp/dimens.xml 平板尺寸
│   │   └── drawable/                17 个自绘矢量图标 + 按钮背景
│   └── app/src/main/assets/template/  MV 脚手架（由 scripts/fetch-template.mjs 生成）
├── scripts/fetch-template.mjs       从本地 RPG Maker MV 安装取出脚手架
├── analysis/                        证据脚本（边界扫描、真机验收）
├── evidence/                        真机截图
├── docs/                            功能支持矩阵 / 兼容层设计 / logcat 记录
└── dist/                            构建产物（APK 不入库，见 .gitignore）
```

### 与前一版（WebView 版）的关系

`app/` 目录保留了上一版 Capacitor + WebView 实现作为对照，**不纳入版本库**（已被
`app-java/` 取代，且会重复打包 7 MB 脚手架与第二套 Gradle 工程）。

---

## 文档

| 文档 | 内容 |
|---|---|
| [`docs/功能支持矩阵.md`](docs/功能支持矩阵.md) | 各功能模块的 ✅/⚠️/❌ 支持矩阵与已知边界 |
| [`docs/compatibility-layer-plan.md`](docs/compatibility-layer-plan.md) | 兼容层设计：Phase-1 边界 → 实现的逐条对应 |
| [`docs/logcat-findings.md`](docs/logcat-findings.md) | 真机 logcat 关键片段与遗留问题 |
| [`android-incompatibility.json`](android-incompatibility.json) | REA 边界图（机器可读） |

---

## 许可

本仓库的**代码**以 MIT 许可发布（见 [`LICENSE`](LICENSE)）。

**不包含** RPG Maker MV 的引擎与美术资源 —— 它们属于 Kadokawa，由
`scripts/fetch-template.mjs` 从你自己的 RPG Maker MV 安装中复制。
RPG Maker MV 本身的许可请参见其官方条款。
