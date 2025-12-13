# Privacy Policy

**Last Updated:** November 19, 2025

Thank you for using Metadata Wiper (the "App"). This privacy policy outlines how data is handled within the application. Your privacy is our top priority.

## 1. Data We Do Not Collect

Metadata Wiper is designed to be a private and secure utility. We **do not** collect, store, share, or transmit any personal information or data from you or your device.

This includes:

- **Personal Information:** We do not ask for or collect any personal information such as your name, email address, location, or contacts.
- **Usage Analytics:** The App does not contain any third-party analytics or tracking tools. We do not monitor how you use the app.
- **Image or File Data:** Your images and their metadata are processed locally on your device and are never sent to any external server.

## 2. How the App Works

The function of Metadata Wiper is to remove EXIF (Exchangeable Image File Format) metadata from images you select. The process is entirely offline:

1. **File Selection:** The App uses the standard Android Storage Access Framework for you to select one or more images from your device's local storage. The App only gains temporary, user-consented access to the specific files you choose.
2. **Local Processing:** All metadata reading and removal happens entirely and exclusively on your device.
3. **Saving the Cleaned File:** After processing, you have two options for saving the cleaned images:
    - **Overwrite Mode:** If you enable this option in the settings, the App will overwrite the original files with the cleaned versions.
    - **Create a New File:** If overwrite mode is disabled, the App will prompt you to choose a new location on your device to save the "cleaned" image(s). You have full control over where the files are saved.
4. **No Original File Modification (by default):** The App does not alter your original image file unless you explicitly enable the overwrite option. By default, it creates a new, separate copy with the metadata removed, leaving your original file untouched.

## 3. Permissions Required

The Metadata Wiper application uses modern Android practices that minimize the need for permissions.

- The app **does not require** broad `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` permissions. It uses the system file picker, which grants temporary access to files selected by the user.

## 4. Third-Party Libraries

The App uses the following open-source libraries, which have been reviewed to ensure they align with our privacy standards:

- **AndroidX Libraries (Jetpack):** Used for core application functionality (`androidx.core.ktx`, `androidx.lifecycle.runtime.ktx`, `androidx.activity.compose`, `androidx.material3`).
- **Jetpack Compose:** Used for building the app's user interface (`androidx.ui`, `androidx.ui.graphics`, etc.).
- **Coil (coil-compose):** Used for efficiently loading and displaying the image you select *locally* on your device's screen.
- **ExifInterface (androidx.exifinterface):** Used for reading and removing the EXIF metadata from the image file on your device.

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
