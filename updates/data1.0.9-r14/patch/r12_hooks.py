import re
from recording_hooks import apply as record_images
def wire(update,out,prelude):
    def method(s,signature,fn):
        a=s.index(signature);b=s.index('.end method',a);return s[:a]+fn(s[a:b])+s[b:]
    def once(s,old,new):
        assert s.count(old)==1,old
        return s.replace(old,new)
    def selected(f):
        return once(f,'    return-object v0','    invoke-static {v0}, Lio/github/phiscript/GamePackages;->resolve(Ljava/lang/String;)Ljava/lang/String;\n    move-result-object v0\n    return-object v0')
    update(4,'io/github/phiscript/PlayMode',lambda s:method(s,'.method public final getGamePackage()',selected))
    def timing(f):
        old='Lio/github/phiscript/AppSettings;->text(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;'
        new='Lio/github/phiscript/GameTiming;->snapshotText(Lio/github/phiscript/AppSettings;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;'
        for key in ('phiraRestartDelay','phiraGlobalOffset','touchOffset'):
            start=f.index('"'+key+'"');end=f.index(old,start)+len(old)
            block=f[start:end];assert block.count('invoke-virtual ')==1
            f=f[:start]+block.replace('invoke-virtual ','invoke-static ').replace(old,new)+f[end:]
        return f
    update(4,'io/github/phiscript/AppSettings',lambda s:method(s,'.method public final snapshot()',timing))
    def loader(f):
        f,count=re.subn(r'invoke-virtual \{[^,}]+, ([^}]+)\}, Lio/github/phiscript/engine/Chart\$Companion;->parse\(Ljava/lang/String;\)Lio/github/phiscript/engine/Chart;',r'invoke-static {\1}, Lio/github/phiscript/assets/SharedChartImport;->parseJson(Ljava/lang/String;)Lio/github/phiscript/engine/Chart;',f);assert count==2;return f
    update(6,'io/github/phiscript/assets/ChartLibrary',lambda s:method(s,'.method public final declared-synchronized load(',loader))
    update(6,'io/github/phiscript/assets/RpeNativeNormalizer',lambda s:method(s,'.method public final normalize(',lambda f:once(f,'    return-object v0','    invoke-static {p1, v0}, Lio/github/phiscript/assets/SharedChartImport;->preserveAreas(Lorg/json/JSONObject;Lorg/json/JSONObject;)V\n    return-object v0')))
    init='    invoke-direct {v1, v2}, Lio/github/phiscript/AppSettings;-><init>(Landroid/content/Context;)V'
    update(5,'io/github/phiscript/capture/CaptureService',lambda s:once(s,init,'    invoke-static {v2}, Lio/github/phiscript/GamePackages;->init(Landroid/content/Context;)V\n'+init))
    record_images(update)
    sig='.method public static forChart(Lio/github/phiscript/engine/Chart;ZZ)Ljava/util/List;'
    update(8,'io/github/phiscript/input/ChordPlanner',lambda s:prelude(s,sig,'''    invoke-static {}, Lio/github/phiscript/input/NoiseWindows;->current()Ljava/util/List;
    move-result-object v0
    if-eqz v0, :r12_full_chart
    return-object v0
    :r12_full_chart
'''))
