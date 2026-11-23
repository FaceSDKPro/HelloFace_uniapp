#!/usr/bin/env python3
"""Copy the shared plugin and app icon assets into the uvue example."""
from pathlib import Path
import json
import shutil
from generate_demo_reference import generate
from install_sdk import retire_obsolete

root = Path(__file__).resolve().parents[1]
generate(root)
example = root / 'demo-uni-app-x'
plugin_id = 'HelloFace-Verify'
destination = example / 'uni_modules' / plugin_id
# This example directory is generated; remove the former ID to avoid duplicate modules.
legacy = destination.parent / 'HelloFaceVerify'
if legacy.is_dir():
    shutil.rmtree(legacy)
shutil.copytree(root / 'uni_modules' / plugin_id, destination, dirs_exist_ok=True)
# 增量复制不会删除旧 AAR/HAR；按锁定清单移出，避免升级后同时联编两个 SDK。
retire_obsolete(json.loads((root / 'sdk-artifacts.json').read_text())['artifacts'], example)
# 这些目录完全由规范资源生成，替换副本以清掉已删除文件；签名配置由宿主单独维护。
for relative in ('app-icons', 'demo', 'static', 'harmony-configs/entry', 'harmony-configs/AppScope'):
    target = example / relative
    if target.is_dir():
        shutil.rmtree(target)
    shutil.copytree(root / relative, target)
print('Prepared ' + str(example))
