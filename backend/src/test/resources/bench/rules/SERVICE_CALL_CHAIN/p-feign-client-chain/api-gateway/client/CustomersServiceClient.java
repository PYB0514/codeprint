package client;

@FeignClient(name = "customers-service")
public interface CustomersServiceClient {
    Object getOwner(int id);
}
