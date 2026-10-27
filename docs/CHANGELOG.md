# Changelog

All notable changes to this project will be documented in this file.

## [2.0.0] - 26-Oct-2026

### New

- **More Formats**: Cleans JPEG, PNG, WebP, HEIC/HEIF, AVIF, GIF and BMP images, not just JPEG.
- **No Quality Loss**: Metadata is removed without re-compressing the image, so the picture itself is unchanged.
- **Removes More Than EXIF**: Also removes XMP, IPTC, comments, embedded thumbnails, Motion Photo videos and data hidden after the end of the image.
- **Grouped by Risk**: Metadata is grouped into Location, People, Device, Date and time, Thumbnail and Embedded media, with the riskiest first and readable values instead of raw tags.
- **Results Screen**: After cleaning, you see what was removed and what was kept for each image. If part of a file isn't recognised, the app says so instead of calling it clean.
- **Share In and Out**: Share images to the app from your gallery or any other app, and use Clean & share to send cleaned copies straight to another app.
- **Privacy Settings**: Choose whether to keep the color profile (on by default) and orientation (on by default), and whether to give cleaned files random names (off by default).

### Improvements

- **Redesigned UI**: New Home, Review, Results and Settings screens with clear Clean & save, Clean & share and Clean & replace actions.
- **Safer Cleaning**: Each cleaned image is checked before it's saved, and the original is only replaced once the cleaned copy is ready.
- **Clearer Wording**: Says "metadata" instead of "EXIF", shows dates in your own format and orientation in plain words, in English and Spanish.
- **No Permissions**: The app asks for no permissions at all.

## [1.4.0] - 22-Mar-2026

- Updated gradle and packages.
- Switched from OpenJDK 17 to OpenJDK 25 for improved performance and security.
- App uses minimization to reduce APK size.

## [1.3.2] - 29-Jan-2026

- **Detailed Metadata View**: You can see exactly what information is attached to your photos, including file names and detailed EXIF counts.
- **Redesigned UI**: Redesigned the button layout and added intuitive tap gestures to make navigating the app faster and easier.
- **Improved Localization**: Improved Spanish translations and clearer prompts throughout the app for a better local experience.
- **Technical Updates**: Updated to support the latest Android 15/16 features and improved overall file handling for faster, more reliable performance.

## [1.1.0] - 13-Dec-2025

### New

- **Multi-Image Selection**: You can now select and process multiple images at once.
- **Overwrite Original File**: Added a setting to overwrite the original image file instead of creating a new one.
- **Image Grid View**: Selected images are now displayed in a 3-column grid.
- **Adaptive Splash Screen**: The splash screen now adapts to the system's light or dark theme.
- **Localization**: Added full localization support, including Spanish translations.
- **Settings Screen Links**: Added direct links to rate the app on the Play Store and to view the privacy policy.

### Improvements

- **Redesigned Settings Screen**: The settings view is now organized into sections for a cleaner, more intuitive layout.
- **Upgraded Icons**: Replaced deprecated Material Icons with modern, downloadable Material Symbols.

## [1.0.1] - 22-Nov-2025

### New

- Initial release of the Metadata Wiper application.
- Basic functionality to select a single image, view its metadata, and save a cleaned copy.
