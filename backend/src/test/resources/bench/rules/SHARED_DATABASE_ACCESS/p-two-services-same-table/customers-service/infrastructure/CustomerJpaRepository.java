package customers.infrastructure;

import customers.domain.Customer;

public interface CustomerJpaRepository extends JpaRepository<Customer, Long> {
}
