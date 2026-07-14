package application.order;

public class OrderService4 {
    private InventoryService2 inventoryService2;

    public void placeOrder() {
        inventoryService2.process();
    }
}
