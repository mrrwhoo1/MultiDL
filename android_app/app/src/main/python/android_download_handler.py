"""
Date [21st-Oct-2026]
MOST OF THE CODE IS THE SAME AS THE PC LAPTOP HANDLER BUT SLIGHTLY WTEAKED TO WORK ON ANDROID.

"""


import os
import glob
import yt_dlp
from urllib.parse import urlparse, urlunparse


def clean_url(url):
    parsed = urlparse(url)
    return urlunparse(
        (parsed.scheme, parsed.netloc, parsed.path, "", "", "")
    )


def download(f, url, progress_hook=None, output_path=None):
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
        }

        if progress_hook:
            common_options["progress_hooks"] = [progress_hook]

        def download_stream(format_selector, suffix):
            options = common_options.copy()
            options["format"] = format_selector
            options["outtmpl"] = os.path.join(
                output_path, f"%(id)s_{suffix}.%(ext)s"
            )

            with yt_dlp.YoutubeDL(options) as ydl:
                info = ydl.extract_info(url, download=True)

                paths = [
                    item.get("filepath") or item.get("filename")
                    for item in (info.get("requested_downloads") or [])
                ]
                paths = [
                    path for path in paths
                    if path and os.path.isfile(path)
                ]

                if paths:
                    return paths[0], info

                expected = ydl.prepare_filename(info)
                if os.path.isfile(expected):
                    return expected, info

                pattern = os.path.join(
                    output_path, f"{info['id']}_{suffix}.*"
                )
                matches = [
                    path for path in glob.glob(pattern)
                    if os.path.isfile(path)
                    and not path.endswith((".part", ".ytdl"))
                ]

                if matches:
                    return max(matches, key=os.path.getmtime), info

                raise FileNotFoundError(
                    f"Downloaded {suffix} stream was not found."
                )

        # Audio mode: preserve the working MP3 conversion.
        if f == "Audio":
            audio_path, info = download_stream(
                "bestaudio/best", "audio"
            )

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

            return {
                "success": True,
                "title": info.get("title", "Unknown audio"),
                "file_path": mp3_path,
            }

        # Download separate video and audio streams.
        if f == "Data Saver":
            video_format = "worstvideo/bestvideo"
            audio_format = "worstaudio/bestaudio"
        else:
            video_format = "bestvideo/best"
            audio_format = "bestaudio/best"

        video_path, info = download_stream(video_format, "video")
        audio_path, _ = download_stream(audio_format, "audio")

        from com.mrrwhoo1.multidl import FfmpegBridge

        # MKV can contain a wider range of video/audio codecs without
        # requiring a potentially slow video re-encode.
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

        return {
            "success": True,
            "title": info.get("title", "Unknown video"),
            "file_path": merged_path,
        }

    except Exception as e:
        return {
            "success": False,
            "title": str(e),
            "file_path": "",
        }
