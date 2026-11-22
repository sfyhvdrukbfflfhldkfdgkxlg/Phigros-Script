"""Forward the existing, authorized projection to the automatic recorder."""
from r17_hooks import replace_once


def apply(decoded):
    path = decoded / "smali_classes5/io/github/phiscript/capture/CaptureService.smali"
    store = "    iput-object v1, v7, Lio/github/phiscript/capture/CaptureService;->projection:Landroid/media/projection/MediaProjection;"
    hook = "    invoke-static {v7, v1}, Lio/github/phiscript/record/CaptureVideo;->authorized(Landroid/content/Context;Landroid/media/projection/MediaProjection;)V"
    path.write_text(replace_once(path.read_text(encoding="utf-8"), store, store + "\n\n" + hook), encoding="utf-8")
