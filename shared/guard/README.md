# NekoGuard: khoá chống sửa, dấu bản quyền từng file, phòng khoá "Just Neko Yuki"

Áp dụng cho mọi game của Neko Yuki: FNaF 1, FNaF 2, DDLC và mọi game sau này.

Khi game bị sửa, dù chỉ một chút (xoá credits Neko Yuki, đổi dữ liệu hoặc tài nguyên, thay file), game hiện
**phòng khoá** che toàn màn hình:

- cảnh báo game đã bị sửa đổi, không phải bản chính chủ;
- chỗ tải game gốc kèm file gốc của Neko Yuki (Facebook, YouTube, Discord);
- dòng bản quyền.

Game không cho chơi tiếp. Mọi phím đều là thoát game. File lưu của người chơi không bị xoá.
Riêng **DDLC**, phòng khoá có cả **Monika** và **Neko Yuki**.

## Thành phần

| File | Việc |
| --- | --- |
| `NekoGuard.java` | module trong game (CLDC 1.0 / MIDP 2.0): đọc danh sách ký, kiểm tra từng tài nguyên, kiểm tra credits, vẽ phòng khoá |
| `../../tools/sign_assets.py` | chạy lúc build: nhúng dấu bản quyền, ghi danh sách ký `neko.sig`, sinh `NekoGuardData.java` |
| `art/nekoyuki_halfbody_*.png`, `art/nekoyuki_fullbody_*.png` | ảnh Neko Yuki đã tách nền, cao 96 / 128 / 176 / 240 / 320 / 480 px, có dấu bản quyền trong file |
| `integration/fnaf_FnafCanvas.java.txt` | mã gắn vào FNaF, đã chạy thử |
| `../../tools/install_modules.py` | chép NekoDevice + NekoGuard vào mã nguồn game |

## Dấu bản quyền: ghi đúng chủ

- **Tài nguyên của Neko Yuki** (ảnh Neko Yuki, chữ Việt hoá, mã nguồn, ảnh tự vẽ) được nhúng dấu
  "Copyright: Neko Yuki". Đánh dấu chúng bằng `--owned`.
- **Tài nguyên lấy từ game gốc** (ảnh, nhạc của FNaF hoặc DDLC) không ghi là bản quyền Neko Yuki.
  Chúng chỉ được ký là "bản build chính thức của Neko Yuki" trong `neko.sig`. Game vẫn kiểm tra và khoá như nhau.
- **Dòng bản quyền trong game:** `NekoGuard.copyright()`. Đặt `NekoGuard.original` theo từng game:
  - FNaF: `"Scott Cawthon"`, hiện ra "© Neko Yuki (chuyển thể & mã nguồn) - game gốc © Scott Cawthon".
  - DDLC: `"Team Salvato"`.
  - Game hoàn toàn của Neko Yuki: `null`, hiện ra "© Neko Yuki".
  - Không ghi "giấy phép hợp lệ" vì chưa có giấy phép của tác giả game gốc.

## Giới hạn thật

- Khoá bắt được: sửa hoặc thay tài nguyên, xoá credits, sửa danh sách ký, thêm file lạ, gỡ dấu bản quyền.
- Người rành sửa thẳng mã game (file `.class`) vẫn có thể gỡ đoạn kiểm tra. Không cách nào chặn tuyệt đối.
  Vì vậy luôn ghi rõ link tải chính thức.
- Dấu trong file chứng minh tài nguyên là của Neko Yuki. Nhưng dấu không thể làm một game khác, do người khác viết,
  tự hiện Neko Yuki lên chặn, vì game đó không chứa mã NekoGuard. Phòng khoá chỉ xuất hiện trong game có NekoGuard.

## Gắn vào game

### 1. Chép module

```
python3 tools/install_modules.py <src của game> [package]
```

### 2. Ký tài nguyên lúc build

Chạy trên bản sao thư mục tài nguyên, trước bước đóng gói:

```
python3 tools/sign_assets.py --res <bản sao res> --game fnaf --java <src build>/NekoGuardData.java \
        --owned "nekoyuki*,lang_*.txt,credits*" --stamp
```

### 3. Kiểm tra lúc chạy

```java
NekoGuard.original = "Scott Cawthon";
NekoGuard.en = /* đang chơi tiếng Anh */;
NekoGuard.loadManifest(getClass().getResourceAsStream("/neko.sig"), NekoGuardData.SIG);
NekoGuard.checkCredits(CREDITS, new String[]{"Scott Cawthon"});
// mỗi khi đọc một tài nguyên:
NekoGuard.checkFile("/img/a.png", bytes);
// game đã có cách kiểm tra riêng thì chỉ cần:
if (bịSửa) NekoGuard.trip("lý do");
```

### 4. Vẽ và phím

- **Vẽ:** khi `NekoGuard.locked`, chỉ gọi
  `NekoGuard.paint(g, w, h, nhânVật, fontTiêuĐề, fontChữ, System.currentTimeMillis())` và không vẽ gì khác.
- **Phím:** mọi phím, kể cả chạm, gọi `notifyDestroyed()`.
- **Font:**
  - Màn nhỏ: truyền font bitmap của game (đủ dấu tiếng Việt). Font phải có dấu ©; với FNaF, chạy lại `buildfont.py`.
  - Màn từ 360 px trở lên (J2ME Loader, APK): truyền `null` để dùng font hệ thống của máy.

### 5. Ảnh nhân vật

- Đặt ảnh Neko Yuki ngoài gói tài nguyên mã hoá (FNaF: thư mục `/ny` của jar), để phòng khoá vẫn hiện được khi
  gói tài nguyên bị sửa.
- Chọn cỡ cao nhất mà không quá 50% chiều cao màn dọc, hoặc 78% chiều cao màn ngang. Màn HD nên thêm ảnh cao 480.

### FNaF 1 / FNaF 2

FNaF đã có kiểm tra riêng (`FnafPack`): hỏng thì chuyển sang `ST_LOCK`. Chỉ cần đổi cách vẽ `ST_LOCK` sang
NekoGuard, theo `integration/fnaf_FnafCanvas.java.txt`. Phần này đã chạy thử trên FNaF 1 v4.7:

- biên dịch, `make.py`, ProGuard đạt;
- chỉ dùng API CLDC 1.0;
- chụp màn từ 128x160 đến 1872x864, tiếng Việt và tiếng Anh.

FNaF 2 làm y hệt, đổi `Fnaf` thành `F2`.

### DDLC (chưa chạy thử: source không kèm ảnh game gốc)

DDLC đã có cờ `tampered` (kiểm tra CRC script, credits, lang, MAS) nhưng hiện chỉ vẽ một dải cảnh báo.
Cần đổi thành khoá hẳn:

1. Khi `tampered` ở bản người dùng: `NekoGuard.trip(...)`, rồi chuyển sang một màn khoá riêng chỉ gọi `NekoGuard.paint`.
2. Truyền hai nhân vật `{ảnh Monika, ảnh Neko Yuki}`, cùng chiều cao.
   - Ảnh Monika lấy từ hình có sẵn trong game, không vẽ mới.
   - Đặt `NekoGuard.just = "Just Monika. Just Neko Yuki."` và `NekoGuard.original = "Team Salvato"`.
3. Font: màn nhỏ dùng font BFont của game qua một lớp `NekoGuard.Text`; màn lớn truyền `null`.
4. Mọi phím và chạm: thoát game.

## Kiểm thử

```
bash tests/run_guard.sh [thư mục lưu ảnh chụp]
```

Ký một bộ tài nguyên mẫu rồi chạy 21 kiểm tra. Có kiểm tra SHA-256, sửa 1 bit, thay file, file lạ, mất dấu bản
quyền, sửa hoặc xoá `neko.sig`, xoá Neko Yuki khỏi credits. Sau đó chụp phòng khoá ở 7 cỡ màn, với 3 kiểu:
FNaF, tiếng Anh, DDLC.
