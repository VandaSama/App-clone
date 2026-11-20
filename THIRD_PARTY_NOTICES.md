# Third-party notices

This project downloads the BlackBox engine source from [chcdg/FBlackBox](https://github.com/chcdg/FBlackBox) during the GitHub Actions build. The engine repository includes an Apache License 2.0 `LICENSE` file. The workflow copies that license into the build artifact and GitHub Release beside the APK as `BLACKBOX-APACHE-2.0-LICENSE.txt`.

The Multi Profil FB app code in this project is separate from the downloaded BlackBox engine. Facebook is not bundled with this project; the user must already have the official `com.facebook.katana` package installed on their device.
