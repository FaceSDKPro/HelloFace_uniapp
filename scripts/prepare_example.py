#!/usr/bin/env python3
"""Refresh the generated uvue dependencies from the canonical plugin and assets."""
from pathlib import Path
import json
import shutil
from generate_demo_reference import generate
from install_sdk import retire_obsolete

ROOT = Path(__file__).resolve().parents[1]
SHARED_DIRECTORIES = ('uni_modules/HelloFace-Verify', 'app-icons', 'demo', 'static',
                      'harmony-configs/entry', 'harmony-configs/AppScope')


def prepare(root=ROOT):
    root = Path(root)
    example = root / 'demo-uni-app-x'
    # 先核对输入；缺少源码或演示基准校验失败时保留现有副本。
    for relative in SHARED_DIRECTORIES:
        if not (root / relative).is_dir():
            raise FileNotFoundError('Missing shared directory: ' + relative)
    artifacts = json.loads((root / 'sdk-artifacts.json').read_text())['artifacts']
    generate(root)
    # 刷新前保存副本中的旧 SDK；刷新后再排除规范目录可能带入的过期库。
    retire_obsolete(artifacts, example)
    legacy = example / 'uni_modules/HelloFaceVerify'
    if legacy.is_dir():
        shutil.rmtree(legacy)
    for relative in SHARED_DIRECTORIES:
        replace_directory(root / relative, example / relative)
    retire_obsolete(artifacts, example)
    print('Prepared ' + str(example))


def replace_directory(source, target):
    # 只刷新列出的生成目录，宿主入口、manifest 和本地签名配置独立维护。
    if target.is_dir():
        shutil.rmtree(target)
    shutil.copytree(source, target)


if __name__ == '__main__':
    prepare()
