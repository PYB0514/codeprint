package apigateway.interfaces;

public class GatewayController {
    void routeToCustomers() {
        webClient.get().uri("http://customers-service/api/customers").retrieve();
    }
}
