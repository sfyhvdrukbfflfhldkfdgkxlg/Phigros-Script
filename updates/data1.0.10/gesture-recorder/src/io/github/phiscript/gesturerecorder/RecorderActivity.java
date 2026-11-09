package io.github.phiscript.gesturerecorder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Deliberately separate from the player: no AccessibilityService or overlay. */
public final class RecorderActivity extends Activity {
    private static final int EXPORT_JSON = 1;
    private static final ExecutorService FILES = Executors.newSingleThreadExecutor();
    private GestureRecording recording, exportRecording;
    private String exportName;
    private EditText name;
    private TextView status;
    private Button start, stop, clear, export, preview;
    private GestureCanvas canvas;
    private boolean exporting, destroyed;
    private boolean restoringDraft, exportCanceled;
    private Uri deferredExportUri;
    private int draftGeneration;

    private static final class Retained {
        final GestureRecording recording, pendingExport;
        final String name, exportName;
        Retained(GestureRecording recording, String name, GestureRecording pendingExport, String exportName) {
            this.recording = recording; this.name = name; this.pendingExport = pendingExport; this.exportName = exportName;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 246, 250));
        int pad = dp(14); root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            } else v.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                    pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout controls;
        if (landscape) {
            root.setOrientation(LinearLayout.HORIZONTAL);
            controls = new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); controls.setPadding(0, 0, dp(10), 0);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(controls);
            root.addView(scroll, new LinearLayout.LayoutParams(dp(230), -1));
        } else controls = root;
        TextView title = new TextView(this); title.setText("手势录入器"); title.setTextSize(23); title.setTextColor(Color.rgb(25, 38, 62));
        controls.addView(title);
        TextView help = new TextView(this);
        help.setText("独立录入工具 · 在下方画布操作，可同时使用多根手指。JSON 仅用于编辑应用手势。");
        help.setTextSize(13); help.setPadding(0, dp(5), 0, dp(7)); controls.addView(help);
        name = new EditText(this); name.setSingleLine(); name.setHint("手势名称"); name.setText("新手势");
        name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(120)});
        name.setContentDescription("导出的手势名称"); controls.addView(name);
        status = new TextView(this); status.setTextSize(13); status.setMinLines(2); status.setPadding(0, dp(6), 0, dp(6));
        controls.addView(status);
        LinearLayout row = new LinearLayout(this);
        start = button("开始录入", row, v -> begin());
        stop = button("停止", row, v -> finish("user-stopped", false));
        clear = button("清空", row, v -> {
            draftGeneration++; FILES.execute(() -> DraftStore.clear(getFilesDir()));
            canvas.stopPreview(); recording = null; canvas.reset(); refresh("已清空，点击开始录入。");
        });
        controls.addView(row);
        canvas = new GestureCanvas();
        LinearLayout.LayoutParams draw = new LinearLayout.LayoutParams(landscape ? 0 : -1, landscape ? -1 : 0, 1);
        draw.topMargin = dp(6); draw.bottomMargin = dp(6);
        root.addView(canvas, draw);
        LinearLayout actions = new LinearLayout(this);
        preview = button("回放预览", actions, v -> canvas.beginPreview());
        export = button("导出 JSON", actions, v -> export());
        controls.addView(actions);
        setContentView(root);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Retained && ((Retained)retained).recording != null) {
            Retained previous = (Retained)retained; recording = previous.recording; name.setText(previous.name);
            exportRecording = previous.pendingExport; exportName = previous.exportName;
            canvas.showAll();
        } else {
            restoringDraft = true;
            if (state != null && state.getString("deferredExportUri") != null)
                deferredExportUri = Uri.parse(state.getString("deferredExportUri"));
            final int generation = draftGeneration;
            FILES.execute(() -> {
                final DraftStore.Draft draft = DraftStore.load(getFilesDir());
                runOnUiThread(() -> {
                    if (destroyed) return;
                    restoringDraft = false;
                    if (recording == null && draft != null && generation == draftGeneration) {
                        recording = draft.recording; name.setText(draft.name); canvas.showAll();
                    }
                    if (recording != null && state != null && state.getBoolean("pendingExport", false) && !exportCanceled) {
                        exportRecording = recording; exportName = state.getString("exportName", draft == null ? "新手势" : draft.name);
                    }
                    if (deferredExportUri != null) {
                        Uri destination = deferredExportUri; deferredExportUri = null;
                        if (exportRecording != null) {
                            GestureRecording snapshot = exportRecording; exportRecording = null;
                            writeExport(destination, snapshot, exportName); return;
                        }
                        refresh("无法恢复待导出的手势，请重新录入。"); return;
                    }
                    refresh(recording == null ? "点击开始录入。每次最多 10 分钟；切换应用会停止并关闭触点。" : "已恢复上次停止的手势，可预览或导出。");
                });
            });
        }
        refresh(recording == null ? "点击开始录入。每次最多 10 分钟；切换应用会停止并关闭触点。" : "已恢复本次手势，可预览或导出。");
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private Button button(String text, LinearLayout row, View.OnClickListener action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(13); b.setOnClickListener(action);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1)); return b;
    }

    private void begin() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;
        draftGeneration++; FILES.execute(() -> DraftStore.clear(getFilesDir()));
        canvas.stopPreview(); canvas.reset();
        recording = new GestureRecording(canvas.getWidth(), canvas.getHeight(), getResources().getDisplayMetrics().densityDpi,
                getWindowManager().getDefaultDisplay().getRotation(), SystemClock.uptimeMillis());
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refresh("正在录入 · 所有操作仅在画布内记录。");
        canvas.postDelayed(limitStop, GestureRecording.MAX_DURATION_MS);
    }

    private final Runnable limitStop = () -> {
        if (recording != null && recording.isRecording()) finish("duration-limit", true);
    };

    private void finish(String reason, boolean interrupted) {
        if (recording == null || !recording.isRecording()) return;
        recording.finish(SystemClock.uptimeMillis(), reason, interrupted);
        canvas.removeCallbacks(limitStop); getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        canvas.showAll();
        saveDraft();
        String message = interrupted ? "录入已中断，活动触点已关闭，可预览或导出。" : "已停止，可预览或导出 JSON。";
        refresh(message);
    }

    private void saveDraft() {
        if (recording == null || recording.isRecording()) return;
        final GestureRecording snapshot = recording;
        final String savedName = name.getText().toString();
        FILES.execute(() -> DraftStore.save(getFilesDir(), snapshot, savedName));
    }

    private void refresh(String message) {
        boolean active = recording != null && recording.isRecording();
        boolean hasData = recording != null && !recording.getFrames().isEmpty();
        start.setEnabled(!active && !exporting && !restoringDraft); start.setText(hasData ? "重新录入" : "开始录入");
        stop.setEnabled(active); clear.setEnabled(!active && !exporting && !restoringDraft && hasData);
        export.setEnabled(!active && !exporting && !restoringDraft && hasData); preview.setEnabled(!active && !exporting && !restoringDraft && hasData);
        name.setEnabled(!active && !exporting && !restoringDraft);
        if (recording != null) message += String.format(Locale.ROOT, "\n%d 帧 · %d 个触点采样 · %.3f 秒",
                recording.getFrames().size(), recording.getSampleCount(), recording.getDurationMs() / 1000.0);
        status.setText(message);
    }

    private void export() {
        if (recording == null || recording.isRecording() || recording.getFrames().isEmpty()) return;
        canvas.stopPreview(); exportRecording = recording; exportCanceled = false;
        exportName = name.getText().toString().trim(); if (exportName.isEmpty()) exportName = "新手势";
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT); create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType("application/json");
        String filename = exportName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        create.putExtra(Intent.EXTRA_TITLE, filename + ".json");
        try { startActivityForResult(create, EXPORT_JSON); }
        catch (android.content.ActivityNotFoundException unavailable) {
            exportRecording = null; Toast.makeText(this, "设备没有可用的文件保存界面。", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_JSON) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            exportRecording = null; deferredExportUri = null; exportCanceled = true;
            refresh("已取消导出，手势仍保留在当前会话。"); return;
        }
        if (exportRecording == null && restoringDraft) {
            deferredExportUri = data.getData(); refresh("正在恢复待导出的手势…"); return;
        }
        final GestureRecording snapshot = exportRecording; exportRecording = null;
        if (snapshot == null) { refresh("无法恢复待导出的手势，请重新选择导出。"); return; }
        writeExport(data.getData(), snapshot, exportName);
    }

    private void writeExport(final Uri destination, final GestureRecording snapshot, final String savedName) {
        exporting = true; refresh("正在写入 JSON…");
        FILES.execute(() -> {
            String error = null;
            try (OutputStream bytes = getContentResolver().openOutputStream(destination, "wt")) {
                if (bytes == null) throw new java.io.IOException("无法打开目标文件");
                try (OutputStreamWriter writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
                    snapshot.writeJson(writer, savedName);
                }
            } catch (Exception failed) {
                error = failed.getMessage() == null ? failed.getClass().getSimpleName() : failed.getMessage();
            }
            final String problem = error;
            runOnUiThread(() -> {
                if (destroyed) return;
                exporting = false;
                refresh(problem == null ? "JSON 已导出。可用于修改手势代码或配置。" : "导出失败，请重新选择文件位置。");
                if (problem != null) Toast.makeText(this, problem, Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override protected void onPause() {
        boolean wasActive = recording != null && recording.isRecording();
        finish("activity-paused", true);
        if (!wasActive) saveDraft();
        if (canvas != null) canvas.stopPreview();
        super.onPause();
    }
    @Override public Object onRetainNonConfigurationInstance() {
        return new Retained(recording, name.getText().toString(), exportRecording, exportName);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pendingExport", exportRecording != null || deferredExportUri != null); state.putString("exportName", exportName);
        if (deferredExportUri != null) state.putString("deferredExportUri", deferredExportUri.toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (canvas != null) { canvas.removeCallbacks(limitStop); canvas.stopPreview(); }
        super.onDestroy();
    }

    private final class GestureCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Path> trails = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private final Map<Integer, Path> paths = new HashMap<>();
        private final Map<Integer, GestureRecording.Pointer> active = new HashMap<>();
        private final int[] palette = {0xff2563eb, 0xffd946ef, 0xff059669, 0xffea580c, 0xff0891b2, 0xffdc2626, 0xff7c3aed, 0xff4d7c0f};
        private boolean playing;
        private long previewStart;
        private int previewCursor;

        GestureCanvas() {
            super(RecorderActivity.this); setBackgroundColor(Color.WHITE);
            setContentDescription("多指手势录入画布"); setFocusable(true);
        }
        void reset() { trails.clear(); colors.clear(); paths.clear(); active.clear(); invalidate(); }
        void consume(GestureRecording.Frame frame) {
            for (GestureRecording.Pointer p : frame.pointers) {
                Path path = paths.get(p.id);
                boolean newContact = ("DOWN".equals(frame.action) || "POINTER_DOWN".equals(frame.action)) && p.id == frame.actionPointerId;
                if (path == null || newContact) {
                    path = new Path(); path.moveTo(p.x, p.y); paths.put(p.id, path);
                    trails.add(path); colors.add(palette[p.id % palette.length]);
                } else path.lineTo(p.x, p.y);
                active.put(p.id, p);
            }
            if ("UP".equals(frame.action) || "POINTER_UP".equals(frame.action)) {
                paths.remove(frame.actionPointerId); active.remove(frame.actionPointerId);
            }
            if ("CANCEL".equals(frame.action) || "UP".equals(frame.action)) { active.clear(); paths.clear(); }
        }
        void showAll() {
            reset(); if (recording != null) for (GestureRecording.Frame f : recording.getFrames()) consume(f);
            invalidate();
        }
        void beginPreview() {
            if (recording == null || recording.isRecording()) return;
            stopPreview(); reset(); playing = true; previewStart = SystemClock.uptimeMillis(); previewCursor = 0;
            refresh("正在按原始时间回放预览…"); postOnAnimation(replay);
        }
        void stopPreview() { playing = false; removeCallbacks(replay); }
        private final Runnable replay = new Runnable() {
            @Override public void run() {
                if (!playing || recording == null) return;
                long time = SystemClock.uptimeMillis() - previewStart;
                List<GestureRecording.Frame> frames = recording.getFrames();
                while (previewCursor < frames.size() && frames.get(previewCursor).timeMs <= time) consume(frames.get(previewCursor++));
                invalidate();
                if (time < recording.getDurationMs()) postOnAnimation(this);
                else { playing = false; refresh("回放完成，可导出 JSON。"); }
            }
        };

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (recording == null || !recording.isRecording()) return true;
            int action = event.getActionMasked(); String label;
            switch (action) {
                case MotionEvent.ACTION_DOWN: label = "DOWN"; break;
                case MotionEvent.ACTION_POINTER_DOWN: label = "POINTER_DOWN"; break;
                case MotionEvent.ACTION_MOVE: label = "MOVE"; break;
                case MotionEvent.ACTION_POINTER_UP: label = "POINTER_UP"; break;
                case MotionEvent.ACTION_UP: label = "UP"; break;
                case MotionEvent.ACTION_CANCEL: label = "CANCEL"; break;
                default: return true;
            }
            getParent().requestDisallowInterceptTouchEvent(true);
            try {
                if (action == MotionEvent.ACTION_MOVE) for (int h = 0; h < event.getHistorySize(); h++) {
                    List<GestureRecording.Pointer> samples = new ArrayList<>();
                    for (int p = 0; p < event.getPointerCount(); p++) samples.add(new GestureRecording.Pointer(event.getPointerId(p),
                            event.getHistoricalX(p, h), event.getHistoricalY(p, h), event.getHistoricalPressure(p, h)));
                    if (!append(event.getHistoricalEventTime(h), "MOVE", -1, true, samples)) return true;
                }
                List<GestureRecording.Pointer> samples = new ArrayList<>();
                for (int p = 0; p < event.getPointerCount(); p++) samples.add(new GestureRecording.Pointer(event.getPointerId(p), event.getX(p), event.getY(p), event.getPressure(p)));
                int id = action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL ? -1 : event.getPointerId(event.getActionIndex());
                append(event.getEventTime(), label, id, false, samples);
            } catch (IllegalArgumentException invalid) { finish("invalid-touch-sequence", true); }
            return true;
        }
        private boolean append(long time, String action, int id, boolean history, List<GestureRecording.Pointer> pointers) {
            int before = recording.getFrames().size();
            if (!recording.add(time, action, id, history, pointers)) { finish("sample-limit", true); return false; }
            if (recording.getFrames().size() > before) consume(recording.getFrames().get(before));
            invalidate(); return true;
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (recording != null && recording.isRecording() && (w != recording.widthPx || h != recording.heightPx)) finish("canvas-resized", true);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            paint.setStyle(Paint.Style.STROKE); paint.setColor(0xffe3e9f3); paint.setStrokeWidth(dp(1));
            for (int i = 1; i < 4; i++) {
                c.drawLine(getWidth() * i / 4f, 0, getWidth() * i / 4f, getHeight(), paint);
                c.drawLine(0, getHeight() * i / 4f, getWidth(), getHeight() * i / 4f, paint);
            }
            float sx = recording == null ? 1 : getWidth() / (float)recording.widthPx;
            float sy = recording == null ? 1 : getHeight() / (float)recording.heightPx;
            c.save(); c.scale(sx, sy);
            paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            for (int i = 0; i < trails.size(); i++) { paint.setColor(colors.get(i)); c.drawPath(trails.get(i), paint); }
            for (GestureRecording.Pointer p : active.values()) {
                paint.setColor(palette[p.id % palette.length]); paint.setStyle(Paint.Style.FILL); c.drawCircle(p.x, p.y, dp(10), paint);
                paint.setColor(Color.WHITE); paint.setTextSize(dp(11)); c.drawText(Integer.toString(p.id), p.x - dp(3), p.y + dp(4), paint);
            }
            c.restore();
            if (trails.isEmpty()) {
                paint.setStyle(Paint.Style.FILL); paint.setColor(0xff667085); paint.setTextSize(dp(16)); paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(recording != null && recording.isRecording() ? "在这里录入手势" : "点击开始录入后在这里操作", getWidth() / 2f, getHeight() / 2f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
    }
}
