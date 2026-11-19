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
    move-object/from16 v1, v24
    invoke-static {v1}, Lio/github/phiscript/assets/PhiraLibrary$Companion;->preparedBatch(Ljava/util/List;)Ljava/util/Map;
    move-result-object v1
    invoke-static {v5, v0, v1}, Lio/github/phiscript/input/PreparedChartStore;->afterPhiraImport(Landroid/content/Context;Lio/github/phiscript/assets/PhiraLibrary;Ljava/util/Map;)V

    return-object v0''')
        text=edit_method(text,'.method public final declared-synchronized importFiles(Landroid/content/Context;Ljava/util/List;Lkotlin/jvm/functions/Function1;)Lio/github/phiscript/assets/PhiraLibrary;',imported)
        # The native importer keeps its verified (Record, bytes) pairs in v24
        # through the successful index commit. Pass just their IDs and hashes.
        return text+'''
.method private static preparedBatch(Ljava/util/List;)Ljava/util/Map;
    .locals 5
    new-instance v0, Ljava/util/HashMap;
    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V
    invoke-interface {p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :next_record
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v2
    if-eqz v2, :batch_done
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Lkotlin/Pair;
    invoke-virtual {v2}, Lkotlin/Pair;->getFirst()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Lio/github/phiscript/assets/PhiraLibrary$Record;
    invoke-virtual {v2}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getId()Ljava/lang/String;
    move-result-object v3
    invoke-virtual {v2}, Lio/github/phiscript/assets/PhiraLibrary$Record;->getSha256()Ljava/lang/String;
    move-result-object v4
    invoke-virtual {v0, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    goto :next_record
    :batch_done
    return-object v0
.end method
'''
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
    def english(text):
        def entries(method):
            freeze='    invoke-static {v0}, Ljava/util/Collections;->unmodifiableMap(Ljava/util/Map;)Ljava/util/Map;'
            return replace_once(method,freeze,
                '    invoke-static {v0}, Lio/github/phiscript/i18n/AdditionalEnglish;->add(Ljava/util/Map;)V\n'+freeze)
        return edit_method(text,'.method public static entries()Ljava/util/Map;',entries)
    edit('EnglishCatalog',english,8,'i18n')
    for name,package,helper in [('GestureSettings','root','io/github/phiscript/root/StyledGestureSettings'),('LanguageSettings','i18n','io/github/phiscript/i18n/StyledLanguageSettings')]:
        def styled(text,helper=helper):
            signature='.method public static open(Landroid/app/Activity;)V'
            return edit_method(text,signature,lambda method:signature+'\n    .locals 0\n    invoke-static {p0}, L'+helper+';->open(Landroid/app/Activity;)V\n    return-void\n.end method')
        edit(name,styled,8,package)
