"""Isolated animation lifecycle host model. Import check(work, api) from the APK runner.

api must put the final APK's dex2jar classes first. Only Android animation/views,
RuntimeState and the dp helper are modeled; UiMotion itself is the actual APK code.
This suite makes no Android renderer, pixel layout or device performance claim.
"""
from run_r16 import compile_to, cp, run, sources


def check(work, api):
    models, tests = work/'ui-motion-models', work/'ui-motion-tests'
    compile_to(models,api,*sources('patch/tests/r20/uimock'))
    host=cp(tests,models,api)
    compile_to(tests,host,'patch/tests/r20/ui/UiMotionRegression.java')
    run('java','-cp',host,'io.github.phiscript.root.UiMotionRegression')
