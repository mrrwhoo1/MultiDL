"""
Date [22nd-Oct-2026]
- Updated the code to better handle facebook specifically as the previous code constatnly failed and data saver completely did not work.
- Also attempted to fix youtube falling back to foreign languages when i select data saver.
- I tried to explain most of the code line by line incase someone ever stables upon it and doesnt want to just copy and paste random jargon.
- Also just noticed the typing errors, My bad. i believe you can still understand what i mean.


- For those wondering how all this data is sent to kotlin, its cause of chaquopy which is acting as a bridge. this functions are all being called from
/home/mrrwhoo/projects/MultiDL/android_app/app/src/main/java/com/mrrwhoo1/multidl/MainActivity.kt

"""


import os
import glob  #Used to find patterns in file names like *.mp3..
import yt_dlp
from urllib.parse import urlparse, urlunparse, parse_qsl, urlencode
from yt_dlp import version as ytdlp_version
from yt_dlp.utils import sanitize_filename


#Seems like during clean url it was messing with the facebook links horribly, made this to leep certain characters i saw appear alot in links.
_QUERY_KEEP = {
    "facebook.com": {"v", "story_fbid", "id", "fbid"},
    "youtube.com": {"v"},
}


def clean_url(url):
    parsed = urlparse(url.strip()) #just removes the white spaces you might pick up in links.
    host = (parsed.hostname or "").lower() #gets the websites host name like youtube.com

    keep = set()
    for domain, keys in _QUERY_KEEP.items():
        if host == domain or host.endswith("." + domain): #self explainatory really.
            #e.g if after host we get wwww.facebook.com then run the if block, domain[0] is just facebook.com so the first styatement is false
            #but then we go to the or statemtn which says if www.facebook.com (host) == .+DOMAIN which is .facebook.com that passes as true cause it wont care about the www.

            keep = keys #if it washost was facebook then keep = {"v", "story_fbid", "id", "fbid"}.
            break

    filtered_params = []

    for k, v in parse_qsl(parsed.query): #parse_qsl = Take a URL query string and turn it into a list of key-value pairs.
        #[
        #     ("v", "abc123"),
        #     ("t", "30")
        # ]
        #and for the parsed.query urlparse(url) gives a parse result conatining multiple stuff like scheme, path, params, query etc so saying
        # parsed.query just says give me the v=abc123&t=30 part of the url as query holds that part of the link. so {v : abc123...}
        if k in keep: #checks if that key 'v' is in keep and if it it then it is added to the filtered_params list.
            filtered_params.append((k, v))

    query = urlencode(filtered_params) #opposite of parse_qsl, it gets the ("v", "abc123") and makes it back to v=abc123




    #Just incase who ever reads this one day forgets that earlier urlparse(url) has slotes like
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
    ) #this gives back the full cleaned link.
#if still lost loook at this https://www.youtube.com/watch?v=abc123&t=420&utm_source=google, everything before the & uniqly identifies a youtube video, keeping the whole thing would cause issues sometimes BUt from my testing youtube is a lot more forgiving, this function was primary used for FACEBOOK as this one is a completely different story ( i know the keep dictionary only has facebook and youtube for now, might expand it later if i notice more issues on the otehr sites...especially data saver as its caused me the most issues.)


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


def download(f, url, progress_hook=None, output_path=None):
    logger = _CollectLogger()

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
            "logger": logger, #This is where the above class is used to provide warnings.

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

        def download_stream(
            format_selector, suffix, start_percent, end_percent, label, sort=None
        ):
            options = common_options.copy()
            options["format"] = format_selector
            if sort:
                options["format_sort"] = sort
            options["outtmpl"] = os.path.join(
                output_path, f"%(id)s_{suffix}.%(ext)s"
            )

            def on_yt_dlp_progress(data):
                status = data.get("status")

                if status == "downloading":
                    downloaded = data.get("downloaded_bytes") or 0
                    total = (
                        data.get("total_bytes")
                        or data.get("total_bytes_estimate")
                        or 0
                    )

                    if total > 0:
                        fraction = min(1.0, downloaded / total)
                        percent = start_percent + (
                            end_percent - start_percent
                        ) * fraction
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
                    item.get("filepath") or item.get("filename")
                    for item in downloads
                ]
                paths = [
                    path for path in paths
                    if path and os.path.isfile(path)
                ]

                if paths:
                    return paths[0], info, fmt

                expected = ydl.prepare_filename(info)
                if os.path.isfile(expected):
                    return expected, info, fmt

                pattern = os.path.join(
                    output_path, f"{info['id']}_{suffix}.*"
                )
                matches = [
                    path for path in glob.glob(pattern)
                    if os.path.isfile(path)
                    and not path.endswith((".part", ".ytdl"))
                ]

                if matches:
                    return max(matches, key=os.path.getmtime), info, fmt

                raise FileNotFoundError(
                    f"Downloaded {suffix} stream was not found."
                )

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
                f'-y -i "{audio_path}" -vn '
                f'-codec:a libmp3lame -b:a 192k "{mp3_path}"'
            )

            result = str(FfmpegBridge.execute(command))

            if (
                not result.startswith("SUCCESS")
                or not os.path.isfile(mp3_path)
                or os.path.getsize(mp3_path) == 0
            ):
                raise RuntimeError(
                    f"MP3 conversion failed: {result[-700:]}"
                )

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
        already_has_audio = (
            vcodec not in (None, "none") and acodec not in (None, "none")
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
            f'-map 0:v:0 -map 1:a:0 '
            f'-c copy "{merged_path}"'
        )

        result = str(FfmpegBridge.execute(command))

        if (
            not result.startswith("SUCCESS")
            or not os.path.isfile(merged_path)
            or os.path.getsize(merged_path) == 0
        ):
            raise RuntimeError(
                f"Video/audio merge failed: {result[-700:]}"
            )

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
