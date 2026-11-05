from pathlib import Path
from collections import Counter
import csv
import random

BASE_DIR = Path(__file__).resolve().parent
OUTPUT = BASE_DIR / "data" / "generated_gocook.csv"

rng = random.Random(42)

OPTIONS = {
    "food": [
        "món chay", "lẩu nấm", "hải sản",
        "đồ nướng", "cơm gia đình", "món Việt"
    ],
    "guests": [2, 4, 6, 10],
    "budget": [
        "500 nghìn", "800 nghìn",
        "1 triệu", "1,5 triệu"
    ],
    "area": [
        "Gia Lai", "Pleiku", "Đà Nẵng",
        "Hà Nội", "TP.HCM"
    ],
    "method": [
        "VNPay", "thẻ ngân hàng", "ví điện tử",
        "chuyển khoản", "thanh toán trực tuyến"
    ],
    "order": [
        "của tôi", "vừa đặt", "đã thanh toán",
        "đang xử lý", "đã hoàn thành"
    ],
    "topic": [
        "đăng ký tài khoản", "đăng ký làm Chef",
        "quyền lợi khách hàng", "chính sách bảo mật",
        "phạm vi dịch vụ"
    ],
    "issue": [
        "lỗi đăng nhập", "vấn đề thanh toán",
        "lỗi hiển thị", "phản hồi dịch vụ",
        "sự cố website"
    ]
}

TEMPLATES = {
    "SEARCH_MENU": [
        "Tìm thực đơn {food} cho {guests} người",
        "Có thực đơn {food} dưới {budget} không?",
        "Cho xem món {food} phục vụ {guests} người",
        "Mình cần thực đơn {food} giá khoảng {budget}",
        "Có món {food} nào phù hợp {guests} người?",
        "Tìm các thực đơn {food} tầm {budget}"
    ],

    "SEARCH_CHEF": [
        "Tìm đầu bếp ở {area} nấu {food}",
        "Có Chef nào tại {area} phục vụ {guests} người?",
        "Tìm Chef chuyên món {food} ở {area}",
        "Đầu bếp nào nhận nấu tiệc {guests} người?",
        "Giới thiệu đầu bếp ở {area}",
        "Tìm đầu bếp chuyên nấu {food}"
    ],

    "RECOMMEND_MENU": [
        "Mình thích {food}, gợi ý thêm món phù hợp",
        "Có thực đơn nào tương tự {food} không?",
        "Mình từng ăn {food}, nên chọn món gì tiếp?",
        "Tư vấn món theo sở thích nếu mình thích {food}",
        "Đề xuất thực đơn giống {food}",
        "Gợi ý các món tương tự {food} cho mình"
    ],

    "CART_HELP": [
        "Cách thêm {food} vào giỏ hàng?",
        "Làm sao xóa {food} khỏi giỏ?",
        "Tại sao món {food} không thêm vào giỏ được?",
        "Xem {food} trong giỏ hàng ở đâu?",
        "Tôi muốn bỏ {food} khỏi giỏ hàng",
        "Cách quản lý giỏ hàng có {food}?"
    ],

    "BOOKING_HELP": [
        "Hướng dẫn đặt đầu bếp nấu {food} tại nhà",
        "Làm sao đặt lịch Chef cho {guests} người?",
        "Tôi muốn đặt lịch Chef ở {area}",
        "Cách chọn ngày phục vụ món {food}?",
        "Quy trình đặt đầu bếp cho {guests} người?",
        "Đặt lịch nấu ăn ở {area} như thế nào?"
    ],

    "PAYMENT_HELP": [
        "GO Cook có chấp nhận {method} không?",
        "Cách thanh toán bằng {method}?",
        "Thanh toán {method} thất bại thì sao?",
        "Hướng dẫn trả tiền qua {method}",
        "Làm sao biết giao dịch {method} thành công?",
        "Vì sao tôi không thanh toán được bằng {method}?"
    ],

    "ORDER_HELP": [
        "Xem trạng thái đơn hàng {order} ở đâu?",
        "Tôi muốn kiểm tra đơn hàng {order}",
        "Làm sao xem chi tiết đơn {order}?",
        "Cách tra cứu đơn hàng {order}",
        "Đơn hàng {order} xem ở đâu?",
        "Lịch sử đơn hàng {order} nằm chỗ nào?"
    ],

    "FAQ": [
        "GO Cook có quy định gì về {topic}?",
        "Thông tin về {topic} trên GO Cook là gì?",
        "Tôi muốn biết thêm về {topic}",
        "Cho tôi hỏi chính sách về {topic}",
        "Tìm thông tin {topic} ở đâu?"
    ],

    "CONTACT_ADMIN": [
        "Tôi muốn gửi yêu cầu hỗ trợ về {issue}",
        "Làm sao liên hệ Admin khi gặp {issue}?",
        "Tôi cần nhân viên xử lý {issue}",
        "Chuyển đến trang liên hệ để báo {issue}",
        "Tôi muốn gửi ticket về {issue}"
    ]
}


def generate():
    rows = []

    for intent, patterns in TEMPLATES.items():
        unique = set()
        attempts = 0

        while len(unique) < 25 and attempts < 1000:
            attempts += 1

            values = {
                key: rng.choice(items)
                for key, items in OPTIONS.items()
            }

            text = rng.choice(patterns).format(**values)
            unique.add(text)

        if len(unique) < 25:
            raise RuntimeError(f"Not enough unique examples for {intent}")

        for text in sorted(unique):
            rows.append({
                "text": text,
                "intent": intent
            })

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT.open("w", newline="", encoding="utf-8-sig") as file:
        writer = csv.DictWriter(file, fieldnames=["text", "intent"])
        writer.writeheader()
        writer.writerows(rows)

    print("Output:", OUTPUT)
    print("Total generated:", len(rows))
    print("Intents:", dict(Counter(row["intent"] for row in rows)))
    print("Completed!")


if __name__ == "__main__":
    generate()
