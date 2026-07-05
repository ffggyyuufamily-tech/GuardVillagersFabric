# Bước 4 - Test Scenario Tạo Combat Situation

## Kịch bản thực thi (Manual):
1. Tạo một world mới (hoặc load existing world từ `run/saves/`)
2. Dùng `/summon com.guardvillagers.entity.GuardEntity` để spawn 1 guard hoặc dùng spawn egg
3. Chụp ảnh vị trí spawn guard (ghi tọa độ)
4. Bay lên trên 10 blocks (creative mode, khoảng cách ngang ~8-10 blocks)
5. Bắn guard bằng cung 1-2 mũi tên để trigger DIRECT_DAMAGE alert
6. Giữ nguyên vị trí cao trên guard trong ít nhất 30 giây (để guard ở trạng thái ENGAGE_TARGET kéo dài)
7. Đợi log in ra các "canStart CALLED" với intent=ENGAGE_TARGET
8. Nếu KHÔNG có log → bác bỏ priority conflict (canStart vẫn không được gọi)
9. Nếu CÓ log → xác nhận priority conflict: combat goal giữ quyền với Guard không thể engineering

## Dòng lệnh debug (trong game):
- `/debug` (nếu hỗ trợ) để bật debug mode cho nearby guards
- Hoặc lấy UUID guard và dùng `/data modify` để set debug flag trực tiếp nếu cần

## Expected result:
- Với priority CONFLICT → log "canStart CALLED" sẽ BIẾN MẤT khi guard vào combat
- Không priority conflict → log tiếp tục xuất hiện
