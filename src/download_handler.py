"""
MultiDL desktop (PC) download handler.

It does the same job as the Android handler (android_download_handler.py) and
returns the same kind of result, so the Flet interface behaves like the app on
the phone:

    download(quality, url, progress_hook, output_path) -> {
        "success":   True / False,
        "title":     video title (or the error text when success is False),
        "file_path": path of the finished file ("" on failure),
    }

`progress_hook` is an optional function: progress_hook(percent: int, status: str)
`quality` is "HD", "Data Saver" or "Audio".

Unlike Android, the desktop version lets yt-dlp merge the streams itself, so
FFmpeg has to be installed on the computer (sudo apt install ffmpeg).
"""

import os
import shutil
import time
from urllib.parse import urlparse, urlunparse, parse_qsl, urlencode

import yt_dlp
from yt_dlp import version as ytdlp_version

# Where finished downloads are saved.
DOWNLOAD_DIR = os.path.expanduser("~/Downloads/multidl")

FFMPEG_MISSING = (
    "FFmpeg is not installed. Install it (for example: sudo apt install ffmpeg) "
    "and try again."
)

# Sites where the video ID lives in the ?query part of the link.
# For these hosts we keep ONLY the listed parameters (the ID) and drop the
# tracking junk. Every other site is cleaned exactly as before (query removed).
_QUERY_KEEP = {
    "facebook.com": {"v", "story_fbid", "id", "fbid"},
    "youtube.com": {"v"},
}


def clean_url(url):
    parsed = urlparse(url.strip())
    host = (parsed.hostname or "").lower()

    keep = set()
    for domain, keys in _QUERY_KEEP.items():
        if host == domain or host.endswith("." + domain):
            keep = keys
            break

    query = urlencode(
        [(k, v) for k, v in parse_qsl(parsed.query) if k in keep]
    )

    return urlunparse(
        (parsed.scheme, parsed.netloc, parsed.path, "", query, "")
    )


class _CollectLogger:
    """Collects yt-dlp warnings so failures can explain themselves."""

    def __init__(self):
        self.warnings = []

    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        self.warnings.append(str(msg))

    def error(self, msg):
        pass


def _newest_new_file(folder, started_at):
    """Fallback: the most recently modified finished file created since started_at."""
    best, best_time = None, 0
    for name in os.listdir(folder):
        if name.startswith(".") or name.endswith((".part", ".ytdl", ".temp")):
            continue
        path = os.path.join(folder, name)
        if not os.path.isfile(path):
            continue
        modified = os.path.getmtime(path)
        if modified >= started_at - 2 and modified > best_time:
            best, best_time = path, modified
    return best


def download(f, url, progress_hook=None, output_path=None):
    logger = _CollectLogger()

    try:
        output_path = os.path.expanduser(output_path or DOWNLOAD_DIR)
        os.makedirs(output_path, exist_ok=True)
        url = clean_url(url)

        if shutil.which("ffmpeg") is None:
            raise RuntimeError(FFMPEG_MISSING)

        last_percent = {"value": 0}

        def report(percent, status):
            if progress_hook is not None:
                # The bar only ever moves forward (yt-dlp's size estimates can
                # wobble, especially for streamed videos).
                percent = max(percent, last_percent["value"])
                last_percent["value"] = percent
                try:
                    progress_hook(
                        int(max(0, min(100, round(percent)))), str(status)
                    )
                except Exception:
                    # A UI callback failure must not cancel the download.
                    pass

        # yt-dlp downloads each stream (video, then audio) separately. Give each
        # stream an equal slice of 0-88%, then use 90-100% for merging/converting.
        stream_order = {}
        plan = {"streams": 1}

        class _Downloader(yt_dlp.YoutubeDL):
            """Learns how many streams yt-dlp is about to download."""

            def process_info(self, info_dict):
                plan["streams"] = max(
                    1, len(info_dict.get("requested_formats") or [])
                )
                return super().process_info(info_dict)

        def on_yt_dlp_progress(data):
            status = data.get("status")
            info = data.get("info_dict") or {}
            stream_count = plan["streams"]

            key = info.get("format_id") or data.get("filename")
            if key not in stream_order:
                stream_order[key] = len(stream_order)
            index = min(stream_order[key], stream_count - 1)

            audio_only = f == "Audio" or info.get("vcodec") == "none"
            label = "Downloading audio" if audio_only else "Downloading video"

            span = 88.0 / stream_count
            start = index * span

            if status == "downloading":
                downloaded = data.get("downloaded_bytes") or 0
                total = (
                    data.get("total_bytes")
                    or data.get("total_bytes_estimate")
                    or 0
                )
                if total > 0:
                    report(start + span * min(1.0, downloaded / total), label)
                else:
                    report(start, f"{label}…")
            elif status == "finished":
                report(start + span, f"{label} complete")

        def on_postprocess(data):
            if data.get("status") != "started":
                return
            name = data.get("postprocessor")
            if name == "Merger":
                report(90, "Merging video and audio…")
            elif name == "ExtractAudio":
                report(90, "Converting to MP3…")

        # Data Saver files get a label so they never overwrite an HD copy.
        label_suffix = " (Data Saver)" if f == "Data Saver" else ""

        options = {
            "noplaylist": True,
            "quiet": True,
            "no_warnings": True,
            "overwrites": True,
            "logger": logger,
            "outtmpl": os.path.join(
                output_path, f"%(title)s{label_suffix}.%(ext)s"
            ),
            "trim_file_name": 120,
            "progress_hooks": [on_yt_dlp_progress],
            "postprocessor_hooks": [on_postprocess],
            # Social sites drop connections now and then; retry instead of failing.
            "retries": 5,
            "fragment_retries": 5,
            "extractor_retries": 3,
            "socket_timeout": 30,
        }

        if f == "Audio":
            options["format"] = "bestaudio/best"
            options["postprocessors"] = [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "192",
                }
            ]
        elif f == "Data Saver":
            # NOT "worstaudio": yt-dlp always ranks language first, so the LAST
            # item is the least-preferred language (a dubbed track).
            # bestaudio + "+abr" = original language, lowest bitrate.
            # "/worst" fallback: some sites (often Facebook) only offer one
            # combined video+audio file.
            options["format"] = "worstvideo+bestaudio/worst"
            options["format_sort"] = ["+abr"]
            options["merge_output_format"] = "mp4"
        else:
            options["format"] = "bestvideo+bestaudio/best"
            options["merge_output_format"] = "mp4"

        started_at = time.time()
        report(0, "Starting download")

        with _Downloader(options) as ydl:
            info = ydl.extract_info(url, download=True)

        final_path = None
        for item in info.get("requested_downloads") or []:
            path = item.get("filepath")
            if path and os.path.isfile(path):
                final_path = path
                break

        if final_path is None:
            final_path = _newest_new_file(output_path, started_at)

        if final_path is None:
            raise FileNotFoundError("The downloaded file was not found.")

        report(100, "Complete")
        return {
            "success": True,
            "title": info.get("title", "Unknown video"),
            "file_path": final_path,
        }

    except Exception as e:
        # Put the real reason in the result: error text, any yt-dlp warnings
        # (e.g. "login required"), and which yt-dlp version is running.
        message = str(e).strip() or e.__class__.__name__
        notes = " | ".join(logger.warnings[-3:])
        if notes:
            message = f"{message} [{notes}]"

        return {
            "success": False,
            "title": f"{message[:500]} (yt-dlp {ytdlp_version.__version__})",
            "file_path": "",
        }
