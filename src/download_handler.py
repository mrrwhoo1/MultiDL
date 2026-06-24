import yt_dlp
import os 
from urllib.parse import urlparse, urlunparse



def clean_url(url):
    parsed = urlparse(url)
    # Strip query string and fragment, keep scheme + netloc + path only
    return urlunparse((parsed.scheme, parsed.netloc, parsed.path, '', '', ''))



def download( f, url,progress_hook = None ,output_path = "~/Downloads/multidl"):
    form_at = f

    try:
        
        expanded_path = os.path.expanduser(output_path)
        
        if not os.path.exists(expanded_path):
            os.makedirs(expanded_path)
            
        url_ = clean_url(url)

        


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
                        'postprocessors': [{         # Extract and convert it into a clean audio file
                            'key': 'FFmpegExtractAudio',
                            'preferredcodec': 'mp3', # Can change to 'm4a' or 'wav'
                            'preferredquality': '192',}]
                    }

        if form_at == "HD":
            ytdlp_options['format'] == "bestvideo+bestaudio/best"
        elif form_at == "Data Saver":
            ytdlp_options['format'] = "mp4" 
        elif form_at =="Audio":
            ytdlp_options['format'] == "bestaudio/best",  # Download only the best audio track
        else:
            ytdlp_options['format'] == "bestvideo+bestaudio/best"
            


        with yt_dlp.YoutubeDL(ytdlp_options) as ydl:
            info_dict = ydl.extract_info(url_, download= True)
            video_title = info_dict.get('title', 'Unknown video')

            filename = ydl.prepare_filename(info_dict)



            print(f"Downloaded: {video_title}")
          
            return {"success": True, 
                    "title": video_title, 
                     "file_path": filename}

    except Exception as e:
        print(f"Error: {e}")

        return {"success": False, "title": str(e)}






if __name__ == "__main__":
    download()