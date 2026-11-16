# data1.1.0 验证记录

- 基于 R19 本地提交 44e1bad 的后续改动；构建基线为校验后的官方签名 R15 APK。
- APK SHA-256：`6120ebee80312fbbb22eff9de56d99d083e6d2c3bde8b712789237642b754996`。
- 包名 io.github.phiscript，versionCode 11000，versionName data1.1.0，minSdk 26 / targetSdk 35。
- 签名证书 SHA-256：`a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`，与 R15–R19 一致；apksigner v2/v3、zipalign 16 KiB 对齐及 DEX 重复/缺失引用检查通过。
- 相对 R15 改动 DEX：classes12.dex, classes4.dex, classes5.dex, classes6.dex, classes7.dex, classes8.dex；另改 Manifest 版本和图标引用，追加图标资源。所有原 res/* 文件、1330 个原资源 ID 与原类型配置块逐字节保留。
- 回归使用最终签名 APK 的实际原生/补丁代码和 Android 主机模型。没有 Android 真机实测。

```text
PASS version manifest: eight short/long/Unicode/legacy names, AAPT2 typed/raw verification, preserved old indices/attributes, icon ordering, numeric raw value and baseline rejection; data1.1.0 code11000.
PASS additive launcher icon: 1330 existing resource IDs and all original res/* bytes preserved; three new mipmap IDs; PNG512, v26/v33 adaptive/monochrome references, manifest icon/roundIcon and UTF8/UTF16 styled pools.
PASS 10 final-APK course routing checks with real AppSettings persistence hooks.
PASS chart planner, 10 holds, startup catch-up, mixed chords, cancellation, release, edge fallback
PASS core/planner/playback 11053 assertions
PASS 240 checks. Both input backends: one/two/six physical fingers; cross-line hold/drag/flick reuse, actual swipe displacement, safe DOWN and MOVE, narrow subtractive window.
root=true peak=1 newDowns=0 yellows=6 reds=2 missed=0
root=false peak=1 newDowns=0 yellows=6 reds=2 missed=0
root=true short noise activation=2/1/2
root=true isolated red: retained DOWN, actual MOVE, release
root=false short noise activation=2/1/2
root=false isolated red: retained DOWN, actual MOVE, release
noise phase=cold root=true blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=cold root=false blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-0 root=true blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-0 root=false blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-1 root=true blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-1 root=false blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-2 root=true blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=warm-2 root=false blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=steady root=true blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
noise phase=steady root=false blockedHoldDowns=2 safeHoldDowns=1 conservativeExtraDowns=0 actualUnsafe=0
PASS active-window separation and quiet linking; one held finger releases/restarts, parallel hold continues, later TAP dispatched on both backends.
PASS 31 noise sweep checks: fast narrow translation, relative motion, offsets, moving holes, easing knots, 0.5 ms activation, right-open boundaries and reachable hold continuation.
PASS 23 course boundary, fresh evidence, repeated-song and stop checks.
PASS 30 course clock checks: native scripts, three independent origins, frozen/stale/wrong notes and moving-line rejection.
PASS large noise interval index 4001 assertions; 10000 reversed intervals
PASS 63 actual course-engine checks: three Root/accessibility songs, per-song clocks, transition prefetch, pause/result, no calibration, stale/ambiguous/mid-song input rejection and cleanup.
PASS 1080p landscape/portrait, bounded capture, padded RGBA rows and 216001 frames on a 60fps clock (host model).
PASS final APK capture coordinator: real frame ingress, 60Hz timestamps, source updates and holds, two reusable buffers, exact-owner and foreground gates, disabled capture, cleanup and encoder failure.
PASS four 720p/1080p x 30/60fps profiles: dimensions, bitrate, one-hour output/source clocks, persisted settings, immutable active session, OCR resolution floor and capture/encoder model cleanup.
PASS 17 live foreground checks: projection sheet, stale events, focused/active overlay windows, empty root, cache invalidation and real app switches.
PASS 20 Phira online-library checks: official API shape, ID and OCR matching, ambiguity/truncation rejection, bounded file origin, actual PEZ import and persistent local chart.
PASS 22 native-noise and real import/persist/reload/play checks: outgoing easing, accumulated anchors, isolated/overlapping inversion, associated PEZ sidecar, static Next BPM/activation and both input backends.
PASS 40 dispatch latency checks: real CPU wall-clock cost, preserved isolated TAP duration, actual DOWN statistics, nearby fresh heads and noise activation boundaries.
5000-key trajectory: 1800 unique exact-time queries cached=2.9256 ms, independent sequential=710.8504 ms
PASS 66548 geometry-prefix checks against independent sequential JSON reference: arbitrary times/aspects, signed and zero scales, changing anchors, duplicate key times, easing, bounded exact snapshots and complex-track speed.
PASS 6 runtime-noise search budget checks: costly blocked/clear active-noise searches yield for release, unrestricted preflight and inactive-noise fast path.
PASS 20 actual imported-library deletion checks: selected file/index only, cancellation, busy/unknown/stale guards, retained chart load/restore, original PEZ and Phigros-store isolation, final empty-library backup cleanup.
PASS 454 read-only clearance design checks; production TapMod/BlockAreaSet unchanged.
PASS 6 runtime-noise search budget checks: costly blocked/clear active-noise searches yield for release, unrestricted preflight and inactive-noise fast path.
PASS 12 runtime-noise search budget checks: costly blocked/clear active-noise searches yield for release, unrestricted preflight and inactive-noise fast path.
PASS 18 runtime-noise search budget checks: costly blocked/clear active-noise searches yield for release, unrestricted preflight and inactive-noise fast path.
PASS 29 clock-uncertainty profile checks: compatible default, 16/24ms getters, narrow activation/safe windows, XOR composition, candidate/segment wiring, invalid preferences and unchanged runtime search budget.
PASS 9 cooperative noise deadline checks: expired point/path, future-deadline stationary-loop yield and preserved unlimited entry; individual geometry primitive remains non-preemptible.
selective phase=cold root=false safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=cold root=true safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-0 root=false safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-0 root=true safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-1 root=false safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-1 root=true safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-2 root=false safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=warm-2 root=true safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=steady root=false safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
selective phase=steady root=true safeHOLDdowns=1 blockedHOLDdowns=2 conservativeExtraDowns=0 unsafe=0
PASS 75 actual-wall-clock submission safety checks: expensive geometry, activation after planning, per-contact rejection, subsequent safe input and release.
PASS 35 native UI motion lifecycle model checks: explicit linear interpolation for entry/press/state/expansion/dialog, system motion policy, playback exclusion, coalesced state, press cancel, rapid expansion and detach; no renderer claim.
PASS 23 final-native-hook checks: actual Phira/Phigros import persistence, actual load cache reuse/corrupt rebuild, committed-source cancellation retention, native ChartLibrary hooks and verified second-import reuse.
PASS 20588 exact geometry lookup checks: wrapping hash collisions, repeated 96-entry eviction, five aspects, adjacent-double keys, independent transform/local coverage and fixed memory bounds.
PASS 5148 safe-gap center checks: composed XOR/parity, edge-safe midpoint, narrow gaps, moving guards, reachable holds, legal rotated bands, independent interval oracle and preparation cache inputs.
PASS 28 exact planner fixtures fingerprint=484efdfda88d9d4c7a3ac9bf6473a4d0095fa8966de7162ffc4a2d3709370b6c
PASS 10 planning cancellation checks; observedGeometry=true partial clearAt=1 responseMs=1
PASS 33 prepared-store checks: real disk encode/restore/corrupt rebuild, geometry/settings/bounds keys, cancellation source retention, namespace isolation, orphan AtomicFile cleanup, file/byte LRU and supported symlink containment.
PASS 325 dense trajectory checks: cache-hit zero geometry calls, native Root/AOSP playback, digest corruption, settings/viewport/fingerprint invalidation, random salt, cancellation and explicit noise gaps.
PASS 19 scoped native-library deletion checks: identical IDs, separate indexes/files/cache, cancellation/running/unknown guards, selected IDs, RuntimeState and final backup-safe restore.
```

《Desultory Signals》原谱 IN/AT，各跑随机开/关和标准/24ms 保护；每毫秒按 Android Path 弧长插值，用独立原生参考检查噪域并核对释放。preflightMs 是这次主机中完整首次预计算的实际耗时，包含规划和轨迹准备，与下面单阶段探针不能混用：

```text
REPLAY desultory-AT.json random=false clockGuardMs=0 notes=2026 unavailableNotes=0 planContacts=1424 preparedSamples=47551 preflightMs=19919 areas=160 duration=146.73267326732673 sends=5086 peak=6 DOWN=1445 arcSamples=141237 blocked=0 released=true
REPLAY desultory-IN.json random=false clockGuardMs=0 notes=2019 unavailableNotes=0 planContacts=1052 preparedSamples=37449 preflightMs=19615 areas=27 duration=147.0111386138614 sends=4178 peak=6 DOWN=1059 arcSamples=118249 blocked=0 released=true
REPLAY desultory-AT.json random=true clockGuardMs=0 notes=2026 unavailableNotes=0 planContacts=1424 preparedSamples=47542 preflightMs=21314 areas=160 duration=146.73267326732673 sends=5085 peak=6 DOWN=1444 arcSamples=141251 blocked=0 released=true
REPLAY desultory-IN.json random=true clockGuardMs=0 notes=2019 unavailableNotes=0 planContacts=1052 preparedSamples=37568 preflightMs=21570 areas=27 duration=147.0111386138614 sends=4177 peak=6 DOWN=1058 arcSamples=118608 blocked=0 released=true
REPLAY desultory-AT.json random=false clockGuardMs=24 notes=2026 unavailableNotes=0 planContacts=1426 preparedSamples=47569 preflightMs=72956 areas=160 duration=146.73267326732673 sends=5033 peak=6 DOWN=1445 arcSamples=139806 blocked=0 released=true
REPLAY desultory-IN.json random=false clockGuardMs=24 notes=2019 unavailableNotes=0 planContacts=1052 preparedSamples=37449 preflightMs=66717 areas=27 duration=147.0111386138614 sends=4082 peak=6 DOWN=1046 arcSamples=117207 blocked=0 released=true
REPLAY desultory-AT.json random=true clockGuardMs=24 notes=2026 unavailableNotes=0 planContacts=1426 preparedSamples=47564 preflightMs=76034 areas=160 duration=146.73267326732673 sends=5035 peak=6 DOWN=1449 arcSamples=139820 blocked=0 released=true
REPLAY desultory-IN.json random=true clockGuardMs=24 notes=2019 unavailableNotes=0 planContacts=1052 preparedSamples=37568 preflightMs=73888 areas=27 duration=147.0111386138614 sends=4083 peak=6 DOWN=1047 arcSamples=117581 blocked=0 released=true
```

本轮噪域提交复核统计（主机模型）：

```text
噪域提交复核：拒绝或抬起 9 次触点，最大复核耗时 5 ms
噪域提交复核：拒绝或抬起 7 次触点，最大复核耗时 3 ms
噪域提交复核：拒绝或抬起 7 次触点，最大复核耗时 5 ms
噪域提交复核：拒绝或抬起 4 次触点，最大复核耗时 3 ms
噪域提交复核：拒绝或抬起 28 次触点，最大复核耗时 5 ms
噪域提交复核：拒绝或抬起 15 次触点，最大复核耗时 4 ms
噪域提交复核：拒绝或抬起 22 次触点，最大复核耗时 5 ms
噪域提交复核：拒绝或抬起 16 次触点，最大复核耗时 4 ms
```

新增检查涵盖实际原生导入时生成缓存、重启加载复用、坏缓存重建、取消保留原谱、两套谱库删除隔离、安全间隙居中和预计算命中零坐标调用。UI 动效检查为主机生命周期模型，未做 Android 设备像素渲染。

离线轨迹采样优化的同机预热对比（不含下载/解析；不是手机时间保证，分配量不等于峰值内存）：

```text
R20 preparation-only benchmark, 2026-11-16.
Single warmed host JVM, unchanged pinned AT/IN chart/planner/salt, two alternating measured before/after pairs per chart.
ReferencePreparation + ReferenceTouchSafety preserve the pre-optimization implementation.
Values are host results, not an Android device timing guarantee.

PREPARE desultory-AT.json contacts=1424 samples=47551 plannerMs=965.3 beforeMs=9204.4 afterMs=7521.4 speedup=1.22x beforeAllocatedMiB=3764.9 afterAllocatedMiB=3053.5 chartPositionCalls=50491 judgeNormalCalls=16987 removedExactSegmentCalls=46125 equivalent=true
PREPARE desultory-IN.json contacts=1052 samples=37449 plannerMs=1411.5 beforeMs=6258.0 afterMs=5907.8 speedup=1.06x beforeAllocatedMiB=3817.5 afterAllocatedMiB=3588.2 chartPositionCalls=39142 judgeNormalCalls=3742 removedExactSegmentCalls=36397 equivalent=true
PASS 379620 exact prepared-coordinate and contact-lifetime comparisons

Compared every sample time, coordinate float bit, connection flag, broken-prefix value, critical boundary, continuity/flick flag. Geometry call counts unchanged.
Sampling remains 4ms plus exact route boundaries; final actual-submission noise verification remains enabled.
No benchmark reruns after this accepted result. Later small regression additions: dense trajectory325, store33, hooked native integration23.

```

原生谱面规划阶段探针与等价、取消验证（同样为主机结果，非完整导入耗时）：

```text
Original native SharedChartImport AT, no wrapped Chart. Host JVM, random=false, real final-APK geometry unless labelled combined.
Frozen final planner + cooperative-cancellation TapMod, guard0: finished3614ms,1424 contacts,unavailable0,clearAt43909,segment801.
Frozen final planner + cooperative-cancellation TapMod, guard24: cancelled10005ms at explicit10s probe budget,clearAt45420,segment702.
Earlier root evidence: r20-guard24-thread.txt shows original guard24 in ContactPlanner.stroke/segment at elapsed292.51s.

Replacement planner + old final-APK geometry, guard0: finished3510ms,1424 contacts,unavailable0,clearAt38886,segment775.
Replacement planner + old final-APK geometry, guard24 after impossible-reuse pruning: finished3479ms,1426 contacts,unavailable0,clearAt20033,segment177.
Combined replacement planner + exact geometry hash/cache, guard24: finished629ms,1426 contacts,unavailable0,clearAt20033,segment177.
Combined planning stats: strokeRequests199,strokeComputations199,candidates1686,bandRequests16697,bandEvaluations6674,noiseSamples5328,lastRedHitMs146733.

Exact chosen contacts/times/route keys/escaped/swipe endpoints for28 fixtures (standard/24ms guards, random on/off, lone/reused/chorded/capacity-limited/noisy red notes): frozen final and combined source both SHA256 484efdfda88d9d4c7a3ac9bf6473a4d0095fa8966de7162ffc4a2d3709370b6c.
PlanningCancellationRegression PASS8; partial segment1452 clearAt calls before cancellation, response7ms. Dense trajectory regression PASS325.
No candidate-time budget or precision reduction used. Reuse pruning removes only branches whose shared mobileEligible predicate proves every stroke result would be discarded; fresh/hold reuse alternatives and unavailable accounting retained.

```

原谱由固定版本外部提供，未放入源码包。预规划找到安全位置不代表每个实际音符均命中；主机零入域不等于真机全连。实时计算或复核耗时过长时会释放相应触点后重试，保守保护也可能增加抬指。预算在几何调用之间检查，不是对单个几何操作的抢占式硬时限；Android 手势回调和 Root 传输延迟也不能被这些检查抢占。

用户新回放的 98.34% / 26 Miss 是本次修改的分析输入，不是 data1.1.0 成绩。对照见回放报告；没有上传原始回放或玩家身份。
