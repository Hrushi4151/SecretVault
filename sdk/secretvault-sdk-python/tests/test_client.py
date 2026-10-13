import unittest
import time
from secretvault import SecretVaultClient, SecretValue, SecretNotFoundError, AuthenticationError
from secretvault.cache import SecretCache

class TestSecretVaultPythonSdk(unittest.TestCase):

    def test_cache_ttl_and_stale(self):
        cache = SecretCache(enabled=True, ttl_sec=0.05, max_stale_sec=0.2)
        cache.set("DB_PASS", "supersecret")

        # Immediate
        item = cache.get("DB_PASS")
        self.assertIsNotNone(item)
        self.assertFalse(item["is_stale"])
        self.assertEqual(item["value"], "supersecret")

        # Stale after 0.06s
        time.sleep(0.06)
        stale_item = cache.get("DB_PASS")
        self.assertIsNotNone(stale_item)
        self.assertTrue(stale_item["is_stale"])

        # Invalidation
        cache.invalidate("DB_PASS")
        self.assertIsNone(cache.get("DB_PASS"))

    def test_secret_value_data_model(self):
        sec = SecretValue(name="PORT", value="5432", version_number=2, environment="production")
        self.assertEqual(sec.as_string(), "5432")
        self.assertEqual(sec.as_int(), 5432)
        self.assertIn("PORT", repr(sec))
        self.assertIn("[MASKED]", repr(sec))

    def test_client_init_and_attributes(self):
        client = SecretVaultClient(
            endpoint="http://localhost:8080",
            workspace="myspace",
            project="auth-service",
            environment="production",
            token="sv_m2m_test123"
        )
        self.assertEqual(client.endpoint, "http://localhost:8080")
        self.assertEqual(client.workspace, "myspace")
        self.assertEqual(client.project, "auth-service")
        self.assertEqual(client.environment, "production")
        self.assertTrue(callable(client.secrets.get))
        self.assertTrue(callable(client.secrets.get_many))
        self.assertTrue(callable(client.secrets.list))
        self.assertTrue(callable(client.secrets.watch))

    def test_error_hierarchy(self):
        err = SecretNotFoundError("API_KEY")
        self.assertEqual(err.status_code, 404)
        self.assertEqual(err.secret_name, "API_KEY")
        self.assertIn("API_KEY", str(err))

        auth = AuthenticationError()
        self.assertEqual(auth.status_code, 401)

if __name__ == "__main__":
    unittest.main()
