package application.order;

public class OrderService3 {
    private InventoryPort inventoryPort;

    public void placeOrder() {
        inventoryPort.reserve();
    }
}
