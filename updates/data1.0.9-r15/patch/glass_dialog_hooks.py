"""Route original Android dialogs through the same window treatment as new screens."""
import re


def apply(decoded):
    builder = "Landroid/app/AlertDialog$Builder;"
    show = re.compile(r"invoke-virtual \{([vp]\d+)\}, " + re.escape(builder) +
                      r"->show\(\)Landroid/app/AlertDialog;")
    replacement = (r"invoke-static {\1}, Lio/github/phiscript/root/UiDesign;->showNative("
                   + builder + ")Landroid/app/AlertDialog;")
    for folder, name in (("smali_classes4", "MainActivity"), ("smali_classes8", "update/UpdateChecker")):
        path = decoded / folder / "io/github/phiscript" / (name + ".smali")
        updated, count = show.subn(replacement, path.read_text(encoding="utf-8"))
        if not count:
            raise ValueError("No native dialogs found in " + name)
        path.write_text(updated, encoding="utf-8")
