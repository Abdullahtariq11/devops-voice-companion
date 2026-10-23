"""Deliberately faulty payment calculation used only by the manual failure demo."""


def calculate_payment_total(payments: list[int]) -> int:
    """Demonstrate a regression that accidentally excludes the final payment."""
    return sum(payments[:-1])
