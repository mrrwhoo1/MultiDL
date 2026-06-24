import yt_dlp
import os
from urllib.parse import urlparse, urlunparse

def clean_url(url):
    parsed = urlparse(url)
    return urlunparse((parsed.scheme, parsed.netloc, parsed.path, '', '', ''))

def download(f, url, progress_hook=None, output_path="~/Downloads/multidl"):
    form_at = f

    try:
        expanded_path = os.path.expanduser(output_path)

        if not os.path.exists(expanded_path):
            os.makedirs(expanded_path)

        url = clean_url(url)

        ytdlp_options = {
            'format': 'bestvideo+bestaudio/best',
            'merge_output_format': 'mp4',
            'outtmpl': f'{expanded_path}/%(title)s.%(ext)s',
            'keepvideo': False,
            'quiet': True,
            'no_warnings': True,
            'noplaylist': True,
            'noprogress': False,
            'progress_hooks': [progress_hook] if progress_hook else [],
        }

        if form_at == "Audio":
            ytdlp_options['format'] = "bestaudio/best"
            ytdlp_options['postprocessors'] = [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '192',
            }]
        elif form_at == "Data Saver":
            ytdlp_options['format'] = "worstvideo+worstaudio/worst"
        else:  # HD or default
            ytdlp_options['format'] = "bestvideo+bestaudio/best"

        # Snapshot files before download
        before = set(os.listdir(expanded_path))

        with yt_dlp.YoutubeDL(ytdlp_options) as ydl:
            info_dict = ydl.extract_info(url, download=True)
            video_title = info_dict.get('title', 'Unknown video')

        # Find the newly added file by comparing before/after
        after = set(os.listdir(expanded_path))
        new_files = after - before

        if new_files:
            # Pick the newest one just in case
            filename = max(
                [os.path.join(expanded_path, f) for f in new_files],
                key=os.path.getmtime
            )
        else:
            # Fallback: grab the most recently modified file in the folder
            all_files = [os.path.join(expanded_path, f) for f in os.listdir(expanded_path)]
            filename = max(all_files, key=os.path.getmtime)

        print(f"Downloaded: {video_title}")
        print(f"Saved to: {filename}")

        return {"success": True, "title": video_title, "file_path": filename}

    except Exception as e:
        print(f"Error: {e}")
        return {"success": False, "title": str(e)}



if __name__ == "__main__":
    download()