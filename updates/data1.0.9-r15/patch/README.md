# Phigros Script APK patch r14

This patch is for the user-supplied APK only:

- package: io.github.phiscript
- source APK SHA-256: ee461650fcbf27f82bb300037d3a2bfeaf703deaa47e908b49a5326c18ee5b7f
- source versionCode: 25; patched versionCode: 38
- upstream reference: https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script.git
- upstream commit: a41da3b102c67ebfbd107f1c6e7ae2e04230724a

The Java sources in src and the asserted hooks in patch_smali.py are the final source of truth. This is a patch against the supplied binary; the upstream root Gradle sources alone do not reproduce this APK version.

## R14

Phigros、Phira 和 Phira Pro 共用 `ContactPlanner`。蓝键和长按先预留触点，红键保留有效滑动，黄键优先使用已按住的触点。全屏黄键段只有在六个位置能覆盖实际判定带时才固定按住六指。规划和发送使用同一玩法视口及系统边缘范围。噪域搜索检查判定线法向、判定带内的横向偏移、减法区域、整数像素及移动轨迹。

用 `bash patch/build.sh` 生成主程序。运行 `bash patch/tests/r12/run.sh --corpus` 检查兼容性，再运行 `bash patch/tests/r14/run.sh path/to/sample.pez path/to/Phigros_Swordalt_All_Scripts.zip`。第二条命令从成品 APK 的 DEX 运行触点与整曲回放测试。记录位于 `patch/results/r14/`。Android 系统输入和时钟使用主机模型，游戏判定与真机时延需要设备验证。

## R13

本版本在 data1.0.9 修订线上构建。主程序沿用 R12 的游戏导入、独立延迟、前台确认和噪域抬指代码。录屏由 data1.0.9 原有的捕获服务和 AVC 编码器修改而来。`patch/gesture-recorder/` 为独立编写的手势录入器，使用自己的包名和 JSON 格式。

主程序构建入口为 `bash patch/build.sh`，独立应用为 `bash patch/gesture-recorder/build.sh`。测试入口为 `bash patch/tests/r12/run.sh`，包括 R13 的录屏与手势样本回归。将完整脚本包置于 `count-current/Phigros_Swordalt_All_Scripts.zip` 后加上 `--corpus` 可以复查 1057 张谱面。设置 `R12_SAMPLE_PEZ` 可验证指定的 PEZ。两个 APK 均构建后，运行 `bash patch/tests/r13/verify_packages.sh` 可从成品复查签名、投屏入口、权限以及录入 JSON。R13 的记录位于 `results/r13/`。

## Build layout

Run from the directory containing patch/ and tools/. Required files:

- upload/attachment_1413700149765945124.apk: original APK above
- analysis/decoded: `java -jar tools/apktool.jar d -r ORIGINAL -o analysis/decoded`
- analysis/original.jar: dex2jar of the original APK
- tools/apktool.jar: Apktool 2.11.1
- tools/ecj.jar: Eclipse compiler 3.39.0
- tools/kotlin.jar: Kotlin stdlib 2.0.21
- tools/platform/android-35/android.jar: Android SDK platform 35
- tools/build-tools/android-15: Android SDK build-tools 35, including D8, apksigner and zipalign
- tools/dex2jar/dex-tools-v2.4: dex2jar 2.4
- JDK 17, Python 3 and ripgrep

The original APK uses the public AOSP Android test certificate. Obtain the matching public test key and certificate from `platform/build/target/product/security` on android.googlesource.com; place them in patch/signing/testkey.pk8 and testkey.x509.pem. These are public AOSP test credentials, not a user's private key. Verify the original certificate SHA-256 is a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc before replacing it.

Run `bash patch/build.sh`. Assemble.java deliberately uses API 26 / DEX 038; raising this to API 35 would emit DEX 041 and break older supported Android versions. The script's explicit Linux loader invocation supports workspace mounts that do not retain executable permission bits.

`manifest_patch.py` only raises versionCode and adds largeHeap; `package_apk.py` preserves other original entry contents, replaces selected DEX files, adds the patch DEX and metadata catalog, and removes old signatures before signing. Classes from the replacement Java sources are removed from their old DEX to avoid duplicates.

## Testing

Host-only Android models are in tests/mock. tests/src includes the regressions; fixtures are supplied by the pinned upstream checkout under upstream/updates/data1.0.9/patch/tests/fixtures. Add org.json 20240303 as tools/json.jar. Compile mocks/tests with ECJ against analysis/patched.jar, kotlin.jar, json.jar and android.jar. Put tests/classes first on the test classpath. Run CoreTest and ExternalTest in their stated packages; SampleTest requires the locally downloaded sample files in analysis/samples. LiveDownloadTest uses the actual public share and may fail when the external service is unavailable. It requires normal HTTPS access (configure the JVM proxy if the host requires it).

The results/ directory records executed checks. They are not device/emulator validation. Full sampled chart contents and transient signed download URLs are intentionally not included in this source archive; sample-inspection.json records names, sizes and metadata presence.

See PhigrosScript_changes.md for the known absence of blockAreaList in sampled user-provided normalized charts. No source can recreate missing noise metadata from the note coordinates alone.

## r3 changes

Phigros now has one retry-delay setting and uses PhiraRestartClock for its epoch. Its old visible-note alignment, drift adjustment and extra legacy offsets are not used. PhigrosRetryClock tests compare it directly with Phira.

The r2 shared-class omission is reproduced by tests/r3/ImportLifecycleRegression.java. restore_shared_dependencies.py retains original classes referenced by the remaining code, including the CAS compatibility helper needed by PhiraImportControl.Session.close. lib/dex_info.py and tests/verify_dependencies.py check original program class/method/field references. The build rejects missing dependencies and duplicate classes before delivery.

Tests/r3 includes import-session lifecycle and retry-timing regressions, plus a check that the active Phigros path has one calibration field and no old alignment paths. Run these against analysis/patched.jar produced from the final signed APK, with the same Android host models and dependencies as the other tests.

## r4 original-chart source and routing audit

SwordaltSource reads raw PGR JSON from the resource repository used by swordalt/phigros-chart-downloader. The bundled catalog pins chart and info commits and includes Git blob hashes, sizes and noise counts. No RPE conversion or website analytics are used. SwordaltLibraryUi provides its own source, single-chart downloads and offline ZIP import.

Data snapshot: 1057 raw charts (1038 site-visible, 19 extra tutorial/variants), 25 nonempty blockAreaList values, 17782 areas. Source PGR formats 1 and 3 are preserved verbatim in the standalone ZIP. MAX noise areas is bounded at 32768; ActiveIndex filters inactive intervals.

Reproduce data from the repository with:

```sh
python3 patch/data-tools/download_swordalt.py
python3 patch/data-tools/extract_swordalt.py
python3 patch/data-tools/build_swordalt_pack.py
python3 patch/data-tools/verify_swordalt_pack.py
```

The small snapshot metadata is under data-tools/snapshot. Extraction verifies every Git blob before writing the raw charts. Output raw data expands to approximately 2.70 GB; the provided offline ZIP is approximately 359 MB.

ModeRouting unifies startup, labels, engine dispatch and preference transitions. AppSettings.save/saveFlag are hooked after their real writes so the retained original Phira import UI also clears incompatible Phigros flags. Successful Phigros imports call commitPhigros; neutral restoration does not change the selected game. APK scan/import callbacks record the correct source only on success. The old Phigros-local plus PHIRA state is migrated once. Main Phira and Phigros-local storage remain separate.

ModeRoutingRegression must use the final APK's real AppSettings, not the AppSettings test mock. A separate routing models JAR excludes io/github/phiscript/AppSettings.class; use the MemoryPreferences Android model with the real production setters to exercise the injected hooks. Tests/r4 includes the source, whole-snapshot parser, full offline import, noise interval index, routing and static flow checks. Results are in results/r4.

Runtime and build dependencies are unchanged. The complete data ZIP is a separate artifact, not embedded in the APK.

## r5 imported-file validation and native scripts

The original PhiraLibrary contained two inlined 8 MiB checks in load and restore,
although importing and bounded reads already permitted 256 MiB of normalized JSON.
patch_smali.py changes both inclusive bounds to 256 MiB. File presence, exact length,
SHA-256 verification, separate Phira/Phigros stores and library total limits remain.
The saved index format is unchanged, so complete previously imported files work
without importing again. tests/r5/LargeImportRegression reproduces both failures
against R4 and exercises fixed import/load/restore and genuine corruption cases.
The application version code is 29 (data1.0.9-r5 in the UI).

PgrScriptConverter exports official PGR 1/3 into phiscriptFormat 1, including title,
difficulty, timed notes, native line keyframes and blockAreaList. Event selection
matches the official parser: stable duplicate-start precedence, clamped gaps,
overlap, skipped reversed scenery and division-rounding behavior at jumps.
The native player's inward clipping at screen edges is allowed only along the
unrestricted normal. Keyframes cover the playable interval plus 10 seconds.
Canonical JSON keeps the exported bytes stable when a missing script is rebuilt.

SwordaltSource recognizes script manifests, uses distinct raw/script cache keys,
and verifies both original and converted hashes. A missing/deleted script is
regenerated from its pinned, verified original; the raw source path is retained.
An 8 GiB combined cache budget allows the complete raw and script libraries to
coexist; each individual imported package remains bounded at 4 GiB.

After building, run `bash patch/tests/r5/run.sh`; add `--full` to test every file
through the completed offline script archive. This uses real AppSettings for
routing and persistence tests and a separate settings model for gesture tests.

To recreate the script archive, retrieve/verify the raw charts with the r4 data
tools, compile data-tools/ExportSwordaltScripts.java against the final patched
classes and the Android host models, and run its main class without arguments.
Then run `python3 patch/data-tools/build_script_pack.py`.
The exported archive can be imported as one file through the Swordalt offline
ZIP action in R5; individual scripts can also use the Phira or Phigros local
import actions. No audio or illustrations are included.

## r6 Phira Pro playback

NativeAnim now holds its first value before its start time and adds all nonempty
layers, matching Phira Pro core/anim.rs. NativeChartProjection evaluates a touched
line and its ancestor chain rather than all lines. It preserves the existing
normalized coordinates and line hierarchy, including offscreen normal clipping.

SharedChartPlayback prepares Phira's Root channel before the retry clock starts.
The retained PhiraSessionEngine in classes5.dex is hooked at prepareRetry, menu
control and run cleanup; Phigros uses the same service.play entry with an owned,
prepared Root channel. RootWarmup returns the acknowledged first DOWN timestamp.
Previous hand-tuned Root retry delays may need retesting after that timing change.
No extra Phigros calibration field was added.

The patch now rebuilds DEX 4/5/6/7/8/11 and adds DEX 12. Shared dependency and
duplicate-class checks still run. Build/runtime dependencies are unchanged.

After building, run `bash patch/tests/r6/run.sh --corpus`. The corpus option needs
the previously delivered script ZIP at count-current/Phigros_Swordalt_All_Scripts.zip.
The normal run omits that large-file check. R6 tests exercise actual APK classes,
including both service.play entry paths with a model Root startup delay, direct
first-frame miss reproductions, parent/aspect reference coordinates, RPE layers,
input cleanup and prior click/import/mode/noise regressions.

Reference repositories were inspected at Phigros-Script a41da3b102c67ebfbd107f1c6e7ae2e04230724a
and Phira-Pro/Phira-Pro 6776bbd4098bc42dc6becbb6f1cf60eb1d91dbb3.
No device or emulator gameplay validation was available; host tests cannot
establish a full combo on a user's device. See PhigrosScript_changes.md.

## r7 Feijipan extracted script directory

The new public share is https://share.feijipan.com/s/feyajI8g. A dedicated import
menu entry activates its bundled path-to-file-ID snapshot. Fresh installations
use this share by default; an existing chosen source remains selected. The
network dialog pre-fills this link and can rebuild its catalog after files move.

FeijipanSource traverses named folders with complete, consistent pagination,
then joins swordalt-catalog.json relative paths to its sibling charts/ directory.
Numeric API IDs are accepted explicitly. The bounded folder count is now 2048
instead of 128. Noise sidecars and metadata never become playable chart entries.
Incomplete uploads report available/expected counts and exclude missing paths.

ExternalCharts retains manifest aliases, manual-only variants, exact lengths,
Git blob hashes and noise counts. Every play downloads the selected file afresh,
verifies it before parsing or caching, and checks parsed noise counts. A failed
download never replaces valid cache bytes or plays unverified fallback data.
Phigros and Phira remain isolated and use the unchanged R6 playback engine.
Cache deletion still keeps the song index for the next download.

The public snapshot under data-tools/snapshot/feijipan-feyajI8g contains only
relative paths, public numeric file IDs, manifests and audit totals. It excludes
transient download URLs, user account records and chart payloads.

Run bash patch/tests/r7/run.sh after building. It tests the final APK's actual
classes with all 334 chart directories and four-page folder pagination, corrupt
and missing uploads, exact-path matching, native-script/noise import, hash and
length failures, cancellation, cache deletion and game routing. Add --live to
download four actual public-share charts (EZ, AT, SP and two noisy charts). The
script ZIP is needed at the same location as the R6 tests. To rebuild the bundled
asset from the recorded snapshot, invoke FeijipanPathRegression --catalog with
the compiled test/model/production classpath before packaging.

Recorded availability: 1052 / 1057 charts in 344 folders (1082 total files).
The five missing paths were re-read independently and are listed in audit.json
and missing-recheck.json. All 25 noisy-chart paths are present.
Run python3 patch/data-tools/build_feijipan_repair.py to extract the five missing
files byte-for-byte from the previously delivered complete script ZIP. Merge
its charts/ folders into Phigros/charts/ on the share and refresh the app catalog.
The repair ZIP is for restoring the share, not a single multi-chart local import.

## r8 one Phira session engine for both chart modes

PhigrosAutoEngine is now a constructor adapter into the actual PhiraSessionEngine
from the supplied APK. The adapter does not implement pause/retry timing, screen
monitoring, playback threads, restart confirmation, viewport rules or cleanup.
Both automatic Phigros recognition and Phigros manual chart choice use this
same engine, including confirmation before loading/pausing. Pure visual mode
retains its own original implementation.

SessionChartSource supplies chart metadata, identity lookup, manual chart IDs,
loads and the target package. Its Phira path delegates directly to the existing
PhiraLibrary, PhiraMatcher and GamePackages.isPhira. Its Phigros path maps the
existing title/rank identities to stable per-chart IDs, preserves imported
aspect metadata, and uses ExternalCharts.loadSelected with cancellation. No
Phigros data is written into RuntimeState.phiraLibrary or the Phira import store.

patch_smali.py adds a source-accepting constructor to the retained engine and
keeps its original PhiraLibrary constructor ABI. References to chart metadata
and loading are retargeted to SessionChartSource; source matching/foreground
methods are the only helpers replaced. User messages become game-neutral.
All 19 other original methods retain the delivered R7 Phira instructions after
source-type and message normalization. This includes the complete confirmation,
pause, retry, start receipt, epoch, playback, screenshot monitoring, await-next
opportunity and cleanup methods.

Both modes now use SessionSettings.phiraRestartDelayMs, phiraRestartPoint,
phiraPauseRoi, phiraAutomaticViewport, phiraGlobalOffsetMs and touchOffsetMs.
The one retry calibration field writes phiraRestartDelay for both games. The
older Phigros retry/manual-delay preferences are no longer active. Phigros
manual selection stores its own phigros_session_chart_id; it cannot accidentally
reuse a saved Phira chart ID. Selecting a manual chart now starts capture and
the same confirmation/retry flow rather than the manual timestamp controller.

After building, run bash patch/tests/r7/run.sh for retained regressions, then
bash patch/tests/r8/run.sh for the new shared-session tests. The R8 tests use
actual final APK classes with deterministic OCR/camera and transport models.
They cover both games, Root handoff, manual choice, confirmation, preview,
restart failure, stale frames, game focus, screen resize, chart switch, user
cancellation and cleanup. Adapter checks exercise local, both Feijipan catalogs
and verified Swordalt script reads. APK instruction parity is checked by
disassembling the actual delivered DEX, not just inspecting source code.

The recorded R7 Phira baseline is under tests/r8/phira-r7-baseline.json with
its APK SHA-256. DumpSession.java uses the installed Apktool/Baksmali library
to inspect the final APK. The runtime/build dependencies remain unchanged.

## r9 game-specific pause controls inside the shared session

The provided 1536x698 Phigros pause screenshot has a thin left chevron, a thin
open retry circle and a filled play triangle. The Phira glyph template fails
on these actual pixels. SessionPauseMenu now supplies the target game's visual
menu evidence and restart point while PhiraSessionEngine retains the session
gates, restart clock, playback and cleanup.

PhigrosMenuGlyphDetector requires a complete ordered three-icon row. It preserves
thin strokes during bounded pooling, checks component shapes, spacing and row
alignment, and rejects incomplete, exchanged and ambiguous rows. The measured
central retry glyph supplies the two retry coordinates. A failed or moving menu
clears the target; it cannot reuse an older coordinate. Phigros pause detection
uses the configured pause ROI directly in physical screen coordinates.

The original Phira pause-menu constructor is retained as phiraPauseMenuFor and
is delegated to directly for Phira/Phira Pro. Their detector DEX is byte-identical
to R8, and their configured restart point and viewport-relative pause ROI remain.
No second session loop, playback clock or calibration field was introduced.

After building run tests/r7/run.sh, tests/r8/run.sh and tests/r9/run.sh (with bash).
The final suite uses the real uploaded JPEG, resized/exposure/contrast/JPEG
variants and negative scenes against actual final-APK classes. The state-machine
model separately covers measured retry coordinates for both touch backends.
The existing parity audit now explicitly accounts for the visual-menu adapter,
Phigros-only physical ROI and the preserved original Phira menu constructor.

The fixture is tests/r9/fixtures/phigros-pause-menu.jpg. These are host/image tests,
not Android device gameplay. Phigros screens without a visible song title can
use manual chart selection followed by the same confirmation/retry session.

## r10 crossing assignment, timing removal and full difficulty audit

The supplied Desultory_Signals_AT.pez reproduced the exact R9 exception:
`Index -1 out of bounds for length 26` in UnifiedGesturePlanner.assign.
After a crossing was rejected, the inner loop still tried live.get(-1).
The asserted smali patch changes that one exit branch to stop the inner loop.
The caller creates a separate contact for the unmatched note. Final APK
disassembly verifies every other planner instruction remains the same.
The PEZ contains 2026 notes, 24 lines and 160 noise areas. Its import, parse
and gesture preflight pass against final signed-APK code. The synthetic test
checks live lists of 3/26/64/128, crossing and ordinary assignments, both bridge
settings and complete note coverage. Audio/artwork are not redistributed.

R9 still wrote timing tables during APK scanning and selected-chart loading;
they did not feed the shared retry epoch. R10 removes both calls, the tool
entry and six obsolete class families listed in tests/r10/verify_final_flow.py.
Final DEX definitions and references are audited. Existing device timing files
are no longer read by this path. PhiraSessionEngine still uses PhiraRestartClock
with chart/global/touch offsets exactly once. PhigrosRetryClock remains only as
a compatibility helper and the UI's shared retry-key definition; the session
does not call it.

RecognitionSettings omits the original device form's second retry-delay editor.
RetryCalibration is the single retry-delay entry. Installed-APK imports merge
Root and ordinary/Shizuku scanning according to the selected input backend.
Permissions/Shizuku tools have one entry; Phigros manual selection is reached
from the home mode picker and main action.

The unchanged full script ZIP was audited against every original chart and
the pinned Git tree: 1057 paths, 677156 notes and 17782 noise areas. Counts are
EZ 334, HD 333, IN 333, AT 56 and SP 1. Source/export hashes, embedded META
level/path, all note types/sides, head/tail times, x positions, speed/floor,
offsets and complete noise objects match. The 1052 current Feijipan locations
agree with that manifest; five known missing paths remain marked missing.
No difficulty swap was found. The old 1022-entry unverified catalog was not
redownloaded for an equivalent content audit.

ChartRankRoutingRegression loads all 1057 charts through final-APK
SessionChartSource with only the selected chart cached, checks counts/ranks,
rejects another rank, and samples 9513 native positions. Seven Ark/Rebirth
difficulties are ambiguous to the current OCR song matcher and require manual
selection; no result substitutes another difficulty. SP is available manually.
These are host checks, not a full-combo Android gameplay claim.

After building, run tests/r7/run.sh, tests/r8/run.sh, tests/r9/run.sh and
`bash patch/tests/r10/run.sh upload/Desultory_Signals_AT.pez --corpus`.
For the independent archive comparison run
`python3 patch/tests/r10/audit_chart_identity.py RAW_ZIP SCRIPT_ZIP RESULT_JSON`.
Results are in results/r10; the full PEZ and chart ZIPs are external inputs.

## r11 formal-name priority in Phigros chart identification

The seven R10 ambiguities were exact-name/alias collisions: Ark is also an
alias for 望影の方舟Six, and rebirth is an alias for both Re：birth and 重生.
PhigrosChartMatcher first finds exact normalized formal titles, then calls
the original SongMatcher with that unique candidate. Without formal-title
evidence, original alias and fuzzy matching remain available. Multiple formal
titles, duplicate normalized formal names or conflicting difficulty evidence
remain unresolved. A rank embedded in the matched title must agree with the
dedicated difficulty region. Missing dedicated rank evidence is not inferred.
SP remains manually selectable under the existing matcher rules.

SessionChartSource uses this adapter only for Phigros. Phira/Phira Pro retain
PhiraMatcher. Shared sessions, clock, touch planning and chart conversion use
the same decoded instructions as R10. verify_scope.py checks all original
classes in each reassembled DEX plus existing patch classes: 2950 methods are
unchanged; the only changed existing methods are source match and the home
version label. DEX data ordering may differ after reassembly, so this is an
instruction comparison rather than an unsupported whole-file hash claim.

All 1037 site-visible non-SP chart difficulties match their formal name; the
previous seven collisions resolve without choosing a different difficulty.
The actual final-APK source also loads all 1057 chart files and rejects wrong
rank requests. The 1057-chart ZIP itself remains the R10-audited data, SHA-256
6c4507b52015c059d04efa07851bb07038abf0867bf78474484bedcc6f5a253f.

After the retained R7/R8/R9 and R10 checks, run `bash patch/tests/r11/run.sh`.
Its scope audit expects the actual R10 APK under r11/current; the final-APK
disassembly is generated by the R10 suite. The baseline reproduction is
RankMatchingRegression --baseline against the real R10 JAR. Test results,
scope audit and release notes are in results/r11. These remain host checks;
no Android device full-combo result is claimed.

## data1.0.9-r15 touch regressions

After building the main APK and running `bash patch/tests/r6/run.sh`, run
`bash patch/tests/r15/run.sh /path/to/chart.pez`. The optional PEZ is
Phira chart 8825, *lost umbrella(yuigot Remix)* IN Lv.13, SHA-256
`ff92fad7854fa1cbe538f652e553dba1af18441505be4dc1fd264bcac742a9b3`.
It is used as a local test input. The synthetic serial drag/flick sequence
and the sub-millisecond noise test run without the PEZ. For chart-wide noise
checks, `patch/tests/r14/run.sh` accepts the Desultory Signals AT PEZ and the
Swordalt all-scripts ZIP. See PhigrosScript_changes.md for the results.
