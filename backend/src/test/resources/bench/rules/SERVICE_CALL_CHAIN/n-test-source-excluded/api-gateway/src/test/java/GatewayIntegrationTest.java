package apigateway;

public class GatewayIntegrationTest {
    void routeToCustomers() {
        webClient.get().uri("http://customers-service/api/customers").retrieve();
    }
}
