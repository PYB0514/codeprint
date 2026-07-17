package interfaces;

import client.CustomersServiceClient;

public class GatewayController {
    private final CustomersServiceClient customersServiceClient;

    GatewayController(CustomersServiceClient customersServiceClient) {
        this.customersServiceClient = customersServiceClient;
    }

    void routeToCustomers() {
        customersServiceClient.getOwner(1);
    }
}
