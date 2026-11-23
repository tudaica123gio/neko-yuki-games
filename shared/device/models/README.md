# Bảng model máy cho mục "Dòng máy"

Dữ liệu dùng chung cho mục **Dòng máy** trong màn Cấu hình thiết bị của mọi game. Người chơi chọn đúng
model, game tự đặt kích thước màn hình, bộ mã phím mềm và mức đồ hoạ cho model đó.

Bảng được tổng hợp từ tra cứu web ngày 23/11/2026. Nguồn chính: lpcwiki (miraheze), Wikipedia, GSMArena,
j2me.fandom (Canvas KeyCodes), tài liệu libretro và GitHub của J2ME Loader / FreeJ2ME.

| File | Nội dung |
| --- | --- |
| `models.csv` | 374 model, gồm Nokia S40 / S60 / Symbian, Sony Ericsson, Samsung, LG, Motorola, Siemens, BlackBerry, máy clone MTK, máy HMD đời mới |
| `key_profiles.csv` | 12 bộ mã phím mềm: N, SE, SS, LG, LG2, M1, M2, M3, S, S2, BB, T |
| `gfx_tiers.csv` | 5 mức đồ hoạ (0 Tối giản đến 4 Rất cao), kèm heap điển hình và FPS gợi ý |
| `emulators.csv` | độ phân giải mặc định của J2ME Loader và các giả lập, cùng màn ảo gợi ý cho từng tỉ lệ màn Android |

## Các cột của models.csv

- `vendor`, `model`: hãng và model. Model trùng tên khác đời có ghi chú, ví dụ 6600 (S60, 2003) khác 6600 slide (S40, 2008).
- `platform`, `edition`: S40 1st…DP2.0, S60 1st…5th, Symbian^3, SE JP-1…JP-8.5, Motorola P2K / MAGX...
- `width`, `height`, `orientation`: kích thước màn (D dọc, N ngang, V vuông).
- `touch`, `qwerty`: 1 = có.
- `midp`, `cldc`, `jsr`: các JSR cách nhau bằng dấu `;`.
  - Ô `midp` trống nghĩa là máy không chạy J2ME (Series 30, KaiOS), cần chơi bằng J2ME Loader.
- `heap_kb`, `jar_kb`: số KB. `inf` là không giới hạn cố định, ô trống là chưa biết.
- `key_profile`: mã trong `key_profiles.csv`. `N+T` nghĩa là bàn phím Nokia cộng cảm ứng.
- `gfx_tier`: mức trong `gfx_tiers.csv`.
- `confidence`: độ tin cậy.
  - A: có nguồn cụ thể.
  - B: suy ra từ nền tảng.
  - C: hiểu biết chung, cần kiểm tra lại trước khi phát hành.

## Lưu ý khi đưa vào game

- **Không tin tuyệt đối vào bảng.** Game luôn gọi `setFullScreenMode(true)` rồi đọc lại `getWidth()` / `getHeight()`. Một số máy SE và Asha có vùng vẽ Java nhỏ hơn màn khi không full-screen, ví dụ 240x266 trên màn 240x320.
- **Siemens dùng -1 / -4 cho phím mềm**, trùng với mã LÊN / PHẢI của Nokia, nên phải chọn đúng hồ sơ phím.
- **Motorola RAZR** có phím mũi tên khác chuẩn, phím xuống là -6.
- **LG Cookie** muốn ẩn bàn phím ảo thì thêm vào manifest `LGE-MIDlet-Display-Nav-Keypad: no` và `LGE-MIDlet-TargetLCD-Width/Height`.
- **Mặc định khi chưa chọn:** S40 3rd+ 240x320, phím N, mức đồ hoạ 2. Mặc định này khớp với J2ME Loader và FreeJ2ME.
- Bảng có thể chỉ đưa một phần vào game, ví dụ bỏ các máy không chạy J2ME, để file JAR nhỏ trên máy yếu.

## Kiểm tra

```
python3 tools/check_models.py
```
