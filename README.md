# AstraxEvent

Hệ thống sự kiện cho server **BoxMine RPG** (Paper 1.21.4, Java 21). Mọi event đều chỉnh được bằng `config.yml` và `messages.yml`.

## Các event

| ID | Tên | Cách chơi |
|---|---|---|
| `awakening` | Nguyên Tố Thức Tỉnh | Một nguyên tố thức tỉnh: người thuộc nguyên tố đó nhận buff + hạt, hạ quái để tích điểm xếp hạng và nhận thưởng nhỏ mỗi lần hạ. |
| `boss` | Boss Thế Giới | Boss xuất hiện ngẫu nhiên, cả server cùng đánh. Thưởng theo sát thương + đòn kết liễu. Hỗ trợ MythicMobs. |
| `rift` | Khe Nứt Nguyên Tố | Khe nứt mở ra, quái ập tới theo từng đợt (wave). Cùng dọn sạch trước khi hết giờ. |
| `treasure` | Mưa Rương Kho Báu | Nhiều rương rơi khắp thế giới, chuột phải trước người đó nhận. Gợi ý tọa độ chính xác, làm tròn hoặc ẩn. |
| (daily) | Điểm danh | `/diemdanh` nhận quà theo chuỗi ngày liên tiếp, có mốc ngày 3/7/14/30. |

Mỗi lúc chỉ có **một** event chạy. Lịch tự động theo giờ trong `schedule.times`.

## Cài đặt

1. Build: `mvn clean package` (cần Java 21 và Maven). File jar nằm ở `target/AstraxEvent.jar`.
2. Bỏ jar vào `plugins/`, khởi động server, chỉnh `plugins/AstraxEvent/config.yml`, rồi `/event reload`.
3. Tùy chọn: **Vault** (thưởng tiền), **PlaceholderAPI**, **MythicMobs**.

Repo dùng **layout phẳng**: mọi file `.java`, `.yml`, `pom.xml`, `README.md` nằm ở thư mục gốc, một package duy nhất, nên upload thẳng lên GitHub được.

## Lệnh và quyền

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/event` | `astraxevent.use` | Xem trạng thái và lịch các event |
| `/event where` | `astraxevent.use` | Vị trí của event đang diễn ra |
| `/diemdanh` | `astraxevent.daily` | Điểm danh hằng ngày |
| `/event start <id> [nguyên tố]` | `astraxevent.admin` | Bắt đầu ngay (bỏ qua yêu cầu số người) |
| `/event stop` | `astraxevent.admin` | Dừng event đang chạy (không phát thưởng) |
| `/event reload` | `astraxevent.admin` | Tải lại config và messages |

## Placeholder (PlaceholderAPI)

`%astraxevent_current%`, `%astraxevent_time%`, `%astraxevent_detail%`, `%astraxevent_next%`, `%astraxevent_<id>_next%`, `%astraxevent_streak%`, `%astraxevent_daily%`

## Tích hợp với hệ Astrax

- **AstraxElementer**: trong `events.awakening.player-placeholder` điền placeholder trả về nguyên tố của người chơi (qua PlaceholderAPI) để chỉ người cùng nguyên tố được buff. Để trống thì mọi người đều được buff.
- Muốn cộng EXP Elementer khi hạ quái: thêm lệnh vào `events.awakening.kill-reward.commands` (ví dụ `elementeradmin give %player% exp 5`, đổi theo cú pháp thật của plugin).
- **AstraxQuest / AstraxPetRPG**: phần thưởng chạy được lệnh console nên có thể trao vật phẩm, điểm hoặc quest bằng lệnh của các plugin này. Sát thương từ thú cưng (Tameable) được tính cho chủ trong Boss Thế Giới.

## Lưu ý

- Phần thưởng chỉ trao cho người **đang online** lúc event kết thúc.
- Quái ở Khe Nứt được buff chống cháy; nên dùng quái miễn nhiễm nắng (HUSK, STRAY...).
- MythicMobs: lệnh spawn mặc định `mm mobs spawn {mob} 1 {world},{x},{y},{z}`; sửa `mythic-command` nếu phiên bản của bạn khác.
- Máu boss vanilla tối đa 1024 (hoặc theo `spigot.yml`).
- Nếu server tắt đột ngột giữa Mưa Rương, các rương chưa mở sẽ còn lại là rương rỗng thường.
