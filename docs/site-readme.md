# Metadata Wiper for Android

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A simple, private, and modern Android application to see and remove metadata from your photos and videos, such as where and when they were taken and which phone took them. Built with Jetpack Compose and Material 3.

## ✨ Features

* **Privacy-First:** All processing happens 100% offline and locally on your device. The app has no internet permission, and your files are never shared or uploaded.
* **Photos and Videos:** Cleans JPEG, PNG, WebP, HEIC/HEIF, AVIF, GIF and BMP images, and MP4, MOV, 3GP, M4V, MKV, WebM, AVI and MPEG-TS (.ts, .mts, .m2ts) videos.
* **No Quality Loss:** Metadata is removed without re-compressing images or re-encoding videos, so the picture and sound are unchanged.
* **More Than EXIF:** Removes EXIF, XMP, IPTC, comments, embedded thumbnails, Motion Photo videos and data hidden after the end of the image.
* **Video Metadata Too:** Removes location, camera make and model, recording dates, titles, tags, cover art, telemetry tracks that can hold GPS (from action cameras and drones), timecode, and the camcorder date and location stored inside the video stream.
* **Built for Large Videos:** Shows progress with a Cancel button, and checks free space before it starts.
* **See What's Inside:** Metadata is grouped by risk (Location, People, Device, Date and time, Thumbnail and Embedded media) with readable values.
* **Clean & Save, Share or Replace:** Save a cleaned copy and leave the original untouched, share cleaned copies straight to another app, or replace the original if you turn that on.
* **Share to the App:** Share photos and videos from your gallery or any other app to clean them.
* **Know What Changed:** See what was removed and what was kept for each file. If part of a file isn't recognised, the app says so instead of calling it clean.
* **Privacy Settings:** Choose whether to keep the color profile and orientation, and whether to give cleaned files random names.
* **No Permissions:** The app requests no permissions at all. It uses the Android file picker and share sheet, which give access to only the files you choose.
* **Dark Theme Support:** Choose between a Light, Dark, or System default theme, with Material You colors.
* **Languages:** Available in English and Spanish.

> [!NOTE]
> For detailed information about the app's privacy practices, please refer to the [Privacy Policy](privacy-policy.html).

## 🛠️ Built With

* **[Kotlin](https://kotlinlang.org/)**: The official language for Android development.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose)**: Android's modern toolkit for building native UI.
* **[Material 3](https://m3.material.io/)**: The latest version of Google's open-source design system.
* **[Coil](https://coil-kt.github.io/coil/)**: An image loading library for Android backed by Kotlin Coroutines, also used for on-device video previews.
* **[AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface)**: For reading camera details to display. Metadata removal is done by the app's own Kotlin code.
* **[Jetpack ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)**: To manage UI-related data in a lifecycle-conscious way.
