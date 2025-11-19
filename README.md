# Metadata Wiper for Android

[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

A simple, private, and modern Android application to remove EXIF metadata from your images. Built with Jetpack Compose and Material 3.

## ✨ Features

* **Privacy-First:** All processing happens 100% offline and locally on your device. Your images are never shared or uploaded.
* **View Metadata:** Select an image to view all its readable EXIF tags in a clean, formatted table.
* **Remove Metadata:** Creates a new, "cleaned" copy of your image with all removable EXIF data wiped, leaving your original file untouched.
* **Modern UI:** Built entirely with Jetpack Compose and styled with Material 3 components.
* **Dark Theme Support:** Choose between a Light, Dark, or System default theme.
* **No Unnecessary Permissions:** Uses the modern Android Storage Access Framework, requiring no broad storage permissions.

> [!NOTE]
> For detailed information about the app's privacy practices, please refer to the [Privacy Policy](docs/privacy-policy.html).

## 🛠️ Built With

* **[Kotlin](https://kotlinlang.org/)**: The official language for Android development.
* **[Jetpack Compose](https://developer.android.com/jetpack/compose)**: Android's modern toolkit for building native UI.
* **[Material 3](https://m3.material.io/)**: The latest version of Google's open-source design system.
* **[Coil](https://coil-kt.github.io/coil/)**: An image loading library for Android backed by Kotlin Coroutines.
* **[AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface)**: For reading and writing EXIF tags from image files.
* **[Jetpack ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)**: To manage UI-related data in a lifecycle-conscious way.
