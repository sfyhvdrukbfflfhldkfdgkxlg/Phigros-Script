"""Resolve a selection from the native library's existing index, before any song data is read."""
from r17_hooks import replace_once


def apply(root):
    directory = root / "smali_classes6/io/github/phiscript/assets"
    def implement(name, method):
        path = directory / (name + ".smali")
        text = replace_once(path.read_text(encoding="utf-8"), ".super Ljava/lang/Object;",
                            ".super Ljava/lang/Object;\n.implements Lio/github/phiscript/assets/PreparedLibrary;")
        path.write_text(text + method, encoding="utf-8")

    implement("PhiraLibrary", """
.method public final openPrepared(Ljava/lang/String;Ljava/lang/String;)Lio/github/phiscript/engine/Chart;
    .locals 7
    iget-object v5, p0, Lio/github/phiscript/assets/PhiraLibrary;->records:Ljava/util/List;
    invoke-interface {v5}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v5
    :find_record
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z
    move-result v0
    if-eqz v0, :missing
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v6
    check-cast v6, Lio/github/phiscript/assets/PhiraLibrary$Record;
    invoke-virtual {v6}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getId()Ljava/lang/String;
    move-result-object v0
    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :find_record
    invoke-virtual {v6}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getLevel()Ljava/lang/String;
    move-result-object v0
    invoke-virtual {v0, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :missing
    iget-object v0, p0, Lio/github/phiscript/assets/PhiraLibrary;->context:Landroid/content/Context;
    invoke-virtual {v6}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getSha256()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v6}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getAspectRatio()D
    move-result-wide v2
    invoke-virtual {v6}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getForceAspectRatio()Z
    move-result v4
    invoke-static/range {v0 .. v4}, Lio/github/phiscript/input/PreparedChartStore;->openPhiraPlayback(Landroid/content/Context;Ljava/lang/String;DZ)Lio/github/phiscript/engine/Chart;
    move-result-object v0
    return-object v0
    :missing
    invoke-static {}, Lio/github/phiscript/assets/ExternalCharts;->selectionChanged()Ljava/lang/IllegalStateException;
    move-result-object v0
    throw v0
.end method
""")
    implement("ChartLibrary", """
.method public final openPrepared(Ljava/lang/String;Ljava/lang/String;)Lio/github/phiscript/engine/Chart;
    .locals 2
    iget-object v0, p0, Lio/github/phiscript/assets/ChartLibrary;->context:Landroid/content/Context;
    iget-object v1, p0, Lio/github/phiscript/assets/ChartLibrary;->reader:Lio/github/phiscript/assets/ChartSource;
    invoke-static {v0, v1, p0, p1, p2}, Lio/github/phiscript/assets/ExternalCharts;->openPreparedApk(Landroid/content/Context;Lio/github/phiscript/assets/ChartSource;Lio/github/phiscript/assets/ChartLibrary;Ljava/lang/String;Ljava/lang/String;)Lio/github/phiscript/engine/Chart;
    move-result-object v0
    return-object v0
.end method
""")
