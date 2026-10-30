-- Backup DB first. Convert only known, unambiguous legacy location suffixes.
-- Do NOT blindly translate free-text street addresses; unmatched Chef must reselect from profile.
-- Re-running leaves existing normalized numeric codes unchanged.
BEGIN;
UPDATE public.chef_profiles
SET service_area = CASE
      WHEN service_area ILIKE '%TP. Hồ Chí Minh' THEN '79'
      WHEN service_area ILIKE '%Thành phố Hồ Chí Minh' THEN '79'
      WHEN service_area ILIKE '%Bình Dương' THEN '79'
      WHEN service_area ILIKE '%Vũng Tàu' THEN '79'
      WHEN service_area ILIKE '%Bà Rịa - Vũng Tàu' THEN '79'
      WHEN service_area ILIKE '%Đồng Nai' THEN '75'
      WHEN service_area ILIKE '%Bình Phước' THEN '75'
      WHEN service_area ILIKE '%Huế' THEN '46'
      WHEN service_area ILIKE '%Thừa Thiên Huế' THEN '46'
      WHEN service_area ILIKE '%Cần Thơ' THEN '92'
      WHEN service_area ILIKE '%Sóc Trăng' THEN '92'
      WHEN service_area ILIKE '%Hậu Giang' THEN '92'
      WHEN service_area ILIKE '%Đà Nẵng' THEN '48'
      WHEN service_area ILIKE '%Quảng Nam' THEN '48'
      WHEN service_area ILIKE '%Hà Nội' THEN '01'
      WHEN service_area ILIKE '%Khánh Hòa' THEN '56'
      WHEN service_area ILIKE '%Ninh Thuận' THEN '56'
      WHEN service_area ILIKE '%Gia Lai' THEN '52'
      WHEN service_area ILIKE '%Bình Định' THEN '52'
      WHEN service_area ILIKE '%Đắk Lắk' THEN '66'
      WHEN service_area ILIKE '%Phú Yên' THEN '66'
      WHEN service_area ILIKE '%Lâm Đồng' THEN '68'
      WHEN service_area ILIKE '%Đắk Nông' THEN '68'
      WHEN service_area ILIKE '%Bình Thuận' THEN '68'
      WHEN service_area ILIKE '%Quảng Ngãi' THEN '51'
      WHEN service_area ILIKE '%Kon Tum' THEN '51'
      WHEN service_area ILIKE '%An Giang' THEN '91'
      WHEN service_area ILIKE '%Kiên Giang' THEN '91'
      WHEN service_area ILIKE '%Cà Mau' THEN '96'
      WHEN service_area ILIKE '%Bạc Liêu' THEN '96'
      WHEN service_area ILIKE '%Vĩnh Long' THEN '86'
      WHEN service_area ILIKE '%Bến Tre' THEN '86'
      WHEN service_area ILIKE '%Trà Vinh' THEN '86'
      WHEN service_area ILIKE '%Đồng Tháp' THEN '82'
      WHEN service_area ILIKE '%Tiền Giang' THEN '82'
      WHEN service_area ILIKE '%Tây Ninh' THEN '80'
      WHEN service_area ILIKE '%Long An' THEN '80'
      WHEN service_area ILIKE '%Hải Phòng' THEN '31'
      WHEN service_area ILIKE '%Hải Dương' THEN '31'
      WHEN service_area ILIKE '%Bắc Ninh' THEN '24'
      WHEN service_area ILIKE '%Bắc Giang' THEN '24'
      WHEN service_area ILIKE '%Phú Thọ' THEN '25'
      WHEN service_area ILIKE '%Vĩnh Phúc' THEN '25'
      WHEN service_area ILIKE '%Hòa Bình' THEN '25'
      WHEN service_area ILIKE '%Hưng Yên' THEN '33'
      WHEN service_area ILIKE '%Thái Bình' THEN '33'
      WHEN service_area ILIKE '%Ninh Bình' THEN '37'
      WHEN service_area ILIKE '%Nam Định' THEN '37'
      WHEN service_area ILIKE '%Hà Nam' THEN '37'
      WHEN service_area ILIKE '%Quảng Trị' THEN '44'
      WHEN service_area ILIKE '%Quảng Bình' THEN '44'
      WHEN service_area ILIKE '%Lào Cai' THEN '15'
      WHEN service_area ILIKE '%Yên Bái' THEN '15'
      WHEN service_area ILIKE '%Thái Nguyên' THEN '19'
      WHEN service_area ILIKE '%Bắc Kạn' THEN '19'
      WHEN service_area ILIKE '%Tuyên Quang' THEN '08'
      WHEN service_area ILIKE '%Hà Giang' THEN '08'
      WHEN service_area ILIKE '%Cao Bằng' THEN '04'
      WHEN service_area ILIKE '%Điện Biên' THEN '11'
      WHEN service_area ILIKE '%Lai Châu' THEN '12'
      WHEN service_area ILIKE '%Sơn La' THEN '14'
      WHEN service_area ILIKE '%Lạng Sơn' THEN '20'
      WHEN service_area ILIKE '%Quảng Ninh' THEN '22'
      WHEN service_area ILIKE '%Thanh Hóa' THEN '38'
      WHEN service_area ILIKE '%Nghệ An' THEN '40'
      WHEN service_area ILIKE '%Hà Tĩnh' THEN '42'
  ELSE service_area END,
  updated_at=(EXTRACT(EPOCH FROM now())*1000)::bigint
WHERE service_area IS NOT NULL AND trim(service_area)<>''
  AND service_area !~ '^[0-9]{2}(,[0-9]{2})*$'
  AND (
    service_area ILIKE '%TP. Hồ Chí Minh' OR
    service_area ILIKE '%Thành phố Hồ Chí Minh' OR
    service_area ILIKE '%Bình Dương' OR
    service_area ILIKE '%Vũng Tàu' OR
    service_area ILIKE '%Bà Rịa - Vũng Tàu' OR
    service_area ILIKE '%Đồng Nai' OR
    service_area ILIKE '%Bình Phước' OR
    service_area ILIKE '%Huế' OR
    service_area ILIKE '%Thừa Thiên Huế' OR
    service_area ILIKE '%Cần Thơ' OR
    service_area ILIKE '%Sóc Trăng' OR
    service_area ILIKE '%Hậu Giang' OR
    service_area ILIKE '%Đà Nẵng' OR
    service_area ILIKE '%Quảng Nam' OR
    service_area ILIKE '%Hà Nội' OR
    service_area ILIKE '%Khánh Hòa' OR
    service_area ILIKE '%Ninh Thuận' OR
    service_area ILIKE '%Gia Lai' OR
    service_area ILIKE '%Bình Định' OR
    service_area ILIKE '%Đắk Lắk' OR
    service_area ILIKE '%Phú Yên' OR
    service_area ILIKE '%Lâm Đồng' OR
    service_area ILIKE '%Đắk Nông' OR
    service_area ILIKE '%Bình Thuận' OR
    service_area ILIKE '%Quảng Ngãi' OR
    service_area ILIKE '%Kon Tum' OR
    service_area ILIKE '%An Giang' OR
    service_area ILIKE '%Kiên Giang' OR
    service_area ILIKE '%Cà Mau' OR
    service_area ILIKE '%Bạc Liêu' OR
    service_area ILIKE '%Vĩnh Long' OR
    service_area ILIKE '%Bến Tre' OR
    service_area ILIKE '%Trà Vinh' OR
    service_area ILIKE '%Đồng Tháp' OR
    service_area ILIKE '%Tiền Giang' OR
    service_area ILIKE '%Tây Ninh' OR
    service_area ILIKE '%Long An' OR
    service_area ILIKE '%Hải Phòng' OR
    service_area ILIKE '%Hải Dương' OR
    service_area ILIKE '%Bắc Ninh' OR
    service_area ILIKE '%Bắc Giang' OR
    service_area ILIKE '%Phú Thọ' OR
    service_area ILIKE '%Vĩnh Phúc' OR
    service_area ILIKE '%Hòa Bình' OR
    service_area ILIKE '%Hưng Yên' OR
    service_area ILIKE '%Thái Bình' OR
    service_area ILIKE '%Ninh Bình' OR
    service_area ILIKE '%Nam Định' OR
    service_area ILIKE '%Hà Nam' OR
    service_area ILIKE '%Quảng Trị' OR
    service_area ILIKE '%Quảng Bình' OR
    service_area ILIKE '%Lào Cai' OR
    service_area ILIKE '%Yên Bái' OR
    service_area ILIKE '%Thái Nguyên' OR
    service_area ILIKE '%Bắc Kạn' OR
    service_area ILIKE '%Tuyên Quang' OR
    service_area ILIKE '%Hà Giang' OR
    service_area ILIKE '%Cao Bằng' OR
    service_area ILIKE '%Điện Biên' OR
    service_area ILIKE '%Lai Châu' OR
    service_area ILIKE '%Sơn La' OR
    service_area ILIKE '%Lạng Sơn' OR
    service_area ILIKE '%Quảng Ninh' OR
    service_area ILIKE '%Thanh Hóa' OR
    service_area ILIKE '%Nghệ An' OR
    service_area ILIKE '%Hà Tĩnh'
  );
-- Normalize only open requests so Admin matching does not strand existing pending requests.
-- Closed requests are historical records and are not rewritten.
UPDATE public.public_cooking_requests
SET requested_area = CASE
      WHEN requested_area ILIKE '%TP. Hồ Chí Minh' THEN '79'
      WHEN requested_area ILIKE '%Thành phố Hồ Chí Minh' THEN '79'
      WHEN requested_area ILIKE '%Bình Dương' THEN '79'
      WHEN requested_area ILIKE '%Vũng Tàu' THEN '79'
      WHEN requested_area ILIKE '%Bà Rịa - Vũng Tàu' THEN '79'
      WHEN requested_area ILIKE '%Đồng Nai' THEN '75'
      WHEN requested_area ILIKE '%Bình Phước' THEN '75'
      WHEN requested_area ILIKE '%Huế' THEN '46'
      WHEN requested_area ILIKE '%Thừa Thiên Huế' THEN '46'
      WHEN requested_area ILIKE '%Cần Thơ' THEN '92'
      WHEN requested_area ILIKE '%Sóc Trăng' THEN '92'
      WHEN requested_area ILIKE '%Hậu Giang' THEN '92'
      WHEN requested_area ILIKE '%Đà Nẵng' THEN '48'
      WHEN requested_area ILIKE '%Quảng Nam' THEN '48'
      WHEN requested_area ILIKE '%Hà Nội' THEN '01'
      WHEN requested_area ILIKE '%Khánh Hòa' THEN '56'
      WHEN requested_area ILIKE '%Ninh Thuận' THEN '56'
      WHEN requested_area ILIKE '%Gia Lai' THEN '52'
      WHEN requested_area ILIKE '%Bình Định' THEN '52'
      WHEN requested_area ILIKE '%Đắk Lắk' THEN '66'
      WHEN requested_area ILIKE '%Phú Yên' THEN '66'
      WHEN requested_area ILIKE '%Lâm Đồng' THEN '68'
      WHEN requested_area ILIKE '%Đắk Nông' THEN '68'
      WHEN requested_area ILIKE '%Bình Thuận' THEN '68'
      WHEN requested_area ILIKE '%Quảng Ngãi' THEN '51'
      WHEN requested_area ILIKE '%Kon Tum' THEN '51'
      WHEN requested_area ILIKE '%An Giang' THEN '91'
      WHEN requested_area ILIKE '%Kiên Giang' THEN '91'
      WHEN requested_area ILIKE '%Cà Mau' THEN '96'
      WHEN requested_area ILIKE '%Bạc Liêu' THEN '96'
      WHEN requested_area ILIKE '%Vĩnh Long' THEN '86'
      WHEN requested_area ILIKE '%Bến Tre' THEN '86'
      WHEN requested_area ILIKE '%Trà Vinh' THEN '86'
      WHEN requested_area ILIKE '%Đồng Tháp' THEN '82'
      WHEN requested_area ILIKE '%Tiền Giang' THEN '82'
      WHEN requested_area ILIKE '%Tây Ninh' THEN '80'
      WHEN requested_area ILIKE '%Long An' THEN '80'
      WHEN requested_area ILIKE '%Hải Phòng' THEN '31'
      WHEN requested_area ILIKE '%Hải Dương' THEN '31'
      WHEN requested_area ILIKE '%Bắc Ninh' THEN '24'
      WHEN requested_area ILIKE '%Bắc Giang' THEN '24'
      WHEN requested_area ILIKE '%Phú Thọ' THEN '25'
      WHEN requested_area ILIKE '%Vĩnh Phúc' THEN '25'
      WHEN requested_area ILIKE '%Hòa Bình' THEN '25'
      WHEN requested_area ILIKE '%Hưng Yên' THEN '33'
      WHEN requested_area ILIKE '%Thái Bình' THEN '33'
      WHEN requested_area ILIKE '%Ninh Bình' THEN '37'
      WHEN requested_area ILIKE '%Nam Định' THEN '37'
      WHEN requested_area ILIKE '%Hà Nam' THEN '37'
      WHEN requested_area ILIKE '%Quảng Trị' THEN '44'
      WHEN requested_area ILIKE '%Quảng Bình' THEN '44'
      WHEN requested_area ILIKE '%Lào Cai' THEN '15'
      WHEN requested_area ILIKE '%Yên Bái' THEN '15'
      WHEN requested_area ILIKE '%Thái Nguyên' THEN '19'
      WHEN requested_area ILIKE '%Bắc Kạn' THEN '19'
      WHEN requested_area ILIKE '%Tuyên Quang' THEN '08'
      WHEN requested_area ILIKE '%Hà Giang' THEN '08'
      WHEN requested_area ILIKE '%Cao Bằng' THEN '04'
      WHEN requested_area ILIKE '%Điện Biên' THEN '11'
      WHEN requested_area ILIKE '%Lai Châu' THEN '12'
      WHEN requested_area ILIKE '%Sơn La' THEN '14'
      WHEN requested_area ILIKE '%Lạng Sơn' THEN '20'
      WHEN requested_area ILIKE '%Quảng Ninh' THEN '22'
      WHEN requested_area ILIKE '%Thanh Hóa' THEN '38'
      WHEN requested_area ILIKE '%Nghệ An' THEN '40'
      WHEN requested_area ILIKE '%Hà Tĩnh' THEN '42'
  ELSE requested_area END,
  updated_at=now()
WHERE status IN ('PENDING','REVIEWING') AND requested_area IS NOT NULL
  AND requested_area !~ '^[0-9]{2}$'
  AND (requested_area ILIKE '%TP. Hồ Chí Minh' OR requested_area ILIKE '%Thành phố Hồ Chí Minh' OR requested_area ILIKE '%Bình Dương' OR requested_area ILIKE '%Vũng Tàu' OR requested_area ILIKE '%Bà Rịa - Vũng Tàu' OR requested_area ILIKE '%Đồng Nai' OR requested_area ILIKE '%Bình Phước' OR requested_area ILIKE '%Huế' OR requested_area ILIKE '%Thừa Thiên Huế' OR requested_area ILIKE '%Cần Thơ' OR requested_area ILIKE '%Sóc Trăng' OR requested_area ILIKE '%Hậu Giang' OR requested_area ILIKE '%Đà Nẵng' OR requested_area ILIKE '%Quảng Nam' OR requested_area ILIKE '%Hà Nội' OR requested_area ILIKE '%Khánh Hòa' OR requested_area ILIKE '%Ninh Thuận' OR requested_area ILIKE '%Gia Lai' OR requested_area ILIKE '%Bình Định' OR requested_area ILIKE '%Đắk Lắk' OR requested_area ILIKE '%Phú Yên' OR requested_area ILIKE '%Lâm Đồng' OR requested_area ILIKE '%Đắk Nông' OR requested_area ILIKE '%Bình Thuận' OR requested_area ILIKE '%Quảng Ngãi' OR requested_area ILIKE '%Kon Tum' OR requested_area ILIKE '%An Giang' OR requested_area ILIKE '%Kiên Giang' OR requested_area ILIKE '%Cà Mau' OR requested_area ILIKE '%Bạc Liêu' OR requested_area ILIKE '%Vĩnh Long' OR requested_area ILIKE '%Bến Tre' OR requested_area ILIKE '%Trà Vinh' OR requested_area ILIKE '%Đồng Tháp' OR requested_area ILIKE '%Tiền Giang' OR requested_area ILIKE '%Tây Ninh' OR requested_area ILIKE '%Long An' OR requested_area ILIKE '%Hải Phòng' OR requested_area ILIKE '%Hải Dương' OR requested_area ILIKE '%Bắc Ninh' OR requested_area ILIKE '%Bắc Giang' OR requested_area ILIKE '%Phú Thọ' OR requested_area ILIKE '%Vĩnh Phúc' OR requested_area ILIKE '%Hòa Bình' OR requested_area ILIKE '%Hưng Yên' OR requested_area ILIKE '%Thái Bình' OR requested_area ILIKE '%Ninh Bình' OR requested_area ILIKE '%Nam Định' OR requested_area ILIKE '%Hà Nam' OR requested_area ILIKE '%Quảng Trị' OR requested_area ILIKE '%Quảng Bình' OR requested_area ILIKE '%Lào Cai' OR requested_area ILIKE '%Yên Bái' OR requested_area ILIKE '%Thái Nguyên' OR requested_area ILIKE '%Bắc Kạn' OR requested_area ILIKE '%Tuyên Quang' OR requested_area ILIKE '%Hà Giang' OR requested_area ILIKE '%Cao Bằng' OR requested_area ILIKE '%Điện Biên' OR requested_area ILIKE '%Lai Châu' OR requested_area ILIKE '%Sơn La' OR requested_area ILIKE '%Lạng Sơn' OR requested_area ILIKE '%Quảng Ninh' OR requested_area ILIKE '%Thanh Hóa' OR requested_area ILIKE '%Nghệ An' OR requested_area ILIKE '%Hà Tĩnh');
COMMIT;
-- Rows needing manual profile update, not automatically matched:
SELECT user_id,service_area FROM public.chef_profiles
WHERE service_area IS NULL OR service_area !~ '^[0-9]{2}(,[0-9]{2})*$';
