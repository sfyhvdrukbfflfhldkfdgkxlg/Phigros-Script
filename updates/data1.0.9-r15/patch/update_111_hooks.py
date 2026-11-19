"""Offer public, stable GitHub releases in the in-app update checker."""
from r17_hooks import edit_method, replace_once


def apply(root):
    target = root / "smali_classes8/io/github/phiscript/update/UpdateCatalog.smali"
    signature = ".method public static latest(Ljava/lang/String;Ljava/lang/String;)"

    def skip_prereleases(method):
        test_draft = """    const-string v5, "draft"

    invoke-virtual {v4, v5, v0}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z

    move-result v5"""
        return replace_once(method, test_draft, test_draft + """
    if-nez v5, :skip_unpublished
    const-string v5, "prerelease"
    invoke-virtual {v4, v5, v0}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z
    move-result v5
    :skip_unpublished""")

    original = target.read_text(encoding="utf-8")
    target.write_text(edit_method(original, signature, skip_prereleases), encoding="utf-8")
