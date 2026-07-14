package application.order;

public class OrderService2 {
    private InventoryRepo inventoryRepo;

    public void placeOrder() {
        inventoryRepo.save();
    }
}
