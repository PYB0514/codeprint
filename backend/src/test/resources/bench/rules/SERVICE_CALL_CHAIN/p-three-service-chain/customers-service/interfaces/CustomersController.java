package customers.interfaces;

public class CustomersController {
    void routeToVisits() {
        webClient.get().uri("http://visits-service/api/visits").retrieve();
    }
}
