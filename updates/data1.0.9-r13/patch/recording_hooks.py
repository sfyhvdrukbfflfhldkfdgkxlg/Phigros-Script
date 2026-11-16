"""Feed the recorder the authorized projection image before OCR's 30/90ms gate."""
def apply(update):
    def capture(s):
        def replace(old,new):
            nonlocal s
            assert s.count(old)==1,old
            s=s.replace(old,new)
        hook='    iget-wide v11, v1, Lio/github/phiscript/capture/CaptureService;->lastGrab:J'
        replace(hook,'    invoke-static {v1, v7}, Lio/github/phiscript/record/CaptureVideo;->image(Landroid/content/Context;Landroid/media/Image;)V\n'+hook)
        replace('    invoke-static {v1, v14, v9, v10}, Lio/github/phiscript/record/CaptureVideo;->frame(Landroid/content/Context;Landroid/graphics/Bitmap;J)V','')
        start='    iget-wide v0, v7, Lio/github/phiscript/capture/CaptureService;->maxCaptureSide:D'
        a=s.index(start);b=s.index('    move-result-wide v19',a)
        return s[:a]+start+'\n    invoke-static {v7, v8, v9, v0, v1}, Lio/github/phiscript/record/CaptureVideo;->captureScale(Landroid/content/Context;IID)D\n'+s[b:]
    update(5,'io/github/phiscript/capture/CaptureService',capture)
