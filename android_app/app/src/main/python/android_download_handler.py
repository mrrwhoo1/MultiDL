"""
Date [24th-Nov-2026]
-Adding playlist support now.

"""

import os
import json
import glob  # Used to find patterns in file names like *.mp3..
import yt_dlp
from urllib.parse import urlparse, urlunparse, parse_qsl, urlencode
from yt_dlp import version as ytdlp_version
from yt_dlp.utils import sanitize_filename


# ---- Pause / Cancel support -------------------------------------------------
# Kotlin writes "pause" or "cancel" into _stop_requests for a download id.
# yt-dlp calls our progress hook many times a second, and the hook checks this
# dictionary. If it finds a request it raises DownloadStopped, which interrupts
# the download. A paused download keeps its .part file so it can continue later.
_stop_requests = {}
_partial_files = {}  # download id -> the .part files yt-dlp is writing for it


class DownloadStopped(Exception):
    """Raised on purpose to interrupt yt-dlp when the user pauses or cancels."""


def request_stop(download_id, reason):
    """Called from Kotlin. reason is "pause" or "cancel"."""
    _stop_requests[download_id] = reason


def _delete_partial_files(download_id):
    for path in _partial_files.pop(download_id, set()):
        for leftover in (path, path + ".ytdl"):
            if os.path.isfile(leftover):
                os.remove(leftover)


def discard_partial(download_id):
    """Called from Kotlin when a PAUSED download is cancelled (nothing is running)."""
    _stop_requests.pop(download_id, None)
    _delete_partial_files(download_id)


# Seems like during clean url it was messing with the facebook links horribly, made this to leep certain characters i saw appear alot in links.
_QUERY_KEEP = {
    "facebook.com": {"v", "story_fbid", "id", "fbid"},
    "youtube.com": {"v"},
}


def clean_url(url):
    parsed = urlparse(
        url.strip()
    )  # just removes the white spaces you might pick up in links.
    host = (
        parsed.hostname or ""
    ).lower()  # gets the websites host name like youtube.com

    keep = set()
    for domain, keys in _QUERY_KEEP.items():
        if host == domain or host.endswith("." + domain):  # self explainatory really.
            # e.g if after host we get wwww.facebook.com then run the if block, domain[0] is just facebook.com so the first styatement is false
            # but then we go to the or statemtn which says if www.facebook.com (host) == .+DOMAIN which is .facebook.com that passes as true cause it wont care about the www.

            keep = keys  # if it washost was facebook then keep = {"v", "story_fbid", "id", "fbid"}.
            break

    filtered_params = []

    for k, v in parse_qsl(
        parsed.query
    ):  # parse_qsl = Take a URL query string and turn it into a list of key-value pairs.
        # [
        #     ("v", "abc123"),
        #     ("t", "30")
        # ]
        # and for the parsed.query urlparse(url) gives a parse result conatining multiple stuff like scheme, path, params, query etc so saying
        # parsed.query just says give me the v=abc123&t=30 part of the url as query holds that part of the link. so {v : abc123...}
        if (
            k in keep
        ):  # checks if that key 'v' is in keep and if it it then it is added to the filtered_params list.
            filtered_params.append((k, v))

    query = urlencode(
        filtered_params
    )  # opposite of parse_qsl, it gets the ("v", "abc123") and makes it back to v=abc123

    # Just incase who ever reads this one day forgets that earlier urlparse(url) has slotes like
    """
    (
    "https",            # scheme
    "www.youtube.com",  # netloc
    "/watch",           # path
    "",                 # params
    "v=abc123",         # query
    ""                  # fragment
)
    """
    return urlunparse(
        (parsed.scheme, parsed.netloc, parsed.path, "", query, "")
    )  # this gives back the full cleaned link.


# if still lost loook at this https://www.youtube.com/watch?v=abc123&t=420&utm_source=google, everything before the & uniqly identifies a youtube video, keeping the whole thing would cause issues sometimes BUt from my testing youtube is a lot more forgiving, this function was primary used for FACEBOOK as this one is a completely different story ( i know the keep dictionary only has facebook and youtube for now, might expand it later if i notice more issues on the otehr sites...especially data saver as its caused me the most issues.)


def safe_filename(title, fallback):
    """Turn a video title into a safe file name (removes / : * ? etc.)."""
    name = sanitize_filename(title or "", restricted=False)
    name = name.strip(" .")[:120].rstrip(" .")
    return name or fallback


class _CollectLogger:
    """Collects yt-dlp warnings so failures can explain themselves. the method names are identified by ytdlp, changing them results in errors."""

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


def download(f, url, progress_hook=None, output_path=None, download_id=""):
    logger = _CollectLogger()

    # A fresh start or a resume: forget any old stop request.
    # setdefault (not "= set()") so a resume keeps the .part files it already knows about.
    _stop_requests.pop(download_id, None)
    _partial_files.setdefault(download_id, set())

    try:
        if not output_path:
            raise ValueError("Android download directory was not provided.")

        os.makedirs(output_path, exist_ok=True)
        url = clean_url(url)

        common_options = {
            "noplaylist": True,
            "quiet": True,
            "no_warnings": True,
            "overwrites": True,
            "logger": logger,  # This is where the above class is used to provide warnings.
            # Social sites drop connections now and then; retry instead of failing.
            "retries": 5,
            "fragment_retries": 5,
            "extractor_retries": 3,
            "socket_timeout": 30,
        }

        # The Kotlin listener is an object with onProgress(percent, status),
        # not a Python callable. Translate yt-dlp's progress dictionaries
        # into calls to that listener instead of passing it directly to yt-dlp.
        def report(percent, status):
            if progress_hook is not None:
                try:
                    progress_hook.onProgress(
                        int(max(0, min(100, round(percent)))),
                        str(status),
                    )
                except Exception:
                    # A UI callback failure must not cancel the download.
                    pass

        def check_stop():
            if _stop_requests.get(download_id):
                raise DownloadStopped()

        def download_stream(
            format_selector, suffix, start_percent, end_percent, label, sort=None
        ):
            # Also check between the video and audio streams, not only inside the hook.
            check_stop()

            options = common_options.copy()
            options["format"] = format_selector
            if sort:
                options["format_sort"] = sort
            options["outtmpl"] = os.path.join(output_path, f"%(id)s_{suffix}.%(ext)s")

            def on_yt_dlp_progress(data):
                # Remember the .part file so Cancel can delete it later.
                if data.get("tmpfilename"):
                    _partial_files[download_id].add(data["tmpfilename"])

                # Pause / Cancel: stop yt-dlp right here.
                check_stop()

                status = data.get("status")

                if status == "downloading":
                    downloaded = data.get("downloaded_bytes") or 0
                    total = (
                        data.get("total_bytes") or data.get("total_bytes_estimate") or 0
                    )

                    if total > 0:
                        fraction = min(1.0, downloaded / total)
                        percent = (
                            start_percent + (end_percent - start_percent) * fraction
                        )
                        report(percent, label)
                    else:
                        report(start_percent, f"{label}…")

                elif status == "finished":
                    report(end_percent, f"{label} complete")

            options["progress_hooks"] = [on_yt_dlp_progress]

            with yt_dlp.YoutubeDL(options) as ydl:
                info = ydl.extract_info(url, download=True)

                downloads = info.get("requested_downloads") or []
                fmt = downloads[0] if downloads else {}

                paths = [
                    item.get("filepath") or item.get("filename") for item in downloads
                ]
                paths = [path for path in paths if path and os.path.isfile(path)]

                if paths:
                    return paths[0], info, fmt

                expected = ydl.prepare_filename(info)
                if os.path.isfile(expected):
                    return expected, info, fmt

                pattern = os.path.join(output_path, f"{info['id']}_{suffix}.*")
                matches = [
                    path
                    for path in glob.glob(pattern)
                    if os.path.isfile(path) and not path.endswith((".part", ".ytdl"))
                ]

                if matches:
                    return max(matches, key=os.path.getmtime), info, fmt

                raise FileNotFoundError(f"Downloaded {suffix} stream was not found.")

        # Audio mode: preserve the working MP3 conversion.
        if f == "Audio":
            report(0, "Starting audio download")
            audio_path, info, _ = download_stream(
                "bestaudio/best", "audio", 0, 88, "Downloading audio"
            )

            report(90, "Converting to MP3…")
            from com.mrrwhoo1.multidl import FfmpegBridge

            mp3_path = os.path.splitext(audio_path)[0] + ".mp3"
            command = (
                f'-y -i "{audio_path}" -vn -codec:a libmp3lame -b:a 192k "{mp3_path}"'
            )

            result = str(FfmpegBridge.execute(command))

            if (
                not result.startswith("SUCCESS")
                or not os.path.isfile(mp3_path)
                or os.path.getsize(mp3_path) == 0
            ):
                raise RuntimeError(f"MP3 conversion failed: {result[-700:]}")

            if os.path.abspath(audio_path) != os.path.abspath(mp3_path):
                os.remove(audio_path)

            # Rename the finished MP3 from "<id>_audio.mp3" to "<title>.mp3".
            final_path = os.path.join(
                output_path,
                safe_filename(info.get("title"), info.get("id", "audio")) + ".mp3",
            )
            if os.path.abspath(mp3_path) != os.path.abspath(final_path):
                os.replace(mp3_path, final_path)

            report(100, "Complete")
            return {
                "success": True,
                "title": info.get("title", "Unknown audio"),
                "file_path": final_path,
            }

        # Download separate video and audio streams.
        label = " (Data Saver)" if f == "Data Saver" else ""

        if f == "Data Saver":
            # "/worst" fallback: some sites (often Facebook) only offer one
            # combined video+audio file, which the video-only selectors miss
            video_format = "worstvideo/worst"
            # NOT "worstaudio": yt-dlp always ranks language first, so the
            # LAST item is the least-preferred language (a dubbed track).
            # bestaudio + "+abr" = original language, lowest bitrate.
            # The previous code especially for youtube would end up giving me the low res video in a different language. apparently this should solve it, thanks claude :)
            audio_format = "bestaudio/best"
            audio_sort = ["+abr"]
        else:
            video_format = "bestvideo/best"
            audio_format = "bestaudio/best"
            audio_sort = None

        report(0, "Starting video download")
        video_path, info, video_fmt = download_stream(
            video_format, "video", 0, 44, "Downloading video"
        )

        title_name = safe_filename(info.get("title"), info.get("id", "video"))

        """
        TO BE HONEST I MYSELF CAN BARELY UNDERSTAND MOST OF THE CODE BELOW, AUDIO HAS NEVER BEEN MY STRONG SUIT. TRIED TO EXPLAIN AS BEST AS I COULD.
        """

        # If the "video" file already contains sound, there is nothing to merge.
        # Only skip when yt-dlp reports REAL codecs for both streams; if the
        # codecs are unknown we merge as before so we never save a silent video.
        vcodec = video_fmt.get("vcodec")
        acodec = video_fmt.get("acodec")
        already_has_audio = vcodec not in (None, "none") and acodec not in (
            None,
            "none",
        )

        if already_has_audio:
            report(95, "Finishing…")
            extension = os.path.splitext(video_path)[1] or ".mp4"
            final_path = os.path.join(output_path, title_name + label + extension)
            if os.path.abspath(video_path) != os.path.abspath(final_path):
                os.replace(video_path, final_path)

            report(100, "Complete")
            return {
                "success": True,
                "title": info.get("title", "Unknown video"),
                "file_path": final_path,
            }

        audio_path, _, _ = download_stream(
            audio_format, "audio", 45, 88, "Downloading audio", sort=audio_sort
        )

        report(90, "Merging video and audio…")
        from com.mrrwhoo1.multidl import FfmpegBridge

        # MKV can contain a wider range of video/audio codecs without
        # requiring a potentially slow video re-encode. BUT this will probably affect most older android phones.
        # until then i will keep it.
        suffix = "data_saver" if f == "Data Saver" else "hd"
        merged_path = os.path.join(
            output_path, f"{info.get('id', 'video')}_{suffix}.mkv"
        )

        command = (
            f'-y -i "{video_path}" -i "{audio_path}" '
            f"-map 0:v:0 -map 1:a:0 "
            f'-c copy "{merged_path}"'
        )

        result = str(FfmpegBridge.execute(command))

        if (
            not result.startswith("SUCCESS")
            or not os.path.isfile(merged_path)
            or os.path.getsize(merged_path) == 0
        ):
            raise RuntimeError(f"Video/audio merge failed: {result[-700:]}")

        # Delete the separate streams only after a successful merge, did this obviosuly to save storage.
        for path in (video_path, audio_path):
            if os.path.abspath(path) != os.path.abspath(merged_path):
                if os.path.isfile(path):
                    os.remove(path)

        # Rename "<id>_hd.mkv" to "<title>.mkv" (Data Saver gets a label
        # so it never overwrites an HD copy of the same video).
        final_path = os.path.join(output_path, title_name + label + ".mkv")
        if os.path.abspath(merged_path) != os.path.abspath(final_path):
            os.replace(merged_path, final_path)

        report(100, "Complete")
        return {
            "success": True,
            "title": info.get("title", "Unknown video"),
            "file_path": final_path,
        }

    except Exception as e:
        # Was this a deliberate Pause / Cancel? Check the flag instead of the exception
        # type, because yt-dlp sometimes re-wraps errors raised inside hooks.
        stop_reason = _stop_requests.pop(download_id, None)
        if stop_reason:
            if stop_reason == "cancel":
                _delete_partial_files(download_id)
            return {
                "success": False,
                "stopped": stop_reason,
                "title": "",
                "file_path": "",
            }

        # Put the real reason on screen: error text, any yt-dlp warnings
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


# ---- Playlist support -------------------------------------------------------
# Kotlin calls list_playlist() with the link exactly as it was pasted. It must NOT go
# through clean_url(), which would throw away the list= part. Nothing is downloaded
# here: this only reads the playlist's contents. Kotlin then queues every entry as a
# normal single-video download, so the rest of this file works exactly as before.

MAX_PLAYLIST_ITEMS = 200


def _playlist_url(url):
    """Turns watch?v=abc&list=xyz into https://www.youtube.com/playlist?list=xyz."""
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()

    is_youtube = (
        host == "youtu.be" or host == "youtube.com" or host.endswith(".youtube.com")
    )
    if is_youtube:
        list_id = dict(parse_qsl(parsed.query)).get("list")
        if list_id:
            return "https://www.youtube.com/playlist?" + urlencode({"list": list_id})

    return url


def list_playlist(url):
    """
    Returns a JSON string (easy for Kotlin to read):
    {"success": true, "title": "...", "truncated": false,
     "entries": [{"title": "...", "url": "https://..."}, ...]}
    or {"success": false, "error": "..."} when something went wrong.
    """
    logger = _CollectLogger()

    try:
        options = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            # "in_playlist" = only read the list of videos, do not open every video page.
            "extract_flat": "in_playlist",
            "noplaylist": False,
            "playlistend": MAX_PLAYLIST_ITEMS
            + 1,  # +1 so we can tell if there were more
            "retries": 3,
            "extractor_retries": 3,
            "socket_timeout": 30,
            "logger": logger,
        }

        with yt_dlp.YoutubeDL(options) as ydl:
            info = ydl.extract_info(_playlist_url(url.strip()), download=False)

        entries = []
        for entry in list(info.get("entries") or []):
            if not entry:
                continue

            title = (entry.get("title") or "").strip()

            # Videos that were removed or made private cannot be downloaded.
            if title in ("[Private video]", "[Deleted video]"):
                continue

            link = entry.get("url") or entry.get("webpage_url") or ""
            if not link.startswith("http") and entry.get("id"):
                link = f"https://www.youtube.com/watch?v={entry['id']}"
            if not link.startswith("http"):
                continue

            entries.append({"title": title, "url": link})

        truncated = len(entries) > MAX_PLAYLIST_ITEMS

        return json.dumps(
            {
                "success": True,
                "title": info.get("title") or "",
                "truncated": truncated,
                "entries": entries[:MAX_PLAYLIST_ITEMS],
            }
        )

    except Exception as e:
        message = str(e).strip() or e.__class__.__name__
        notes = " | ".join(logger.warnings[-3:])
        if notes:
            message = f"{message} [{notes}]"

        return json.dumps(
            {
                "success": False,
                "error": f"{message[:500]} (yt-dlp {ytdlp_version.__version__})",
            }
        )
