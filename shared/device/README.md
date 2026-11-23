# Cấu hình thiết bị và tự tối ưu: chuẩn chung cho mọi game của Neko Yuki

Mọi game mới đều phải có màn **Cấu hình thiết bị** theo chuẩn dưới đây. Mỗi game tự vẽ giao diện theo màu
và phong cách riêng (đỏ đen kiểu FNaF, hồng kiểu DDLC), còn phần nhận diện máy và chọn cấu hình
dùng chung module `NekoDevice.java` trong thư mục này.

Đã áp dụng: FNaF 1 v4.7, FNaF 2 v1.4, DDLC v2.5.

## Màn hình phải có

Thứ tự từ trên xuống:

1. **Dòng thông tin máy:** tên máy, nền tảng, độ phân giải, bộ nhớ Java, có cảm ứng hay không.
   Dùng `NekoDevice.scan(...)`, ví dụ: `Máy: Nokia5310 (Java), 240x320, bộ nhớ Java 2 MB, cảm ứng: không.`
2. **TỰ ĐỘNG TỐI ƯU (khuyên dùng):** nổi bật nhất. Đưa mọi mục về Tự động theo máy. Lần đầu mở game thì
   vào game luôn; mở từ Cài đặt thì báo đã chọn gì.
3. **Nền tảng / Hệ điều hành:** Tự động, Điện thoại Java, Symbian, J2ME Loader (Android), Android (APK),
   Giả lập PC. Game nào gộp nhóm thì vẫn phải có lựa chọn **Android**.
4. **Tự tối ưu cho \<nền tảng đang chọn\>:** giữ nền tảng, đặt lại mọi mục bên dưới cho hợp nền tảng đó,
   ở lại màn và báo kết quả.
5. **Các mục chỉnh tay**, mỗi mục có giá trị Tự động:
   - độ phân giải / kích thước màn hình
   - hiệu năng và FPS
   - đồ hoạ (Lite / Full / HD, hiệu ứng)
   - điều khiển (cảm ứng / bàn phím)
   - bố cục (ngang / dọc / vuông)
   - cỡ chữ
6. **Độ phân giải:** dòng giải thích. Ví dụ với J2ME Loader: màn nhỏ phóng to sẽ vỡ ảnh, và chỉ cách đặt lại
   trong J2ME Loader. Với APK thì không cần chỉnh.
7. **Bắt đầu** (lần đầu) hoặc **Lưu & quay lại** (mở từ Cài đặt).

Dưới danh sách có một dòng gợi ý cho mục đang chọn. Màn rất nhỏ (128x128, 128x160) thì rút gọn chữ.

## Dùng module

```
python3 tools/install_device.py ../TenGame/src            # lớp không package
python3 tools/install_device.py ../TenGame/src/pkg pkg    # lớp trong package pkg
```

```java
int p = NekoDevice.resolve(nenTangNguoiChoiChon, "vn.nekoyuki.tengame.MainActivity");
NekoDevice.Profile pf = NekoDevice.optimize(p, w, h, Runtime.getRuntime().totalMemory(), hasPointerEvents(), coBanHD);
// pf.gfx, pf.perf, pf.fps, pf.input, pf.layout, pf.font, pf.pack, pf.recW x pf.recH, pf.effects, pf.sound
String info = NekoDevice.scan(p, w, h, mem, touch, false);
String loiKhuyen = NekoDevice.advice(pf, w, h, false);
```

Tham số thứ hai của `detect` / `resolve` là tên lớp Activity trong APK riêng của game. Lớp này chỉ có trong
APK, không có trong file jar chạy bằng J2ME Loader, nên game phân biệt được APK với J2ME Loader. APK cũng có thể
đặt `System.setProperty("nekoyuki.platform", "android-apk")` lúc mở.

Module chỉ dùng API CLDC 1.0: không float, không generics, chạy được trên điện thoại Java đời cũ.

## Quy tắc tự tối ưu

| Nền tảng | Đồ hoạ | Hiệu năng | FPS | Gói tài nguyên | Chữ | Độ phân giải |
| --- | --- | --- | --- | --- | --- | --- |
| Android (APK) | Siêu nét / Cực cao / Cao theo màn | Cao | 30 | HD | Lớn | theo màn máy |
| J2ME Loader | như APK | Cao (màn trên 2,6 triệu điểm ảnh: Cân bằng) | 30 | HD nếu cạnh ngắn ≥ 360 | Lớn | gợi ý 1280x720 (HD) / 640x360 |
| Giả lập PC | Siêu nét / Cực cao | Cao | 30 | HD | Vừa | gợi ý như J2ME Loader |
| Symbian | Cao (cạnh ngắn ≥ 360) / Trung bình | Cân bằng | 25 | HD nếu màn lớn và đủ 8 MB, còn lại Full | Vừa | theo màn máy |
| Điện thoại Java | Trung bình, máy dưới 3 MB hoặc màn nhỏ: Thấp | Cân bằng, dưới 3 MB: Tiết kiệm | 20 | Full, máy yếu: Lite | Vừa | theo màn máy |

Thêm vào đó:

- Màn có cạnh ngắn dưới 176 thì dùng chữ Nhỏ.
- Máy có cảm ứng thì điều khiển bằng cảm ứng, không có thì bàn phím theo cỡ màn.
- Bố cục: ngang nếu cạnh dài gấp từ 1,2 lần, gần vuông (máy gập) là Vuông.

## Danh sách Dòng máy

Mục **Dòng máy** lấy từ bảng dữ liệu chung [models/](models/README.md). Bảng gồm 374 model, kèm độ phân giải,
bộ mã phím mềm và mức đồ hoạ của từng model. Khi làm game mới, đưa bảng này vào game.

## Lưu cấu hình

- Lưu lựa chọn của người chơi, không lưu kết quả tự động, để đổi máy hay cập nhật game thì Tự động vẫn tính lại.
- Giá trị hỏng hoặc của bản mới hơn trong file lưu thì coi là Tự động, không được làm lỗi màn hình.

## Kiểm thử

```
bash tests/run_device.sh
```

Giả lập thuộc tính hệ thống của Nokia S40, Symbian, Sony Ericsson, J2ME Loader, APK, KEmulator, FreeJ2ME,
rồi kiểm tra nhận diện và hồ sơ tối ưu (47 kiểm tra).
