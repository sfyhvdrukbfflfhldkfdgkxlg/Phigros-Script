"""Set the native fallback delays and use the session's chart-offset policy."""
from r17_hooks import replace_once


def apply(root):
    def edit(index, name, transform):
        path = root / f"smali_classes{index}/io/github/phiscript/{name}.smali"
        path.write_text(transform(path.read_text(encoding="utf-8")), encoding="utf-8")

    # Kotlin's synthetic constructor supports callers that omit the retry delay.
    edit(4, "SessionSettings", lambda text: replace_once(text,
        "    const/16 v1, 0x2bc\n\n    move/from16 v17, v1",
        "    invoke-static {v15}, Lio/github/phiscript/GameTiming;->defaultDelay(Lio/github/phiscript/PlayMode;)I\n"
        "    move-result v1\n\n    move/from16 v17, v1"))

    def offsets(text):
        source = "Lio/github/phiscript/assets/SessionChartSource;"
        library = "Lio/github/phiscript/capture/PhiraSessionEngine;->library:" + source
        getter = "Lio/github/phiscript/engine/Chart;->getOffsetSeconds()D"
        policy = source + "->restartChartOffset(Lio/github/phiscript/engine/Chart;)D"
        # Validate and start with exactly the same offset on both clock calls.
        text = replace_once(text, "    invoke-virtual {v4}, " + getter,
            "    iget-object v0, v7, " + library + "\n    invoke-virtual {v0, v4}, " + policy)
        return replace_once(text, "    invoke-virtual/range {p3 .. p3}, " + getter,
            "    iget-object v0, v1, " + library + "\n"
            "    move-object/from16 v34, v0\n    move-object/from16 v35, p3\n"
            "    invoke-virtual/range {v34 .. v35}, " + policy)

    edit(5, "capture/PhiraSessionEngine", offsets)
    edit(8, "root/ManualChartControl", lambda text: replace_once(text,
        '"phigros_manual_delay_ms"\n\n    const-string v4, "1000"',
        '"phigros_manual_delay_ms"\n\n    const-string v4, "1700"'))
