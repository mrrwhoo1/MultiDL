import flet as ft
from download_handler import download
import os


class MultiDL:
    def __init__(self, page: ft.Page):

        self.page = page
        self.page.title = "MultiDL"
        self.page.padding = 0
        self.page.spacing = 0
        self.page.horizontal_alignment = ft.CrossAxisAlignment.CENTER
        self.page.vertical_alignment = ft.MainAxisAlignment.CENTER

        self.page.on_resize = self.window_resizer

        self.Build_UI()
        self.load_existing_downloads()

        self.text_scale_size()
        self.page.update()

    def play_video(self, file_path):
        import subprocess

        # On Fedora, 'xdg-open' launches your default video player (like VLC)
        subprocess.Popen(["xdg-open", file_path])

    def get_download_items(self):
        """Reads the downloads folder and returns a fresh list of ListTiles."""
        output_path = "~/Downloads/multidl"
        expanded_path = os.path.expanduser(output_path)
        items = []

        if os.path.exists(expanded_path):
            files = os.listdir(expanded_path)

            # Sort files so the newest downloads appear at the top
            files.sort(
                key=lambda x: os.path.getmtime(os.path.join(expanded_path, x)),
                reverse=True,
            )

            for file_name in files:
                if file_name.startswith("."):  # ignoring hidden files
                    continue

                full_file_path = os.path.join(expanded_path, file_name)
                items.append(self.make_download_tile(file_name, full_file_path))

        return items

    def make_download_tile(self, file_name, full_file_path):
        return ft.ListTile(
            leading=ft.Icon(ft.Icons.VIDEO_LIBRARY, color="white"),
            title=ft.Text(file_name, color="black", weight="bold", max_lines=1),
            subtitle=ft.Text("Saved locally", color="white70", size=11),
            trailing=ft.Icon(ft.Icons.CHECK_CIRCLE, color="green"),
            on_click=lambda e, path=full_file_path: self.play_video(path),
        )

    def load_existing_downloads(self):
        self.downloads_list.controls = self.get_download_items()

    def open_view_all(self, e):
        all_view = ft.View(
            route="/all_downloads",
            padding=0,
            controls=[
                ft.AppBar(
                    title=ft.Text("All Downloads"),
                    leading=ft.IconButton(
                        icon=ft.Icons.ARROW_BACK,
                        on_click=self.close_view_all,
                        bgcolor=ft.Colors.TRANSPARENT,
                    ),
                ),
                ft.Container(
                    expand=True,
                    padding=10,
                    image=ft.DecorationImage(src="background.png", fit=ft.BoxFit.COVER),
                    content=ft.ListView(
                        controls=self.get_download_items(),  # re-read so it's current
                        expand=True,
                        spacing=10,
                    ),
                ),
            ],
        )
        self.page.views.append(all_view)
        self.page.update()

    def close_view_all(self, e):
        self.page.views.pop()
        self.page.update()

    def Build_UI(self):
        self.URL_Field = ft.TextField(
            hint_text="https://example.com/video...",
            bgcolor="gray",
            opacity=0.3,
            col={"xs": 9, "sm": 9, "md": 9},
            border_radius=30,
            border_color="black",
        )
        self.dropdown_btn = ft.Dropdown(
            label="Select Format",
            elevation=5,
            options=[
                ft.dropdown.Option("HD"),
                ft.dropdown.Option("Data Saver"),
                ft.dropdown.Option("Audio"),
            ],
            col={"xs": 3, "sm": 3, "md": 3},
            border_radius=30,
            border_color="transparent",
            text_style=ft.TextStyle(color=ft.Colors.BLACK),
            label_style=ft.TextStyle(color=ft.Colors.BLACK),
        )

        self.first_row = ft.ResponsiveRow(
            controls=[self.URL_Field, self.dropdown_btn],
            vertical_alignment=ft.CrossAxisAlignment.CENTER,
        )

        self.dynamid_MultiDL_label = ft.Text(
            value="Video Downloader", weight="bold", color="black"
        )
        self.supported_text = ft.Text(
            value="───────Supported Platforms───────", color="black"
        )

        self.tiktok_logo = ft.Image(src="tiktok.png", width=20, height=20)
        self.insta_logo = ft.Image(src="instagram.png", width=20, height=20)
        self.facebook_logo = ft.Image(src="facebook.png", width=20, height=20)
        self.yt_logo = ft.Image(src="yt.png", width=20, height=20)
        self.x_logo = ft.Image(src="x.png", width=20, height=20)
        self.linkedin_logo = ft.Image(
            src="linkedin.png",
            width=30,
            height=30,
        )
        self.twitch_logo = ft.Image(src="twitch.png", width=20, height=20)

        self.supported_logos_row = ft.Row(
            controls=[
                self.tiktok_logo,
                self.insta_logo,
                self.facebook_logo,
                self.yt_logo,
                self.x_logo,
                self.linkedin_logo,
                self.twitch_logo,
            ],
            alignment=ft.MainAxisAlignment.CENTER,
            spacing=5,
        )

        # row for recently downloaded label & view all
        self.recently_downloaded_label = ft.Text(
            value="Recently downloaded", color="black", col={"xs": 8, "sm": 9, "md": 9}
        )
        self.view_all = ft.TextButton(
            content="View all >",
            col={"xs": 4, "sm": 3, "md": 3},
            style=ft.ButtonStyle(
                color="black",
            ),
            align=ft.Alignment.CENTER_RIGHT,
            on_click=self.open_view_all,
        )
        self.r_v = ft.ResponsiveRow(
            controls=[
                self.recently_downloaded_label,
                self.view_all,
            ],
            vertical_alignment=ft.CrossAxisAlignment.CENTER,
        )

        self.downloads_list = ft.ListView(expand=True, spacing=10, padding=10)

        self.recently_downloaded_container = ft.Container(
            bgcolor="#9D9D9D",
            opacity=0.5,
            border_radius=30,
            expand=True,
            content=self.downloads_list,
        )

        self.container_row = ft.ResponsiveRow(
            controls=[self.recently_downloaded_container],
            alignment=ft.MainAxisAlignment.CENTER,
            expand=True,
        )

        self.button_progress = ft.ProgressBar(
            value=0.0,
            height=50,
            color="#13d855",
            bgcolor="#9A05FD",
        )

        self.btn_text = ft.Text(value="Download", color="white")

        self.download_button = ft.Button(
            content=self.btn_text,
            icon=ft.Icons.DOWNLOAD,
            col={"xs": 10, "sm": 10, "md": 10},
            height=50,
            style=ft.ButtonStyle(
                bgcolor=ft.Colors.TRANSPARENT,
                shadow_color=ft.Colors.TRANSPARENT,
            ),
            on_click=self.handler,
            elevation=5,
        )

        self.stacked_button_control = ft.Container(
            col={"xs": 10, "sm": 10, "md": 10},
            border_radius=30,
            clip_behavior=ft.ClipBehavior.ANTI_ALIAS,  # Keeps corners neatly rounded
            content=ft.Stack(
                controls=[
                    self.button_progress,  # Layer 0 (Back)
                    self.download_button,  # Layer 1 (Front)
                ]
            ),
        )

        self.MainContainer = ft.Container(
            expand=True,
            padding=20,
            image=ft.DecorationImage(src="background.png", fit=ft.BoxFit.COVER),
            content=ft.Column(
                controls=[
                    self.dynamid_MultiDL_label,
                    self.first_row,
                    ft.Divider(height=10, thickness=1, color="transparent"),
                    self.supported_text,
                    self.supported_logos_row,
                    self.r_v,
                    self.container_row,
                    self.stacked_button_control,
                ],
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            ),
        )

        self.page.add(self.MainContainer)

    def handler(self, e):
        url_ = self.URL_Field.value
        format_selected = self.dropdown_btn.value
        if not url_:
            return

        # Update text instantly on the main thread
        self.btn_text.value = "Downloading 0%..."
        self.button_progress.value = 0.0
        self.page.update()

        # This calculates our decimal stream percentages

        def yt_progress_hook(d):
            if d["status"] == "downloading":
                total = (
                    d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                )  # full proofing cause just getting total bytes backfires
                downloaded = d.get("downloaded_bytes", 0)

                if total > 0:
                    percentage = downloaded / total

                    # Thread-safe update using run_task
                    async def update_ui():
                        self.button_progress.value = percentage
                        self.btn_text.value = f"Downloading {int(percentage * 100)}%..."
                        self.page.update()

                    self.page.run_task(update_ui)

        def background_download():
            try:
                result = download(
                    format_selected, url=url_, progress_hook=yt_progress_hook
                )

                if result and result["success"]:
                    new_video_path = result.get("file_path")

                    new_item = self.make_download_tile(
                        result["title"], new_video_path
                    )

                    self.downloads_list.controls.insert(0, new_item)
                    self.URL_Field.value = ""
                    self.page.update()

            except Exception as thread_error:
                print(f"Background Thread Error: {thread_error}")

            finally:
                self.btn_text.value = "Download"
                self.button_progress.value = 0.0
                self.page.update()

        self.page.run_thread(background_download)

    def text_scale_size(self):
        current_size = self.page.width

        calculated_size = current_size * 0.04  # 4% fo the screen width.

        if calculated_size < 18:
            self.dynamid_MultiDL_label.size = 18
            self.supported_text.size = 10
            self.recently_downloaded_label.size = 10
            self.view_all.size = 10
            self.download_button.width = 500

        elif calculated_size > 32:
            self.dynamid_MultiDL_label.size = 32
            self.supported_text.size = 15
            self.recently_downloaded_label.size = 15
            self.view_all.size = 18
            self.download_button.width = 1000

        else:
            self.dynamid_MultiDL_label.size = calculated_size
            self.supported_text.size = 10
            self.recently_downloaded_label.size = 15
            self.view_all.size = 18
            self.download_button.width = 1000

    def window_resizer(self, e):
        self.text_scale_size()
        self.dynamid_MultiDL_label.update()
        self.supported_text.update()
        self.recently_downloaded_label.update()
        self.recently_downloaded_container.update()


if __name__ == "__main__":
    ft.run(main=MultiDL, assets_dir="assets")