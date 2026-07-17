import requests


def route_to_payment(order_id):
    return requests.get(f"http://payment-service/api/pay/{order_id}")
