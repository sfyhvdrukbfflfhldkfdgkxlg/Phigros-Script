"""Asserted hooks on the exact public R15 native DEX. Helpers live in classes12."""
import re
from pathlib import Path


def replace_once(text, old, new):
    assert text.count(old) == 1, old[:160]
    return text.replace(old, new, 1)


def edit_method(text, signature, transform):
    start = text.index(signature)
    end = text.index(".end method", start) + len(".end method")
    return text[:start] + transform(text[start:end]) + text[end:]


def apply(decoded):
    decoded = Path(decoded)
    def update(index, name, transform):
        path = decoded / ("smali_classes"+str(index)) / (name+".smali")
        original = path.read_text(encoding="utf-8")
        result = transform(original)
        assert result != original, name
        path.write_text(result, encoding="utf-8")

    def foreground(text):
        return edit_method(text, ".method public final getGameForeground()Z", lambda method:
            ".method public final getGameForeground()Z\n    .locals 1\n"
            "    invoke-static {p0}, Lio/github/phiscript/input/GameForeground;->isForeground(Lio/github/phiscript/input/TouchService;)Z\n"
            "    move-result v0\n    return v0\n.end method")
    update(7, "io/github/phiscript/input/TouchService", foreground)

    def activity(text):
        def start(method):
            # Online recognition can start with an empty local library. Skip the
            # old library/selected-id checks only for the explicitly enabled route.
            begin = method.index('    .local v0, "library"')
            at = method.index("    if-nez v0, :cond_3", begin)
            hook = """    invoke-static {p0}, Lio/github/phiscript/capture/OnlinePhiraPlayback;->shouldAllowWithoutLibrary(Landroid/content/Context;)Z
    move-result v1
    if-nez v1, :cond_8

"""
            return method[:at]+hook+method[at:]
        return edit_method(text, ".method private final startSession(Z)V", start)
    update(4, "io/github/phiscript/MainActivity", activity)

    def capture(text):
        # The service repeats the library-null check after projection approval.
        text = replace_once(text, """    :cond_9
    new-instance v0, Ljava/lang/IllegalStateException;""", """    :cond_9
    invoke-static {v7}, Lio/github/phiscript/capture/OnlinePhiraPlayback;->shouldAllowWithoutLibrary(Landroid/content/Context;)Z
    move-result v1
    if-eqz v1, :r17_need_phira_library
    const/4 v0, 0x0
    goto :goto_5
    :r17_need_phira_library
    new-instance v0, Ljava/lang/IllegalStateException;""")
        start = text.index("    new-instance v0, Lio/github/phiscript/capture/PhiraSessionEngine;")
        end = text.index("    goto :goto_0", start)
        bridge = """    iget-object v1, p1, Lio/github/phiscript/capture/CaptureService;->frames:Lio/github/phiscript/capture/FrameStore;
    move-object v2, p0
    move v3, p2
    iget-object v4, p1, Lio/github/phiscript/capture/CaptureService;->stop:Ljava/util/concurrent/atomic/AtomicBoolean;
    move-object v5, p3
    move-object v6, p1
    invoke-static/range {v1 .. v6}, Lio/github/phiscript/capture/OnlinePhiraPlayback;->run(Lio/github/phiscript/capture/FrameStore;Lio/github/phiscript/SessionSettings;ZLjava/util/concurrent/atomic/AtomicBoolean;Lio/github/phiscript/capture/RecognitionOverlay;Landroid/content/Context;)V

"""
        return text[:start]+bridge+text[end:]
    update(5, "io/github/phiscript/capture/CaptureService", capture)

    def engine(text):
        def entry(method):
            return replace_once(method, """    iget-object v0, v7, Lio/github/phiscript/capture/PhiraSessionEngine;->candidates:Ljava/util/List;

    check-cast v0, Ljava/util/Collection;

    invoke-interface {v0}, Ljava/util/Collection;->isEmpty()Z""", """    iget-object v0, v7, Lio/github/phiscript/capture/PhiraSessionEngine;->library:Lio/github/phiscript/assets/SessionChartSource;
    invoke-virtual {v0}, Lio/github/phiscript/assets/SessionChartSource;->isEmptyForSession()Z""")
        return edit_method(text, ".method public final run()V", entry)
    update(5, "io/github/phiscript/capture/PhiraSessionEngine", engine)
