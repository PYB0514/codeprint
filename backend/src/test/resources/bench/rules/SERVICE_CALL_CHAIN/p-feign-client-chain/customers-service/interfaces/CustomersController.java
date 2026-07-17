package interfaces;

import client.VisitsServiceClient;

public class CustomersController {
    private final VisitsServiceClient visitsServiceClient;

    CustomersController(VisitsServiceClient visitsServiceClient) {
        this.visitsServiceClient = visitsServiceClient;
    }

    void routeToVisits() {
        visitsServiceClient.getVisits(1);
    }
}
