"""Route the existing authorized VirtualDisplay through the GPU recorder fanout.

No second createVirtualDisplay call or second use of a MediaProjection token.
Retain the ImageReader capture callback and its acquire-time uptime timestamp.
"""
from pathlib import Path

def patch(app: Path):
    source=app/'smali_classes5/io/github/phiscript/capture/CaptureService.smali'
    text=source.read_text()
    hooks=(
        ('invoke-virtual/range {v10 .. v18}, Landroid/media/projection/MediaProjection;->createVirtualDisplay(Ljava/lang/String;IIIILandroid/view/Surface;Landroid/hardware/display/VirtualDisplay$Callback;Landroid/os/Handler;)Landroid/hardware/display/VirtualDisplay;',
         'invoke-static/range {v10 .. v18}, Lio/github/phiscript/record/CaptureVideo;->createVirtualDisplay(Landroid/media/projection/MediaProjection;Ljava/lang/String;IIIILandroid/view/Surface;Landroid/hardware/display/VirtualDisplay$Callback;Landroid/os/Handler;)Landroid/hardware/display/VirtualDisplay;'),
        ('invoke-virtual {v2, v1, v5, v3}, Landroid/hardware/display/VirtualDisplay;->resize(III)V',
         'invoke-static {v2, v1, v5, v3}, Lio/github/phiscript/record/CaptureVideo;->resize(Landroid/hardware/display/VirtualDisplay;III)V'),
        ('invoke-virtual {v2, v3}, Landroid/hardware/display/VirtualDisplay;->setSurface(Landroid/view/Surface;)V',
         'invoke-static {v2, v3}, Lio/github/phiscript/record/CaptureVideo;->setSurface(Landroid/hardware/display/VirtualDisplay;Landroid/view/Surface;)V'),
    )
    for old,new in hooks:
        assert text.count(old)==1,(source,old,text.count(old))
        text=text.replace(old,new)
    # Keep ImageReader consumption independent from configure()/GL backpressure.
    # The callback body, acquire-time uptime, and packed Bitmap pixels are unchanged.
    import re
    text,count=re.subn(r'invoke-virtual \{([^}]+)\}, Landroid/media/ImageReader;->setOnImageAvailableListener\(Landroid/media/ImageReader\$OnImageAvailableListener;Landroid/os/Handler;\)V',
        r'invoke-static {\1}, Lio/github/phiscript/record/CaptureVideo;->setReaderListener(Landroid/media/ImageReader;Landroid/media/ImageReader$OnImageAvailableListener;Landroid/os/Handler;)V',text)
    assert count==3,('ImageReader listeners',count)
    text,count=re.subn(r'invoke-virtual \{([^}]+)\}, Landroid/media/ImageReader;->close\(\)V',
        r'invoke-static {\1}, Lio/github/phiscript/record/CaptureVideo;->closeReader(Landroid/media/ImageReader;)V',text)
    assert count==2,('ImageReader cleanup',count)
    source.write_text(text)
    print('Recording: existing capture display now fans out on GPU; OCR Bitmap callback retained without video copies.')
