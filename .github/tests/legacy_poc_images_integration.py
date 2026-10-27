"""Opt-in PostgreSQL 18 integration tests; run only in the documented disposable Docker DB."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
QUERIES = ROOT / "docs/deploy/queries"
REPLACEMENT = "https://dcsjrkmummxi7.cloudfront.net/approved.jpg"
TABLES = ("biz_product", "biz_order", "biz_purchase_intent")


class LegacyPocImagesIntegrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.environ.get("POC_IMAGE_TEST_DATABASE") != "1":
            raise unittest.SkipTest("Requires explicit disposable Docker database opt-in")
        if os.environ.get("PGDATABASE") != "poc_image_migration_test":
            raise RuntimeError("Refusing to reset any database except poc_image_migration_test")

    def sql(self, sql):
        result = subprocess.run(
            ["psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-c", sql],
            text=True, capture_output=True, check=True,
        )
        return result.stdout.strip()

    def setUp(self):
        self.sql("DROP SCHEMA IF EXISTS domain CASCADE; CREATE SCHEMA domain;")
        migrations = {
            "biz_product": "V4__catalog.sql",
            "biz_order": "V12__order.sql",
            "biz_purchase_intent": "V15__purchase_intent.sql",
            "sys_user": "V3__identity.sql",
            "sys_user_oauth": "V3__identity.sql",
        }
        for table, filename in migrations.items():
            content = (ROOT / "backend/blocbuy-bootstrap/src/main/resources/db/migration" / filename).read_text()
            ddl = re.search(rf"CREATE TABLE {table} \([\s\S]*?\n\);", content).group()
            self.sql("SET search_path=domain,pg_catalog; " + ddl)
        self.sql(f"""
            SET search_path=domain,pg_catalog;
            INSERT INTO biz_product(product_no,title,original_price,currency,status,cover_image,images,version,deleted,update_time)
            SELECT 'legacy-'||n, 'PoC placeholder', 10, 'sgd',
                   CASE WHEN n<3 THEN 'draft' ELSE 'off_sale' END,
                   'https://loremflickr.com/cover/'||n,
                   jsonb_build_array('https://loremflickr.com/gallery/'||n||'/1',
                                     '{REPLACEMENT}', 'https://loremflickr.com/gallery/'||n||'/2'),
                   7, n<3, '2026-10-20T00:00:00Z' FROM generate_series(1,3) n;
            INSERT INTO biz_product(product_no,title,original_price,currency,status,cover_image,images)
            VALUES ('approved','Approved',12,'sgd','on_sale','{REPLACEMENT}','[]');
            INSERT INTO biz_order(order_no,payment_id,user_id,deal_id,product_id,product_title,
                                  product_image,quantity,unit_price,total_amount,currency,status,paid_time,version,update_time)
            SELECT 'order-'||n,uuidv7(),uuidv7(),uuidv7(),p.id,'PoC snapshot',
                   'https://loremflickr.com/order/'||n,1,10,10,'sgd',
                   CASE WHEN n=5 THEN 'pending' ELSE 'confirmed' END,
                   '2026-10-20T00:00:00Z',9,'2026-10-20T00:00:00Z'
            FROM generate_series(1,5) n CROSS JOIN (SELECT id FROM biz_product WHERE product_no='legacy-3') p;
            INSERT INTO biz_purchase_intent(user_id,operation,deal_id,product_id,product_title,product_image,
                amount,currency,target_size,quote_valid_until,business_deadline,fulfilled_payment_id,version,update_time)
            SELECT uuidv7(),'START',uuidv7(),p.id,'PoC intent','https://loremflickr.com/intent/'||n,
                   10,'sgd',2,'2026-10-20T00:00:00Z','2026-12-01T00:00:00Z',uuidv7(),11,'2026-10-20T00:00:00Z'
            FROM generate_series(1,5) n CROSS JOIN (SELECT id FROM biz_product WHERE product_no='legacy-3') p;
        """)
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def run_script(self, name="replace", apply=False, replacement=REPLACEMENT, directory=None):
        script = "replace-legacy-poc-images.sql" if name == "replace" else "rollback-legacy-poc-images.sql"
        command = ["psql", "-X", "-v", "ON_ERROR_STOP=1", "-v", f"replacement_url={replacement}"]
        if apply:
            command += ["-v", "apply=on"]
        command += ["-f", str(QUERIES / script)]
        result = subprocess.run(command, cwd=directory or self.directory.name, text=True, capture_output=True,
                                umask=0o077)
        return result

    def snapshot(self):
        return {table: json.loads(self.sql(
            f"SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]') FROM domain.{table} t")) for table in TABLES}

    def test_default_is_rollback_and_backup_is_private(self):
        before = self.snapshot()
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("DRY RUN", result.stdout)
        self.assertEqual(before, self.snapshot())
        backup = Path(self.directory.name) / "legacy-image-backup.csv"
        self.assertEqual(backup.stat().st_mode & 0o777, 0o600)
        self.assertEqual(len(backup.read_text().splitlines()), 14)

    def test_apply_and_guarded_rollback_preserve_business_fields(self):
        before = self.snapshot()
        result = self.run_script(apply=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        after = self.snapshot()
        for table in TABLES:
            for old, new in zip(before[table], after[table]):
                excluded = {"cover_image", "images", "product_image", "version", "update_time"}
                self.assertEqual({k: v for k, v in old.items() if k not in excluded},
                                 {k: v for k, v in new.items() if k not in excluded})
                if old.get("product_no") == "approved":
                    self.assertEqual(old, new)
                else:
                    self.assertEqual(new["version"], old["version"] + 1)
                    self.assertNotIn("loremflickr.com", json.dumps(new))
                    if table == "biz_product":
                        self.assertEqual(new["images"], [REPLACEMENT] * 3)
        rehearsal = self.run_script(name="rollback")
        self.assertEqual(rehearsal.returncode, 0, rehearsal.stderr)
        self.assertEqual(after, self.snapshot())
        rollback = self.run_script(name="rollback", apply=True)
        self.assertEqual(rollback.returncode, 0, rollback.stderr)
        restored = self.snapshot()
        for table in TABLES:
            for old, new in zip(before[table], restored[table]):
                self.assertEqual({k: v for k, v in old.items() if k not in {"version", "update_time"}},
                                 {k: v for k, v in new.items() if k not in {"version", "update_time"}})
                if old.get("product_no") != "approved":
                    self.assertEqual(new["version"], old["version"] + 2)

    def test_changed_counts_abort_without_backup_or_updates(self):
        self.sql("DELETE FROM domain.biz_order WHERE id=(SELECT id FROM domain.biz_order LIMIT 1)")
        before = self.snapshot()
        result = self.run_script(apply=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Inventory changed", result.stderr)
        self.assertEqual(before, self.snapshot())
        self.assertFalse((Path(self.directory.name) / "legacy-image-backup.csv").exists())

    def test_invalid_or_uninventoried_replacements_are_rejected(self):
        before = self.snapshot()
        for url in ["https://evil.example/x.jpg", "https://dcsjrkmummxi7.cloudfront.net/missing.jpg",
                    REPLACEMENT + "?token=x", "https://dcsjrkmummxi7.cloudfront.net.evil.example/x.jpg"]:
            with self.subTest(url=url):
                result = self.run_script(apply=True, replacement=url)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Replacement must", result.stderr)
                self.assertEqual(before, self.snapshot())

    def test_existing_backup_is_never_overwritten(self):
        self.assertEqual(self.run_script().returncode, 0)
        backup = Path(self.directory.name) / "legacy-image-backup.csv"
        contents = backup.read_bytes()
        before = self.snapshot()
        result = self.run_script(apply=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(contents, backup.read_bytes())
        self.assertEqual(before, self.snapshot())

    def test_null_and_empty_galleries_keep_their_representation(self):
        self.sql("""
            UPDATE domain.biz_product SET images=NULL WHERE product_no='legacy-1';
            UPDATE domain.biz_product SET images='[]' WHERE product_no='legacy-2';
            UPDATE domain.biz_product SET images=(SELECT jsonb_agg('https://loremflickr.com/image/'||n)
                FROM generate_series(1,6) n) WHERE product_no='legacy-3';
        """)
        before = self.snapshot()
        result = self.run_script(apply=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        products = {p['product_no']: p for p in self.snapshot()['biz_product']}
        self.assertIsNone(products['legacy-1']['images'])
        self.assertEqual(products['legacy-2']['images'], [])
        result = self.run_script(name="rollback", apply=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        for old, new in zip(before['biz_product'], self.snapshot()['biz_product']):
            self.assertEqual(old['images'], new['images'])

    def test_gallery_count_drift_aborts(self):
        self.sql("UPDATE domain.biz_product SET images=images||'\"https://loremflickr.com/extra\"'::jsonb WHERE product_no='legacy-1'")
        before = self.snapshot()
        result = self.run_script(apply=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Catalog reference counts changed", result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_missing_replacement_aborts(self):
        before = self.snapshot()
        result = subprocess.run(["psql", "-X", "-f", str(QUERIES / "replace-legacy-poc-images.sql")],
                                cwd=self.directory.name, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, self.snapshot())

    def test_inventory_uses_domain_schema(self):
        result = subprocess.run(["psql", "-X", "-v", "ON_ERROR_STOP=1", "-f", str(QUERIES / "image-origin-inventory.sql")],
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("https://loremflickr.com", result.stdout)

    def test_repeat_apply_stops_on_zero_remaining_targets(self):
        self.assertEqual(self.run_script(apply=True).returncode, 0)
        before = self.snapshot()
        with tempfile.TemporaryDirectory() as second:
            result = self.run_script(apply=True, directory=second)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Inventory changed", result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_rollback_refuses_intervening_application_write(self):
        self.assertEqual(self.run_script(apply=True).returncode, 0)
        self.sql("UPDATE domain.biz_order SET version=version+1 WHERE id=(SELECT id FROM domain.biz_order LIMIT 1)")
        before = self.snapshot()
        result = self.run_script(name="rollback", apply=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Rows changed since apply", result.stderr)
        self.assertEqual(before, self.snapshot())

    def test_rollback_refuses_dry_run_backup(self):
        self.assertEqual(self.run_script().returncode, 0)
        before = self.snapshot()
        result = self.run_script(name="rollback", apply=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, self.snapshot())


if __name__ == "__main__":
    unittest.main()
