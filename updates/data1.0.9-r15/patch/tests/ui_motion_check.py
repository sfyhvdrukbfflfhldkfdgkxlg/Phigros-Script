"""Isolated animation lifecycle host model. Import check(work, api) from the APK runner.

api must put the final APK's dex2jar classes first. Only Android animation/views,
RuntimeState and the dp helper are modeled; UiMotion itself is the actual APK code.
This suite makes no Android renderer, pixel layout or device performance claim.
"""
import os
from zipfile import ZipFile
from run_r16 import compile_to, cp, run, sources


def check(work, api):
    models, tests = work/'ui-motion-models', work/'ui-motion-tests'
    compile_to(models,api,*sources('patch/tests/r20/uimock'),
               'patch/tests/mock/android/graphics/Insets.java','patch/tests/mock/android/os/Build.java')
    host=cp(tests,models,api)
    compile_to(tests,host,'patch/tests/r20/ui/UiMotionRegression.java')
    run('java','-cp',host,'io.github.phiscript.root.UiMotionRegression')
    # Older APKs still use this shared motion runner and predate edge-to-edge support.
    with ZipFile(str(api).split(os.pathsep)[0]) as classes:
        if 'io/github/phiscript/root/HomeInsets.class' in classes.namelist():
            compile_to(tests,host,'patch/tests/r20/ui/HomeInsetsRegression.java')
            run('java','-cp',host,'io.github.phiscript.root.HomeInsetsRegression')
        if 'io/github/phiscript/root/GlassStage.class' in classes.namelist():
            compile_to(tests,host,'patch/tests/r20/ui/DockInsetsRegression.java')
            run('java','-cp',host,'io.github.phiscript.root.DockInsetsRegression')
