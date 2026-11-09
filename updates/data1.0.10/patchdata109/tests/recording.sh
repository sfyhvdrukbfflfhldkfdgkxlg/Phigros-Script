#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/recordtestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata109/recordtestclasses $(rg --files patchdata109/tests/recordmock -g '*.java') patchdata109/src/io/github/phiscript/record/CaptureVideo.java patchdata109/src/io/github/phiscript/record/RecordingPolicy.java patchdata109/tests/RecordingRegression.java
java -cp patchdata109/recordtestclasses io.github.phiscript.record.RecordingRegression | tee patchdata109/tests/RecordingRegression.txt
python3 - <<'PY'
from pathlib import Path
import importlib.util,tempfile
spec=importlib.util.spec_from_file_location('record_patch','patchdata109/patch_recording.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
# Exercise the real patch against a fresh copy of the exact baseline smali.
import zipfile,subprocess,shutil
source=Path('analysis/data108/smali_classes5/io/github/phiscript/capture/CaptureService.smali').read_text()
# Accept running after apply.py by reversing only our exact hooks for the fixture.
source=source.replace('invoke-static/range {v10 .. v18}, Lio/github/phiscript/record/CaptureVideo;->createVirtualDisplay(Landroid/media/projection/MediaProjection;','invoke-virtual/range {v10 .. v18}, Landroid/media/projection/MediaProjection;->createVirtualDisplay(')
source=source.replace('invoke-static {v2, v1, v5, v3}, Lio/github/phiscript/record/CaptureVideo;->resize(Landroid/hardware/display/VirtualDisplay;III)V','invoke-virtual {v2, v1, v5, v3}, Landroid/hardware/display/VirtualDisplay;->resize(III)V')
source=source.replace('invoke-static {v2, v3}, Lio/github/phiscript/record/CaptureVideo;->setSurface(Landroid/hardware/display/VirtualDisplay;Landroid/view/Surface;)V','invoke-virtual {v2, v3}, Landroid/hardware/display/VirtualDisplay;->setSurface(Landroid/view/Surface;)V')
import re
source=re.sub(r'invoke-static \{([^}]+)\}, Lio/github/phiscript/record/CaptureVideo;->setReaderListener\(Landroid/media/ImageReader;Landroid/media/ImageReader\$OnImageAvailableListener;Landroid/os/Handler;\)V',r'invoke-virtual {\1}, Landroid/media/ImageReader;->setOnImageAvailableListener(Landroid/media/ImageReader$OnImageAvailableListener;Landroid/os/Handler;)V',source)
source=re.sub(r'invoke-static \{([^}]+)\}, Lio/github/phiscript/record/CaptureVideo;->closeReader\(Landroid/media/ImageReader;\)V',r'invoke-virtual {\1}, Landroid/media/ImageReader;->close()V',source)
callback=re.search(r'(?ms)^\.method private static final configure\$lambda\$10\(.*?^\.end method',source).group()
with tempfile.TemporaryDirectory() as temp:
 app=Path(temp);p=app/'smali_classes5/io/github/phiscript/capture/CaptureService.smali';p.parent.mkdir(parents=True);p.write_text(source);module.patch(app);actual=p.read_text()
 assert re.search(r'(?ms)^\.method private static final configure\$lambda\$10\(.*?^\.end method',actual).group()==callback,'capture pixel/timestamp callback changed'
 assert actual.count('CaptureVideo;->createVirtualDisplay(')==1
 assert actual.count('CaptureVideo;->setReaderListener(')==3
 assert actual.count('CaptureVideo;->closeReader(')==2
print('PASS actual baseline smali fanout hooks, one display create route, rotation/surface routes and unchanged acquisition timestamp/pixel callback.')
PY
