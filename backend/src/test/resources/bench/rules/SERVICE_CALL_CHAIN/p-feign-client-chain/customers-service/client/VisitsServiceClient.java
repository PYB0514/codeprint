package client;

@FeignClient(name = "visits-service")
public interface VisitsServiceClient {
    Object getVisits(int ownerId);
}
