package application.order;

public class OrderService {
    private InventoryService inventoryService;

    public void placeOrder() {
        inventoryService.reserveStock();
    }
}
