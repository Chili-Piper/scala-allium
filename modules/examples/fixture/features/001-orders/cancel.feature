Feature: Cancelling an order

  @obligation:rule-success.CancelOrder
  Scenario: an open order is cancelled
    Given an open order
    When the customer cancels it
    Then the order is gone

  @obligation:rule-failure.CancelOrder.1
  Scenario: a placed order cannot be cancelled
    Given a placed order
    When the customer cancels it
    Then the cancellation is rejected
