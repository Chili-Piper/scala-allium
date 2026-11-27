Feature: Placing an order

  @obligation:rule-success.PlaceOrderChecked
  Scenario: an open order within the item limit is placed
    Given an open order with 3 items
    When the customer places it
    Then the order is placed

  Rule: rejections

    @obligation:rule-failure.PlaceOrderChecked.1
    Scenario: a placed order cannot be placed again
      Given a placed order
      When the customer places it
      Then the request is rejected

    @obligation:rule-failure.PlaceOrderChecked.2
    Scenario: an order over the item limit is rejected
      Given an open order with 11 items
      When the customer places it
      Then the request is rejected

  @obligation:surface-actor.Checkout @obligation:surface-provides.Checkout
  @obligation:surface-guarantee.Checkout.OneOrderPerPlacement
  Scenario: a retried placement creates one order
    Given an open order with 3 items
    When the customer places it twice
    Then there is one placed order
