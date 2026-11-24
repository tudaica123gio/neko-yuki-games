# APK cài được trên mọi máy Android

Chuẩn cho APK của mọi game Neko Yuki. Trước khi phát hành, chạy:

```
python3 tools/check_apk.py <file.apk>
```

Kết quả phải là "CAI DUOC". Công cụ tìm các nguyên nhân gây lỗi "Đã xảy ra sự cố khi phân tích cú pháp gói" và
"Chưa cài đặt ứng dụng". Nó chỉ kiểm tra cấu trúc, không kiểm tra chữ ký có hợp lệ về mặt mã hoá hay không.
Muốn kiểm tra chữ ký thì dùng `apksigner verify`.

## Chuẩn

| Mục | Giá trị | Vì sao |
| --- | --- | --- |
| minSdk | 21 (Android 5.0) | phủ gần như mọi máy còn dùng; dưới 21 cần sửa và thử thêm phần chạy MIDP |
| targetSdk | 36 (Android 16) | chuẩn Google Play; lên 36 phải xử lý nút Back kiểu mới (OnBackInvokedCallback) |
| Chữ ký | v1 + v2 + v3 | v1 cho Android 5-6; v2/v3 bắt buộc từ Android 11 khi targetSdk ≥ 30 |
| resources.arsc | lưu không nén, căn lề 4 byte (`zipalign -p 4`) | Android 11+ từ chối cài nếu sai |
| classes.dex | dịch với `--min-api 21` (dex 035) | dex mới hơn không chạy trên máy cũ |
| Thư viện native | không dùng, hoặc đủ armeabi-v7a + arm64-v8a (+ x86 / x86_64) | thiếu chip nào thì máy đó không cài được |
| `android:exported` | ghi rõ cho Activity có intent-filter | thiếu là lỗi phân tích gói từ Android 12 |
| testOnly / debuggable | không bật ở bản người chơi | testOnly không cài tay được; debuggable bị Play Protect cảnh báo |
| Khoá ký | một khoá cố định cho mọi phiên bản | đổi khoá thì bản mới không cài đè lên bản cũ được |

## Phát hành

- **Gửi APK thành file riêng**, không chỉ để trong file ZIP. Bấm vào APK nằm trong ZIP (xem trong trình quản lý file)
  sẽ báo lỗi phân tích gói. Bắt buộc phải giải nén ra trước khi cài.
- **Ghi dung lượng và mã SHA-256 của APK** cạnh link tải, để người chơi biết file tải về có đủ không. Gửi qua
  Zalo / Messenger hoặc tải bị ngắt có thể làm file thiếu.
- **Máy Xiaomi / Redmi (HyperOS):** cho phép "Cài ứng dụng không rõ nguồn gốc" cho app quản lý file đang dùng.

## Đã kiểm

DDLC v2.6 bản người chơi (90,3 MB, nhánh apk-smoke) đạt:
- minSdk 21, targetSdk 35, dex 035;
- không có thư viện native;
- chữ ký v1 + v2 + v3;
- resources.arsc lưu không nén và căn lề đúng.

Công cụ cũng bắt đúng 4 kiểu file hỏng: bị cắt ngắn, đóng gói lại sai, APK nằm trong ZIP, file khác đổi đuôi.
