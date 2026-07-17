package visits.infrastructure;

import visits.domain.Customer;

public interface CustomerJpaRepository extends JpaRepository<Customer, Long> {
}
