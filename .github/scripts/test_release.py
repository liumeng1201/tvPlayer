import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import release


REPO = "owner/tvPlayer"
SHA = "a" * 40
UPLOADS = {"id": 1, "assets": [{"name": name, "state": "uploaded"} for name in release.ASSET_NAMES]}


class PublishTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        manifest = Path(directory.name) / "update.json"
        manifest.write_text(json.dumps({"versionCode": 340, "versionName": "3.0"}))
        self.addCleanup(patch.stopall)
        patch.object(release, "MANIFEST", manifest).start()
        self.api = patch.object(release, "request").start()
        self.upload = patch.object(release.subprocess, "run").start()

    def test_creates_draft_before_upload_and_promotes_only_after_assets_exist(self):
        self.api.side_effect = [None, {}, [], {"id": 1}, UPLOADS, None, {"html_url": "example"}]

        release.publish(REPO, SHA)

        calls = self.api.call_args_list
        self.assertEqual(calls[1].kwargs["data"], {"ref": "refs/tags/v340", "sha": SHA})
        self.assertTrue(calls[3].kwargs["data"]["draft"])
        self.upload.assert_called_once()
        self.assertEqual(calls[-1].kwargs["data"]["make_latest"], "true")

    def test_retry_reuses_draft_and_keeps_a_newer_latest_release(self):
        draft = {"id": 1, "tag_name": "v340", "draft": True}
        self.api.side_effect = [
            {}, {"sha": SHA}, [draft], UPLOADS, {"tag_name": "v341"}, {"html_url": "example"},
        ]

        release.publish(REPO, SHA)

        self.upload.assert_called_once()
        self.assertEqual([call.kwargs["method"] for call in self.api.call_args_list
                          if "method" in call.kwargs], ["PATCH"])
        self.assertEqual(self.api.call_args.kwargs["data"]["make_latest"], "false")

    def test_rerun_of_published_tag_keeps_the_assets(self):
        published = dict(UPLOADS, tag_name="v340", draft=False, prerelease=False)
        self.api.side_effect = [{}, {"sha": SHA}, [published]]

        release.publish(REPO, SHA)

        self.upload.assert_not_called()
        self.assertEqual(self.api.call_count, 3)

    def test_missing_asset_leaves_release_unpublished(self):
        draft = {"id": 1, "tag_name": "v340", "draft": True}
        self.api.side_effect = [{}, {"sha": SHA}, [draft], {"assets": []}]

        with self.assertRaisesRegex(ValueError, "missing an uploaded"):
            release.publish(REPO, SHA)

        self.assertFalse(any(call.kwargs.get("method") == "PATCH" for call in self.api.call_args_list))
