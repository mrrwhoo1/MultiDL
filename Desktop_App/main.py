"""
MultiDL desktop app (Python + Flet 1.0).

This is a screen-for-screen port of the Android app (MainActivity.kt):
the same colours, the same layout, the same screens (Home, All downloads,
Settings, About, Open-source licences), the same messages and the same way of
working. The only difference is that there is no background picture.

Images (the platform logos) are referenced exactly like before, by file name
("yt.png", "tiktok.png", ...) and are loaded from the assets folder.
"""

import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
import webbrowser

import flet as ft

from download_handler import DOWNLOAD_DIR, FFMPEG_MISSING, download

# ---------------------------------------------------------------------------
# App details (same values as the Android app)
# ---------------------------------------------------------------------------
APP_NAME = "MultiDL"
APP_VERSION_NAME = "1.0"
APP_VERSION_CODE = 1
DEVELOPER_NAME = "mrrwhoo"
GITHUB_PROFILE_URL = "https://github.com/mrrwhoo1"
GITHUB_REPO_URL = "https://github.com/mrrwhoo1/MultiDL"
VERSION_URL = (
    "https://raw.githubusercontent.com/mrrwhoo1/MultiDL/main/desktop_version.json"
)

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS_DIR = os.path.join(HERE, "assets")

# Desktop layout: at or above WIDE_BREAKPOINT the home screen uses two columns
# (form on the left, downloads on the right); below it, one column like the phone.
WIDE_BREAKPOINT = 860
MAX_HOME_WIDTH = 1200  # widest the two-column home screen gets
MAX_CONTENT_WIDTH = 720  # widest the single-column screens get
FORM_COLUMN_WIDTH = 440  # left column of the wide home screen
WIDE_RECENT_COUNT = 8  # how many recent downloads the wide home screen lists

VIDEO_EXTENSIONS = {".mp4", ".mkv", ".webm", ".mov", ".m4v", ".avi", ".flv", ".ts"}
HIDDEN_SUFFIXES = (".part", ".ytdl", ".temp")

# ---------------------------------------------------------------------------
# Colours: the exact palettes of the Android app. Rule in BOTH themes: file rows
# are LIGHTER than the panel they sit on.
# ---------------------------------------------------------------------------
DARK = {
    "background": "#111421",
    "panel": "#202438",
    "row": "#2B3050",
    "accent": "#7C83FF",
    "text": "#FFFFFF",
    "muted": "#B8BDCF",
    "border": "#555B72",
    "message": "#FFC1C1",
}

LIGHT = {
    "background": "#F4F5FA",
    "panel": "#E6E9F5",
    "row": "#FFFFFF",
    "accent": "#5560F0",
    "text": "#1B1E2E",
    "muted": "#5A6075",
    "border": "#9AA0B8",
    "message": "#B3261E",
}

SUCCESS_GREEN = "#66BB6A"


def blend(fg, bg, alpha):
    """Mix two #RRGGBB colours: `fg` drawn over `bg` with the given opacity.
    (The Android app draws its panels with a little transparency.)"""
    f = [int(fg[i : i + 2], 16) for i in (1, 3, 5)]
    b = [int(bg[i : i + 2], 16) for i in (1, 3, 5)]
    mixed = [round(f[i] * alpha + b[i] * (1 - alpha)) for i in range(3)]
    return "#{:02X}{:02X}{:02X}".format(*mixed)


def format_size(size):
    if size < 1024:
        return f"{size} B"
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KB"
    return f"{size / (1024 * 1024):.1f} MB"


# ---------------------------------------------------------------------------
# Settings (remembers the theme between runs, like SharedPreferences on Android)
# ---------------------------------------------------------------------------
def config_dir():
    if sys.platform.startswith("win"):
        base = os.environ.get("APPDATA") or os.path.expanduser("~")
    elif sys.platform == "darwin":
        base = os.path.expanduser("~/Library/Application Support")
    else:
        base = os.environ.get("XDG_CONFIG_HOME") or os.path.expanduser("~/.config")
    return os.path.join(base, "MultiDL")


def load_settings():
    try:
        with open(os.path.join(config_dir(), "settings.json"), encoding="utf-8") as fh:
            data = json.load(fh)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def save_settings(settings):
    try:
        os.makedirs(config_dir(), exist_ok=True)
        with open(
            os.path.join(config_dir(), "settings.json"), "w", encoding="utf-8"
        ) as fh:
            json.dump(settings, fh)
    except Exception:
        pass


def read_license_text():
    for candidate in (
        os.path.join(HERE, "..", "LICENSE"),
        os.path.join(HERE, "LICENSE"),
    ):
        try:
            with open(candidate, encoding="utf-8") as fh:
                return fh.read()
        except Exception:
            continue
    return (
        "The full licence text could not be found next to this app.\n"
        "Read it at https://www.gnu.org/licenses/gpl-3.0.html"
    )


def open_path(path):
    """Open a file with the default app (the same as tapping a file on Android)."""
    if sys.platform.startswith("win"):
        os.startfile(path)  # noqa: S606 - opening the user's own download
    elif sys.platform == "darwin":
        subprocess.Popen(["open", path])
    else:
        subprocess.Popen(["xdg-open", path])


def make_thumbnail(path):
    """A small preview picture of a video (needs ffmpeg). Returns JPEG bytes or None."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        return None
    try:
        stat = os.stat(path)
        key = hashlib.sha1(
            f"{path}|{stat.st_mtime}|{stat.st_size}".encode()
        ).hexdigest()
        cache_dir = os.path.join(config_dir(), "thumbs")
        os.makedirs(cache_dir, exist_ok=True)
        cached = os.path.join(cache_dir, key + ".jpg")

        if not (os.path.isfile(cached) and os.path.getsize(cached) > 0):
            for seek in ("1", "0"):
                subprocess.run(
                    [
                        ffmpeg,
                        "-y",
                        "-loglevel",
                        "error",
                        "-ss",
                        seek,
                        "-i",
                        path,
                        "-frames:v",
                        "1",
                        "-vf",
                        "scale=164:124:force_original_aspect_ratio=increase,crop=164:124",
                        "-q:v",
                        "4",
                        cached,
                    ],
                    stdin=subprocess.DEVNULL,
                    capture_output=True,
                    timeout=20,
                )
                if os.path.isfile(cached) and os.path.getsize(cached) > 0:
                    break

        if os.path.isfile(cached) and os.path.getsize(cached) > 0:
            with open(cached, "rb") as fh:
                return fh.read()
    except Exception:
        pass
    return None


class MultiDL:
    def __init__(self, page: ft.Page):
        self.page = page

        # ---- remembered settings ----
        self.settings = load_settings()
        self.is_dark = bool(self.settings.get("dark_theme", True))

        # ---- app state (the same state the Android screen keeps) ----
        self.screen = "home"  # home | all | settings | about | licences
        self.url = ""
        self.selected_format = "HD"
        self.message = ""
        self.is_downloading = False
        self.download_progress = 0
        self.download_status = ""
        self.files = []
        self.thumb_cache = {}
        self.url_field = None
        self.dialog_open = False

        # ---- window ----
        page.title = APP_NAME
        page.padding = 0
        page.spacing = 0
        page.window.width = 1100
        page.window.height = 720
        page.window.min_width = 520
        page.window.min_height = 560

        # The area at the bottom of the home screen (progress / message + button).
        # One container is kept for the whole life of the app so it can be
        # refreshed while a download is running.
        self.bottom = ft.Container()

        # The centred phone-style column that holds whichever screen is showing.
        self.content_box = ft.Container(
            width=self._content_width(),
            padding=ft.Padding.symmetric(horizontal=20),
        )
        self.was_wide = self._is_wide()
        self.root = ft.Row(
            controls=[self.content_box],
            alignment=ft.MainAxisAlignment.CENTER,
            vertical_alignment=ft.CrossAxisAlignment.STRETCH,
            expand=True,
        )

        page.on_resize = self._on_resize
        page.on_keyboard_event = self._on_key

        self.refresh_files()
        page.add(self.root)
        self.render()

        # Look for a newer version in the background (never blocks the app). (if your pc has 1 thread then sorry lol.)
        page.run_thread(self._check_for_update)

    # ------------------------------------------------------------------
    # small helpers
    # ------------------------------------------------------------------
    @property
    def c(self):
        return DARK if self.is_dark else LIGHT

    @property
    def card(self):
        """Panel colour with the slight transparency the Android app uses."""
        return blend(self.c["panel"], self.c["background"], 0.93)

    @property
    def field_bg(self):
        return blend(self.c["panel"], self.c["background"], 0.95)

    def _page_width(self):
        return (
            getattr(self.page, "width", None)
            or getattr(self.page.window, "width", None)
            or 1100
        )

    def _is_wide(self):
        return self._page_width() >= WIDE_BREAKPOINT

    def _content_width(self):
        width = self._page_width()
        if self.screen == "home" and self._is_wide():
            return max(300, min(width, MAX_HOME_WIDTH))
        return max(300, min(width, MAX_CONTENT_WIDTH))

    def _ui(self, fn):
        """Run `fn` on the ui thred (safe to call from a worker thred)."""

        async def run():
            fn()

        self.page.run_task(run)

    def _text(self, value, size=14, color=None, weight=None, **kwargs):
        return ft.Text(
            value,
            size=size,
            color=color or self.c["text"],
            weight=weight,
            **kwargs,
        )

    @staticmethod
    def _gap(height=0, width=0):
        return ft.Container(height=height or None, width=width or None)

    def _input_border(self):
        """Rounded outline: thin in the normal state, accent coloured while typing."""
        c = self.c
        return {
            ft.ControlState.DEFAULT: ft.OutlineInputBorder(
                border_radius=14, side=ft.BorderSide(1, c["border"])
            ),
            ft.ControlState.FOCUSED: ft.OutlineInputBorder(
                border_radius=14, side=ft.BorderSide(2, c["accent"])
            ),
        }

    def _toast(self, text):
        self.page.show_dialog(ft.SnackBar(content=ft.Text(text), duration=3000))

    # ------------------------------------------------------------------
    # window events
    # ------------------------------------------------------------------
    def _on_resize(self, e):
        wide = self._is_wide()
        if wide != self.was_wide and self.screen == "home":
            self.render()  # the home screen changes layout (one column <-> two)
            return
        self.was_wide = wide
        self.content_box.width = self._content_width()
        try:
            self.content_box.update()
        except Exception:
            pass

    def _on_key(self, e):
        # Esc works like the Android back gesture.
        if e.key == "Escape" and self.screen != "home" and not self.dialog_open:
            self.go_back()

    # ------------------------------------------------------------------
    # navigation
    # ------------------------------------------------------------------
    def go(self, screen):
        self.screen = screen
        self.render()

    def go_back(self, e=None):
        self.go("settings" if self.screen in ("about", "licences") else "home")

    def render(self):
        c = self.c
        self.page.theme_mode = ft.ThemeMode.DARK if self.is_dark else ft.ThemeMode.LIGHT
        self.page.bgcolor = c["background"]

        builders = {
            "home": self._home_screen,
            "all": self._all_downloads_screen,
            "settings": self._settings_screen,
            "about": self._about_screen,
            "licences": self._licences_screen,
        }
        self._thumb_jobs = []
        self.was_wide = self._is_wide()
        wide_home = self.screen == "home" and self.was_wide
        self.content_box.width = self._content_width()
        self.content_box.padding = ft.Padding.symmetric(
            horizontal=32 if wide_home else 20
        )
        self.content_box.content = builders[self.screen]()
        self.page.update()
        self._start_thumbnails()

    # ------------------------------------------------------------------
    # files
    # ------------------------------------------------------------------
    def refresh_files(self):
        files = []
        try:
            if os.path.isdir(DOWNLOAD_DIR):
                for name in os.listdir(DOWNLOAD_DIR):
                    path = os.path.join(DOWNLOAD_DIR, name)
                    if name.startswith(".") or name.endswith(HIDDEN_SUFFIXES):
                        continue
                    if not os.path.isfile(path):
                        continue
                    stat = os.stat(path)
                    files.append(
                        {
                            "name": name,
                            "path": path,
                            "size": stat.st_size,
                            "mtime": stat.st_mtime,
                            "is_video": os.path.splitext(name)[1].lower()
                            in VIDEO_EXTENSIONS,
                        }
                    )
        except Exception:
            pass
        files.sort(key=lambda f: f["mtime"], reverse=True)  # newest first
        self.files = files

    def open_file(self, file):
        try:
            open_path(file["path"])
        except Exception:
            self._toast("Could not open this file.")

    def delete_file(self, file):
        try:
            os.remove(file["path"])
            self.refresh_files()
            self.message = ""
        except Exception as e:
            self.message = f"Could not delete file: {e}"
        self.render()

    def rename_file(self, file, requested_name):
        trimmed = requested_name.strip()
        if not trimmed:
            self.message = "File name cannot be empty."
            self.render()
            return

        extension = os.path.splitext(file["name"])[1]  # ".mp4"
        safe_name = trimmed.replace("/", "_").replace("\\", "_")
        if extension and not safe_name.lower().endswith(extension.lower()):
            final_name = safe_name + extension
        else:
            final_name = safe_name

        target = os.path.join(DOWNLOAD_DIR, final_name)
        try:
            if os.path.exists(target) and target != file["path"]:
                self.message = "A file with that name already exists."
            else:
                os.rename(file["path"], target)
                self.refresh_files()
                self.message = f"Renamed to {final_name}"
        except Exception as e:
            self.message = f"Could not rename file: {e}"
        self.render()

    # ------------------------------------------------------------------
    # thumbnails (loaded in the background; a play/music symbol shows until ready)
    # ------------------------------------------------------------------
    def _start_thumbnails(self):
        jobs = [job for job in self._thumb_jobs if job[0]["is_video"]]
        if jobs:
            self.page.run_thread(self._thumbnail_worker, jobs)

    def _thumbnail_worker(self, jobs):
        for file, holder in jobs:
            key = (file["path"], file["mtime"])
            data = self.thumb_cache.get(key)
            if data is None:
                data = make_thumbnail(file["path"])
                if data:
                    self.thumb_cache[key] = data
            if data:
                self._ui(lambda h=holder, d=data: self._set_thumbnail(h, d))

    def _set_thumbnail(self, holder, data):
        holder.content = ft.Image(src=data, width=82, height=62, fit=ft.BoxFit.COVER)
        try:
            holder.update()
        except Exception:
            pass  # that screen is no longer showing

    # ------------------------------------------------------------------
    # update check (reads the same version.json as the Android app)
    # ------------------------------------------------------------------
    def _check_for_update(self):
        try:
            request = urllib.request.Request(
                VERSION_URL, headers={"User-Agent": APP_NAME}
            )
            with urllib.request.urlopen(request, timeout=5) as response:
                data = json.loads(response.read().decode("utf-8"))
            code = int(data["versionCode"])
            name = str(data["versionName"])
            link = str(data["downloadUrl"])
            notes = str(data.get("releaseNotes", ""))
        except Exception:
            return  # An update check failure must not stop MultiDL from working.

        if code <= APP_VERSION_CODE or not link.startswith("https://github.com/"):
            return

        self._ui(lambda: self._show_update_dialog(name, link, notes))

    def _show_update_dialog(self, name, link, notes):
        def download_update(e):
            webbrowser.open(link)
            self._close_dialog()

        self._open_dialog(
            f"Update available: {name}",
            self._text(notes or "A newer version of MultiDL is available.", 14),
            [
                self._dialog_button("Later", lambda e: self._close_dialog()),
                self._dialog_button("Download", download_update),
            ],
        )

    # ------------------------------------------------------------------
    # dialogs
    # ------------------------------------------------------------------
    def _dialog_button(self, label, on_click):
        return ft.TextButton(
            content=ft.Text(label, color=self.c["accent"], weight=ft.FontWeight.W_600),
            on_click=on_click,
        )

    def _open_dialog(self, title, content, actions):
        self.dialog_open = True
        self.page.show_dialog(
            ft.AlertDialog(
                modal=False,
                title=ft.Text(title, color=self.c["text"]),
                content=content,
                actions=actions,
                bgcolor=self.c["panel"],
                shape=ft.RoundedRectangleBorder(radius=24),
                on_dismiss=lambda e: setattr(self, "dialog_open", False),
            )
        )

    def _close_dialog(self):
        self.dialog_open = False
        self.page.pop_dialog()

    def _ask_rename(self, file):
        c = self.c
        field = ft.TextField(
            value=os.path.splitext(file["name"])[0],
            label="File name",
            autofocus=True,
            color=c["text"],
            border=self._input_border(),
            cursor_color=c["accent"],
            label_style=ft.TextStyle(color=c["muted"]),
        )

        def save(e):
            name = field.value
            self._close_dialog()
            self.rename_file(file, name)

        field.on_submit = save
        self._open_dialog(
            "Rename file",
            ft.Container(width=320, content=field),
            [
                self._dialog_button("Cancel", lambda e: self._close_dialog()),
                self._dialog_button("Save", save),
            ],
        )

    def _ask_delete(self, file):
        def confirm(e):
            self._close_dialog()
            self.delete_file(file)

        folder = os.path.basename(DOWNLOAD_DIR)
        self._open_dialog(
            "Delete file?",
            self._text(f"{file['name']} will be removed from Downloads/{folder}.", 14),
            [
                self._dialog_button("Cancel", lambda e: self._close_dialog()),
                self._dialog_button("Delete", confirm),
            ],
        )

    # ------------------------------------------------------------------
    # download (the same flow as the Android app)
    # ------------------------------------------------------------------
    def start_download(self):
        submitted_url = self.url.strip()

        if not submitted_url:
            self._set_message("Enter a video URL first.")
            return
        if not submitted_url.startswith(("https://", "http://")):
            self._set_message("Enter a valid URL starting with http:// or https://.")
            return
        if self.is_downloading:
            return

        self.is_downloading = True
        self.download_progress = 0
        self.download_status = "Preparing download…"
        self.message = ""
        self._refresh_bottom()

        quality = self.selected_format

        def on_progress(percent, status):
            # yt-dlp reports from its own thread, so hop to the UI thread.
            self._ui(lambda: self._set_progress(percent, status))

        def worker():
            success, detail = False, ""
            try:
                result = download(quality, submitted_url, on_progress)
                success = bool(result and result.get("success"))
                detail = (result or {}).get("title", "")
            except Exception as e:  # noqa: BLE001
                detail = str(e)
            if not success:
                print(f"Download failed: {detail}")
            self._ui(lambda: self._finish_download(success, detail))

        self.page.run_thread(worker)

    def _set_progress(self, percent, status):
        percent = max(0, min(100, int(percent)))
        if percent == self.download_progress and status == self.download_status:
            return
        self.download_progress = percent
        self.download_status = status
        self._refresh_bottom()

    def _finish_download(self, success, detail):
        self.is_downloading = False
        if success:
            self.refresh_files()
            self.message = "Completed"
        elif detail.startswith(FFMPEG_MISSING[:20]):
            self.message = detail  # the one failure the user can fix themselves
        else:
            self.message = (
                "Couldn't complete the download. Check the link and try again."
            )
        if self.screen in ("home", "all"):
            self.render()
        else:
            self._refresh_bottom()

    def _set_message(self, text):
        self.message = text
        self._refresh_bottom()

    def _on_url_change(self, e):
        self.url = e.control.value or ""
        self.url_field.suffix = self._clear_button() if self.url else None
        try:
            self.url_field.update()
        except Exception:
            pass
        if self.message and not self.is_downloading:
            self.message = ""
            self._refresh_bottom()

    def _clear_url(self, e=None):
        self.url = ""
        self.url_field.value = ""
        self.url_field.suffix = None
        self.url_field.update()
        if self.message and not self.is_downloading:
            self.message = ""
            self._refresh_bottom()

    # ------------------------------------------------------------------
    # reusable pieces
    # ------------------------------------------------------------------
    def _panel(self, content, padding=14):
        return ft.Container(
            bgcolor=self.card,
            border_radius=16,
            padding=padding,
            content=content,
        )

    def _filled_button(self, label, on_click, height=52):
        return ft.Container(
            height=height,
            border_radius=14,
            bgcolor=self.c["accent"],
            alignment=ft.Alignment.CENTER,
            ink=True,
            on_click=on_click,
            content=self._text(label, 16, "#FFFFFF", ft.FontWeight.BOLD),
        )

    def _outlined_button(self, label, on_click, height=52):
        return ft.Container(
            height=height,
            border_radius=14,
            border=ft.Border.all(1, self.c["border"]),
            alignment=ft.Alignment.CENTER,
            ink=True,
            on_click=on_click,
            content=self._text(label, 14, self.c["text"], ft.FontWeight.W_500),
        )

    def _divider(self):
        return ft.Container(
            height=1,
            margin=ft.Margin.symmetric(horizontal=16),
            bgcolor=blend(self.c["border"], self.card, 0.4),
        )

    def _clear_button(self):
        return ft.IconButton(
            icon=ft.Icons.CLEAR,
            icon_color=self.c["muted"],
            icon_size=20,
            tooltip="Clear URL",
            on_click=self._clear_url,
        )

    def _screen_header(self, title, subtitle=None):
        c = self.c
        texts = [self._text(title, 22, weight=ft.FontWeight.BOLD)]
        if subtitle:
            texts.append(self._text(subtitle, 13, c["muted"]))
        return ft.Container(
            padding=ft.Padding.only(top=12),
            content=ft.Row(
                spacing=8,
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Container(
                        width=44,
                        height=44,
                        border_radius=22,
                        bgcolor=self.field_bg,
                        alignment=ft.Alignment.CENTER,
                        ink=True,
                        on_click=self.go_back,
                        tooltip="Back",
                        content=ft.Icon(ft.Icons.ARROW_BACK, color=c["text"], size=24),
                    ),
                    ft.Column(spacing=0, controls=texts),
                ],
            ),
        )

    def _file_row(self, file, container_color):
        c = self.c
        symbol = "▶" if file["is_video"] else "♫"

        thumb_holder = ft.Container(
            width=82,
            height=62,
            border_radius=8,
            clip_behavior=ft.ClipBehavior.ANTI_ALIAS,
            bgcolor=blend(c["background"], container_color, 0.45),
            alignment=ft.Alignment.CENTER,
            content=self._text(symbol, 24, c["accent"], ft.FontWeight.BOLD),
        )
        cached = self.thumb_cache.get((file["path"], file["mtime"]))
        if cached:
            thumb_holder.content = ft.Image(
                src=cached, width=82, height=62, fit=ft.BoxFit.COVER
            )
        else:
            self._thumb_jobs.append((file, thumb_holder))

        menu = ft.PopupMenuButton(
            icon=ft.Icons.MORE_VERT,
            icon_color=c["muted"],
            tooltip="File options",
            bgcolor=self.c["panel"],
            items=[
                ft.PopupMenuItem(
                    icon=ft.Icons.EDIT,
                    content=ft.Text("Rename", color=c["text"]),
                    on_click=lambda e, f=file: self._ask_rename(f),
                ),
                ft.PopupMenuItem(
                    icon=ft.Icons.DELETE,
                    content=ft.Text("Delete", color=c["text"]),
                    on_click=lambda e, f=file: self._ask_delete(f),
                ),
            ],
        )

        return ft.Container(
            bgcolor=container_color,
            border_radius=12,
            padding=10,
            ink=True,
            on_click=lambda e, f=file: self.open_file(f),
            content=ft.Row(
                spacing=12,
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    thumb_holder,
                    ft.Column(
                        expand=True,
                        spacing=3,
                        controls=[
                            self._text(
                                file["name"],
                                14,
                                weight=ft.FontWeight.W_500,
                                max_lines=2,
                                overflow=ft.TextOverflow.ELLIPSIS,
                            ),
                            self._text(format_size(file["size"]), 12, c["muted"]),
                            self._text(
                                "Tap to play" if file["is_video"] else "Tap to open",
                                11,
                                c["accent"],
                            ),
                        ],
                    ),
                    menu,
                ],
            ),
        )

    # ------------------------------------------------------------------
    # HOME screen
    # ------------------------------------------------------------------
    def _home_screen(self):
        c = self.c

        # ---- header: title on the left, settings gear on the right ----
        header = ft.Container(
            padding=ft.Padding.only(top=12),
            content=ft.Row(
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Column(
                        expand=True,
                        spacing=0,
                        controls=[
                            self._text(APP_NAME, 32, weight=ft.FontWeight.BOLD),
                            self._text(
                                "Download media from your favourite platforms",
                                14,
                                c["muted"],
                            ),
                        ],
                    ),
                    ft.IconButton(
                        icon=ft.Icons.SETTINGS,
                        icon_color=c["text"],
                        icon_size=24,
                        tooltip="Settings",
                        on_click=lambda e: self.go("settings"),
                    ),
                ],
            ),
        )

        # ---- URL field ----
        self.url_field = ft.TextField(
            value=self.url,
            hint_text="https://example.com/video...",
            hint_style=ft.TextStyle(color=c["muted"]),
            color=c["text"],
            border=self._input_border(),
            cursor_color=c["accent"],
            filled=True,
            bgcolor=self.field_bg,
            focused_bgcolor=self.field_bg,
            content_padding=ft.Padding.symmetric(horizontal=16, vertical=14),
            suffix=self._clear_button() if self.url else None,
            on_change=self._on_url_change,
            on_submit=lambda e: self.start_download(),
        )

        # ---- quality selector (looks like the Android drop-down) ----
        def choose(quality):
            def handler(e):
                self.selected_format = quality
                self.render()

            return handler

        quality_menu = ft.PopupMenuButton(
            bgcolor=c["panel"],
            items=[
                ft.PopupMenuItem(
                    content=ft.Text(name, color=c["text"]), on_click=choose(name)
                )
                for name in ("HD", "Data Saver", "Audio")
            ],
            content=ft.Container(
                height=48,
                border_radius=12,
                border=ft.Border.all(1, c["border"]),
                bgcolor=self.field_bg,
                padding=ft.Padding.symmetric(horizontal=16),
                content=ft.Row(
                    vertical_alignment=ft.CrossAxisAlignment.CENTER,
                    controls=[
                        ft.Container(
                            expand=True, content=self._text(self.selected_format, 14)
                        ),
                        ft.Icon(ft.Icons.KEYBOARD_ARROW_DOWN, color=c["text"], size=24),
                    ],
                ),
            ),
        )

        # ---- supported platforms (images are referenced by file name) ----
        def platform_logo(src, name):
            return ft.Column(
                width=42,
                spacing=5,
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Image(src=src, width=30, height=30, fit=ft.BoxFit.CONTAIN),
                    self._text(name, 8, c["muted"], max_lines=1, no_wrap=True),
                ],
            )

        logos = ft.Row(
            alignment=ft.MainAxisAlignment.SPACE_BETWEEN,
            vertical_alignment=ft.CrossAxisAlignment.CENTER,
            controls=[
                platform_logo("yt.png", "YouTube"),
                platform_logo("instagram.png", "Instagram"),
                platform_logo("tiktok.png", "TikTok"),
                platform_logo("facebook.png", "Facebook"),
                platform_logo("x.png", "X"),
                platform_logo("twitch.png", "Twitch"),
            ],
        )

        # The input form: identical on the phone-style and the desktop layout.
        form = [
            self._text("Video URL", 15, weight=ft.FontWeight.W_600),
            self._gap(8),
            self.url_field,
            self._gap(18),
            self._text("Download quality", 15, weight=ft.FontWeight.W_600),
            self._gap(8),
            quality_menu,
            self._gap(24),
            self._text("Supported platforms", 15, weight=ft.FontWeight.W_600),
            self._gap(14),
            logos,
        ]

        # "Recently downloaded" title + "View all" link
        recent_header = ft.Row(
            vertical_alignment=ft.CrossAxisAlignment.CENTER,
            controls=[
                ft.Container(
                    expand=True,
                    content=self._text(
                        "Recently downloaded", 18, weight=ft.FontWeight.BOLD
                    ),
                ),
                ft.Container(
                    border_radius=8,
                    padding=ft.Padding.symmetric(horizontal=8, vertical=6),
                    ink=True,
                    on_click=lambda e: self.go("all"),
                    content=self._text("View all  ›", 14, c["accent"]),
                ),
            ],
        )

        def empty_state():
            return [
                self._text("No downloads yet", 14, weight=ft.FontWeight.W_600),
                self._text("Your downloaded files will appear here.", 13, c["muted"]),
            ]

        self.bottom.content = self._build_bottom()

        # ================= DESKTOP: two columns =================
        if self._is_wide():
            left = ft.Container(
                width=FORM_COLUMN_WIDTH,
                content=ft.Column(
                    expand=True,
                    spacing=0,
                    horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
                    controls=[
                        # the form scrolls if the window is short ...
                        ft.Column(
                            expand=True,
                            spacing=0,
                            scroll=ft.ScrollMode.AUTO,
                            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
                            controls=[self._gap(20), *form],
                        ),
                        # ... but the Download button never leaves the screen
                        self.bottom,
                    ],
                ),
            )

            if self.files:
                recent_body = ft.ListView(
                    expand=True,
                    spacing=10,
                    controls=[
                        self._file_row(f, c["row"])
                        for f in self.files[:WIDE_RECENT_COUNT]
                    ],
                )
            else:
                recent_body = ft.Column(spacing=10, controls=empty_state())

            right = ft.Container(
                expand=True,
                padding=ft.Padding.only(top=20, bottom=16),
                content=ft.Column(
                    expand=True,
                    spacing=0,
                    horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
                    controls=[
                        recent_header,
                        self._gap(12),
                        ft.Container(
                            expand=True,
                            bgcolor=self.card,
                            border_radius=16,
                            padding=14,
                            content=recent_body,
                        ),
                    ],
                ),
            )

            body = ft.Row(
                expand=True,
                spacing=32,
                vertical_alignment=ft.CrossAxisAlignment.STRETCH,
                controls=[left, right],
            )
            return ft.Column(
                expand=True,
                spacing=0,
                horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
                controls=[header, body],
            )

        # ================= NARROW WINDOW: one column (phone-style) =================
        if self.files:
            recent_controls = [self._file_row(f, c["row"]) for f in self.files[:3]]
        else:
            recent_controls = empty_state()

        scrolling = ft.Column(
            expand=True,
            spacing=0,
            scroll=ft.ScrollMode.AUTO,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[
                self._gap(20),
                *form,
                self._gap(28),
                recent_header,
                self._gap(12),
                self._panel(ft.Column(spacing=10, controls=recent_controls)),
                self._gap(16),
            ],
        )
        return ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[header, scrolling, self.bottom],
        )

    def _build_bottom(self):
        """Progress / message area and the Download button (always visible)."""
        c = self.c
        controls = []

        if self.is_downloading:
            controls.append(
                ft.Container(
                    padding=ft.Padding.symmetric(vertical=8),
                    content=ft.Column(
                        spacing=6,
                        controls=[
                            ft.ProgressBar(
                                value=self.download_progress / 100,
                                bar_height=5,
                                border_radius=50,
                                color=c["accent"],
                                bgcolor=c["panel"],
                                year_2023=True,
                            ),
                            self._text(
                                f"{self.download_status} · {self.download_progress}%",
                                13,
                                c["muted"],
                            ),
                        ],
                    ),
                )
            )
        elif self.message:
            controls.append(
                ft.Container(
                    padding=ft.Padding.symmetric(vertical=8),
                    content=self._text(
                        self.message,
                        13,
                        SUCCESS_GREEN if self.message == "Completed" else c["message"],
                    ),
                )
            )

        if self.is_downloading:
            button_content = ft.Row(
                alignment=ft.MainAxisAlignment.CENTER,
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                spacing=10,
                controls=[
                    ft.ProgressRing(
                        width=22, height=22, stroke_width=2, color="#FFFFFF"
                    ),
                    self._text("Downloading...", 14, "#FFFFFF"),
                ],
            )
        else:
            button_content = self._text("Download", 16, "#FFFFFF", ft.FontWeight.BOLD)

        controls.append(
            ft.Container(
                height=54,
                border_radius=14,
                bgcolor=c["accent"],
                alignment=ft.Alignment.CENTER,
                ink=not self.is_downloading,
                on_click=lambda e: self.start_download(),
                content=button_content,
            )
        )
        controls.append(self._gap(16))
        return ft.Column(
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=controls,
        )

    def _refresh_bottom(self):
        self.bottom.content = self._build_bottom()
        try:
            self.bottom.update()
        except Exception:
            pass  # the home screen is not showing right now

    # ------------------------------------------------------------------
    # ALL DOWNLOADS screen
    # ------------------------------------------------------------------
    def _all_downloads_screen(self):
        c = self.c
        count = len(self.files)
        subtitle = "1 file" if count == 1 else f"{count} files"

        if not self.files:
            body = ft.Container(
                expand=True,
                alignment=ft.Alignment.CENTER,
                content=self._text(
                    "No downloads yet.\nDownloaded files will appear here.",
                    14,
                    c["muted"],
                    text_align=ft.TextAlign.CENTER,
                ),
            )
        else:
            body = ft.ListView(
                expand=True,
                spacing=10,
                padding=ft.Padding.only(bottom=16),
                controls=[self._file_row(f, self.card) for f in self.files],
            )

        return ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[
                self._screen_header("All downloads", subtitle),
                self._gap(16),
                body,
            ],
        )

    # ------------------------------------------------------------------
    # SETTINGS screen
    # ------------------------------------------------------------------
    def _settings_screen(self):
        c = self.c

        def change_theme(e):
            self.is_dark = bool(e.control.value)
            self.settings["dark_theme"] = self.is_dark
            save_settings(self.settings)
            self.render()

        theme_row = ft.Container(
            padding=16,
            content=ft.Row(
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Column(
                        expand=True,
                        spacing=0,
                        controls=[
                            self._text("Dark theme", 16, weight=ft.FontWeight.W_600),
                            self._text(
                                "On (switch off for the light theme)"
                                if self.is_dark
                                else "Off (light theme)",
                                13,
                                c["muted"],
                            ),
                        ],
                    ),
                    ft.Switch(
                        value=self.is_dark,
                        on_change=change_theme,
                        active_color="#FFFFFF",
                        active_track_color=c["accent"],
                        inactive_thumb_color=c["muted"],
                        inactive_track_color=c["row"],
                        track_outline_color=c["border"],
                    ),
                ],
            ),
        )

        about_row = ft.Container(
            padding=16,
            ink=True,
            on_click=lambda e: self.go("about"),
            content=ft.Row(
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Container(
                        expand=True,
                        content=self._text("About", 16, weight=ft.FontWeight.W_600),
                    ),
                    self._text("›", 22, c["muted"]),
                ],
            ),
        )

        licences_row = ft.Container(
            padding=16,
            ink=True,
            on_click=lambda e: self.go("licences"),
            content=ft.Row(
                vertical_alignment=ft.CrossAxisAlignment.CENTER,
                controls=[
                    ft.Column(
                        expand=True,
                        spacing=0,
                        controls=[
                            self._text(
                                "Open-source licences", 16, weight=ft.FontWeight.W_600
                            ),
                            self._text("View MultiDL's licence", 13, c["muted"]),
                        ],
                    ),
                    self._text("›", 22, c["muted"]),
                ],
            ),
        )

        panel = ft.Container(
            bgcolor=self.card,
            border_radius=16,
            clip_behavior=ft.ClipBehavior.ANTI_ALIAS,
            content=ft.Column(
                spacing=0,
                controls=[
                    theme_row,
                    self._divider(),
                    about_row,
                    self._divider(),
                    licences_row,
                ],
            ),
        )

        return ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[self._screen_header("Settings"), self._gap(20), panel],
        )

    # ------------------------------------------------------------------
    # ABOUT screen
    # ------------------------------------------------------------------
    def _about_screen(self):
        c = self.c

        panel = self._panel(
            ft.Column(
                spacing=0,
                controls=[
                    self._text(APP_NAME, 28, weight=ft.FontWeight.BOLD),
                    self._text(f"Version {APP_VERSION_NAME}", 14, c["muted"]),
                    self._gap(20),
                    self._text("Developer", 12, c["muted"]),
                    self._text(DEVELOPER_NAME, 18, weight=ft.FontWeight.W_600),
                ],
            ),
            padding=20,
        )

        scrolling = ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            scroll=ft.ScrollMode.AUTO,
            controls=[
                self._gap(20),
                panel,
                self._gap(20),
                self._text(
                    "Enjoying MultiDL? A star on GitHub is the best way to rate it "
                    "and support the project.",
                    14,
                    c["muted"],
                ),
                self._gap(14),
                self._filled_button(
                    "★  Rate on GitHub", lambda e: webbrowser.open(GITHUB_REPO_URL)
                ),
                self._gap(10),
                self._outlined_button(
                    "Developer's GitHub", lambda e: webbrowser.open(GITHUB_PROFILE_URL)
                ),
                self._gap(16),
            ],
        )

        return ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[self._screen_header("About"), scrolling],
        )

    # ------------------------------------------------------------------
    # OPEN-SOURCE LICENCES screen
    # ------------------------------------------------------------------
    def _licences_screen(self):
        c = self.c

        about_licence = self._panel(
            ft.Column(
                spacing=8,
                controls=[
                    self._text(APP_NAME, 20, weight=ft.FontWeight.BOLD),
                    self._text(
                        "GNU General Public License v3.0 (GPL-3.0)",
                        14,
                        c["accent"],
                        ft.FontWeight.W_600,
                    ),
                    self._text("Copyright © 2026 Maron H. Chilomo", 13, c["muted"]),
                    self._text(
                        "MultiDL is distributed under the GNU GPL version 3. "
                        "You may copy, modify, and redistribute it under the terms "
                        "of that licence. The complete licence text follows.",
                        14,
                    ),
                ],
            ),
            padding=16,
        )

        full_text = self._panel(
            self._text(read_license_text(), 12, selectable=True), padding=16
        )

        third_party = self._panel(
            ft.Column(
                spacing=6,
                controls=[
                    self._text("Third-party components", 17, weight=ft.FontWeight.BOLD),
                    self._text(
                        "MultiDL includes third-party software which retains "
                        "its own copyright notices and licence terms. Refer to the "
                        "project's NOTICE file and the respective component licence "
                        "texts for details.",
                        13,
                        c["muted"],
                    ),
                ],
            ),
            padding=16,
        )

        scrolling = ft.Column(
            expand=True,
            spacing=12,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            scroll=ft.ScrollMode.AUTO,
            controls=[about_licence, full_text, third_party, self._gap(8)],
        )

        return ft.Column(
            expand=True,
            spacing=0,
            horizontal_alignment=ft.CrossAxisAlignment.STRETCH,
            controls=[
                self._screen_header(
                    "Open-source licences", "MultiDL and third-party software"
                ),
                self._gap(16),
                scrolling,
            ],
        )


if __name__ == "__main__":
    ft.run(main=MultiDL, assets_dir=ASSETS_DIR)
