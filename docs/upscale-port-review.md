# 图像增强(upscale)移植审查 — feature/image-upscale

参照系:`HaoweiLi97/mihon_img_upscale`(v1.3.9,master)相对其 mihon 基线(v1.1.5 时代)的 upscale 相关改动。
被审对象:`komikku feature/image-upscale` 分支相对 `master` 的全部变更。
结论先行:**核心链路(NCNN/Vulkan 推理、模型管理、Coil 解码管线、阅读器集成、设置 UI、QNN/NPU)已完整移植并在 SM8750 真机可用;发现并修复 2 个移植缺陷;另有 7 处有意差异(全部有理由),无未解释的遗漏。**

---

## 1. 逐文件对账

### 1.1 新增文件(上游新增 → 本分支新增)

| 上游文件 | 状态 | 说明 |
|---|---|---|
| `app/src/main/cpp/waifu2x.{h,cpp}`(Vulkan 分块推理、fp16/fp32 融合 compute shader) | ✅ 原样 | 自 v1.3.9 master 拷贝 |
| `app/src/main/cpp/waifu2x_jni.cpp`、`shaders.h`、`waifu2x_fused_*.comp` | ✅ 原样 | JNI 导出面与 Kotlin `external fun` 一一对应 |
| `app/src/main/cpp/anime4k.{h,cpp}`(GLSL Anime4K) | ✅ 原样 | 休眠能力:JNI/Kotlin 已接,UI 无入口(上游亦然) |
| `app/src/main/cpp/qnn_backend.{h,cpp}` | ✅ 原样 + 配置差异 | 见 §3.3,以 `MIHON_ENABLE_QNN=1` 编译 |
| `app/src/main/cpp/CMakeLists.txt` | ✏️ 适配 | 剔除 spatial/depth 源文件;QNN 只查头文件不查 SDK lib(运行库来自 AAR) |
| `third_party/ncnn-20260113-android-vulkan/`(99MB) | ✅ 原样 | 与上游同版本同布局;`local.properties`/`-PncnnSdkDir`/环境变量均可覆盖 |
| `third_party/qnn-include/` + LICENSE + README | 🆕 替代方案 | 上游用本地 QAIRT SDK 头文件;本分支 vendor 自 `qualcomm/geniex-qairt-plugin`(BSD-3-Clause,QNN C API 2.27,运行时 2.49 向后兼容)。**头文件无法从 release APK 获得** |
| `util/waifu2x/Waifu2x.kt` | ✅ 原样 | 模型生命周期、assets 解压、GitHub/HF 按需下载、QNN 初始化 |
| `util/waifu2x/ImageEnhancer.kt` | ✅ 原样 | 优先级队列(可见页 3/副页 2/提升 1/预载 0 + 距离 + FIFO)、抢占、代际取消 |
| `util/waifu2x/ImageEnhancementCache.kt` | ✅ 原样 | 磁盘缓存(3GB 上限)、configHash、skip 标记、透明图拒收 |
| `util/qnn/QualcommHtp.kt` | ✅ 原样 | HTP 架构探测(JNI),spatial 注释残留无碍 |
| `util/image/ImageFilter.kt`(ink 滤镜) | ❌ 跳过 | 见 §2 |
| `assets/` 模型(realcugan SE/Pro、realesrgan、waifu2x、nose、upconv7、span、sudo、anime4k) | ✅ 原样 | ~90MB;animejanai 仅 ATTRIBUTION(按需下载,与上游一致) |
| `assets/qnn-contexts/`(150 个,437MB,v69–v81) | ✅ 原样 | 从上游 release APK 提取,字节级一致;上游是构建期从 `qnn.context.dir` stage,本分支直接入库 |
| `app/src/main/jniLibs/arm64-v8a/libQnnModelDlc.so` | ✅ 原样 | 从上游 release APK 提取;已验证 `libQnnHtp.so` 不依赖它(仅 DLC 模型路径用),对齐上游打包 |
| i18n:37 个 `reader_*` 字符串 | ✏️ 适配 | **KMR / i18n-kmk base**(上游是 MR / i18n),符合本仓库 i18n 规范;Weblate 可译 |

### 1.2 修改的既有文件

| 文件 | 上游改动 | 状态 | 说明 |
|---|---|---|---|
| `app/build.gradle.kts` | cmake 接入、NCNN/QNN 路径解析、QNN staging 任务、AAR 依赖、ndkVersion | ✏️ 适配 | 无 QNN staging 任务(上下文直接入库);`ndkVersion` 不钉死(用 AGP 默认,本机 27 自动装);AAR 依赖同上游 |
| `data/coil/Utils.kt` | +5 个 Coil 参数(enhanced/mangaId/chapterId/pageIndex/pageVariant) | ✅ 原样 | KMK 块 |
| `data/coil/TachiyomiImageDecoder.kt` | 重写:缓存优先 → 预缩放 → 推理 → 纹理上限 → 回写缓存;BitmapFactory 兜底;`DecodeResult?` 软失败 | ✅ 原样 + 保留 SY | 保留 komikku 的 SY 封面归档(CbzCrypto)两条路径;ink 滤镜调用点未移植 |
| `ui/reader/model/ReaderPage.kt` | +`enhancementStream`/`enhancementKeySuffix` | ✏️ 适配 | 放在**类体**而非构造参数(构造参数会破坏既有尾随 lambda 调用点,上游 mihon 无此调用模式) |
| `ui/reader/setting/ReaderPreferences.kt` | +15 个 `realCugan*` 偏好 | ✅ 原样 | 键名与上游完全一致(升级用户偏好可迁移);未移植其遗留 `waifu2xEnabled`/`inkFilter*`/`realCuganProEnabled`(无 UI 引用) |
| `presentation/reader/settings/ColorFilterPage.kt` | +增强设置段 | ✏️ 适配 | `SettingsChipRow` 传 `StringResource`(komikku 组件签名与上游不同);字符串 KMR |
| `ui/reader/ReaderActivity.kt` | setUiBusy 全套(菜单/触摸/按键/销毁)、底栏增强开关按钮、spatial 场景 | ✏️ 部分移植 | setUiBusy ✅(`UI_BUSY_COOLDOWN_MS=1s`);底栏按钮 ❌(§2);spatial ❌ |
| `ui/reader/ReaderViewModel.kt` | `toggleImageEnhancement()`、`ImageEnhancer.reset()` | ✅ 原样 | reset 挂在 `init()`(komikku 的初始化入口与上游不同) |
| `loader/HttpPageLoader.kt` | preloadSize 联动 + internalLoadPage 增强流替换 | ✏️ 适配 | **本分支修正**:增强关闭时回落 komikku 的 `preloadSize()`(SY 偏好)而非上游的常量 4,避免关闭功能时行为回归 |
| `loader/DownloadPageLoader.kt` | loadPage 时触发增强 | ✅ 原样 | |
| `viewer/ReaderPageImageView.kt` | 485→1396 行:状态角标、增强图轮询、交叉淡入交换、缓存自愈、onPageSelected 剪枝 | ✏️ 部分移植 + 1 处修正 | 保留 komikku 的 KMK 缩放特性(disableZoomIn/doubleTapZoom/landscapeZoomScaleType)与 webtoon 长条 Coil 解码路径(并在该路径也接入增强);`enhancementDisplayEnabled` 为本分支新增开关;**修复 SSIV recycle 竞态**(§3.1) |
| `viewer/pager/PagerViewer.kt` | offscreenPageLimit 联动、destroy 时 cancelAll、hasSplitPage | ✏️ 适配 | `else 1` 保留 komikku 默认(上游 mihon 为 2);新增 `hasSplitPage` |
| `viewer/pager/PagerConfig.kt` | 16 个偏好变更监听(cancelAll + 清缓存 + 刷 adapter) | ✅ 原样 | |
| `viewer/pager/PagerPageHolder.kt` | 增强上下文绑定、缓存优先显示、拆页变体 | ✏️ 适配(差异最大) | 见 §2 拆页设计 |
| `viewer/webtoon/WebtoonViewer.kt` | destroy cancelAll、onPageSelected reprioritize、currentGlobalPageIndex | ✅ 原样 | |
| `viewer/webtoon/WebtoonConfig.kt` | 同 PagerConfig 监听(以 appContext 清缓存) | ✅ 原样 | |
| `viewer/webtoon/WebtoonPageHolder.kt` | (上游无此文件改动;本分支主动接入) | 🆕 本分支扩展 | webtoon 侧缓存优先显示;上游 webtoon 无拆页概念故无需变体 |
| `presentation/reader/appbars/*` | 底栏增强开关按钮 | ❌ 跳过 | §2 |
| `App.kt` / `SettingsAdvancedScreen` / `GLUtil` | 上游含 GLUtil(DEVICE_TEXTURE_LIMIT 等) | ⭕ N/A | komikku 自带(KMK 特性),直接使用 |

## 2. 有意差异(7 项)

1. **拆页增强重新设计**:上游 auto-split 模式对每个"半页"独立增强(`wide_left/right` 变体 + 拆分增强流),与其自研 PageSpreadDetector 深度耦合。komikku 用 SY 的 `dualPageSplit`(显示期拆分 + InsertPage 独立 holder),照搬会有重复插入 InsertPage 的风险。本分支方案:**整页增强 + 显示期重切**(`processEnhanced` 不再触发 `onPageSplit`,以 `hasSplitPage` 守卫缓存过早命中),两半共享同一份整页缓存。代价:拆页父页首载必须先看一次原始整页(与上游守卫语义一致)。
2. **SY 合并双页(extraPage)无在线切换**:两源重跑 `mergePages` 有副作用(fullPage 标记/插入拆页),上游的合并函数是副作用轻量版。本分支合并页保持原始显示,增强缓存就绪后于 holder 重建时生效;由 `enhancementDisplayEnabled` 关闭该 holder 的增强显示/轮询。
3. **底栏快捷开关按钮未移植**:komikku 底栏是 SY 的用户可配置按钮系统(`ReaderBottomButton` 枚举 + 设置页),上游是固定图标行,直接搬会破坏按钮自定义体系。`toggleImageEnhancement()` 已就绪,后续若要加按钮走 `ReaderBottomButton` 枚举扩展。
4. **ink 滤镜未移植**:上游隐藏偏好控制(无设置 UI、默认全关),与 upscale 无关。
5. **spatial depth(Depth Anything)未移植**:独立特性,非 upscale;相关 cpp/Kotlin/assets/字符串均剔除。
6. **QNN 构建方式**:上游 = 本地 QAIRT SDK(头文件 + DLC + 上下文生成本地化);本分支 = vendored 头文件 + Maven AAR 运行库 + 上下文直接入库。**构建不再依赖高通 SDK**,换来的成本是仓库 +~530MB 二进制(见 §4 待决)。
7. **遗留偏好不搬**:`pref_waifu2x_enabled`(上游旧版残留,无 UI)、`realCuganProEnabled`(Pro 由模型枚举决定)。新装用户与升级用户偏好键均与上游对齐。

## 3. 审查发现的问题(均已修复)

### 3.1 [已修] SSIV recycle 竞态崩溃(真机复现)
上游的 `clearProcessedSwapView`/`completeProcessedSwapTransition` 对**仍挂在视图树上的** SSIV 先 `recycle()` 后 `removeView()`;tachiyomiorg SSIV 的 `onDraw→sendStateChanged` 会解引用已被置空的 `vTranslate` → NPE 闪退(真机 PID 28748 复现,栈:`SubsamplingScaleImageView.sendStateChanged ← onDraw`)。修复:先 `removeView` 再 `recycle`,并在 recycle 前摘除 `OnStateChangedListener`。**上游代码存在同样的潜伏竞态。**

### 3.2 [已修] webtoon 预载页数回归(移植笔误)
上游 `HttpPageLoader.preloadSize` 回落值是常量 4(纯 mihon 语义);直译到 komikku 会覆盖 SY 的 `preloadSize()` 偏好。已改为回落 `readerPreferences.preloadSize().get()`。

### 3.3 [已修] 暂存区混入无关文件
`qnncheck/`(检查 AAR 的临时解包)曾误入 index,已移除;`gradle.properties`(+tooling.parallel)是用户在会话前的既有改动,**不属于本分支,提交时必须排除**。

## 4. 机械检查结果

- ✅ `i18n/`、`i18n-sy/` 非 base 无任何新增(diff 为空) — 符合 AGENTS.md i18n 硬规则
- ✅ 字符串仅新增于 `i18n-kmk/.../base/strings.xml`(37 个)
- ✅ `spotlessApply` → `spotlessCheck` 通过;`assembleDebug` / `assemblePreview`(R8)通过
- ✅ 无上游标识泄漏(未动 `AppUpdateChecker.GITHUB_REPO`、applicationId 保持 `app.komikku`)
- ✅ 偏好键与上游一致,上游用户迁移路径可用
- ⚠️ 未跟踪的 `app/src/main/assets/Anime4K_*.glsl`(根目录 7 个)是用户本地既有文件,与本次移植无关,保持未跟踪

## 5. 遗留/待决事项

1. **仓库体积**:third_party(ncnn 99MB + qnn-include 1.9MB)、assets(~527MB)、libQnnModelDlc(3.2MB)拟全部入库(上游同样入库),仓库将增 ~630MB。备选:Git LFS / 下载脚本 / 只保留 v79 上下文 — **待确认后提交**。
2. `reader_image_enhancement_toast` 字符串目前无引用(为底栏按钮预留)。
3. NPU 上下文为静态资产;若未来要为新架构重新生成,需上游 `tools/qnn` 的 Docker 管线(未移植)。
4. 合并双页模式的在线切换(§2.2)可在重设计 `mergePages` 副作用后补齐。
