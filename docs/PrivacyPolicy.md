# Privacy Policy

**Last Updated:** October 26, 2026

Thank you for using Metadata Wiper (the "App"). This privacy policy outlines how data is handled within the application. Your privacy is our top priority.

## 1. Data We Do Not Collect

Metadata Wiper is designed to be a private and secure utility. We **do not** collect, store, share, or transmit any personal information or data from you or your device.

This includes:

- **Personal Information:** We do not ask for or collect any personal information such as your name, email address, location, or contacts.
- **Usage Analytics:** The App does not contain any third-party analytics, crash reporting, advertising or tracking tools. We do not monitor how you use the app.
- **Image or File Data:** Your images and their metadata are processed locally on your device and are never sent to any external server.

The App works entirely offline. It does not have the internet permission, so it has no way to send anything off your device.

## 2. How the App Works

The function of Metadata Wiper is to remove metadata, such as location, device details and dates, from images you select. It supports JPEG, PNG, WebP, HEIC/HEIF, AVIF, GIF and BMP images. The process is entirely offline:

1. **File Selection:** You choose images in one of two ways, and the App only gains temporary, user-consented access to the specific files you choose:
    - **The system file picker:** The App uses the standard Android Storage Access Framework for you to select one or more images.
    - **Sharing to the App:** You can share images to the App from your gallery or another app. Android gives the App access to just the images you shared.
2. **Local Processing:** All metadata reading and removal happens entirely and exclusively on your device. While an image is being cleaned, the App keeps a temporary copy in its private storage, which other apps can't access. That copy is deleted as soon as the image is done, including when cleaning fails.
3. **What Is Kept:** By default the App keeps the color profile and orientation, so images still look right. They rarely identify anyone. You can choose to remove them too in the App's Privacy settings.
4. **Saving or Sharing the Cleaned File:** After processing, you choose what happens to the cleaned images:
    - **Clean & save:** The App asks you to choose a location on your device to save the cleaned image(s). You have full control over where the files are saved.
    - **Clean & replace:** If you enable overwriting in the settings, the App replaces the original files with the cleaned versions. It only replaces a file once the cleaned copy has been checked.
    - **Clean & share:** The App saves the cleaned copies in its private storage and opens the Android share sheet. Only the app you pick is given access to those copies. Anything you send that way is handled under that app's own privacy policy. The App deletes these cleaned copies the next time you share or open the App.
5. **No Original File Modification (by default):** The App does not alter your original image file unless you explicitly enable the overwrite option. By default, it creates a new, separate copy with the metadata removed, leaving your original file untouched.

## 3. Permissions Required

The App requests **no permissions at all**.

- It does not have the internet permission, so it cannot connect to any network.
- It does not require broad `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` or `READ_MEDIA_*` permissions. It uses the system file picker and Android's share sheet, which grant temporary access to only the files you choose.
- It does not request `ACCESS_MEDIA_LOCATION`. Because of this, Android hides the exact GPS position in your images from the App, and the App shows only that location data is present. The location data is still removed from the cleaned image.

## 4. Third-Party Libraries

The App uses the following open-source libraries, which have been reviewed to ensure they align with our privacy standards:

- **AndroidX Libraries (Jetpack):** Used for core application functionality (`androidx.core.ktx`, `androidx.lifecycle.runtime.ktx`, `androidx.activity.compose`, `androidx.material3`, `androidx.core.splashscreen`).
- **Jetpack Compose:** Used for building the app's user interface (`androidx.ui`, `androidx.ui.graphics`, etc.).
- **Coil (coil-compose):** Used for efficiently loading and displaying the image you select *locally* on your device's screen.
- **ExifInterface (androidx.exifinterface):** Used for reading camera details, such as exposure and lens settings, to show them to you. Metadata removal is done by the App's own code.

None of these libraries are used to collect or transmit personal user data from the App.

## 5. In-App Links

- **Rate and Review:** The app includes a link to its Google Play Store page to allow you to provide a rating and review. This action is entirely optional and is initiated by you.
- **Privacy Policy:** The app includes a link to this privacy policy for your convenience.

### 6. Children's Privacy

This App does not collect any personal information, making it safe for all users, including children. We do not knowingly collect any data from children under the age of 13.

### 7. Changes to This Privacy Policy

We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy on the app's store listing. You are advised to review this Privacy Policy periodically for any changes.

### 8. Contact Us

If you have any questions or suggestions about this Privacy Policy, do not hesitate to contact us.

Email: <prateek.mahendrakar@gmail.com>
