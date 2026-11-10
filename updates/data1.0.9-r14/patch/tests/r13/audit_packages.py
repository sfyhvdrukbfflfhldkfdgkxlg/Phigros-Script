from pathlib import Path
import hashlib, re, zipfile
root = Path("deliverables")
main = root / "PhigrosScript_data1.0.9-r14.apk"
recorder = root / "GestureCapture_1.0.apk"
def dex_bytes(apk):
    with zipfile.ZipFile(apk) as z:
        assert z.testzip() is None
        return b"".join(z.read(n) for n in z.namelist() if n.endswith(".dex"))
main_dex = dex_bytes(main)
rec_dex = dex_bytes(recorder)
assert b"Lio/github/phiscript/gesturecapture/" not in main_dex
assert all(x.encode() in main_dex for x in ("Lio/github/phiscript/record/FramePixels;", "Lio/github/phiscript/record/CaptureVideo;", "Lio/github/phiscript/root/GameLaunch;"))
assert b"Lio/github/phiscript/input/TouchService;" not in rec_dex
assert b"Lio/github/phiscript/gesturecapture/CaptureActivity;" in rec_dex
manifest = Path("r13/manifest.log").read_text()
assert '"data1.0.9-r14"' in manifest and "0x26" in manifest and '"org.flos.phirapro"' in manifest
badging = Path("r13/gesture-badging.log").read_text()
assert "io.github.phiscript.gesturecapture" in badging and "uses-permission" not in badging
c = Path("r13/smali/io/github/phiscript/capture/CaptureService.smali").read_text()
image = c.index("CaptureVideo;->image(")
assert c.index("->acquireLatestImage()") < image < c.index("->lastGrab:J", image) < c.index("->frameInterval:J", image)
assert c.count("->createVirtualDisplay(") == 1 and c.count("CaptureVideo;->captureScale(") == 1
assert "CaptureVideo;->frame(" not in c
signatures = [Path(path).read_text() for path in ("analysis/signature.txt","r13/base-signature.log","r13/recorder-signature.log")]
digests = [re.search(r"certificate SHA-256 digest: (\w+)", s)[1] for s in signatures]
assert len(set(digests)) == 1 and all("v2 scheme (APK Signature Scheme v2): true" in s and "v3 scheme (APK Signature Scheme v3): true" in s for s in signatures)
print("PASS signed APKs: distinct packages, standalone gesture trace model and permission-free manifest; recording hook before OCR throttle, one projection display; Phira Pro manifest query; signatures verified against the input APK.")
