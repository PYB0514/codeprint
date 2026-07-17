package visits.infrastructure;

import visits.domain.Visit;

public interface VisitJpaRepository extends JpaRepository<Visit, Long> {
}
