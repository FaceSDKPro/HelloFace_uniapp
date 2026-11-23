import hashlib
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
from prepare_example import SHARED_DIRECTORIES, prepare


class ExamplePreparationTests(unittest.TestCase):
    def workspace(self, root):
        for relative in SHARED_DIRECTORIES:
            (root / relative).mkdir(parents=True, exist_ok=True)
        for relative in ['sdk-artifacts.json', 'demo/face-reference.json', 'static/demo-face.png']:
            shutil.copyfile(ROOT / relative, root / relative)
        plugin = root / 'uni_modules/HelloFace-Verify'
        (plugin / 'readme.md').write_text('Current plugin instructions')
        example = root / 'demo-uni-app-x'
        (example / 'harmony-configs').mkdir(parents=True)
        (example / 'App.uvue').write_text('Independent app entry')
        (example / 'manifest.json').write_text('{"appid":"custom-demo"}')
        (example / 'harmony-configs/build-profile.json5').write_text('Local signing settings')
        return example

    def test_refresh_removes_deleted_files_but_preserves_sdk_backups_and_host_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            example = self.workspace(root)
            stale = example / 'uni_modules/HelloFace-Verify/notices/removed-note.md'
            stale.parent.mkdir(parents=True)
            stale.write_text('Deleted from the canonical plugin')
            old_sdk = example / 'uni_modules/HelloFace-Verify/utssdk/app-android/libs/hiface-0.8.14.aar'
            old_sdk.parent.mkdir(parents=True)
            old_sdk.write_bytes(b'old SDK backup fixture')
            digest = hashlib.sha256(old_sdk.read_bytes()).hexdigest()
            preserved = {path: path.read_bytes() for path in [example / 'App.uvue', example / 'manifest.json',
                         example / 'harmony-configs/build-profile.json5']}
            prepare(root)
            self.assertFalse(stale.exists())
            backup = example / '.sdk-cache/retired-sdk' / (digest + '-' + old_sdk.name)
            self.assertEqual(backup.read_bytes(), b'old SDK backup fixture')
            self.assertEqual((example / 'uni_modules/HelloFace-Verify/readme.md').read_text(), 'Current plugin instructions')
            self.assertEqual((example / 'demo/face-reference.uts').read_bytes(), (root / 'demo/face-reference.uts').read_bytes())
            for path, content in preserved.items():
                self.assertEqual(path.read_bytes(), content)

    def test_missing_source_does_not_delete_existing_generated_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            example = self.workspace(root)
            existing = example / 'uni_modules/HelloFace-Verify/keep.uts'
            existing.parent.mkdir(parents=True)
            existing.write_text('Existing generated plugin')
            (root / 'harmony-configs/AppScope').rmdir()
            with self.assertRaisesRegex(FileNotFoundError, 'harmony-configs/AppScope'):
                prepare(root)
            self.assertEqual(existing.read_text(), 'Existing generated plugin')
