"""Prevent Windows checkout/archive conversions from creating CloudFront drift."""

import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
FUNCTION = "deployment/frontend/cloudfront/spa-fallback.js"


class CloudFrontFunctionBytesTest(unittest.TestCase):
    def test_checked_out_function_uses_lf(self):
        source = (ROOT / FUNCTION).read_bytes()
        self.assertNotIn(b"\r", source)
        self.assertTrue(source.endswith(b"\n"))

    @unittest.skipUnless(shutil.which("git"), "Requires git for a disposable archive fixture")
    def test_archive_preserves_lf_even_with_autocrlf_enabled(self):
        # No commits, network, user signing changes or writes to the real index.
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            env = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1")
            for name in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE"):
                env.pop(name, None)

            def git(*args):
                return subprocess.run(["git", *args], cwd=fixture, env=env,
                                      capture_output=True, check=True).stdout

            git("init", "--quiet")
            git("config", "core.autocrlf", "true")
            (fixture / ".gitattributes").write_bytes((ROOT / ".gitattributes").read_bytes())
            path = fixture / FUNCTION
            path.parent.mkdir(parents=True)
            expected = (ROOT / FUNCTION).read_bytes().replace(b"\r\n", b"\n")
            path.write_bytes(expected.replace(b"\n", b"\r\n"))
            git("add", ".gitattributes", FUNCTION)
            tree = git("write-tree").decode().strip()
            archive = git("archive", "--format=tar", tree, FUNCTION)
            with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
                self.assertEqual(tar.extractfile(FUNCTION).read(), expected)


if __name__ == "__main__":
    unittest.main()
