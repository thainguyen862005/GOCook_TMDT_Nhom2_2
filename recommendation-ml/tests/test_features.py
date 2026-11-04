import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from feature_encoder import normalize, encode, dot, hash_token, DEFAULT_CONFIG

class FeatureTests(unittest.TestCase):
    def test_vietnamese(self):
        self.assertEqual(normalize("Đậu hũ, nấm và CƠM"), "dau hu nam va com")

    def test_hash_stable(self):
        self.assertEqual(hash_token("g: cha", 16384),  zlib_crc("g: cha", 16384))

    def test_similarity(self):
        a = {"name":"Mâm chay", "category":"Món chay", "dishItems":"Đậu hũ nấm"}
        b = {"name":"Lẩu chay", "category":"Món chay", "dishItems":"Đậu hũ rau"}
        c = {"name":"BBQ mực", "category":"Hải sản", "dishItems":"Mực tôm"}
        self.assertGreater(dot(encode(a),encode(b)), dot(encode(a),encode(c)))
        self.assertAlmostEqual(dot(encode(a),encode(a)), 1.0, places=6)

def zlib_crc(s, dim):
    import zlib
    return zlib.crc32(s.encode("ascii")) % dim

if __name__ == "__main__":
    unittest.main()
