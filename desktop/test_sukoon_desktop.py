import unittest

from sukoon_desktop import DEFAULTS, alarm_for, to_nightscout


class SukoonDesktopTest(unittest.TestCase):
    def test_readings_become_nightscout_entries(self):
        e = to_nightscout([{"ts": "2026-10-17T09:30:00+00:00", "mg_dl": 142, "trend": 1}])[0]
        self.assertEqual((e["sgv"], e["direction"], e["type"]), (142, "FortyFiveUp", "sgv"))
        self.assertEqual(e["date"], 1792229400000)
        self.assertEqual(e["dateString"], "2026-10-17T09:30:00.000Z")
        self.assertEqual(to_nightscout([{"ts": "2026-10-17T09:30:00Z", "mg_dl": 50, "trend": -2}])[0]["direction"], "DoubleDown")

    def test_which_alarm(self):
        c = DEFAULTS
        self.assertEqual(alarm_for(52, 2, c), "urgent")
        self.assertEqual(alarm_for(55, 2, c), "urgent")
        self.assertEqual(alarm_for(64, 2, c), "low")
        self.assertIsNone(alarm_for(120, 2, c))
        self.assertEqual(alarm_for(280, 2, c), "high")
        self.assertEqual(alarm_for(120, 25, c), "stale")  # an old reading never counts as current
        self.assertEqual(alarm_for(None, None, c), "stale")


if __name__ == "__main__":
    unittest.main()
