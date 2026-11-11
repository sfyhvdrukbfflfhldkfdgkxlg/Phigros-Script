"""Reject regressions where a referenced original class/member is omitted."""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'lib'))
from dex_info import apk_dexes,aggregate,base_type
original=aggregate(apk_dexes(sys.argv[1]).values());final=aggregate(apk_dexes(sys.argv[2]).values())
missing_classes=sorted({base_type(t) for t in final['types']} & original['classes']-final['classes'])
missing_methods=sorted(final['methods'] & original['defined_methods']-final['defined_methods'])
missing_fields=sorted(final['fields'] & original['defined_fields']-final['defined_fields'])
result={'missing_original_classes':missing_classes,'missing_original_methods':missing_methods,'missing_original_fields':missing_fields,'final_classes':len(final['classes'])}
print(json.dumps(result,ensure_ascii=False,indent=2))
if missing_classes or missing_methods or missing_fields:sys.exit(1)
