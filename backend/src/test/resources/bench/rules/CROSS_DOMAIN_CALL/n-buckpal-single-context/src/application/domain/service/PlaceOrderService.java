package application.domain.service;

public class PlaceOrderService {
    private AccountService accountService;

    public void placeOrder() {
        accountService.withdraw();
    }
}
