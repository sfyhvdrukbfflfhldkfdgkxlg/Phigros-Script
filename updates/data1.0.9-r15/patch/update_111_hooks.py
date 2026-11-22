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


def recognize_revisions(root):
    """Treat a stable -rN suffix as a fourth numeric version component."""
    path = root / "smali_classes8/io/github/phiscript/update/UpdateCatalog.smali"
    text = replace_once(path.read_text(encoding="utf-8"), "(?:[-+][A-Za-z0-9.-]+)?$",
                        "(?:-r([0-9]{1,6})|[-+][A-Za-z0-9.-]+)?$")

    def parts(method):
        method = replace_once(method, ".locals 3", ".locals 4")
        method = replace_once(method, ":cond_1\n    const/4 v0, 0x1", """:cond_1
    const/4 v3, 0x4
    invoke-virtual {p0, v3}, Ljava/util/regex/Matcher;->group(I)Ljava/lang/String;
    move-result-object v3
    if-nez v3, :parse_revision
    const/4 v3, 0x0
    goto :revision_ready
    :parse_revision
    invoke-static {v3}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I
    move-result v3
    :revision_ready
    const/4 v0, 0x1""")
        return replace_once(method, "filled-new-array {v0, v1, p0}, [I", "filled-new-array {v0, v1, p0, v3}, [I")

    text = edit_method(text, ".method private static parts(Ljava/lang/String;)[I", parts)
    text = edit_method(text, ".method public static compare(Ljava/lang/String;Ljava/lang/String;)I",
                       lambda body: replace_once(body, "const/4 v2, 0x3", "const/4 v2, 0x4"))
    path.write_text(text, encoding="utf-8")
