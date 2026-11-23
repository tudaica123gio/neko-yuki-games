# Kho tài nguyên game của Neko Yuki

Kho này giúp game **nhẹ khi cài** và **chỉ tải tài nguyên của phần người chơi đang vào**.
Mọi việc đóng gói và phát hành đều chạy tự động.

- Bạn bỏ file tài nguyên vào đúng thư mục.
- GitHub tự nén thành gói, tính mã kiểm tra SHA-256 và đưa lên mục Releases.
- Game tải gói khi cần, đối chiếu mã kiểm tra, rồi lưu lại để lần sau không tải nữa.

## Thêm hoặc sửa tài nguyên

**Cách dễ nhất:** gửi file cho Claude và nói rõ game nào, gói nào. Claude sẽ đưa lên giúp.

**Tự làm trên web GitHub:**

1. Mở thư mục `games/<tên-game>/packs/<tên-gói>/` trong kho này.
2. Bấm **Add file → Upload files**, kéo file vào, bấm **Commit changes**.
3. Chờ khoảng một phút. Tab **Actions** hiện dấu tích xanh là gói mới đã được phát hành.

Muốn tạo gói mới thì tạo thư mục mới trong `packs/`. Tên game và tên gói chỉ dùng
chữ thường, số, dấu `-` và `_` (ví dụ `fnaf`, `night-1`, `music_hd`).

## Cấu hình thiết bị dùng chung

Mọi game có màn **Cấu hình thiết bị** theo một chuẩn chung: nút Tự động tối ưu, chọn hệ điều hành (có Android),
nút Tự tối ưu cho hệ điều hành đang chọn, độ phân giải, hiệu năng, đồ hoạ, điều khiển, bố cục, cỡ chữ.
Phần nhận diện máy và chọn cấu hình nằm trong `shared/device/NekoDevice.java`, cài vào game bằng
`python3 tools/install_device.py <thư mục src của game> [package]`. Chi tiết: [shared/device/README.md](shared/device/README.md).

## Cấu trúc thư mục

```
games/
  demo/                     một game
    packs/
      chapter1/             một gói = một phần của game
        img/title.png
        script.txt
      chapter2/
shared/credits/             ảnh đại diện và link dùng cho màn hình credits
shared/device/              module nhận diện máy + tự tối ưu dùng chung (NekoDevice)
client/android-pc/          code tải tài nguyên cho Android và PC
client/j2me/                code tải tài nguyên cho J2ME
tools/                      script đóng gói (tự chạy, không cần đụng tới)
tests/                      bộ kiểm thử
```

Nên chia gói theo phần chơi: mỗi chương, mỗi đêm, nhạc, ảnh HD là một gói riêng.
Phần cốt lõi (menu, phần chơi đầu, credits) vẫn để trong bản cài để chơi được khi không có mạng.

## Địa chỉ tải

Mỗi game có một địa chỉ cố định:

```
https://github.com/tudaica123gio/neko-yuki-games/releases/download/assets-<tên-game>/
```

Trong đó có `manifest.txt` (danh sách gói) và các file gói `.zip` (Android/PC), `.npk` (J2ME).

## Dùng trong game

### Android và PC

Chép `client/android-pc/nekoyuki/assets/NekoAssets.java` vào project.

```java
NekoAssets assets = new NekoAssets(
    "https://github.com/tudaica123gio/neko-yuki-games/releases/download/assets-demo/",
    new File(getFilesDir(), "assets"));          // PC: new File("assets")

// Chạy trong luồng riêng, không chạy trên luồng giao diện.
try { assets.refreshManifest(); }
catch (IOException e) { assets.loadCachedManifest(); }   // mất mạng: dùng bản đã lưu

assets.ensurePack("chapter1", new NekoAssets.Progress() {
    public void onProgress(String pack, long done, long total) {
        // cập nhật thanh tiến trình
    }
});
InputStream in = assets.open("chapter1", "img/title.png");
```

### J2ME

Chép `Sha256.java` và `NekoAssetsME.java` trong `client/j2me/nekoyuki/assets/` vào project.
Cần MIDP 2.0 và JSR-75 (J2ME Loader có sẵn).

```java
NekoAssetsME assets = new NekoAssetsME(
    "https://github.com/tudaica123gio/neko-yuki-games/releases/download/assets-demo/",
    NekoAssetsME.defaultDir("demo"));

// Chạy trong Thread riêng.
try { assets.refreshManifest(); }
catch (IOException e) { assets.loadCachedManifest(); }

assets.ensurePack("chapter1", null);
byte[] png = assets.read("chapter1", "img/title.png");
Image img = Image.createImage(png, 0, png.length);
```

### Các hàm chính (giống nhau ở cả hai bản)

| Hàm | Tác dụng |
| --- | --- |
| `refreshManifest()` | Lấy danh sách gói mới nhất |
| `loadCachedManifest()` | Dùng danh sách đã lưu khi mất mạng |
| `isInstalled(gói)` | Gói đã có trên máy và đúng bản mới nhất chưa |
| `sizeOf(gói)` | Dung lượng cần tải, để hỏi người chơi trước khi tải |
| `ensurePack(gói, tiến trình)` | Tải gói nếu chưa có hoặc đã có bản mới |
| `verify(gói)` | Kiểm tra lại gói trên máy, trả về `false` nếu đã bị sửa |
| `open(gói, file)` / `read(gói, file)` | Đọc một file trong gói |
| `remove(gói)` | Xoá gói để giải phóng bộ nhớ |

## Chống sửa file

- Gói tải về sai mã SHA-256 sẽ bị huỷ và báo lỗi `TamperedException`. Game nên hiện
  cảnh báo "Tài nguyên không phải bản chính thức của Neko Yuki".
- Gọi `verify(gói)` khi mở game để phát hiện file trên máy bị sửa sau khi tải.
- Danh sách mã kiểm tra nằm trên kho GitHub này, chỉ chủ kho mới sửa được.

Giới hạn cần biết: cách này bảo vệ **tài nguyên tải về**. Nếu ai đó sửa chính file cài
(APK/JAR) thì họ cũng có thể gỡ đoạn kiểm tra. Vì vậy hãy luôn ghi rõ link tải chính
thức trên Facebook, YouTube và Discord.

## Giới hạn dung lượng

- Upload qua web: tối đa 25 MB mỗi file. File lớn hơn thì gửi cho Claude.
- Một file trong kho: tối đa 100 MB. Một gói phát hành: tối đa 2 GB.
- Nên giữ mỗi gói dưới 100 MB để tải được bằng mạng di động.

## Kiểm thử

```
bash tests/run.sh          # tải tài nguyên
bash tests/run_device.sh   # nhận diện máy + tự tối ưu
python3 tools/check_models.py   # bảng model máy
```

Lệnh này đóng gói thử, dựng máy chủ giả lập GitHub, rồi chạy cả hai bản code tải:
tải gói, đối chiếu nội dung, phát hiện file bị sửa, chạy khi mất mạng và cập nhật gói.
