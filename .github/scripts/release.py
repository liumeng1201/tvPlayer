"""Prepare release assets from the signed APK and publish them to GitHub."""

import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen


ASSETS = Path("release-assets")
APK = ASSETS / "tvPlayer.apk"
MANIFEST = ASSETS / "update.json"
ASSET_NAMES = {APK.name, MANIFEST.name}
TAG_PATTERN = re.compile(r"v([1-9][0-9]*)")


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def prepare(repo):
    (apk,) = Path("app/build/outputs/apk/release").glob("*.apk")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools"
    aapt = sorted(tools.glob("*/aapt2"))[-1]
    subprocess.run([aapt.with_name("apksigner"), "verify", str(apk)], check=True)
    badging = command(str(aapt), "dump", "badging", str(apk))
    package = re.search(
        r"^package: name='com\.lm\.android\.tv\.player' versionCode='([0-9]+)' versionName='([^']+)'",
        badging,
        re.MULTILINE,
    )
    expected_code = 301 + int(command("git", "rev-list", "--count", "HEAD"))
    if package is None or int(package.group(1)) != expected_code:
        raise ValueError("The signed APK does not match the expected app ID and Git version code")

    ASSETS.mkdir(exist_ok=True)
    shutil.copyfile(apk, APK)
    data = {
        "versionCode": expected_code,
        "versionName": package.group(2),
        "downloadUrl": f"https://github.com/{repo}/releases/download/v{expected_code}/{APK.name}",
    }
    MANIFEST.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared release v{expected_code} ({data['versionName']})", flush=True)


def request(repo, endpoint, *, method="GET", data=None, missing=False):
    req = Request(
        f"https://api.github.com/repos/{repo}/{endpoint}",
        data=json.dumps(data).encode() if data is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method=method,
    )
    try:
        with urlopen(req, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        if missing and error.code == 404:
            return None
        detail = error.read(1500).decode(errors="replace")
        raise RuntimeError(f"GitHub API {method} {endpoint} returned {error.code}: {detail}") from error


def find_release(repo, tag):
    page = 1
    while True:
        releases = request(repo, f"releases?per_page=100&page={page}")
        for release in releases:
            if release["tag_name"] == tag:
                return release
        if len(releases) < 100:
            return None
        page += 1


def ensure_tag(repo, tag, sha):
    ref = request(repo, f"git/ref/tags/{tag}", missing=True)
    if ref is None:
        request(repo, "git/refs", method="POST", data={"ref": f"refs/tags/{tag}", "sha": sha})
    elif request(repo, f"commits/{tag}")["sha"] != sha:
        raise ValueError(f"Tag {tag} already points to a different commit")


def ensure_assets(release):
    uploaded = {asset["name"] for asset in release["assets"] if asset["state"] == "uploaded"}
    if not ASSET_NAMES <= uploaded:
        raise ValueError("The release is missing an uploaded APK or update manifest")


def publish(repo, sha):
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    code, name = data["versionCode"], data["versionName"]
    tag = f"v{code}"
    ensure_tag(repo, tag, sha)
    release = find_release(repo, tag)
    if release is not None and not release["draft"]:
        if release["prerelease"]:
            raise ValueError(f"{tag} is already published as a prerelease")
        ensure_assets(release)
        print(f"{tag} is already published for {sha}")
        return
    if release is None:
        release = request(repo, "releases", method="POST", data={
            "tag_name": tag,
            "target_commitish": sha,
            "name": f"tvPlayer {name} ({code})",
            "body": f"Android TV APK\n\n- versionName: `{name}`\n- versionCode: `{code}`\n- Commit: `{sha}`",
            "draft": True,
            "prerelease": False,
        })

    subprocess.run([
        "gh", "release", "upload", tag, str(APK), str(MANIFEST),
        "--repo", repo, "--clobber",
    ], check=True)
    endpoint = f"releases/{release['id']}"
    ensure_assets(request(repo, endpoint))

    latest = request(repo, "releases/latest", missing=True)
    latest_tag = TAG_PATTERN.fullmatch(latest["tag_name"]) if latest else None
    make_latest = latest_tag is None or int(latest_tag.group(1)) < code
    result = request(repo, endpoint, method="PATCH", data={
        "draft": False,
        "prerelease": False,
        "make_latest": "true" if make_latest else "false",
    })
    print(result["html_url"])


if __name__ == "__main__":
    repository = os.environ["GITHUB_REPOSITORY"]
    if sys.argv[1:] == ["prepare"]:
        prepare(repository)
    elif sys.argv[1:] == ["publish"]:
        publish(repository, os.environ["GITHUB_SHA"])
    else:
        sys.exit("Usage: release.py prepare|publish")
