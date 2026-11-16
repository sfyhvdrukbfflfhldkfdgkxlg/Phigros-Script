"""R20 preparation hooks on exact R15 native import/library methods."""
from pathlib import Path
from r17_hooks import edit_method,replace_once

def apply(root):
    root=Path(root)
    def edit(name,transform,index=6,package='assets'):
        path=root/f'smali_classes{index}/io/github/phiscript/{package}'/f'{name}.smali'
        original=path.read_text(encoding='utf-8');result=transform(original)
        assert result!=original,name
        path.write_text(result,encoding='utf-8')
    def phira(text):
        def load(method):
            return replace_once(method,'    move-result-object v3\n    :try_end_2','''    move-result-object v3
    iget-object v4, p0, Lio/github/phiscript/assets/PhiraLibrary;->context:Landroid/content/Context;
    invoke-static {v4, p0, p1, v3, v2}, Lio/github/phiscript/input/PreparedChartStore;->afterPhiraLoad(Landroid/content/Context;Lio/github/phiscript/assets/PhiraLibrary;Ljava/lang/String;Lio/github/phiscript/engine/Chart;[B)V
    :try_end_2''')
        return edit_method(text,'.method public final declared-synchronized load(Ljava/lang/String;)Lio/github/phiscript/engine/Chart;',load)
    edit('PhiraLibrary',phira)
    def companion(text):
        def imported(method):
            return replace_once(method,'    monitor-exit p0\n\n    return-object v0','''    monitor-exit p0
    invoke-static {v5, v0}, Lio/github/phiscript/input/PreparedChartStore;->afterPhiraImport(Landroid/content/Context;Lio/github/phiscript/assets/PhiraLibrary;)V

    return-object v0''')
        return edit_method(text,'.method public final declared-synchronized importFiles(Landroid/content/Context;Ljava/util/List;Lkotlin/jvm/functions/Function1;)Lio/github/phiscript/assets/PhiraLibrary;',imported)
    edit('PhiraLibrary$Companion',companion)
    def chart(text):
        def load(method):
            method=replace_once(method,'    move-result-object v0\n    :try_end_1','''    move-result-object v0
    invoke-static {v1, v0, v10}, Lio/github/phiscript/input/PreparedChartStore;->afterChartLoad(Lio/github/phiscript/assets/ChartLibrary;Lio/github/phiscript/engine/Chart;Ljava/lang/String;)V
    :try_end_1''')
            return replace_once(method,'    move-result-object v5\n\n    .line 65','''    move-result-object v5
    invoke-static {v1, v5, v4}, Lio/github/phiscript/input/PreparedChartStore;->afterChartLoad(Lio/github/phiscript/assets/ChartLibrary;Lio/github/phiscript/engine/Chart;Ljava/lang/String;)V

    .line 65''')
        return edit_method(text,'.method public final declared-synchronized load(Ljava/lang/String;Ljava/lang/String;)Lio/github/phiscript/engine/Chart;',load)
    edit('ChartLibrary',chart)
    for name,package,helper in [('GestureSettings','root','io/github/phiscript/root/StyledGestureSettings'),('LanguageSettings','i18n','io/github/phiscript/i18n/StyledLanguageSettings')]:
        def styled(text,helper=helper):
            signature='.method public static open(Landroid/app/Activity;)V'
            return edit_method(text,signature,lambda method:signature+'\n    .locals 0\n    invoke-static {p0}, L'+helper+';->open(Landroid/app/Activity;)V\n    return-void\n.end method')
        edit(name,styled,8,package)
