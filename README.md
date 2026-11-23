# Mod UI — Liquid Glass cho Google Gemini

Ứng dụng Android (Kotlin, Material 3 Dark Theme, `minSdk 26`, `targetSdk 34`) tùy biến giao diện web **Google Gemini** (`https://gemini.google.com/app`) theo phong cách **Liquid Glass (Kính mờ)**, hỗ trợ Hình nền Tĩnh, GIF Động, Video MP4 60fps và Lối tắt (Pinned Shortcut) trực tiếp ra màn hình chính.

## Tính năng chính
- **Tích hợp kết nối Shizuku (`MainActivity`):**
  - Thẻ trạng thái Shizuku ở vị trí đầu tiên trên cùng, tự động cập nhật tức thì qua `addBinderReceivedListener`, `addBinderDeadListener` và `addRequestPermissionResultListener`.
  - Hỗ trợ yêu cầu quyền (`Shizuku.requestPermission(1001)`), mở nhanh ứng dụng Shizuku (`moe.shizuku.privileged.api`) và tự động thu gọn thẻ kèm huy hiệu xanh khi đã kết nối.
- **Màn hình điều khiển hợp nhất (`MainActivity`):**
  - Công tắc **Bật Mod UI** lưu trạng thái tức thì vào `SharedPreferences`.
  - Tạo lối tắt **Gemini** ra màn hình chính (`ShortcutManagerCompat.requestPinShortcut`) mở thẳng `GeminiLauncherActivity`.
  - 2 Tab chọn hình nền rõ ràng (`MaterialButtonToggleGroup`):
    - **Ảnh Tĩnh:** Bộ hình nền mặc định + nút `+` chọn ảnh (`JPG`, `PNG`, `WEBP`) lưu vào `filesDir/bg_static.jpg`.
    - **Ảnh Động:** Bộ GIF động mặc định (kèm biểu tượng Play) + nút `+` chọn `GIF`, `MP4`, `WEBM` lưu vào `filesDir/bg_animated.gif` hoặc `filesDir/bg_animated.mp4`.
  - Chỉnh sửa hiệu ứng: **Độ mờ kính (0px – 30px)**, **Độ tối lớp phủ (0% – 80%)**, **Viền phát sáng (Ambient Glow)**, **Ẩn câu hỏi gợi ý**.
- **Trình chạy Gemini (`GeminiLauncherActivity`):**
  - Edge-to-Edge toàn màn hình (Status Bar & Navigation Bar trong suốt).
  - Kiến trúc `FrameLayout` 3 lớp tối ưu hiệu năng:
    1. **Background Layer:** `ImageView` (Coil + Coil-GIF) cho Ảnh Tĩnh / GIF; `TextureView` + `MediaPlayer` (tự động lặp, tắt tiếng, center-crop) cho Video MP4 60fps.
    2. **Dim Overlay Layer:** Lớp phủ tối điều chỉnh `alpha` theo cài đặt.
    3. **WebView Layer:** Trong suốt, tăng tốc phần cứng (`LAYER_TYPE_HARDWARE`), xóa `"; wv"` khỏi User-Agent để đăng nhập Google bình thường, hỗ trợ Cookie bên thứ ba, DOM Storage và đính kèm ảnh/tệp (`onShowFileChooser`), tự động tiêm `mod_ui.css` và `mod_script.js` trong `onPageFinished` và `doUpdateVisitedHistory`.

## Hướng dẫn build APK
1. Mở thư mục `ModUI` bằng **Android Studio** (JDK 17, Android SDK 34).
2. Chờ Android Studio đồng bộ Gradle (Gradle 8.7 / AGP 8.5.2).
3. Chọn **Build > Build Bundle(s) / APK(s) > Build APK(s)** (hoặc chạy `gradle assembleDebug` từ terminal đã cấu hình Android SDK).
