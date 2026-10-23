"""A real assertion failure for demonstrating read-only CI investigation."""

import unittest

from payment_service import calculate_payment_total


class PaymentServiceTest(unittest.TestCase):
    def test_total_includes_every_payment(self):
        payments = [60, 30, 10]
        expected_total = 100
        actual_total = calculate_payment_total(payments)

        self.assertEqual(
            expected_total,
            actual_total,
            f"Expected payment total: {expected_total}. Actual payment total: {actual_total}.",
        )
